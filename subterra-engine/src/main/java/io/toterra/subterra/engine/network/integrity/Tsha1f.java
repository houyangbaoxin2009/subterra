package io.toterra.subterra.engine.network.integrity;

/**
 * TSHA1 v2 快速模型（model f）的纯 JDK 可移植实现（p.2.4.2），state-per-n 通用 W 字压缩核。
 * 逐式镜像 tie-main {@code std/tsha1.tie}（命名空间 {@code tsha}）的 f 模型通用路径：
 * IV/RCON 来自内联 32 位常量表（SEED_F 的 SHA-256 计数器流预计算值）、计数器式块压缩、
 * {@code fin_synth} 终筛与末块折回归链 {@code h}、base-48 最小编码（定长补零）。单线程确定性，
 * 无共享可变状态风险。
 * <p>
 * A pure-JDK portable implementation of the TSHA1 v2 fast model (model f, p.2.4.2) with a
 * state-per-n generic W-word compression core. It mirrors the f-model generic path of
 * tie-main {@code std/tsha1.tie} (namespace {@code tsha}) equation by equation: IV/RCON
 * come from inline 32-bit word tables (the precomputed SEED_F SHA-256 counter stream),
 * counter-mode block compression, a {@code fin_synth} final sweep with the final-block
 * fold-back into the chaining state {@code h}, and minimal base-48 encoding (zero-padded
 * to fixed length). Single-threaded and deterministic, with no shared mutable state.
 *
 * <p>公开 API / Public API：
 * <ul>
 *   <li>{@link #tsha1f(byte[], int, int)} — 快速模型，base ∈ {2,3,8,16,48}（默认 48）。
 *   <li>{@link #digestHex(String, byte[], int)} — 全宽 hex 摘要（强校验锚点；model 仅 f，其它视为 f）。
 * </ul>
 *
 * <p>已核对黄金向量 / Validated against golden vectors (from tie-main probes)：
 * {@code tsha1f(msg,8,48)} 的 ""→"5juavlyl"、```123456789```→"3Kz1piuc"、
 * "abc"→"5FGIxu1J"、a×55/56/63/64/65/1000，以及 {@code tsha1_digest("f","123456789",48)}
 * 全宽 hex 前 64 字符 {@code 260c7340...76bf}，还有 n=48 锚点 "abc"→"g8e3aCs...cwxaa"。
 * @see <a href="https://github.com/">tie-main std/tsha1.tie</a>（只读参考）
 */
public final class Tsha1f {

    /** base48 字符台（索引即值 0..47）。base-48 alphabet, index == value. */
    static final String B48_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL";

    /** 合法位长集合。The allowed bit-length set. */
    private static final int[] VALID_N = {2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 69, 88, 92, 96, 128, 144};

    // ---- 常量表（tsha1.tie ensure_ivf / ensure_rcf 的内联值，32 位/字，大端 hex）----
    /** tsha1f IV（32 字）。tsha1f IV, 32 words. */
    private static final int[] IVF = parseHex32(
            "6f316818201ca2758e20cd45f4c7b538293d6b8de29e1c968f30cc67ad81cc6b"
                    + "0be4be38d3b994622e2ec7d603ac7d3079ce6a3441df35400f4659174d5f2f552"
                    + "6d77d9563cbfc8a547b09e5eb9ae1bc7ca9205c619064ba2cd8ab9e3a603f54"
                    + "3f1f16a3a9044df1956d21362cdfd96d1d518e07e49def028ce433befcd7e551");

    /** tsha1f 轮常量（16 字，位于 IV 32 字之后）。tsha1f round constants, 16 words. */
    private static final int[] RCONF = parseHex32(
            "7b440874437636f66dac446ced74969b9efe93b52a73975f59917ee17503e6d7e"
                    + "e9bfe4844bc2fe32327586f8c4dbff7c7a81e4d91daee663cab77ce54de351c");

    private Tsha1f() {
    }

    /**
     * 把连续 hex 串解析为 32 位字数组（每 8 个 hex 字符一个字，大端）。
     * Parses a contiguous hex string into an array of 32-bit words (8 hex chars per word, BE).
     */
    private static int[] parseHex32(String hex) {
        int words = hex.length() / 8;
        int[] out = new int[words];
        for (int w = 0; w < words; w++) {
            int v = 0;
            for (int k = 0; k < 8; k++) {
                v = (v << 4) | hexNibble(hex.charAt(w * 8 + k));
            }
            out[w] = v;
        }
        return out;
    }

    private static int hexNibble(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return 0;
    }

    // ---- 位平面 / 旋转运算符（与 tie helpers 等价；rotlen 边界防御）----

    /** rr32：32 位值循环右移 n 位（n&31，n=0 时不移动）。Rotate-right 32, n masked to 5 bits. */
    private static int rr32(int v, int n) {
        n &= 31;
        if (n == 0) {
            return v;
        }
        return (v >>> n) | (v << (32 - n));
    }

    /** rl32：32 位值循环左移 n 位。Rotate-left 32. */
    private static int rl32(int v, int n) {
        n &= 31;
        if (n == 0) {
            return v;
        }
        return (v << n) | (v >>> (32 - n));
    }

    /** rrp：循环右移 r & 31。Rotate-right by r&31. */
    private static int rrp(int v, int r) {
        return rr32(v, r);
    }

    /** rrp16：先掩 0xFFFF 再做 r & 15 的循环右移（16-trit 半宽）。Rotate r&15 after masking low 16 bits. */
    private static int rrp16(int v, int r) {
        return rr32(v & 0xFFFF, r & 15);
    }

    /** fold16：高 16 位折叠进低 16 位。Fold the high 16 bits into the low 16. */
    private static int fold16(int v) {
        return (v & 0xFFFF) ^ (v >>> 16);
    }

    /** rotlane：half 为真走 rrp16（含 fold16），否则 rrp。Generic rotation lane. */
    private static int rotlane(int x, int r, boolean half) {
        if (half) {
            return rrp16(fold16(x), r);
        }
        return rrp(x, r);
    }

    // ---- 平衡三进制（balanced base-3 trits）两位位平面运算 ----

    /** tadd2：两位平衡三进制 trit 加法（ma=增值平面, na=减值平面），无进位。 */
    private static int[] tadd2(int ma, int na, int mb, int nb) {
        int nma = ma ^ 0xFFFFFFFF;
        int nna = na ^ 0xFFFFFFFF;
        int nmb = mb ^ 0xFFFFFFFF;
        int nnb = nb ^ 0xFFFFFFFF;
        int aP = ma & nna;
        int aN = ma & na;
        int bP = mb & nnb;
        int bN = mb & nb;
        int oPos = (nma & mb & nnb) | (ma & nna & nmb) | (aN & bN);
        int oNeg = (nma & mb & nb) | (ma & na & nmb) | (aP & bP);
        return new int[]{oPos | oNeg, oNeg};
    }

    /** tmul2：两位平衡三进制 trit 乘法。 */
    private static int[] tmul2(int ma, int na, int mb, int nb) {
        int amp = ma & mb;
        int no = (na ^ nb) & amp;
        return new int[]{amp, no};
    }

    /** quant3：三组位平台 majority 量化（+1 计数 ≥2 则该位为 1，平局→0）。 */
    private static int quant3(int m0, int n0, int m1, int n1, int m2, int n2) {
        int a = m0 & (n0 ^ 0xFFFFFFFF);
        int b = m1 & (n1 ^ 0xFFFFFFFF);
        int c = m2 & (n2 ^ 0xFFFFFFFF);
        return (a & b) | (b & c) | (c & a);
    }

    // ---- 度量 / 调度 ----

    private static boolean isBits48(int n) {
        for (int v : VALID_N) {
            if (v == n) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBaseOk(int base) {
        return base == 2 || base == 3 || base == 8 || base == 16 || base == 48;
    }

    /** words_for(n, model)：f 模型（32 trit/字）的字宽 W；非法输入回 1。 */
    private static int wordsFor(int n) {
        long w = (n * 1745300781475361L + 9999999999999999L) / 10000000000000000L;
        if (w < 1) {
            return 1;
        }
        return (int) w;
    }

    // ---- 64 字节块 → 两组 (M,N) 平面 + Pw 位平面 + 3 基调度键（单遍扫描）----

    /** 位平面吸收：16 条 Pw 按锚点字 w%W 平衡加并入 lanes。 */
    private static void absorb(int[] lanes, int W, int[] Pw, int skey, boolean half) {
        for (int w = 0; w < 16; w++) {
            if (Pw[w] == 0) {
                continue;
            }
            int a = (w * 5 + ((skey >>> (w & 7)) & 7)) & 31;
            int an = w % W;
            int i2 = 2 * an;
            int v = half ? rrp16(fold16(Pw[w]), a) : rrp(Pw[w], a);
            int v2 = half ? rrp16(fold16(Pw[w]), a + 17) : rrp(Pw[w], ((a + 17) & 31));
            int[] s = tadd2(lanes[i2], lanes[i2 + 1], v, v2);
            lanes[i2] = s[0];
            lanes[i2 + 1] = s[1];
        }
    }

    // ---- 通用环式扩散（值语义唯一定义；idx 为连续区间 [start, start+L)）----

    /**
     * ring_mix 单轨扩散。{@code inj=true} 时把 64 字节消息平面的 (M0,N0)/(M1,N1) 注入轨道；
     * 轮常量 rcon 以 rcon_idx 错位注入。S 复用调用方 scratch（长度 ≥ 2·L）。
     */
    private static void ringMix(int[] lanes, int start, int L, int r, int skey, int[] rcon,
                                boolean half, boolean inj,
                                int M0, int N0, int M1, int N1,
                                int mA, int mB, int rconIdx, int[] S) {
        if (L == 0) {
            return;
        }
        int rA = (r * 3 + (skey & 7)) & 31;
        int rB = (r * 7 + ((skey >>> 3) & 7)) & 31;
        int j2r = (0 + 2) % L;
        int j3r = (0 + 3) % L;
        for (int k = 0; k < L; k++) {
            int iw = start + k;
            int j2 = start + j2r;
            int j3 = start + j3r;
            int a = (rA + 5 * k) & 31;
            int b = (rB + 7 * k + 3) & 31;
            int[] s1 = tadd2(lanes[2 * iw], lanes[2 * iw + 1],
                    rotlane(lanes[2 * j2], a, half), rotlane(lanes[2 * j2 + 1], a, half));
            int[] s2 = tadd2(s1[0], s1[1],
                    rotlane(lanes[2 * j3], b, half), rotlane(lanes[2 * j3 + 1], b, half));
            S[2 * k] = s2[0];
            S[2 * k + 1] = s2[1];
            j2r++;
            if (j2r >= L) {
                j2r = 0;
            }
            j3r++;
            if (j3r >= L) {
                j3r = 0;
            }
        }
        for (int k = 0; k < L; k++) {
            int iw = start + k;
            lanes[2 * iw] = S[2 * k];
            lanes[2 * iw + 1] = S[2 * k + 1];
        }
        int k2r = (0 + 2) % L;
        int k1r = (0 + 1) % L;
        for (int k = 0; k < L; k++) {
            int[] pm = tmul2(S[2 * k], S[2 * k + 1], S[2 * k2r], S[2 * k2r + 1]);
            int i1 = start + k1r;
            lanes[2 * i1] = lanes[2 * i1] ^ pm[0];
            lanes[2 * i1 + 1] = lanes[2 * i1 + 1] ^ pm[1];
            k2r++;
            if (k2r >= L) {
                k2r = 0;
            }
            k1r++;
            if (k1r >= L) {
                k1r = 0;
            }
        }
        int maj;
        if (L >= 3) {
            maj = quant3(S[0], S[1], S[2], S[3], S[4], S[5]);
        } else if (L == 2) {
            maj = quant3(S[0], S[1], S[2], S[3], S[0], S[1]);
        } else {
            maj = quant3(S[0], S[1], S[0], S[1], S[0], S[1]);
        }
        int il = start + (L - 1);
        lanes[2 * il] = lanes[2 * il] ^ maj;
        lanes[2 * il + 1] = lanes[2 * il + 1] ^ rotlane(maj, (rB + 7) & 31, half);
        if (inj) {
            int i0 = start;
            int[] s1 = tadd2(lanes[2 * i0], lanes[2 * i0 + 1],
                    rotlane(M0, mA, half), rotlane(N0, mA, half));
            lanes[2 * i0] = s1[0];
            lanes[2 * i0 + 1] = s1[1];
            if (L >= 2) {
                int i1 = start + 1;
                int[] s2 = tadd2(lanes[2 * i1], lanes[2 * i1 + 1],
                        rotlane(M1, (mA + 7) & 31, half), rotlane(N1, (mA + 7) & 31, half));
                lanes[2 * i1] = s2[0];
                lanes[2 * i1 + 1] = s2[1];
            }
        }
        int i0b = start;
        lanes[2 * i0b] = lanes[2 * i0b] ^ rcon[rconIdx & 15];
        int ilb = start + (L - 1);
        lanes[2 * ilb + 1] = lanes[2 * ilb + 1]
                ^ rotlane(rcon[(rconIdx + 1) & 15], (r * 5) & 31, half);
    }

    // ---- 多样块规划（64 字节，越界按 0 = 纯零填充）----

    /** 从 msg 的 pos 起取 64 字节（越界为 0）构建 (M0,N0,M1,N1,skey,Pw)。 */
    private static int[] blockPlanesSkey(byte[] msg, int pos, int nbytes, boolean full, int[] Pw) {
        for (int kk = 0; kk < 16; kk++) {
            Pw[kk] = 0;
        }
        int m0v = 0;
        int n0v = 0;
        int m1v = 0;
        int n1v = 0;
        int skey = 0;
        for (int j = 0; j < 64; j++) {
            int b;
            int wv;
            int bit;
            if (full) {
                b = msg[pos + j] & 0xFF;
                wv = j / 32;
                bit = j % 32;
                int bits3 = b & 7;
                int tf = (b >>> 6) & 3;
                int t = tf - 1;
                skey = (skey * 3 + (bits3 * 3 + (t + 1)));
                if (t != 0) {
                    if (t > 0) {
                        if (wv == 0) {
                            m0v |= (1 << bit);
                        } else {
                            m1v |= (1 << bit);
                        }
                    } else {
                        if (wv == 0) {
                            n0v |= (1 << bit);
                        } else {
                            n1v |= (1 << bit);
                        }
                    }
                }
                for (int biti = 0; biti < 8; biti++) {
                    int pi = biti * 2 + wv;
                    Pw[pi] |= (((b >>> biti) & 1) << bit);
                }
            } else {
                b = (pos + j < nbytes) ? (msg[pos + j] & 0xFF) : 0;
                wv = j / 32;
                bit = j % 32;
                int bits3 = b & 7;
                int tf = (b >>> 6) & 3;
                int t = tf - 1;
                skey = (skey * 3 + (bits3 * 3 + (t + 1)));
                if (t != 0) {
                    if (t > 0) {
                        if (wv == 0) {
                            m0v |= (1 << bit);
                        } else {
                            m1v |= (1 << bit);
                        }
                    } else {
                        if (wv == 0) {
                            n0v |= (1 << bit);
                        } else {
                            n1v |= (1 << bit);
                        }
                    }
                }
                for (int biti = 0; biti < 8; biti++) {
                    int pi = biti * 2 + wv;
                    Pw[pi] |= (((b >>> biti) & 1) << bit);
                }
            }
        }
        return new int[]{m0v, n0v, m1v, n1v, skey};
    }

    // ---- 单块压缩（模型 f：model=0，half=false）----
    // 直接置换（W<4 → 单环 ring kind=1）与通用路径一致；多用 scratch 一次性预见尺寸。

    private static void compressF(int[] h, byte[] msg, int pos, int nbytes,
                                  int tLo, int tHi, boolean last,
                                  int[] lanes, int[] Pw, int[] S) {
        int W = h.length;
        boolean full = (pos + 64 <= nbytes);
        int[] msk = blockPlanesSkey(msg, pos, nbytes, full, Pw);
        int M0 = msk[0], N0 = msk[1], M1 = msk[2], N1 = msk[3], skey = msk[4];
        int mPlan = 0xFFFFFFFF; // model f 全宽（half=false）

        for (int i = 0; i < W; i++) {
            lanes[2 * i] = h[i];
            lanes[2 * i + 1] = rotlane(h[(i + W / 2) % W], 7, false);
        }
        for (int i = 0; i < W; i++) {
            lanes[2 * i] = lanes[2 * i] ^ (IVF[i % 8] & mPlan);
            lanes[2 * i + 1] = lanes[2 * i + 1] ^ (IVF[(i + 4) % 8] & mPlan);
        }
        lanes[1] = lanes[1] ^ (tLo & mPlan);
        int hi = 2 * (W / 2) + 1;
        lanes[hi] = lanes[hi] ^ (tHi & mPlan);
        if (last) {
            int lk = 2 * ((W / 2) % W) + 1;
            lanes[lk] = lanes[lk] ^ 0xFFFFFFFF;
        }
        absorb(lanes, W, Pw, skey, false);

        // 轨分配（alloc_tracks 退化）；f 模型：W≥16 双轨 / 8≤W<16 双轨 / W<8 单环。
        int tCount;
        int[] ts = new int[4];
        int[] tl = new int[4];
        int[] tk = new int[4];
        if (W >= 16) {
            int la = (W + 1) / 2;
            ts[0] = 0; tl[0] = la; tk[0] = 0;
            ts[1] = la; tl[1] = W - la; tk[1] = 0;
            tCount = 2;
        } else if (W >= 8) {
            int la3 = (W + 1) / 2;
            ts[0] = 0; tl[0] = la3; tk[0] = 0;
            ts[1] = la3; tl[1] = W - la3; tk[1] = 0;
            tCount = 2;
        } else {
            ts[0] = 0; tl[0] = W; tk[0] = 1;
            tCount = 1;
        }
        int R = 12; // model f R_F = 12

        for (int r = 0; r < R; r++) {
            int mA = (r * 5 + ((skey >>> 6) & 7)) & 31;
            int mB = (r * 11 + ((skey >>> 9) & 7)) & 31;
            int mC = (r * 7 + ((skey >>> 12) & 7)) & 31;
            int mD = (r * 13 + ((skey >>> 15) & 7)) & 31;
            int dualSeen = 0;
            for (int tt = 0; tt < tCount; tt++) {
                int kind = tk[tt];
                if (kind == 0) {
                    int injmA = mA;
                    int injmB = mB;
                    int rc = r;
                    if (dualSeen > 0) {
                        injmA = mC;
                        injmB = mD;
                        rc = (r + 8) & 15;
                    }
                    ringMix(lanes, ts[tt], tl[tt], r, skey, RCONF, false, true,
                            M0, N0, M1, N1, injmA, injmB, rc, S);
                    dualSeen = dualSeen + 1;
                } else {
                    // kind==1 单环 ring；rcon/skey 用 f 表（与 tie 通用路径的 kind=1 一致）
                    ringMix(lanes, ts[tt], tl[tt], r, skey, RCONF, false, true,
                            M0, N0, M1, N1, mA, mB, r, S);
                }
            }
            // f/b/x 模型无跨轨耦合（仅 b/x 的 W≥16 多轨形态才有）；f 模型无。
        }

        // 末块折回链值 h
        for (int i = 0; i < W; i++) {
            h[i] = h[i] ^ lanes[2 * i] ^ rotlane(lanes[2 * i + 1], (i * 3) & 31, false);
        }
        finSynth(h, lanes, Pw, S);
    }

    /** 最后综合（终筛）：S=4 轮全 W 字单环收束（rcon=0 表，skey=0x13579BDF），随后投影。 */
    private static void finSynth(int[] h, int[] lanes, int[] Pw, int[] S) {
        int W = h.length;
        if (W == 1) {
            return;
        }
        for (int i = 0; i < W; i++) {
            lanes[2 * i] = h[i];
            lanes[2 * i + 1] = rotlane(h[(i + 1) % W], 7, false);
        }
        for (int z = 0; z < 16; z++) {
            Pw[z] = 0;
        }
        for (int sf = 0; sf < 4; sf++) {
            ringMix(lanes, 0, W, sf, 0x13579BDF, Pw, false, false,
                    0, 0, 0, 0, 0, 0, sf, S);
        }
        for (int i = 0; i < W; i++) {
            h[i] = lanes[2 * i] ^ rotlane(lanes[2 * i + 1], (i * 5) & 31, false);
        }
    }

    // ---- 摘要驱动（model f；全宽 hex = W 字 × 8 hex）----

    /**
     * 计算 model 的 full-width 状态 hex 摘要。*只要求 f 模型*：{@code model} 非 "f" 时
     * 仍按 f 的常量与压缩语义处理（本实现不横向构建 r/b/x 轨道，任务范围定为 f）。
     * 非法位长返回空串。
     */
    static String digestHexF(byte[] msg, int n) {
        if (!isBits48(n)) {
            return "";
        }
        int nbytes = msg.length;
        int W = wordsFor(n);
        int[] h = new int[W];
        for (int i = 0; i < W; i++) {
            h[i] = IVF[i];
        }
        h[0] = h[0] ^ 0x01010000 ^ 32;

        int tLo = 0;
        int tHi = 0;
        int pos = 0;
        int[] lanes = new int[2 * W + 2];
        int[] Pw = new int[16];
        int[] S = new int[2 * W + 2];

        while (pos + 64 < nbytes) {
            long lo64 = tLo & 0xFFFFFFFFL;
            tHi = (tHi + (int) ((lo64 + 64) >>> 32));
            tLo = (int) ((lo64 + 64) & 0xFFFFFFFFL);
            compressF(h, msg, pos, nbytes, tLo, tHi, false, lanes, Pw, S);
            pos = pos + 64;
        }
        int rem = nbytes - pos;
        long lo64b = tLo & 0xFFFFFFFFL;
        tHi = (tHi + (int) ((lo64b + rem) >>> 32));
        tLo = (int) ((lo64b + rem) & 0xFFFFFFFFL);
        compressF(h, msg, pos, nbytes, tLo, tHi, true, lanes, Pw, S);

        StringBuilder out = new StringBuilder(8 * W);
        for (int i = 0; i < W; i++) {
            out.append(hex8(h[i]));
        }
        return out.toString();
    }

    private static String hex8(int v) {
        String hex = "0123456789abcdef";
        StringBuilder out = new StringBuilder(8);
        for (int p = 0; p < 8; p++) {
            int shift = 28 - p * 4;
            out.append(hex.charAt((v >>> shift) & 0xF));
        }
        return out.toString();
    }

    // ---- 输出编码（无 XOF；全宽 hex → 池 前缀重编码）----

    /** base48 最小编码（无定长补零）。Minimal base-48 encoding of a hex byte string. */
    private static String b48Min(String hx) {
        if (hx.isEmpty()) {
            return "";
        }
        int L = hx.length() / 2;
        int[] b = new int[L];
        for (int i = 0; i < L; i++) {
            b[i] = hexByte(hx, i * 2);
        }
        java.util.ArrayList<Integer> digits = new java.util.ArrayList<>();
        int pos = 0;
        while (pos < L) {
            int rem = 0;
            for (int i = 0; i < L; i++) {
                int cur = rem * 256 + b[i];
                b[i] = cur / 48;
                rem = cur % 48;
            }
            while (pos < L && b[pos] == 0) {
                pos = pos + 1;
            }
            digits.add(rem);
        }
        int dlen = digits.size();
        StringBuilder out = new StringBuilder(dlen);
        for (int i = dlen - 1; i >= 0; i--) {
            out.append(B48_ALPHABET.charAt(digits.get(i)));
        }
        if (out.length() == 0) {
            return "0";
        }
        return out.toString();
    }

    private static int hexByte(String hex, int i) {
        int hi = hexNibble(hex.charAt(i));
        int lo = hexNibble(hex.charAt(i + 1));
        return hi * 16 + lo;
    }

    /** b 字节信息量在 base 进制下所需符号数 m。 */
    private static int baseChars(int b, int base) {
        if (base == 2) {
            return b * 8;
        }
        if (base == 3) {
            return (b * 504744 + 99999) / 100000;
        }
        if (base == 8) {
            return (b * 8 + 2) / 3;
        }
        if (base == 16) {
            return b * 2;
        }
        return (b * 1432407) / 1000000 + 1; // base 48
    }

    /** 字节数组 hex → base 进制字符串（长除法，补零到恰 m 符号）。 */
    private static String padRadix(String hex, int base, int m) {
        int L = hex.length() / 2;
        int[] b = new int[L];
        for (int i = 0; i < L; i++) {
            b[i] = hexByte(hex, i * 2);
        }
        java.util.ArrayList<Integer> digs = new java.util.ArrayList<>();
        while (true) {
            int rem = 0;
            int[] nq = new int[b.length];
            boolean allz = true;
            for (int j = 0; j < b.length; j++) {
                int cur = rem * 256 + b[j];
                int q = cur / base;
                int r = cur % base;
                if (q != 0) {
                    allz = false;
                }
                nq[j] = q;
                rem = r;
            }
            digs.add(rem);
            b = nq;
            if (allz) {
                break;
            }
        }
        StringBuilder out = new StringBuilder();
        for (int k = digs.size() - 1; k >= 0; k--) {
            int d = digs.get(k);
            out.append(d < 10 ? (char) ('0' + d) : (char) ('a' + (d - 10)));
        }
        while (out.length() < m) {
            out.insert(0, '0');
        }
        return out.toString();
    }

    /**
     * tsha1f（快速模型 f）确定性输出。
     * 计算 model f 的状态全宽 hex 摘要，再:
     *  - base==48：取 {@code b48_min} 结果，不足 {@code n} 符号用字符台首位 '0' 补足，否则取前 n 符号；
     *  - base∈{2,3,8,16}：按信息量取池前缀重编码。
     * 非法位长或 base 返回空串（与 tie 错误约定一致）。
     *
     * @param msg  原始字节（二进制安全，含 0x00/0xFF）；null 视为空载荷
     * @param n    48 进制位数 / bit length in the 48-symbol sense
     * @param base 输出进制（2/3/8/16/48；≤0 视为默认 48）
     * @return 定长编码字符串 / fixed-length encoding string
     */
    public static String tsha1f(byte[] msg, int n, int base) {
        if (!isBits48(n)) {
            return "";
        }
        int b = base;
        if (b <= 0) {
            b = 48;
        }
        if (!isBaseOk(b)) {
            return "";
        }
        byte[] m = msg == null ? new byte[0] : msg;
        String hexstr = digestHexF(m, n);
        if (hexstr.isEmpty()) {
            return "";
        }
        int nbytes = m.length;
        if (b == 48) {
            String full = b48Min(hexstr);
            int fl = full.length();
            if (fl < n) {
                StringBuilder pad = new StringBuilder();
                for (int k = 0; k < n - fl; k++) {
                    pad.append('0');
                }
                return pad + full;
            }
            return full.substring(0, n);
        }
        int b0 = (n * 698121) / 1000000;
        if (hexstr.length() / 2 < b0) {
            return "";
        }
        String bb48 = hexstr.substring(0, b0 * 2);
        if (b == 16) {
            return bb48;
        }
        int m2 = baseChars(b0, b);
        return padRadix(bb48, b, m2);
    }

    /**
     * 全宽 hex 摘要（强完整性的权威锚点）。{@code model} 仅 f 为实现承诺；传 "r"/"b"/"x"
     * 时按 f 语义计算（不构建对应轨道），其余字符串依旧返回 f 结果。非法位长返回空串。
     * <p>
     * Full-width hex digest (authoritative anchor for strong integrity). Only model f is
     * implemented; "r"/"b"/"x" or any other model string fall back to the f computation.
     * Invalid bit length returns the empty string.
     *
     * @param model 模型名（"f"；其它按 f） / model name, "f" required (others treated as f)
     * @param msg   原始字节 / raw bytes (binary-safe)
     * @param n     位长 / bit length (48-symbol sense)
     * @return 全宽小写 hex（W 字 × 8 字符）/ full-width lowercase hex (W words × 8 chars)
     */
    public static String digestHex(String model, byte[] msg, int n) {
        byte[] m = msg == null ? new byte[0] : msg;
        return digestHexF(m, n);
    }
}