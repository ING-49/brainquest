"""
脑力大冒险 · 联机对战服务器（好友码房间制 + 快速匹配 + ELO + 云存档，WebSocket）

协议（JSON，v1.6.9 服务器中立对战）：
  客户端 → 服务器：
    {"t":"create","name":"昵称","subject":"科目"}    → 创建好友房（不计分；科目=出题与排行榜桶）
    {"t":"join","code":"123456","name":"昵称"}       → 加入好友房（不计分）
    {"t":"quick_match","name":"昵称","subject":"混合"} → 快速匹配队列（同版本才配对，计分；科目=先入队方）
    {"t":"cancel_match"}                            → 退出匹配队列
    {"t":"online"}                                  → 查询在线人数
    {"t":"leaderboard","name":"昵称","subject":"混合"} → 查询某科目排行榜（Top10 + 我的排名）
    {"t":"ready"}                                   → 准备就绪；双方就绪后服务器自动抽题下发
    {"t":"answer","idx":0,"choice":2,"timeMs":1234} → 上报所选选项，服务器按题库判分并累计（客户端不再自报对错）
    {"t":"finish","timeMs":9876}                    → 交卷（判分以服务器累计为准，timeMs 用于平局比快）
    {"t":"save_put","code":"XXXX","owner":"身份码","data":"<BQENC1信封>"} → 上传云存档（加密+归属绑定）
    {"t":"save_get","code":"XXXX","owner":"身份码","new_owner":"新身份码"} → 下载云存档（归属校验/换机转移；60s 限 6 次）
    {"t":"save_del","code":"XXXX","owner":"身份码"}  → 删除云端存档（需归属身份）
    {"t":"chat","text":"..."}                       （预留）
  服务器 → 客户端：
    {"t":"created","code":"123456"}
    {"t":"joined","code":"123456","peer":"对方昵称"}
    {"t":"peer_joined","peer":"对方昵称"}
    {"t":"online","players":N,"waiting":K,"rooms":M}   （广播：在线/等待匹配/房间数）
    {"t":"leaderboard","top":[{name,rating,wins,losses,games}],"me":{...rank}|null}
    {"t":"save_ok","size":N} / {"t":"save_data","data":"...","ts":...} / {"t":"save_del_ok"}
    {"t":"start","questions":[...]}                 （双方就绪后服务器抽题，同发双方）
    {"t":"peer_answer","idx":0,"correct":true}      （服务器判分结果转发给对手）
    {"t":"peer_finish","correct":3,"timeMs":9876}
    {"t":"result","outcome":"win|lose|draw","my":{...},"peer":{...},
                   "rating":{"ranked":true,"my":1032,"delta":+18,"peer":976}}   计分局附积分
    {"t":"peer_left"}
    {"t":"error","msg":"..."}

v1.6.9 起远程对战服务器中立：题目由服务器从 /opt/pk/questions 题库抽取（口算/逻辑程序生成），
对错由服务器按题库答案判定，结算/ELO 用服务器累计分；客户端发来的 start（旧版房主发题）被忽略。
局域网/热点对战走 App 内嵌服务器（EmbeddedPkServer），保持房主出题模式。
云存档三重防护：存档码（查找）+ 身份码（归属校验，换机需原身份码转移）+ 口令（AES-GCM 加密）。

用法：python pk_server.py [端口，默认 8765]
数据目录：环境变量 PK_DATA_DIR，默认 /opt/pk（不存在则用脚本所在目录）
"""
import asyncio
import json
import os
import random
import re
import sys
import time

import websockets

DATA_DIR = os.environ.get("PK_DATA_DIR") or ("/opt/pk" if os.path.isdir("/opt/pk") else os.path.dirname(os.path.abspath(__file__)))
RATINGS_FILE = os.path.join(DATA_DIR, "ratings.json")
SAVES_DIR = os.path.join(DATA_DIR, "saves")
OWNERS_FILE = os.path.join(DATA_DIR, "save_owners.json")

K_FACTOR = 32          # ELO K 值
DEFAULT_RATING = 1000  # 初始积分
DEFAULT_SUBJECT = "混合"  # 排行榜默认科目（房主未选科目时）
PK_QUESTION_COUNT = 10  # 对战题数

SAVE_ENVELOPE_FMT = "BQENC1"   # App 端 SaveCrypto 产出的信封格式（服务器当不透明字符串存，仅校验结构）
SAVE_MAX_BYTES = 256 * 1024    # 单个云存档上限
SAVE_GET_LIMIT = 6             # save_get 限流：每连接滑动窗口内最多次数（防暴力试码）
SAVE_GET_WINDOW_S = 60

rooms = {}   # code -> {"host","guest","names","versions","ranked","subject","ready":set,"questions":list,"score":{ws:int},"finish":{ws:timeMs}}
online = set()   # 当前所有连接
queue = []   # 快速匹配等待队列：[{"ws","name","version","subject"}, ...]
ratings = {}  # 科目 -> {name -> {"r": 1000, "w": 0, "l": 0, "g": 0}}（排行榜按科目分桶）
save_get_hist = {}  # ws -> [time.time(), ...]（save_get 滑动窗口限流）
save_owners = {}  # 存档码 -> 身份码（云存档归属；无记录 = 旧存档，首次操作时认领）


# ---------- 题库（服务器中立对战：服务器出题 + 判分，v1.6.9） ----------

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
QUESTION_DIRS = [d for d in (
    os.environ.get("PK_QUESTION_DIR"),
    os.path.join(DATA_DIR, "questions"),                      # 线上：deploy 脚本上传
    os.path.join(_SCRIPT_DIR, "questions"),                   # 备选
    os.path.join(_SCRIPT_DIR, "..", "app", "src", "main", "assets", "questions"),  # 仓库开发环境
    os.path.join(_SCRIPT_DIR, "..", "update-server", "packs", "src"),
    os.path.join(_SCRIPT_DIR, "packs", "src"),
) if d and os.path.isdir(d)]

BANK = {}  # id -> 题目 dict


def load_bank():
    global BANK
    bank = {}
    for d in QUESTION_DIRS:
        for fn in sorted(os.listdir(d)):
            if not fn.endswith(".json"):
                continue
            try:
                with open(os.path.join(d, fn), encoding="utf-8") as f:
                    data = json.load(f)
            except (OSError, ValueError):
                continue
            for q in data.get("questions", []):
                if isinstance(q, dict) and q.get("id") and isinstance(q.get("answer"), int):
                    q.setdefault("type", "single")
                    bank[q["id"]] = q
    BANK = bank
    print(f"[bank] 题库加载 {len(bank)} 题（来源 {len(QUESTION_DIRS)} 个目录）", flush=True)


def _gen_options(ans_int, rng, spread=3):
    """仿 App MathGenerator.buildOptions：正确项 + 3 个干扰项并打乱，返回 (options, answer_idx)"""
    pool = set()
    delta = 1
    while len(pool) < 6 and delta < 50:
        pool.add(ans_int + delta)
        pool.add(ans_int - delta)
        delta += rng.choice([spread, 1])
    if ans_int > 10:
        pool.add(ans_int * 2)
        pool.add(ans_int // 2)
    wrongs = [v for v in pool if v != ans_int and v >= 0]
    rng.shuffle(wrongs)
    wrongs = wrongs[:3]
    pad = 1
    while len(wrongs) < 3:
        wrongs.append(ans_int + 100 * pad)
        pad += 1
    all_opts = wrongs + [ans_int]
    rng.shuffle(all_opts)
    return [str(v) for v in all_opts], all_opts.index(ans_int)


def _gen_math(d, rng):
    """口算生成器（移植 App MathGenerator，仅选择题）"""
    if d == 1:
        k = rng.randrange(3)
        if k == 0:
            a, b = rng.randint(2, 19), rng.randint(1, 19); text, ans = f"{a} + {b}", a + b
        elif k == 1:
            a, b = rng.randint(10, 29), rng.randint(1, 29); text, ans = f"{a} − {b}", a - b
        else:
            a, b = rng.randint(2, 9), rng.randint(2, 9); text, ans = f"{a} × {b}", a * b
    elif d == 2:
        k = rng.randrange(3)
        if k == 0:
            a, b, c = rng.randint(2, 11), rng.randint(2, 9), rng.randint(1, 29); text, ans = f"{a} × {b} + {c}", a * b + c
        elif k == 1:
            b, q = rng.randint(2, 9), rng.randint(2, 11); text, ans = f"{b * q} ÷ {b}", q
        else:
            a, b, c = rng.randint(20, 79), rng.randint(10, 49), rng.randint(10, 39); text, ans = f"{a} + {b} − {c}", a + b - c
    elif d == 3:
        k = rng.randrange(3)
        if k == 0:
            a, b, c = rng.randint(2, 11), rng.randint(1, 11), rng.randint(2, 8); text, ans = f"({a} + {b}) × {c}", (a + b) * c
        elif k == 1:
            a, b, c, dd = rng.randint(3, 11), rng.randint(3, 9), rng.randint(2, 8), rng.randint(2, 8); text, ans = f"{a} × {b} − {c} × {dd}", a * b - c * dd
        else:
            x, a, b = rng.randint(3, 19), rng.randint(2, 29), rng.randint(20, 79); text, ans = f"x + {a} = {b}，x = ?", b - a
    elif d == 4:
        k = rng.randrange(3)
        if k == 0:
            a, b = rng.randint(6, 15), rng.randint(2, 14); text, ans = f"({a} − {b}) × {b + 1}", (a - b) * (b + 1)
        elif k == 1:
            x, a, b = rng.randint(3, 14), rng.randint(3, 14), rng.randint(2, 39); text, ans = f"x × {a} + {b} = {x * a + b}，x = ?", x
        else:
            a = rng.randint(4, 13); text, ans = f"{a}²", a * a
    else:
        k = rng.randrange(4)
        if k == 0:
            a, b = rng.randint(5, 15), rng.randint(3, 11); text, ans = f"{a}² − {b}²", a * a - b * b
        elif k == 1:
            n = rng.randint(1, 8); text, ans = f"2^{n}", 2 ** n
        elif k == 2:
            s = rng.randint(4, 15); text, ans = f"√{s * s}", s
        else:
            pct = rng.choice([10, 20, 25, 50, 75]); base = rng.randint(2, 19) * 20; text, ans = f"{pct}% of {base}", base * pct // 100
    opts, idx = _gen_options(ans, rng)
    return {"id": f"gen_m_{time.time_ns()}_{rng.randrange(9999)}", "subject": "数学口算", "difficulty": d,
            "type": "single", "question": f"{text} = ?", "options": opts, "answer": idx,
            "explanation": f"{text} = {ans}", "tags": ["口算"]}


def _gen_logic(d, rng):
    """找规律生成器（移植 App MathGenerator）"""
    if d == 1:
        a, step = rng.randint(1, 9), rng.randint(2, 5)
        seq = [a + i * step for i in range(4)]; ans = a + 4 * step; explain = f"等差数列，公差 {step}"
    elif d == 2:
        a, r = rng.randint(1, 3), rng.randint(2, 3)
        seq = [a * (2 ** (i * r)) for i in range(4)]; ans = a * (2 ** (4 * r)); explain = f"等比数列，公比 {2 ** r}"
    elif d == 3:
        a, p, q = rng.randint(2, 14), rng.randint(2, 4), rng.randint(1, 3)
        seq = [a, a + p, a + p + q, a + 2 * p + q]; ans = a + 2 * p + 2 * q; explain = f"隔项看：奇数位差 {p}，偶数位差 {q}"
    elif d == 4:
        base = rng.randint(1, 5)
        seq = [(i + base) ** 2 for i in range(4)]; ans = (base + 4) ** 2; explain = f"完全平方数列：{base}² 起步"
    else:
        a, d1 = rng.randint(1, 7), rng.randint(2, 4)
        seq, cur, gap = [a], a, d1
        for _ in range(3):
            cur += gap; gap += 1; seq.append(cur)
        ans = cur + gap; explain = f"相邻差是 {d1}, {d1 + 1}, {d1 + 2}, {d1 + 3}…（二阶等差）"
    opts, idx = _gen_options(ans, rng, spread=max(2, int(ans * 0.2)))
    return {"id": f"gen_l_{time.time_ns()}_{rng.randrange(9999)}", "subject": "逻辑推理", "difficulty": d,
            "type": "single", "question": f"找规律：{'，'.join(str(v) for v in seq)}，下一项是？", "options": opts,
            "answer": idx, "explanation": f"规律：{explain}，下一项为 {ans}", "tags": ["找规律"]}


def draw_questions(subject, n=PK_QUESTION_COUNT):
    """按科目抽 n 题（远程对战服务器出题）；口算/逻辑程序生成，其余走题库，池不足从全科补"""
    rng = random.Random()
    if subject == "数学口算":
        return [_gen_math(2 + i % 3, rng) for i in range(n)]
    if subject == "逻辑推理":
        return [_gen_logic(2 + i % 3, rng) for i in range(n)]
    pool = [q for q in BANK.values() if q.get("subject") == subject and q.get("type") != "fill"]
    if len(pool) < n:
        pool += [q for q in BANK.values() if q.get("subject") != subject and q.get("type") != "fill"]
    if len(pool) >= n:
        return random.sample(pool, n)
    return [_gen_math(2 + i % 3, rng) for i in range(n)]  # 兜底


def is_save_envelope(data):
    """校验 data 是否为 BQENC1 加密信封（不验证密码学内容，App 端解密时自校验）"""
    try:
        obj = json.loads(data)
    except (json.JSONDecodeError, ValueError):
        return False
    return (isinstance(obj, dict) and obj.get("fmt") == SAVE_ENVELOPE_FMT
            and isinstance(obj.get("salt"), str) and obj.get("salt")
            and isinstance(obj.get("iv"), str) and obj.get("iv")
            and isinstance(obj.get("ct"), str) and obj.get("ct"))


# ---------- 积分持久化（快速匹配 ELO，按科目分桶，仅计分局更新） ----------

def load_ratings():
    global ratings
    try:
        with open(RATINGS_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        # 兼容 v1.6.3 的旧扁平结构（name -> 积分）→ 迁移进「混合」桶
        if data and all(isinstance(v, dict) and "r" in v for v in data.values()):
            data = {DEFAULT_SUBJECT: data}
        ratings = data
        n = sum(len(v) for v in ratings.values())
        print(f"[elo] 已载入 {len(ratings)} 个科目桶 / {n} 名玩家积分")
    except Exception:
        ratings = {}


def save_ratings():
    try:
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(RATINGS_FILE, "w", encoding="utf-8") as f:
            json.dump(ratings, f, ensure_ascii=False)
    except Exception as e:
        print(f"[elo] 积分保存失败: {e}")


def rating_of(subject, name):
    return ratings.setdefault(subject, {}).setdefault(
        name, {"r": DEFAULT_RATING, "w": 0, "l": 0, "g": 0})


# ---------- 云存档归属（v1.6.9：存档码 ↔ 身份码绑定，防猜码盗档/覆盖） ----------

def load_owners():
    global save_owners
    try:
        with open(OWNERS_FILE, "r", encoding="utf-8") as f:
            save_owners = json.load(f)
        print(f"[save] 已载入 {len(save_owners)} 条存档归属", flush=True)
    except Exception:
        save_owners = {}


def save_owners_to_disk():
    try:
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(OWNERS_FILE, "w", encoding="utf-8") as f:
            json.dump(save_owners, f, ensure_ascii=False)
    except Exception as e:
        print(f"[save] 归属保存失败: {e}")


def apply_elo(subject, host_name, guest_name, host_score):
    """host_score: 1 胜 / 0.5 平 / 0 负；返回 (host_delta, host_rating, guest_rating)"""
    h, g = rating_of(subject, host_name), rating_of(subject, guest_name)
    exp_h = 1 / (1 + 10 ** ((g["r"] - h["r"]) / 400))
    delta = round(K_FACTOR * (host_score - exp_h))
    h["r"] += delta
    g["r"] -= delta
    h["g"] += 1
    g["g"] += 1
    if host_score == 1:
        h["w"] += 1; g["l"] += 1
    elif host_score == 0:
        h["l"] += 1; g["w"] += 1
    save_ratings()
    return delta, h["r"], g["r"]


def rank_of(subject, name):
    order = sorted(ratings.get(subject, {}).items(), key=lambda kv: -kv[1]["r"])
    for i, (n, _) in enumerate(order):
        if n == name:
            return i + 1
    return 0


# ---------- 基础工具 ----------

def send(ws, obj):
    async def _do():
        try:
            await ws.send(json.dumps(obj, ensure_ascii=False))
        except Exception:
            pass
    asyncio.ensure_future(_do())


def presence():
    return {"t": "online", "players": len(online),
            "waiting": len(queue), "rooms": len(rooms)}


def broadcast_presence():
    snap = presence()
    for ws in list(online):
        send(ws, snap)


def new_code():
    c = f"{random.randint(0, 999999):06d}"
    while c in rooms:
        c = f"{random.randint(0, 999999):06d}"
    return c


def enqueue(ws, name, version, subject):
    if any(e["ws"] == ws for e in queue):
        return
    queue.append({"ws": ws, "name": name, "version": version, "subject": subject})
    print(f"[match] {name} v{version} 科目[{subject}] 入队（等待 {len(queue)}）")


def dequeue(ws):
    before = len(queue)
    queue[:] = [e for e in queue if e["ws"] != ws]
    return len(queue) != before


def try_match():
    """队列里找版本号相同的两人配对建房；成功返回 True"""
    for i in range(len(queue)):
        for j in range(i + 1, len(queue)):
            a, b = queue[i], queue[j]
            if a["version"] == b["version"]:
                queue.pop(j)
                queue.pop(i)
                pair(a, b)
                return True
    return False


def pair(a, b):
    code = new_code()
    rooms[code] = {"host": a["ws"], "guest": b["ws"],
                   "names": {a["ws"]: a["name"], b["ws"]: b["name"]},
                   "versions": {a["ws"]: a["version"], b["ws"]: b["version"]},
                   "finish": {}, "score": {}, "ready": set(), "questions": None,
                   "ranked": True,          # 快速匹配 = 计分局
                   "subject": a.get("subject") or DEFAULT_SUBJECT}  # 甲方选定科目 = 计分桶与出题科目
    # 甲方走房主路径：created → peer_joined（收到后本地选题并发 start）
    send(a["ws"], {"t": "created", "code": code})
    send(a["ws"], {"t": "peer_joined", "peer": b["name"], "version": b["version"]})
    # 乙方走加入方路径
    send(b["ws"], {"t": "joined", "code": code, "peer": a["name"],
                   "peer_version": a["version"]})
    print(f"[match] {code} 配对成功: {a['name']} vs {b['name']}（计分 · 科目[{rooms[code]['subject']}]）")


def peer_of(code, ws):
    room = rooms.get(code)
    if not room:
        return None
    return room["guest"] if room["host"] == ws else room["host"]


def try_result(code):
    """结算：对错计数用服务器判分累计（room["score"]，防客户端虚报），timeMs 用客户端交卷值"""
    room = rooms.get(code)
    if not room or len(room["finish"]) < 2:
        return
    host, guest = room["host"], room["guest"]
    hc, gc = room.get("score", {}).get(host, 0), room.get("score", {}).get(guest, 0)
    ht, gt = room["finish"][host], room["finish"][guest]
    # 判定（先算双方 outcome，再按需结算 ELO）
    if hc != gc:
        host_score = 1.0 if hc > gc else 0.0
    elif ht != gt:
        host_score = 1.0 if ht < gt else 0.0
    else:
        host_score = 0.5
    rating = None
    if room.get("ranked"):
        h_name = room["names"].get(host, "玩家")
        g_name = room["names"].get(guest, "玩家")
        subject = room.get("subject") or DEFAULT_SUBJECT
        delta, h_r, g_r = apply_elo(subject, h_name, g_name, host_score)
        rating = {
            host: {"ranked": True, "my": h_r, "delta": delta, "peer": g_r},
            guest: {"ranked": True, "my": g_r, "delta": -delta, "peer": h_r},
        }
        print(f"[elo] [{subject}] {h_name}({h_r}) vs {g_name}({g_r}) delta={delta:+d}")
    for ws, my, other in (
        (host, (hc, ht), (gc, gt)),
        (guest, (gc, gt), (hc, ht)),
    ):
        if my[0] != other[0]:
            outcome = "win" if my[0] > other[0] else "lose"
        elif my[1] != other[1]:
            outcome = "win" if my[1] < other[1] else "lose"
        else:
            outcome = "draw"
        msg = {"t": "result", "outcome": outcome,
               "my": {"correct": my[0], "timeMs": my[1]},
               "peer": {"correct": other[0], "timeMs": other[1]}}
        if rating:
            msg["rating"] = rating[ws]
        else:
            msg["rating"] = {"ranked": False}
        send(ws, msg)
    rooms.pop(code, None)  # 一局结束，房间关闭


async def handler(ws):
    online.add(ws)
    broadcast_presence()
    try:
        async for raw in ws:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            t = msg.get("t")
            if t == "create":
                code = new_code()
                rooms[code] = {"host": ws, "guest": None,
                               "names": {ws: msg.get("name", "玩家")},
                               "versions": {ws: msg.get("version", "?")}, "finish": {},
                               "score": {}, "ready": set(), "questions": None,
                               "subject": str(msg.get("subject") or DEFAULT_SUBJECT),
                               "ranked": False}   # 好友房间不计分
                send(ws, {"t": "created", "code": code})
                broadcast_presence()
                print(f"[room] {code} created by {msg.get('name')} 科目[{rooms[code]['subject']}]", flush=True)

            elif t == "join":
                c = str(msg.get("code", ""))
                room = rooms.get(c)
                if not room or room["guest"] is not None:
                    send(ws, {"t": "error", "msg": "房间不存在或已满"})
                    continue
                room["guest"] = ws
                room["names"][ws] = msg.get("name", "玩家")
                room["versions"] = room.get("versions", {})
                room["versions"][ws] = msg.get("version", "?")
                send(ws, {"t": "joined", "code": c, "peer": room["names"][room["host"]],
                          "peer_version": room["versions"].get(room["host"], "?")})
                send(room["host"], {"t": "peer_joined", "peer": room["names"][ws],
                                    "version": room["versions"].get(ws, "?")})
                broadcast_presence()
                print(f"[room] {c} joined by {msg.get('name')}")

            elif t == "quick_match":
                enqueue(ws, msg.get("name", "玩家"), msg.get("version", "?"),
                        str(msg.get("subject") or DEFAULT_SUBJECT))
                if not try_match():
                    broadcast_presence()

            elif t == "cancel_match":
                if dequeue(ws):
                    broadcast_presence()

            elif t == "online":
                send(ws, presence())

            elif t == "leaderboard":
                name = msg.get("name", "")
                subject = str(msg.get("subject") or DEFAULT_SUBJECT)
                table = ratings.get(subject, {})
                top = sorted(table.items(), key=lambda kv: -kv[1]["r"])[:10]
                me = None
                if name in table:
                    v = table[name]
                    me = {"name": name, "rating": v["r"], "wins": v["w"],
                          "losses": v["l"], "games": v["g"], "rank": rank_of(subject, name)}
                send(ws, {"t": "leaderboard", "subject": subject,
                          "top": [{"name": n, "rating": v["r"], "wins": v["w"],
                                   "losses": v["l"], "games": v["g"]} for n, v in top],
                          "me": me})

            elif t == "save_put":
                code = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("code", "")))[:24]
                owner = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("owner", "")))[:24]
                data = str(msg.get("data", ""))
                if not code or not owner or not data:
                    send(ws, {"t": "error", "msg": "云存档参数不完整"})
                elif not is_save_envelope(data):
                    # 新上传必须是加密信封：防止客户端回退明文存储（旧明文文件仍可下载，直至被覆盖）
                    send(ws, {"t": "error", "msg": "存档须为加密格式，请更新 App 后再上传"})
                elif len(data.encode("utf-8")) > SAVE_MAX_BYTES:
                    send(ws, {"t": "error", "msg": "存档过大"})
                elif save_owners.get(code, owner) != owner:
                    # 归属校验：别人无法覆盖你的存档（即使猜中存档码）
                    send(ws, {"t": "error", "msg": "该存档码已绑定其他身份，无法覆盖"})
                else:
                    os.makedirs(SAVES_DIR, exist_ok=True)
                    with open(os.path.join(SAVES_DIR, code + ".json"), "w", encoding="utf-8") as f:
                        f.write(data)
                    if save_owners.get(code) != owner:
                        save_owners[code] = owner
                        save_owners_to_disk()
                    send(ws, {"t": "save_ok", "size": len(data.encode("utf-8"))})
                    print(f"[save] {code} 上传 {len(data)} 字符（加密信封，归属 {owner}）", flush=True)

            elif t == "save_get":
                now = time.time()
                hist = [ts for ts in save_get_hist.get(ws, []) if now - ts < SAVE_GET_WINDOW_S]
                if len(hist) >= SAVE_GET_LIMIT:
                    save_get_hist[ws] = hist
                    send(ws, {"t": "error", "msg": "操作过于频繁，请稍后再试"})
                    continue
                hist.append(now)
                save_get_hist[ws] = hist
                code = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("code", "")))[:24]
                owner = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("owner", "")))[:24]
                new_owner = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("new_owner", "") or owner))[:24]
                path = os.path.join(SAVES_DIR, code + ".json")
                stored = save_owners.get(code)
                if not code or not owner:
                    send(ws, {"t": "error", "msg": "云存档参数不完整"})
                elif stored and stored != owner:
                    # 归属校验：换设备需输入原设备身份码
                    send(ws, {"t": "error", "msg": "身份不符：请输入原设备的身份码"})
                elif code and os.path.isfile(path):
                    with open(path, "r", encoding="utf-8") as f:
                        data = f.read()
                    if stored != new_owner:
                        # 归属转移（换设备恢复）或认领（旧存档首次）
                        save_owners[code] = new_owner
                        save_owners_to_disk()
                    send(ws, {"t": "save_data", "data": data, "ts": int(os.path.getmtime(path) * 1000)})
                    print(f"[save] {code} 下载 {len(data)} 字符（归属 {new_owner}）", flush=True)
                else:
                    send(ws, {"t": "error", "msg": "云存档不存在，请先在上传过存档的设备上上传"})

            elif t == "save_del":
                code = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("code", "")))[:24]
                owner = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("owner", "")))[:24]
                path = os.path.join(SAVES_DIR, code + ".json")
                stored = save_owners.get(code)
                if code and os.path.isfile(path) and stored in (None, owner):
                    os.remove(path)
                    save_owners.pop(code, None)
                    save_owners_to_disk()
                    send(ws, {"t": "save_del_ok"})
                    print(f"[save] {code} 已删除", flush=True)
                elif stored not in (None, owner):
                    send(ws, {"t": "error", "msg": "身份不符：无法删除他人存档"})
                else:
                    send(ws, {"t": "error", "msg": "云存档不存在"})

            elif t in ("answer", "ready", "finish", "start"):
                # 在自己所在房间内处理（快速匹配与 create/join 统一按成员关系查）
                target = None
                for c, room in rooms.items():
                    if ws in (room["host"], room["guest"]):
                        target = c
                        break
                if not target:
                    continue
                room = rooms[target]
                peer = peer_of(target, ws)

                if t == "ready":
                    # 双方就绪 → 服务器抽题并同发给双方（v1.6.9：远程不再由房主发题）
                    room.setdefault("ready", set()).add(ws)
                    if peer:
                        send(peer, {"t": "peer_ready"})
                    if (room.get("questions") is None and room.get("guest") is not None
                            and len(room.get("ready", ())) >= 2):
                        subject = room.get("subject") or DEFAULT_SUBJECT
                        room["questions"] = draw_questions(subject)
                        room["score"] = {room["host"]: 0, room["guest"]: 0}
                        start_msg = {"t": "start", "questions": room["questions"]}
                        send(room["host"], start_msg)
                        send(room["guest"], start_msg)
                        print(f"[match] {target} 服务器出题 {len(room['questions'])} 题 科目[{subject}]", flush=True)

                elif t == "start":
                    # 兼容旧客户端的房主发题消息：远程模式已改为服务器出题，忽略
                    print(f"[match] {target} 收到旧版 start，已忽略（等待双方 ready）", flush=True)

                elif t == "answer":
                    # 服务器判分：choice 与题库答案比对，累计到 room["score"]
                    idx = msg.get("idx", -1)
                    qs = room.get("questions") or []
                    correct = False
                    if isinstance(idx, int) and 0 <= idx < len(qs):
                        correct = (msg.get("choice") == qs[idx]["answer"])
                    room.setdefault("score", {room["host"]: 0, room["guest"]: 0})
                    room["score"][ws] = room["score"].get(ws, 0) + (1 if correct else 0)
                    if peer:
                        send(peer, {"t": "peer_answer", "idx": idx,
                                    "correct": correct, "timeMs": msg.get("timeMs", 0)})

                elif t == "finish":
                    room["finish"][ws] = msg.get("timeMs", 0)
                    if peer:
                        send(peer, {"t": "peer_finish", "correct": room.get("score", {}).get(peer, 0),
                                    "timeMs": msg.get("timeMs", 0)})
                    try_result(target)

            elif t == "ping":
                send(ws, {"t": "pong"})
    except websockets.ConnectionClosed:
        pass
    finally:
        online.discard(ws)
        save_get_hist.pop(ws, None)
        dequeue(ws)
        # 统一按成员关系清理房间（覆盖快速匹配/create/join 三种来源）
        for c in [c for c, room in rooms.items() if ws in (room["host"], room["guest"])]:
            room = rooms[c]
            peer = room["guest"] if room["host"] == ws else room["host"]
            if peer:
                send(peer, {"t": "peer_left"})
            room["names"].pop(ws, None)
            if room["host"] == ws:
                rooms.pop(c, None)
            elif room["guest"] == ws:
                room["guest"] = None
            print(f"[room] {c} member left")
        broadcast_presence()


async def main(port):
    load_ratings()
    load_owners()
    load_bank()
    async with websockets.serve(handler, "0.0.0.0", port, ping_interval=20):
        print(f"PK 服务器已启动: ws://0.0.0.0:{port}（数据目录 {DATA_DIR}）")
        print("模拟器连接: ws://10.0.2.2:%d | 手机(同Wi-Fi): ws://<电脑IP>:%d" % (port, port))
        await asyncio.Future()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    asyncio.run(main(port))
