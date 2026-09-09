package io.toterra.subterra.engine.network.strategy;

/**
 * 三级载荷策略（p.2.4.3）的层级枚举：客户端⇄服务端增强通道的带宽节省依赖
 * 差分同步 / 订阅域（interest-domain）门控 / 抽取（decimation），本枚举只是
 * 引擎层抽象分级，不接任何 Minecraft 实体或真实通道。
 * <ul>
 *   <li>{@link #L1} 高频增量差分 / 兴趣域订阅：近实时兴趣域内的小字段变更，按 key 差分，
 *       通常要求 differential 差分开关为真，配合订阅过滤命中域。</li>
 *   <li>{@link #L2} 中频快照 + 变更流：周期性整体快照 + 有序变更流，兼顾确定性重放。</li>
 *   <li>{@link #L3} 低频加密：低频载荷携带 {@code encrypted} 加密开关（真正加密动作落在
 *       p.2.4.5），本级只标记策略为加密。</li>
 * </ul>
 * 每个层级带一个相对 {@link #rank()}（频率序，L1 最频繁）与名义周期提示
 * {@link #nominalPeriodNanos()}。纯 JDK，确定性。
 * <p>
 * Three-tier payload-strategy levels (p.2.4.3) for the enhanced client⇄server channel.
 * Bandwidth savings come from differential sync / interest-domain subscription / decimation;
 * this enum is only the engine-layer abstraction, decoupled from any Minecraft entity or real
 * channel. Each level carries a relative {@link #rank()} (frequency order, L1 most frequent)
 * and a nominal period hint {@link #nominalPeriodNanos()}. Pure JDK, deterministic.
 */
public enum PayloadLevel {

    /** 高频增量差分 / 兴趣域订阅。High-frequency incremental diff / interest-domain subscription. */
    L1(0, 50_000_000L),
    /** 中频快照 + 变更流。Mid-frequency snapshot + change stream. */
    L2(1, 500_000_000L),
    /** 低频加密（策略携带加密开关）。Low-frequency encrypted (strategy carries the encryption flag). */
    L3(2, 5_000_000_000L);

    private final int rank;
    private final long nominalPeriodNanos;

    PayloadLevel(int rank, long nominalPeriodNanos) {
        this.rank = rank;
        this.nominalPeriodNanos = nominalPeriodNanos;
    }

    /** 相对频率序（L1 最频繁，序最小）。Relative frequency rank (smaller = more frequent). */
    public int rank() {
        return rank;
    }

    /** 名义刷新周期提示（纳秒）。Nominal refresh-period hint in nanoseconds. */
    public long nominalPeriodNanos() {
        return nominalPeriodNanos;
    }

    /** 按 rank 反解层级；越界抛 {@link IllegalArgumentException}。Resolve a level by rank. */
    public static PayloadLevel ofRank(int rank) {
        for (PayloadLevel l : values()) {
            if (l.rank == rank) {
                return l;
            }
        }
        throw new IllegalArgumentException("unknown payload level rank: " + rank);
    }
}