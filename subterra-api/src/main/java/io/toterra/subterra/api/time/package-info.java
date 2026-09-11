/**
 * p.2.26.2 时间缩放契约面：四域固定序 TimeDomain + FlowRateSpec/TimeScaleSpec 值类型 + TimeScaleApi
 * 确定性门面（纯 JDK，api 不依赖 engine；engine.time 为实现侧镜像）。
 * <p>
 * p.2.26.2 time-scaling contract surface: four-domain fixed-order TimeDomain + FlowRateSpec/TimeScaleSpec
 * value types + the deterministic TimeScaleApi facade (pure JDK, no engine dependency; engine.time mirrors as
 * its implementation).
 */
package io.toterra.subterra.api.time;