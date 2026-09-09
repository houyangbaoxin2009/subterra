package io.toterra.subterra.engine.save.migrate;

import io.toterra.subterra.engine.config.TdValue;

import java.util.Map;

/**
 * 原版 {@code players/<uuid>.dat} 语义核的轻量载体（p.2.3.3）。字段名按原版 players dat 长期稳定的
 * NBT key 提取：{@code Dimension}（STRING）、{@code Pos}（LIST DOUBLE ×3）、{@code Rotation}
 * （LIST FLOAT ×2）、{@code DataVersion}（INT）；其余键按 schema-free 跳过：嵌套结构（如
 * {@code Abilities} COMPOUND、{@code Inventory} LIST）不进 {@code extra}，仅保留根 compound 里
 * 的扁平标量键视图（Key→{@link TdValue}，数值归一为 long、字符串原样）。按 {@code NbtTreeReader} +
 * {@code PlayerDatReader} 以纯 JDK 提取，engine 不依赖任何 MC 类。{@code uuid} 通常由外层从文件名
 * （{@code players/&lt;uuid&gt;.dat}）注入，本 carrier 不负责反解 UUID。
 * <p>
 * Lightweight carrier of the semantic core of the vanilla {@code players/<uuid>.dat} (p.2.3.3).
 * Field names follow the long-stable NBT keys of the vanilla player file: {@code Dimension} (STRING),
 * {@code Pos} (LIST DOUBLE x3), {@code Rotation} (LIST FLOAT x2), {@code DataVersion} (INT). The
 * remaining keys are skipped schema-free: nested structures (e.g. {@code Abilities} COMPOUND,
 * {@code Inventory} LIST) stay out of {@code extra}, which holds only the flat scalar-key view of the
 * root compound (Key→{@link TdValue}, numerics normalised to long, strings kept as-is). Extraction is
 * key-based via {@link NbtTreeReader} + {@link PlayerDatReader} in pure JDK — the engine never depends
 * on Minecraft classes. {@code uuid} is typically injected by a caller from the file name
 * ({@code players/&lt;uuid&gt;.dat}); this carrier does not decode a UUID itself.
 *
 * @param uuid    玩家唯一标识（默认 ""，由调用方注入）/ player unique id (default "", caller-injected).
 * @param dimension 所在维度 ({@code Dimension})，默认 "".
 * @param pos     位置 doubles ({@code Pos})，默认 {0,0,0}.
 * @param rotation 朝向 ({@code Rotation})，默认 {0,0}.
 * @param dataVersion 数据版本 ({@code DataVersion})，默认 0.
 * @param extra   其余非核心键的扁平标量视图（Key→标量；嵌套结构跳过）/ flat scalar view of the
 *                remaining non-core keys (Key→scalar; nested structures skipped).
 */
public record PlayerDatum(String uuid, String dimension, double[] pos, float[] rotation,
                          int dataVersion, Map<String, TdValue> extra) {
}