package io.toterra.subterra.engine.save.migrate;

import io.toterra.subterra.engine.config.TdValue;

import java.util.Map;

/**
 * 原版 {@code level.dat} 语义核的轻量载体（p.2.3.2）。字段名按原版 level.dat 长期稳定的 NBT key
 * 提取：{@code LevelName} / {@code RandomSeed}（旧版）或 {@code WorldGenSettings.seed}（新版）、
 * {@code DayTime} 或 {@code Time}、{@code GameRules}；按 {@code NbtTreeReader} + {@code LevelDatReader}
 * 以纯 JDK 按 key 提取，engine 不依赖任何 MC 类。{@code rules} 是 {@code GameRules} 的键值视图
 * （值归类为 {@link TdValue}：bool 折叠 1/0、数值为 long、字符串原样），供上层叠到
 * {@code SaveContainer} 规则槽。
 * <p>
 * Lightweight carrier of the semantic core of the vanilla {@code level.dat} (p.2.3.2). Field names
 * follow the long-stable NBT keys of the vanilla level.dat: {@code LevelName}, {@code RandomSeed}
 * (legacy) or {@code WorldGenSettings.seed} (modern), {@code DayTime} or {@code Time},
 * {@code GameRules}; extraction is key-based via {@link NbtTreeReader} + {@link LevelDatReader} in
 * pure JDK — the engine never depends on Minecraft classes. {@code rules} is the key-view of
 * {@code GameRules} (values normalised to {@link TdValue}: booleans folded to 1/0, numerics to long,
 * strings kept as-is) for stacking onto the {@code SaveContainer} rule slot at a higher layer.
 *
 * @param name  世界名 world name ({@code LevelName}).
 * @param seed  世界种子 world seed ({@code RandomSeed} or {@code WorldGenSettings.seed}).
 * @param dayTime 世界内时间 ticks ({@code DayTime} or {@code Time}).
 * @param rules {@code GameRules} 键值视图（顺序保序）/ key→value view of {@code GameRules}.
 */
public record LevelDatum(String name, long seed, long dayTime, Map<String, TdValue> rules) {
}