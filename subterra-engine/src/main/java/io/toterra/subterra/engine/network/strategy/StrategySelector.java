package io.toterra.subterra.engine.network.strategy;

import java.util.Collection;
import java.util.Objects;

/**
 * 确定性纯函数选择器（p.2.4.3）：给一个载荷描述符
 * {@code Descriptor(frequency, domain, kindFlags)} 返回唯一一个 {@link PayloadStrategy}。
 * 同输入恒同输出，无随机。映射规则：
 * <ul>
 *   <li>频率类（drop-in 投放依据）决定层级：{@code HOT → L1}、{@code WARM → L2}、{@code COLD → L3}。</li>
 *   <li>L3 输出 {@code encrypted=true}；L1 输出 {@code differential=true}。</li>
 *   <li>兴趣域透传；{@code subscribedTo} 对 L1/L2 提供确定性的订阅门控过滤。</li>
 * </ul>
 * 纯 JDK，引擎层抽象，不接 MC 实体。
 * <p>
 * Deterministic pure-function selector (p.2.4.3): given a payload descriptor
 * {@code Descriptor(frequency, domain, kindFlags)} it returns exactly one {@link PayloadStrategy}.
 * Same input always yields the same output, no randomness. Mapping rule: frequency class decides the
 * drop-in level ({@code HOT → L1}, {@code WARM → L2}, {@code COLD → L3}); L3 is produced with
 * {@code encrypted=true}, L1 with {@code differential=true}; the interest domain is passed through and
 * {@link #subscribedTo} offers a deterministic subscription gate for L1/L2. Pure JDK, engine-layer
 * abstraction, no Minecraft entities.
 */
public final class StrategySelector {

    private StrategySelector() {
    }

    /** 频率类：决定投放层级。Frequency class: decides the drop-in level. */
    public enum Frequency {
        /** 高频（兴趣域小字段频繁变更）→ L1。Hot: high-frequency small-field changes → L1. */
        HOT,
        /** 中频（周期性快照+变更流）→ L2。Warm: periodic snapshot + change stream → L2. */
        WARM,
        /** 低频（加密）→ L3。Cold: low-frequency encrypted → L3. */
        COLD
    }

    /**
     * 载荷描述符：频率类 + 域名 + 种类标志。Payload descriptor: frequency class + domain name + kind flags.
     *
     * @param kindFlags 自由形式正整数标志，用于刻画载荷种类；当前仅原样记录，不参与层级推导（后续可扩展）。
     *                  Free-form positive integer flags describing payload kind; recorded verbatim today,
     *                  not part of level derivation (may be extended later).
     */
    public record Descriptor(Frequency frequency, String domain, int kindFlags) {
        public Descriptor {
            Objects.requireNonNull(frequency, "frequency must not be null");
            Objects.requireNonNull(domain, "domain must not be null");
            if (kindFlags < 0) {
                throw new IllegalArgumentException("kind flags must be non-negative: " + kindFlags);
            }
        }
    }

    /**
     * 选择策略（纯函数，确定性）。Select the strategy (pure function, deterministic).
     */
    public static PayloadStrategy select(Descriptor d) {
        PayloadLevel level = switch (d.frequency()) {
            case HOT -> PayloadLevel.L1;
            case WARM -> PayloadLevel.L2;
            case COLD -> PayloadLevel.L3;
        };
        boolean differential = level == PayloadLevel.L1;
        boolean encrypted = level == PayloadLevel.L3;
        return PayloadStrategy.builder(level)
                .interestDomain(d.domain())
                .differential(differential)
                .encrypted(encrypted)
                .build();
    }

    /**
     * 订阅门控（确定性）：策略的兴趣域是否在所需域名集合中。用于 L1/L2 的确定性订阅过滤。
     * Subscription gate (deterministic): whether the strategy's interest domain is in the wanted set.
     * Used for deterministic subscription filtering of L1/L2.
     */
    public static boolean subscribedTo(PayloadStrategy strategy, Collection<String> wantedDomains) {
        if (wantedDomains == null) {
            return false;
        }
        return wantedDomains.contains(strategy.interestDomain());
    }
}