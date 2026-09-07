package io.toterra.subterra.optim.worldgen.pipeline.router;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.toterra.subterra.optim.worldgen.pipeline.noise.XoroRandom;

/**
 * The vanilla-general positional seed chain (p.1.8.12), bit-identical to MC
 * 1.21.1's {@code net.minecraft.world.level.levelgen.XoroshiroRandomSource}
 * {@code forkPositional()} / {@code fromHashOf(String)} chain (verified against
 * the de-obfuscated 1.21.1 classes via javap). This is the seed half of the
 * general {@code PerlinNoise.create(RandomSource, ...)} path: the legacy
 * single-stream branch was mirrored by p.1.8.7, this type supplies the
 * per-octave positional forking.
 * <p>
 * MC derives a noise field's random source from the world seed in three steps:
 * (1) {@code RandomState} seeds an {@code XoroshiroRandomSource(worldSeed)}
 * (single long, upgraded to a 128-bit state) and calls {@code forkPositional()},
 * which draws exactly <em>two</em> {@code nextLong()} calls to become the
 * factory base {@code (seedLo, seedHi)}; (2) for each noise the factory calls
 * {@code fromHashOf(label)}: the label's UTF-8 bytes are hashed with
 * {@code RandomSupport.seedFromHashOf} — <em>Guava MD5</em>, split big-endian
 * into the 16-byte digest's first and second halves — then XOR-ed with
 * {@code (seedLo, seedHi)} to form the 128-bit {@code XoroshiroRandomSource}
 * state (used as-is, no extra mixing); (3) that source is handed to
 * {@code NormalNoise.create(...)}, which internally {@code fork()}s per inner
 * layer. NOTE: 1.21.1 uses {@code MD5}, not SHA-256 — a correction to the
 * p.1.8.12 brief.
 * <p>
 * This class reproduces steps (1)-(2) exactly and acts as a self-contained
 * Xoroshiro128++ stream source seeded from a derived 128-bit state (matching the
 * {@link XoroRandom} core), plus {@link #deriveLong(long, String)} for callers
 * that fold a noise label down to a single deterministic long seed.
 * <p>
 * 原生通用位置种子链（p.1.8.12），与 MC 1.21.1 的
 * {@code XoroshiroRandomSource} {@code forkPositional()} /
 * {@code fromHashOf(String)} 链逐位一致（经 javap 对照反混淆后的 1.21.1 类验证）。
 * 这是通用 {@code PerlinNoise.create(RandomSource,...)} 路径的种子半边：单流
 * 分支已由 p.1.8.7 镜像，本类型补足逐八度位置分叉。
 * <p>
 * MC 从世界种子派生噪声场随机源分三步：(1) {@code RandomState} 以
 * {@code XoroshiroRandomSource(worldSeed)} 建源并调用 {@code forkPositional()}，
 * 恰好抽<em>两个</em> {@code nextLong()} 得到工厂基 {@code (seedLo, seedHi)}；
 * (2) 每个噪声调用 {@code fromHashOf(label)}：label 的 UTF-8 字节经
 * {@code RandomSupport.seedFromHashOf} 散列——<em>Guava MD5</em>，按大端拆成
 * 16 字节摘要的前后两半——再与 {@code (seedLo, seedHi)} 异或得到
 * {@code XoroshiroRandomSource} 的 128 位状态（原样使用，不再混合）；(3) 该源
 * 交给 {@code NormalNoise.create(...)}，其内部再 {@code fork()} 内外层。注意：
 * 1.21.1 用 {@code MD5} 而非 SHA-256——这是对 p.1.8.12 简报的修正。
 */
public final class PositionalRand {

    private static final double DOUBLE_UNIT = 0x1.0p-53; // 2^-53

    /** Xoroshiro128++ state / factory base words. */
    private long seedLo;
    private long seedHi;

    private final String tdLabel;

    /**
     * @param seedLo low 64-bit state word (used as-is, no mixing).
     * @param seedHi high 64-bit state word.
     */
    public PositionalRand(long seedLo, long seedHi) {
        this(seedLo, seedHi, null);
    }

    private PositionalRand(long seedLo, long seedHi, String tdLabel) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
        this.tdLabel = tdLabel;
    }

    /**
     * Step (1): the factory base for a world seed — draws exactly two
     * {@code nextLong()} calls from {@code XoroRandom(worldSeed)} exactly as
     * vanilla's {@code forkPositional()} does.
     *
     * @param worldSeed the master world seed.
     */
    public static PositionalRand ofMaster(long worldSeed) {
        XoroRandom master = new XoroRandom(worldSeed);
        return new PositionalRand(master.nextLong(), master.nextLong());
    }

    /**
     * Alias of {@link #ofMaster(long)} documenting the vanilla name
     * {@code forkPositional()}.
     */
    public static PositionalRand forkPositional(long worldSeed) {
        return ofMaster(worldSeed);
    }

    /**
     * Step (2): derives a fresh source for a label = {@code MD5(label).xor(base)}.
     *
     * @param label exact vanilla label string, e.g. {@code "minecraft:temperature"}.
     */
    public PositionalRand fromHashOf(String label) {
        long[] h = md5Pair(label);
        return new PositionalRand(h[0] ^ this.seedLo, h[1] ^ this.seedHi, label);
    }

    /** Vanilla {@code fromSeed}: source seed = {@code (seed ^ seedLo, seed ^ seedHi)}. */
    public PositionalRand fromSeed(long seed) {
        return new PositionalRand(seed ^ this.seedLo, seed ^ this.seedHi);
    }

    /** The label used to derive this instance ({@code null} for a factory base). */
    public String label() {
        return this.tdLabel;
    }

    /** Low 64-bit state word. */
    public long seedLo() {
        return this.seedLo;
    }

    /** High 64-bit state word. */
    public long seedHi() {
        return this.seedHi;
    }

    /** Next 64-bit value from the Xoroshiro128++ core (identical to {@link XoroRandom}). */
    public long nextLong() {
        long s0 = this.seedLo;
        long s1 = this.seedHi;
        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        this.seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        this.seedHi = Long.rotateLeft(s1, 28);
        return result;
    }

    /** Next 32-bit value ({@code (int) nextLong()}). */
    public int nextInt() {
        return (int) nextLong();
    }

    /** Next boolean. */
    public boolean nextBoolean() {
        return (nextLong() & 1L) == 1L;
    }

    /** Next double in {@code [0,1)} ({@code (nextLong()>>>11) * 2^-53}). */
    public double nextDouble() {
        return (double) (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    /**
     * Next integer in {@code [0, bound)} using the same low-32-bit rejection
     * reduction as {@link XoroRandom}.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long l = Integer.toUnsignedLong(nextInt());
        long prod = l * (long) bound;
        long r = prod & 0xFFFF_FFFFL;
        if (r < bound) {
            int throttle = Integer.remainderUnsigned(-bound, bound);
            while (r < throttle) {
                l = Integer.toUnsignedLong(nextInt());
                prod = l * (long) bound;
                r = prod & 0xFFFF_FFFFL;
            }
        }
        return (int) (prod >>> 32);
    }

    /**
     * Convenience for noise builders that need one deterministic long per label:
     * the avalanche-mixed XOR of the derived 128-bit state words. Different
     * labels and different world seeds produce distinct values.
     *
     * @param worldSeed master world seed.
     * @param label     vanilla noise label (e.g. {@code "minecraft:contentalness"}).
     */
    public static long deriveLong(long worldSeed, String label) {
        byte[] h = md5Bytes(label);
        long lo = fromBytes(h, 0);
        long hi = fromBytes(h, 8);
        PositionalRand master = ofMaster(worldSeed);
        long mlo = lo ^ master.seedLo;
        long mhi = hi ^ master.seedHi;
        return mixStafford13(mlo ^ mhi);
    }

    /** MD5 of {@code label} (UTF-8) split into two big-endian longs. */
    public static long[] md5Pair(String label) {
        byte[] h = md5Bytes(label);
        return new long[]{fromBytes(h, 0), fromBytes(h, 8)};
    }

    /** MD5 digest of a label's UTF-8 bytes via the JDK provider (deterministic). */
    static byte[] md5Bytes(String label) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(label.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    /** Guava-style {@code Longs.fromBytes} big-endian read of {@code src[off..off+7]}. */
    private static long fromBytes(byte[] src, int off) {
        long v = 0L;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (src[off + i] & 0xFF);
        }
        return v;
    }

    /** Hex (lowercase) MD5 of a label, for probe reference-vector pinning. */
    public static String md5Hex(String label) {
        byte[] h = md5Bytes(label);
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) {
            int v = b & 0xFF;
            sb.append(Character.forDigit(v >>> 4, 16)).append(Character.forDigit(v & 0xF, 16));
        }
        return sb.toString();
    }

    /** Stafford13 / splitmix64 finalizer. */
    private static long mixStafford13(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /** Self-describing snippet {@code "[ seedLo = <lo>, seedHi = <hi> ]"}. */
    public String td() {
        return "[ seedLo = " + this.seedLo + ", seedHi = " + this.seedHi + " ]";
    }

    /** Parses a {@link #td()} snippet back into an equal-state source. */
    public static PositionalRand fromTd(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        int lb = td.indexOf('[');
        int rb = td.indexOf(']');
        if (lb < 0 || rb < 0 || rb <= lb) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        String body = td.substring(lb + 1, rb);
        long lo = 0L;
        long hi = 0L;
        String[] parts = body.split(",");
        for (String p : parts) {
            String[] kv = p.trim().split("=");
            if (kv.length != 2) {
                throw new IllegalArgumentException("bad token in td: " + p);
            }
            long v;
            try {
                v = Long.parseLong(kv[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad value in td: " + p, e);
            }
            if (kv[0].trim().equals("seedLo")) {
                lo = v;
            } else if (kv[0].trim().equals("seedHi")) {
                hi = v;
            } else {
                throw new IllegalArgumentException("unknown key in td: " + kv[0].trim());
            }
        }
        return new PositionalRand(lo, hi);
    }
}