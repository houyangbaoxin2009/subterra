package io.toterra.subterra.api.time;

import java.util.Objects;

import static io.toterra.subterra.api.time.TimeScaleApi.validateRate;

/**
 * p.2.26.2 针对单个 {@link TimeDomain} 的不可变流速档位契约（对外契约面，纯 JDK）。{@code rate} 是相对
 * 原版时间流速的无量纲倍率：{@code 1.0} 为原速（不变），{@code > 1} 加快该域时间流速（每墙钟单位更多
 * tick），{@code < 1} 减慢。流速必须有限且严格 {@code > 0}。语义与 {@code engine.time.FlowRate}（p.2.26.1）
 * 一致，engine 为实现镜像、api 不依赖 engine。
 * <p>确定性：流速在规范构造时校验为有限且 {@code > 0}；同输入恒得同 double 位。
 * <p>
 * p.2.26.2 immutable flow-rate assignment for one {@link TimeDomain} (the external contract surface, pure
 * JDK). The {@code rate} is a dimensionless speed multiplier relative to vanilla time-flow: {@code 1.0} is
 * normal speed, {@code > 1} speeds time up within that domain, {@code < 1} slows it down. A rate must be
 * finite and strictly {@code > 0}. Semantics match {@code engine.time.FlowRate} (p.2.26.1); the engine
 * mirrors as its implementation and the api does not depend on the engine.
 * <p>Deterministic: the rate is validated at canonical-constructor time as finite and {@code > 0}; the same
 * input always yields the identical double bits.
 *
 * @param domain the time domain this rate applies to. 该流速所作用的域。
 * @param rate   the flow-rate multiplier (finite, {@code > 0}). 流速倍率（有限、{@code > 0}）。
 */
public record FlowRateSpec(TimeDomain domain, double rate) {

    /**
     * 校验 {code domain} 非 null、{@code rate} 有限且 {@code > 0}。
     * Validates that {@code domain} is non-null and {@code rate} is finite and {@code > 0}.
     *
     * @throws IllegalArgumentException if {@code domain} is null, or {@code rate} is not finite and {@code > 0}.
     *                                  若 {@code domain} 为 null，或 {@code rate} 非有限且非 {@code > 0} 时抛出。
     */
    public FlowRateSpec {
        Objects.requireNonNull(domain, "domain must not be null");
        validateRate(rate);
    }
}