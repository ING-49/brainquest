from collections import deque
import random
COLS, ROWS, EXIT = 4, 5, (3, 1)

def free_on(blocks, ignore, r, c, w, h):
    if r < 0 or c < 0 or r + h > ROWS or c + w > COLS: return False
    for k, o in enumerate(blocks):
        if k == ignore: continue
        if r < o[1] + o[4] and o[1] < r + h and c < o[2] + o[3] and o[2] < c + w: return False
    return True

def norm(blocks):
    cao = None
    groups = {}
    for b in blocks:
        if b[0] == "曹操":
            cao = (b[1], b[2])
        else:
            groups.setdefault((b[3], b[4]), []).append((b[1], b[2]))
    return (cao, tuple(sorted((wh, tuple(sorted(pos))) for wh, pos in groups.items())))

def bfs(defs):
    blocks = [[n, r, c, w, h] for (n, r, c, w, h) in defs]
    if blocks[0][1] == EXIT[0] and blocks[0][2] == EXIT[1]: return 0
    seen = {norm(blocks)}
    q = deque([(blocks, 0)])
    while q:
        cur, d = q.popleft()
        for i in range(len(cur)):
            n, r, c, w, h = cur[i]
            for dr, dc in ((1,0),(-1,0),(0,1),(0,-1)):
                if not free_on(cur, i, r+dr, c+dc, w, h): continue
                nxt = [list(x) for x in cur]
                nxt[i][1] += dr; nxt[i][2] += dc
                k = norm(nxt)
                if k in seen: continue
                if nxt[0][1] == EXIT[0] and nxt[0][2] == EXIT[1]: return d + 1
                seen.add(k); q.append((nxt, d + 1))
    return None

def scramble(defs, steps, seed):
    rng = random.Random(seed)
    blocks = [[n, r, c, w, h] for (n, r, c, w, h) in defs]
    for _ in range(steps):
        moves = []
        for i in range(len(blocks)):
            n, r, c, w, h = blocks[i]
            for dr, dc in ((1,0),(-1,0),(0,1),(0,-1)):
                if free_on(blocks, i, r+dr, c+dc, w, h): moves.append((i, dr, dc))
        if moves:
            i, dr, dc = rng.choice(moves)
            blocks[i][1] += dr; blocks[i][2] += dc
    return [tuple(b) for b in blocks]

jiangyong = [("曹操",1,1,2,2),("张飞",0,1,1,1),("赵云",0,2,1,1),("马超",0,0,1,2),("黄忠",0,3,1,2),
             ("关羽",3,1,2,1),("卒",3,0,1,1),("卒",3,3,1,1),("卒",4,1,1,1),("卒",4,2,1,1)]
hengdao = [("曹操",0,1,2,2),("张飞",0,0,1,2),("赵云",0,3,1,2),("马超",2,0,1,2),("黄忠",2,3,1,2),
           ("关羽",2,1,2,1),("卒",3,1,1,1),("卒",3,2,1,1),("卒",4,0,1,1),("卒",4,3,1,1)]

print("自检 将拥曹营:", bfs(list(jiangyong)), "(应 24)", flush=True)
for name, base, steps, seed in (("重整旗鼓", jiangyong, 14, 42), ("固若金汤", hengdao, 30, 7)):
    s = scramble(base, steps, seed)
    par = bfs(list(s))
    print(name, "par =", par, flush=True)
    print("    " + ", ".join(f'("{n}",{r},{c},{w},{h})' for (n,r,c,w,h) in s), flush=True)
