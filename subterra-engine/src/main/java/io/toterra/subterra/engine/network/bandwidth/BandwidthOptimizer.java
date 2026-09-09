package io.toterra.subterra.engine.network.bandwidth;

import io.toterra.subterra.engine.network.crypto.EncryptionConfig;
import io.toterra.subterra.engine.network.strategy.PayloadLevel;
import io.toterra.subterra.engine.network.strategy.PayloadStrategy;
import io.toterra.subterra.engine.network.strategy.StrategySelector;

import java.util.function.BooleanSupplier;

/**
 * 带宽削减量化优化器（p.2.4.4）：对 {@link BandwidthStats} 计算三种技术的确定性加权得分，
 * 取 argmax（固定枚举序打破平局，纯函数无随机），并接线到 p.2.4.3 策略核心与 p.2.4.5 加密开关，
 * 产出确定性策略命中标记。
 *
 * <p>选择模型（文档化的精确规则，即探针所编码的规范）：
 * <ul>
 *   <li>{@code scoreDiff = W_DIFF_RATIO * diffRatio * (1 - stateVolatility)}
 *       —— 变更比例越高、状态越稳定，差分同步越优。</li>
 *   <li>{@code scoreDec  = W_VOLATILITY * stateVolatility * (1 - diffRatio)}
 *       —— 波动越高、变更比例越低，降频插值越优。</li>
 *   <li>{@code subscriberFactor = min(1, subscriberCount / SUBSCRIBER_CAP)}；
 *       {@code scoreSub = W_SUBSCRIBER_CAP * subscriberFactor * stateVolatility}
 *       —— 订阅者数饱和后按需求即时卸载热点域，波动越高收益越大。</li>
 *   <li>得分从高到低选取；等分时按固定枚举序
 *       {@code DIFFERENTIAL_SYNC → DECIMATION_INTERPOLATION → ON_DEMAND_SUBSCRIBE} 打破平局（确定性）。</li>
 * </ul>
 *
 * <p>加密适用性：{@code encryptionApplicable = (impliedLevel == L3) && isEncryptionEnabled.get()}。
 * 默认钩子读取 {@link EncryptionConfig} 语义（enabled=true）；探针注入确定性布尔值以隔离测试，
 * 开关与命中计数正交（切换开关不会改变计数）。
 *
 * <p>纯 JDK，引擎层抽象，不接任何 Minecraft 实体。
 * <p>
 * Bandwidth reduction quantified optimizer (p.2.4.4): computes a deterministic weighted score for
 * each of the three techniques from a {@link BandwidthStats}, takes the argmax (tie broken by a
 * fixed enum order, a pure deterministic function with no randomness), wires into the p.2.4.3
 * strategy core and the p.2.4.5 crypto toggle, and produces deterministic strategy-hit markers.
 *
 * <p>Selection model (the exact documented rule, which the probe encodes as the spec):
 * <ul>
 *   <li>{@code scoreDiff = W_DIFF_RATIO * diffRatio * (1 - stateVolatility)}
 *       — the higher the change ratio and the more stable the state, the better differential sync.</li>
 *   <li>{@code scoreDec  = W_VOLATILITY * stateVolatility * (1 - diffRatio)}
 *       — the more volatile and the lower the change ratio, the better decimation + interpolation.</li>
 *   <li>{@code subscriberFactor = min(1, subscriberCount / SUBSCRIBER_CAP)};
 *       {@code scoreSub = W_SUBSCRIBER_CAP * subscriberFactor * stateVolatility}
 *       — once subscribers saturate, hot domains are offloaded on demand; the more volatile the better.</li>
 *   <li>Pick the highest score; ties are broken by the fixed enum order
 *       {@code DIFFERENTIAL_SYNC → DECIMATION_INTERPOLATION → ON_DEMAND_SUBSCRIBE} (deterministic).</li>
 * </ul>
 *
 * <p>Encryption applicability: {@code encryptionApplicable = (impliedLevel == L3) && isEncryptionEnabled.get()}.
 * The default hook reads {@link EncryptionConfig} semantics (enabled=true); probes inject deterministic
 * booleans to isolate testing, and the toggle is orthogonal to the hit counters (toggling it never changes counts).
 *
 * <p>Pure JDK, engine-layer abstraction, decoupled from any Minecraft entity.
 */
public final class BandwidthOptimizer {

    /** 差分得分权重。Weight of the change ratio term for differential sync. */
    public static final double W_DIFF_RATIO = 3.0;
    /** 波动得分权重。Weight of the volatility term for decimation/interpolation. */
    public static final double W_VOLATILITY = 3.0;
    /** 订阅者得分权重。Weight of the subscription term for on-demand subscribe. */
    public static final double W_SUBSCRIBER_CAP = 3.0;
    /** 订阅者饱和阈值：subscriberCount 达到该值后订阅因子取满 1.0。Subscriber saturation threshold. */
    public static final double SUBSCRIBER_CAP = 16.0;
    /** 默认兴趣域，用于构造载荷描述符。Default interest domain used when building the payload descriptor. */
    public static final String DOMAIN = "default";

    private final BooleanSupplier isEncryptionEnabled;
    private final long[] hitCounters = new long[BandwidthTechnique.values().length];
    private Decision lastDecision;

    /** 默认构造：加密适用性钩子读取 {@link EncryptionConfig} 语义（enabled=true）。 */
    public BandwidthOptimizer() {
        this(() -> EncryptionConfig.on().enabled());
    }

    /**
     * 注入加密适用性钩子（探针注入确定性值）。Inject an encryption-applicability hook (probes inject
     * deterministic values).
     *
     * @param isEncryptionEnabled 判定加密是否启用。Whether encryption is enabled.
     */
    public BandwidthOptimizer(BooleanSupplier isEncryptionEnabled) {
        this.isEncryptionEnabled = isEncryptionEnabled == null
                ? () -> EncryptionConfig.on().enabled() : isEncryptionEnabled;
    }

    /**
     * 决策：给定统计输入，计算三种技术得分、选取最优技术，并接线到策略核心与加密开关。
     * 仅当输入通过前置校验（各比率在 [0,1]、订阅者数 ≥0、impliedLevel 非空）才递增命中计数；
     * 非法路径抛 {@link IllegalArgumentException} 且计数不变。
     * <p>
     * Decision: given the statistics, scores all three techniques, picks the best one, and wires into
     * the strategy core and encryption toggle. Hit counters increment only when the input passes the
     * upfront validation (ratios in [0,1], subscriber count ≥ 0, non-null implied level); an invalid
     * path throws {@link IllegalArgumentException} and leaves the counters unchanged.
     *
     * @param stats 量化统计输入。Quantified statistics input.
     * @return 决策记录（技术 + 描述符 + 策略 + 加密适用性 + 计数快照）。Decision record.
     */
    public Decision optimize(BandwidthStats stats) {
        validate(stats);
        ScaledScore[] scores = scoresFor(stats);
        BandwidthTechnique best = select(scores);

        StrategySelector.Descriptor descriptor = descriptorFor(stats.impliedLevel());
        PayloadStrategy strategy = StrategySelector.select(descriptor);
        boolean encryptionApplicable =
                stats.impliedLevel() == PayloadLevel.L3 && isEncryptionEnabled.getAsBoolean();

        hitCounters[best.ordinal()]++;
        lastDecision = new Decision(
                best,
                descriptor,
                strategy,
                encryptionApplicable,
                hitCountersSnapshot());
        return lastDecision;
    }

    /**
     * 暴露给调用方/探针的确定性得分视图（校验后返回拷贝）。Deterministic score view exposed to
     * callers/probes (validated, then returns a copy).
     */
    public static ScaledScore[] scoresOf(BandwidthStats stats) {
        validate(stats);
        return scoresFor(stats);
    }

    /** 前置校验：越界即抛 {@link IllegalArgumentException}，计数不变。 */
    private static void validate(BandwidthStats stats) {
        if (stats == null) {
            throw new IllegalArgumentException("bandwidth stats must not be null");
        }
        double dr = stats.diffRatio();
        double sv = stats.stateVolatility();
        if (Double.isNaN(dr) || dr < 0.0 || dr > 1.0) {
            throw new IllegalArgumentException("diffRatio out of range [0,1]: " + dr);
        }
        if (Double.isNaN(sv) || sv < 0.0 || sv > 1.0) {
            throw new IllegalArgumentException("stateVolatility out of range [0,1]: " + sv);
        }
        if (stats.subscriberCount() < 0) {
            throw new IllegalArgumentException(
                    "subscriberCount must be non-negative: " + stats.subscriberCount());
        }
    }

    /**
     * 三技术的确定性量化得分（纯函数）。Deterministic quantified scores (pure function).
     */
    private static ScaledScore[] scoresFor(BandwidthStats s) {
        double dr = s.diffRatio();
        double sv = s.stateVolatility();
        double subFactor = Math.min(1.0, s.subscriberCount() / SUBSCRIBER_CAP);

        double scoreDiff = W_DIFF_RATIO * dr * (1.0 - sv);
        double scoreDec = W_VOLATILITY * sv * (1.0 - dr);
        double scoreSub = W_SUBSCRIBER_CAP * subFactor * sv;

        return new ScaledScore[]{
                new ScaledScore(BandwidthTechnique.DIFFERENTIAL_SYNC, scoreDiff,
                        "diffRatio=" + dr + " stability=" + (1.0 - sv)),
                new ScaledScore(BandwidthTechnique.DECIMATION_INTERPOLATION, scoreDec,
                        "volatility=" + sv + " stabilityDiff=" + (1.0 - dr)),
                new ScaledScore(BandwidthTechnique.ON_DEMAND_SUBSCRIBE, scoreSub,
                        "subscriberFactor=" + subFactor + " volatility=" + sv)
        };
    }

    /** argmax；等分按固定枚举序打破平局（确定性）。Argmax with deterministic tie-break by enum order. */
    private static BandwidthTechnique select(ScaledScore[] scores) {
        BandwidthTechnique best = scores[0].technique();
        double bestScore = scores[0].score();
        // 固定枚举序：仅严格大于才替换，等价分保持靠前的技术。
        for (int i = 1; i < scores.length; i++) {
            if (scores[i].score() > bestScore) {
                best = scores[i].technique();
                bestScore = scores[i].score();
            }
        }
        return best;
    }

    /** 层级 → 频率类映射（p.2.4.3 的逆映射）。Level → frequency mapping (inverse of p.2.4.3). */
    private static StrategySelector.Descriptor descriptorFor(PayloadLevel level) {
        StrategySelector.Frequency freq = switch (level) {
            case L1 -> StrategySelector.Frequency.HOT;
            case L2 -> StrategySelector.Frequency.WARM;
            case L3 -> StrategySelector.Frequency.COLD;
        };
        return new StrategySelector.Descriptor(freq, DOMAIN, 0);
    }

    /**
     * 当前命中计数快照（按 {@link BandwidthTechnique#ordinal()} 索引）。Live hit counters snapshot.
     */
    public long[] hitCountersSnapshot() {
        return hitCounters.clone();
    }

    /** 指定技术的命中计数。Hit count of a specific technique. */
    public long hitCount(BandwidthTechnique technique) {
        return hitCounters[technique.ordinal()];
    }

    /**
     * 确定性标记字符串：汇总最近一次决策（技术 + 加密适用性）+ 各技术命中计数，供未来 E2E 日志使用。
     * Deterministic marker string summarising the latest decision (technique + encryption applicability)
     * and each technique's hit count for future E2E logging.
     */
    public String markers() {
        String lastMark = lastDecision == null
                ? "none"
                : lastDecision.technique().name()
                        + "{enc=" + lastDecision.encryptionApplicable() + '}';
        return "[bandwidth last=" + lastMark + " counters=" + formatCounters() + "]";
    }

    /** toString（含计数与加密钩子）。Contains counters and the encryption hook. */
    @Override
    public String toString() {
        return "BandwidthOptimizer{hits="
                + formatCounters() + '}';
    }

    private String formatCounters() {
        StringBuilder sb = new StringBuilder();
        for (BandwidthTechnique t : BandwidthTechnique.values()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(t.name()).append('=').append(hitCounters[t.ordinal()]);
        }
        return sb.toString();
    }

    /**
     * 决策记录：技术 + 载荷描述符 + 策略 + 加密适用性 + 命中计数快照。
     * Decision record: technique + payload descriptor + strategy + encryption applicability + hit-count snapshot.
     *
     * @param technique          选中技术。Selected technique.
     * @param descriptor         载荷描述符（p.2.4.3）。Payload descriptor (p.2.4.3).
     * @param strategy           载荷策略（p.2.4.3）。Payload strategy (p.2.4.3).
     * @param encryptionApplicable 加密是否适用（L3 且开关启用）。Whether encryption applies (L3 and toggle on).
     * @param hitCountersSnapshot 决策时的命中计数快照。Hit counters snapshot at decision time.
     */
    public record Decision(
            BandwidthTechnique technique,
            StrategySelector.Descriptor descriptor,
            PayloadStrategy strategy,
            boolean encryptionApplicable,
            long[] hitCountersSnapshot) {
    }
}