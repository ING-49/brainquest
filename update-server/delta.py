"""
自研增量差分编码 BQDELTA1（numpy 加速版）
格式：
  magic "BQDELTA1" (8 字节)
  varint oldSize, varint newSize
  操作流：0x00 varint(len) data | 0x01 varint(oldOff) varint(len)
"""
import hashlib
import time

import numpy as np

WINDOW = 16
MAGIC = b"BQDELTA1"
MOD = 65521


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def read_varint(data: bytes, pos: int):
    shift = 0
    result = 0
    while True:
        b = data[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            return result, pos
        shift += 7


def window_hashes(data: bytes) -> tuple:
    """为 data 每个偏移计算 16 字节窗口的 adler32 → (hashes uint32 数组)"""
    arr = np.frombuffer(data, dtype=np.uint8).astype(np.int64)
    n = len(data) - WINDOW + 1
    cs = np.concatenate(([0], np.cumsum(arr)))
    s = cs[WINDOW:] - cs[:-WINDOW]          # S_i
    k = np.arange(len(data), dtype=np.int64)
    kd = k * arr
    csk = np.concatenate(([0], np.cumsum(kd)))
    u = csk[WINDOW:] - csk[:-WINDOW]        # U_i = sum k*d_k
    t = u - np.arange(n, dtype=np.int64) * s  # T_i
    b = (WINDOW + WINDOW * s - t) % MOD
    a = (1 + s) % MOD
    h = ((b << 16) | a).astype(np.uint32)
    return h[:n]


def delta_bytes(old: bytes, new: bytes, verbose=True) -> bytes:
    t0 = time.time()
    old_h = window_hashes(old)
    order = np.argsort(old_h, kind="stable")
    sorted_h = old_h[order]
    sorted_o = order.astype(np.uint32)
    del old_h, order
    if verbose:
        print(f"[delta] 旧文件索引完成 {len(sorted_h)} 项 {time.time()-t0:.1f}s", flush=True)

    new_h = window_hashes(new)
    old_arr = np.frombuffer(old, dtype=np.uint8)
    new_arr = np.frombuffer(new, dtype=np.uint8)

    ops = bytearray()
    ops += MAGIC
    ops += varint(len(old))
    ops += varint(len(new))

    def search(hash_val: int) -> np.ndarray:
        lo = np.searchsorted(sorted_h, hash_val, side="left")
        hi = np.searchsorted(sorted_h, hash_val, side="right")
        return sorted_o[lo:hi]

    def extend(i: int, off: int) -> int:
        """从 (i,off)+WINDOW 起向后扩展，返回总匹配长"""
        l = WINDOW
        step = 4096
        n_rem = len(new) - (i + l)
        o_rem = len(old) - (off + l)
        lim = min(n_rem, o_rem)
        while lim > 0:
            take = min(step, lim)
            a = new_arr[i + l:i + l + take]
            b = old_arr[off + l:off + l + take]
            neq = np.flatnonzero(a != b)
            if len(neq):
                return l + int(neq[0])
            l += take
            lim -= take
        return l

    i = 0
    n = len(new)
    lit_start = 0
    copied = 0
    # 一次性找出所有"哈希有候选"的位置（向量化），只遍历这些点
    lo_all = np.searchsorted(sorted_h, new_h, side="left")
    size = len(sorted_h)
    valid = (lo_all < size) & (sorted_h[np.minimum(lo_all, size - 1)] == new_h)
    cand_positions = np.flatnonzero(valid)
    if verbose:
        print(f"[delta] 候选匹配点 {len(cand_positions)} 个", flush=True)
    ci = 0
    nc = len(cand_positions)
    last_report = 0
    while i < n:
        rem = n - i
        if rem < WINDOW:
            break
        if verbose and i - last_report >= (2 << 20):
            last_report = i
            print(f"\n[delta] 进度 {i/1e6:.1f}/{n/1e6:.1f}MB 拷贝 {copied/1e6:.1f}MB ci={ci}", flush=True)
        matched = 0
        matched_off = -1
        # 跳到下一个候选点（保留恰好位于当前位置的候选）
        while ci < nc and int(cand_positions[ci]) < i:
            ci += 1
        if ci < nc:
            nxt_cand = int(cand_positions[ci])
        else:
            nxt_cand = n
        if nxt_cand != i:
            i = max(i + 1, nxt_cand)
            continue
        # 验证真实字节：直接用 lo_all 切片取偏移，避免重复二分
        plo = int(lo_all[i])
        for c in sorted_o[plo:plo + 4]:
            off = int(c)
            if old[off:off + WINDOW] == new[i:i + WINDOW]:
                matched = extend(i, off)
                if matched >= WINDOW:
                    matched_off = off
                    break
        if matched >= WINDOW:
            if i > lit_start:
                lit = new[lit_start:i]
                ops.append(0x00)
                ops += varint(len(lit))
                ops += lit
            ops.append(0x01)
            ops += varint(matched_off)
            ops += varint(matched)
            copied += matched
            i += matched
            lit_start = i
        else:
            i += 1  # 该候选验证失败，前进 1 字节
        if verbose and copied and i % (4 << 20) < WINDOW:
            print(f"\r[delta] {i/1e6:.0f}/{n/1e6:.0f}MB 拷贝 {copied/1e6:.0f}MB", end="", flush=True)

    if lit_start < n:
        ops.append(0x00)
        ops += varint(n - lit_start)
        ops += new[lit_start:n]
    if verbose:
        print(f"\n[delta] 完成：COPY {copied/1e6:.1f}MB | 补丁 {len(ops)/1024:.1f}KB | 用时 {time.time()-t0:.1f}s", flush=True)
    return bytes(ops)


def verify(delta: bytes, old: bytes, expect_new_sha: str):
    assert delta[:8] == MAGIC
    pos = 8
    old_size, pos = read_varint(delta, pos)
    new_size, pos = read_varint(delta, pos)
    assert old_size == len(old), "旧文件大小不匹配"
    out = bytearray(new_size)
    w = 0
    while pos < len(delta):
        op = delta[pos]; pos += 1
        if op == 0x00:
            ln, pos = read_varint(delta, pos)
            out[w:w + ln] = delta[pos:pos + ln]
            pos += ln; w += ln
        elif op == 0x01:
            off, pos = read_varint(delta, pos)
            ln, pos = read_varint(delta, pos)
            assert off + ln <= len(old), "COPY 越界"
            out[w:w + ln] = old[off:off + ln]
            w += ln
        else:
            raise ValueError(f"未知操作码 {op}")
    assert w == new_size, "输出不完整"
    got = hashlib.sha256(bytes(out)).hexdigest()
    assert got == expect_new_sha, f"校验失败 {got[:12]} != {expect_new_sha[:12]}"
    print(f"[verify] BQDELTA1 合成结果与新版一致 ✓ ({len(delta)/1024:.1f} KB)")
