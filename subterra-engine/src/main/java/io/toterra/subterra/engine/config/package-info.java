/**
 * 配置核心（纯 JDK）：双层配置的 td 持久化与装载模型。
 * Configuration core (pure JDK): the dual-layer config's td persistence and
 * loading model.
 * <p>
 * 包含 td 文档的具体化 {@link io.toterra.subterra.engine.config.Td}、表
 * {@link io.toterra.subterra.engine.config.TdTable}、值
 * {@link io.toterra.subterra.engine.config.TdValue} 与条目
 * {@link io.toterra.subterra.engine.config.TdEntry}，以及打包双层配置的
 * {@link io.toterra.subterra.engine.config.ConfigPack}。纯 JVM，不碰 MC；
 * 确定性求值沿用全 engine 范式。
 * <p>
 * Holds the concrete Td document {@link io.toterra.subterra.engine.config.Td},
 * table {@link io.toterra.subterra.engine.config.TdTable}, value
 * {@link io.toterra.subterra.engine.config.TdValue} and entry
 * {@link io.toterra.subterra.engine.config.TdEntry} model, together with the
 * dual-layer {@link io.toterra.subterra.engine.config.ConfigPack}. Pure JVM, no
 * MC coupling; deterministic evaluation follows the whole-engine paradigm.
 */
package io.toterra.subterra.engine.config;