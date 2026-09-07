package io.toterra.subterra.optim.worldgen.pipeline.noise;

/**
 * Deterministic PRNG (p.1.8.6), bit-identical to Minecraft 1.21.1's
 * {@code net.minecraft.world.level.levelgen.XoroshiroRandomSource}.
 * <p>
 * The 128-bit core is Xoroshiro128++ (Xoroshiro128PlusPlus): {@code nextLong}
 * runs the double-rotation state update
 * {@code result = rotl(s0 + s1, 17) + s0} with
 * {@code s1 ^= s0; s0 = rotl(s0,49) ^ s1 ^ (s1<<21); s1 = rotl(s1,28);}.
 * When built from a single {@code long} seed the state-doubling goes through
 * {@code RandomSupport.upgradeSeedTo128bit}: {@code a = seed ^ SILVER},
 * {@code b = a + GOLDEN}, then each word is run through the splitmix64 /
 * Stafford13 finalizer. {@code nextDouble} is {@code (nextLong()>>>11) *
 * 2^-53}, {@code nextInt} is {@code (int)nextLong}, and {@code nextInt(bound)}
 * uses the low-32-bit rejection (Lemire-style) reduction. {@code nextGaussian}
 * carries the cached Marsaglia-polar spare and is reset by {@link #reset}.
 * <p>
 * 确定性 PRNG（p.1.8.6），与 Minecraft 1.21.1 的
 * {@code XoroshiroRandomSource} 逐位一致：128 位核心为 Xoroshiro128++，
 * 由单个 long 种子初始化时先做
 * {@code RandomSupport.upgradeSeedTo128bit} 状态倍增并施加 splitmix64
 * 最终化；{@code nextDouble} 为 {@code (nextLong()>>>11)*2^-53}；
 * {@code nextInt(bound)} 采用低 32 位舍弃的归约；{@code nextGaussian}
 * 携带缓存极坐标 spare，并在 {@link #reset} 时清除。
 */
public final class XoroRandom {

    /** Golden-ratio 64-bit constant used by the seed upgrade. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L; // -7046029254386353131L
    /** Silver-ratio 64-bit constant (coprime) used by the seed upgrade. */
    private static final long SILVER = 0x6A09E667F3BCC909L; //  7640891576956012809L

    private static final double DOUBLE_UNIT = 0x1.0p-53;     // 2^-53

    /** The construct-time master seed, retained for td self-description. */
    private final long seed;

    /** Xoroshiro128++ state words. */
    private long seedLo;
    private long seedHi;

    // Cached spare for the Marsaglia-polar nextGaussian.
    private boolean haveNextNextGaussian;
    private double nextNextGaussian;

    /**
     * @param seed the master seed (upgraded to a 128-bit state internally).
     */
    public XoroRandom(long seed) {
        this.seed = seed;
        Seed128 s = upgradeSeedTo128bit(seed);
        this.seedLo = s.seedLo;
        this.seedHi = s.seedHi;
    }

    /**
     * Re-seeds a fresh state from {@code seed} and clears any cached spare.
     *
     * @param seed the new master seed.
     */
    public void reset(long seed) {
        Seed128 s = upgradeSeedTo128bit(seed);
        this.seedLo = s.seedLo;
        this.seedHi = s.seedHi;
        this.haveNextNextGaussian = false;
        this.nextNextGaussian = 0.0;
    }

    /** Next 32-bit value ({@code (int) nextLong()}). */
    public int nextInt() {
        return (int) nextLong();
    }

    /**
     * Next integer in {@code [0, bound)}: multiplies the low 32 bits as an
     * unsigned long by {@code bound} and rejects while the low word is below
     * the {@code 2^32 mod bound} threshold (unbiased, no modulo bias).
     *
     * @param bound exclusive upper bound, must be positive.
     * @throws IllegalArgumentException if {@code bound <= 0}.
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

    /** Next 64-bit value from the Xoroshiro128++ core. */
    public long nextLong() {
        long s0 = this.seedLo;
        long s1 = this.seedHi;
        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        this.seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        this.seedHi = Long.rotateLeft(s1, 28);
        return result;
    }

    /** Next double in {@code [0, 1)} ({@code (nextLong()>>>11) * 2^-53}). */
    public double nextDouble() {
        return (double) (this.nextLong() >>> 11) * DOUBLE_UNIT;
    }

    /** Next normally-distributed double (cached Marsaglia-polar spare). */
    public double nextGaussian() {
        if (this.haveNextNextGaussian) {
            this.haveNextNextGaussian = false;
            return this.nextNextGaussian;
        }
        double v1;
        double v2;
        double s;
        do {
            v1 = 2.0 * this.nextDouble() - 1.0;
            v2 = 2.0 * this.nextDouble() - 1.0;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1.0 || s == 0.0);
        double multiplier = Math.sqrt(-2.0 * Math.log(s) / s);
        this.nextNextGaussian = v2 * multiplier;
        this.haveNextNextGaussian = true;
        return v1 * multiplier;
    }

    /** Stafford13 / splitmix64 finalizer (multiply-xorshift avalanche). */
    private static long mixStafford13(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /**
     * Doubles a {@code long} seed into a well-mixed 128-bit state:
     * {@code a = seed ^ SILVER}, {@code b = a + GOLDEN}, then both words are
     * Stafford13-mixed. The fully-zero state is mapped onto the golden-ratio
     * pair so the core stream is never the degenerate all-zero run.
     */
    private static Seed128 upgradeSeedTo128bit(long seed) {
        long a = seed ^ SILVER;
        long b = a + GOLDEN;
        Seed128 s = new Seed128(mixStafford13(a), mixStafford13(b));
        if ((s.seedLo | s.seedHi) == 0L) {
            s = new Seed128(GOLDEN, SILVER);
        }
        return s;
    }

    /** Immutable pair of 64-bit state words. */
    private static final class Seed128 {
        final long seedLo;
        final long seedHi;

        Seed128(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }
    }

    /**
     * A minimal self-contained td table-literal describing the construct-time
     * parameter ({@code seed}): {@code "[ seed = <long> ]"}. No td parser
     * dependency is imported (subterra-optim depends only on subterra-api), so
     * the snippet is intentionally restricted to the single scalar parameter.
     *
     * 描述构造参数（{@code seed}）的最小 td 表字面量 {@code "[ seed =
     * <long> ]"}：不引入 td 解析器依赖（subterra-optim 仅依赖
     * subterra-api），仅包含单个标量参数。
     */
    public String td() {
        return "[ seed = " + this.seed + " ]";
    }

    /**
     * Parses the snippet produced by {@link #td()} back into an instance with
     * the same construct-time seed (and therefore the same bit-exact stream).
     *
     * 解析{@link #td()}生成的片段，还原出构造种子相同的实例（因此拥有
     * 相同的逐位一致流）。
     *
     * @param td a {@code "[ seed = <long> ]"} table-literal snippet.
     * @return a freshly seeded {@link XoroRandom}.
     * @throws IllegalArgumentException if the snippet cannot be parsed.
     */
    public static XoroRandom fromTd(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        int lb = td.indexOf('[');
        int rb = td.indexOf(']');
        if (lb < 0 || rb < 0 || rb <= lb) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        String body = td.substring(lb + 1, rb);
        int eq = body.indexOf('=');
        if (eq < 0) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        String value = body.substring(eq + 1).trim();
        final long fromSeed;
        try {
            fromSeed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad seed in td: " + value, e);
        }
        return new XoroRandom(fromSeed);
    }
}