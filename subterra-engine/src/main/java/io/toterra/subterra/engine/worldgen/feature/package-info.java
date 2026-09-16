/**
 * p.2.29.3 世界生成「规则 → 特征/成矿」装配核心（纯 JDK）：{@link
 * io.toterra.subterra.engine.worldgen.feature.FeatureAssembly} 把有效规则表（{@code Map<String,String>}）
 * 一次解析为确定性挂载计划（矿物 / 植被 placed feature、母岩前置、矿脉走向、结构护栏），供 runtime 侧
 * 的 MC 装配面消费。不 import MC、不读存储、不碰 I/O；与 p.2.29.1 {@code engine.worldgen.assembly} 同
 * 一确定性范式（未知键忽略、缺失键取缺省、非法值整表拒绝、同输入同字节）。
 *
 * <p>p.2.29.3 the "rules → feature/ore" worldgen assembly core (pure JDK): {@link
 * io.toterra.subterra.engine.worldgen.feature.FeatureAssembly} parses an effective rule table into a
 * deterministic mount plan (mineral / vegetation placed features, host-rock prerequisite, vein trend,
 * structure guard), consumed by the runtime MC assembly surface. No MC import, no storage, no I/O;
 * same deterministic paradigm as p.2.29.1 {@code engine.worldgen.assembly}.
 */
package io.toterra.subterra.engine.worldgen.feature;
