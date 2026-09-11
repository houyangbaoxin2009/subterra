package io.toterra.subterra.api.time;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static io.toterra.subterra.api.time.TimeScaleApi.validateRate;

/**
 * p.2.26.2 按域流速的不可变聚合契约（对外契约面，纯 JDK）：不可变四域流速聚合。流速表归一化为固定
 * {@link TimeDomain} 枚举序（与构建顺序无关）；从未被显式设置的域经 {@link #get(TimeDomain)} 返回
 * {@code 1.0}，经 {@link #effective(TimeDomain)} 回退到全局流速。语义与 {@code engine.time.TimeScale}
 * （p.2.26.1）完全对齐——本处为契约形状、无 engine 依赖，engine 为实现镜像。
 * <p>确定性与不可变：{@link #get(TimeDomain)} 返回显式设置值或未设置时 {@code 1.0}；
 * {@link #with(TimeDomain, double)} 派生出新实例；{@link #effective(TimeDomain)} 沿文档化回退链
 * {@code GLOBAL -> ENTITY / ZONE / PLAYER 之一} 将某域解析为有效流速——未设置的非全局域解析为
 * {@code effective(GLOBAL)}，未设置的 GLOBAL 解析为 {@code 1.0}。取值为有限且 {@code > 0}；equals/hashCode
 * 以按域字段集为准，相等性与顺序无关。无随机、无时序。
 * <p>
 * p.2.26.2 immutable aggregate of per-domain flow rates (the external contract surface, pure JDK): an
 * immutable four-domain flow-rate aggregate. The rate map is normalized to the fixed {@link TimeDomain}
 * enumeration order (regardless of build order); a domain never explicitly set reports {@code 1.0} via
 * {@link #get(TimeDomain)} and falls back to the global rate via {@link #effective(TimeDomain)}. Semantics
 * fully align with {@code engine.time.TimeScale} (p.2.26.1) — this is the contract shape with no engine
 * dependency; the engine mirrors as its implementation.
 * <p>Deterministic and immutable: {@link #get(TimeDomain)} returns the explicitly set value or {@code 1.0}
 * when absent; {@link #with(TimeDomain, double)} derives a new instance; {@link #effective(TimeDomain)}
 * resolves a domain along the documented fall-back chain {@code GLOBAL -> ENTITY / ZONE / PLAYER} — an unset
 * non-global domain resolves to {@code effective(GLOBAL)} and an unset GLOBAL resolves to {@code 1.0}. Values
 * are finite and {@code > 0}; equals/hashCode use the per-domain field set so equality is order-independent.
 * No randomness, no timing.
 */
public final class TimeScaleSpec {

    /** Explicitly set per-domain rates, keyed in fixed enumeration order. */
    private final Map<TimeDomain, Double> rates;
    /** Domains that were explicitly set (drives effective fall-back). */
    private final Map<TimeDomain, Boolean> set;

    private TimeScaleSpec(Map<TimeDomain, Double> rates, Map<TimeDomain, Boolean> set) {
        this.rates = rates;
        this.set = set;
    }

    /**
     * Builds a {@link TimeScaleSpec} from the given explicit rates. The map is normalized to the fixed
     * {@link TimeDomain} enumeration order; absent domains are recorded as unset (reporting {@code 1.0} via
     * {@link #get(TimeDomain)}). Each value must be finite and {@code > 0}.
     * / 由给定显式流速构建 {@link TimeScaleSpec}。表统一归一化为固定 {@link TimeDomain} 枚举序；缺失域被记录为
     * 未设置（经 {@link #get(TimeDomain)} 返回 {@code 1.0}）。每项取值必须有限且 {@code > 0}。
     *
     * @param rates the explicit per-domain rates (non-null).
     * @return a new immutable {@link TimeScaleSpec}.
     * @throws NullPointerException     if {@code rates} is null.
     * @throws IllegalArgumentException if any value is not finite and {@code > 0}.
     */
    public static TimeScaleSpec of(Map<TimeDomain, Double> rates) {
        Objects.requireNonNull(rates, "rates must not be null");
        EnumMap<TimeDomain, Double> normalized = new EnumMap<>(TimeDomain.class);
        EnumMap<TimeDomain, Boolean> set = new EnumMap<>(TimeDomain.class);
        for (TimeDomain d : TimeDomain.values()) {
            Double v = rates.get(d);
            if (v != null) {
                validateRate(v);
                normalized.put(d, v);
                set.put(d, Boolean.TRUE);
            } else {
                normalized.put(d, 1.0D);
            }
        }
        return new TimeScaleSpec(Collections.unmodifiableMap(new LinkedHashMap<>(normalized)),
                Collections.unmodifiableMap(new EnumMap<>(set)));
    }

    /** A fully-unset {@link TimeScaleSpec}: every domain reports {@code 1.0} everywhere. /
     *  全未设置的 {@link TimeScaleSpec}：每域各处均 {@code 1.0}。 */
    public static TimeScaleSpec neutral() {
        return of(Map.of());
    }

    /** The explicitly set or default value for a domain ({@code 1.0} when unset). /
     *  某域的显式值，未设置时为默认 {@code 1.0}。 */
    public double get(TimeDomain domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        return rates.get(domain);
    }

    /** Derives a new {@link TimeScaleSpec} with one domain replaced (marked explicitly set). /
     *  派生一个替换某域并标记为显式设置的新实例。 */
    public TimeScaleSpec with(TimeDomain domain, double value) {
        Objects.requireNonNull(domain, "domain must not be null");
        validateRate(value);
        Map<TimeDomain, Double> copy = new EnumMap<>(TimeDomain.class);
        for (TimeDomain d : set.keySet()) {
            copy.put(d, rates.get(d));
        }
        copy.put(domain, value);
        return of(copy);
    }

    /**
     * Resolves a domain to its effective flow rate along the documented fall-back chain
     * {@code GLOBAL -> ENTITY / ZONE / PLAYER}: an explicitly set domain returns its own value; an unset
     * non-global domain resolves to {@code effective(GLOBAL)}; an unset GLOBAL resolves to {@code 1.0}.
     * Deterministic. / 沿文档化回退链 {@code GLOBAL -> ENTITY / ZONE / PLAYER} 将某域解析为有效流速：显式设置的
     * 域返回自身值；未设置的非全局域解析为 {@code effective(GLOBAL)}；未设置的 GLOBAL 解析为 {@code 1.0}。确定性。
     */
    public double effective(TimeDomain domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        if (set.containsKey(domain)) {
            return rates.get(domain);
        }
        if (domain == TimeDomain.GLOBAL) {
            return 1.0D;
        }
        return rates.get(TimeDomain.GLOBAL);
    }

    /** Whether the given domain was explicitly set (vs. falling back to the default). /
     *  给定域是否被显式设置（而非回退默认）。 */
    public boolean isSet(TimeDomain domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        return set.containsKey(domain);
    }

    /**
     * The explicit per-domain rates, all four domains present in fixed enumeration order
     * ({@code GLOBAL, ENTITY, ZONE, PLAYER}), with {@code 1.0} for unset domains. Immutable.
     * / 显式按域流速，四域齐备且按固定枚举序（{@code GLOBAL, ENTITY, ZONE, PLAYER}），未设置域为 {@code 1.0}。不可变。
     */
    public Map<TimeDomain, Double> rates() {
        return Collections.unmodifiableMap(rates);
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof TimeScaleSpec other && rates.equals(other.rates) && set.equals(other.set));
    }

    @Override
    public int hashCode() {
        return 31 * rates.hashCode() + set.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TimeScaleSpec{");
        boolean first = true;
        for (TimeDomain d : TimeDomain.values()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(d.form()).append('=').append(rates.get(d));
        }
        sb.append('}');
        return sb.toString();
    }
}