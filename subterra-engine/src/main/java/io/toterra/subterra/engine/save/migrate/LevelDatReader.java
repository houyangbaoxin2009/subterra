package io.toterra.subterra.engine.save.migrate;

import io.toterra.subterra.engine.config.TdValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版 {@code level.dat} 语义提取器（p.2.3.2）。提取规则：根 compound 内取钥匙 {@code Data} 的子
 * compound（level compound），字段级按长期稳定的 NBT key 提取：
 * <ul>
 *   <li>{@code name} ← {@code Data.LevelName}（STRING）；</li>
 *   <li>{@code seed} ← {@code Data.RandomSeed}（LONG，旧版）缺失时回退
 *       {@code Data.WorldGenSettings.seed}（LONG 或 INT，新版）；</li>
 *   <li>{@code dayTime} ← {@code Data.DayTime}（LONG/INT）缺失时回退 {@code Data.Time}（LONG/INT）；</li>
 *   <li>{@code rules} ← {@code Data.GameRules}（COMPOUND）：迭代每一 {@code k→v}，按 {@code v} 的 tag
 *       归一为 {@link TdValue}——布尔（数值 0/1 或字符串 {@code true}/{@code false}）→ {@code TdValue.of(boolean)}
 *       折叠 1/0；整型数值 → {@code of(long)}；可解析数字字符串 → {@code of(long)}；其余字符串原样。</li>
 * </ul>
 * 字段缺失给默认（name="", seed=0, dayTime=0, rules 空），不做严格校验（schema-free）。解析失败或
 * 非 level compound → 同默认。javadoc 注明提取规则为只读迁移前提，不使用任何 MC 类。
 * <p>
 * Vanilla {@code level.dat} semantic extractor (p.2.3.2). Extraction rules: within the root compound
 * the child compound keyed {@code Data} is the level compound; fields are read by the long-stable NBT
 * keys:
 * <ul>
 *   <li>{@code name} ← {@code Data.LevelName} (STRING);</li>
 *   <li>{@code seed} ← {@code Data.RandomSeed} (LONG, legacy), falling back to
 *       {@code Data.WorldGenSettings.seed} (LONG or INT, modern);</li>
 *   <li>{@code dayTime} ← {@code Data.DayTime} (LONG/INT), falling back to {@code Data.Time} (LONG/INT);</li>
 *   <li>{@code rules} ← {@code Data.GameRules} (COMPOUND): each {@code k→v} is normalised by the tag of
 *       {@code v} into {@link TdValue} — boolean (numeric 0/1 or string {@code true}/{@code false}) →
 *       {@code TdValue.of(boolean)} folded to 1/0; integral numerics → {@code of(long)}; parseable numeric
 *       strings → {@code of(long)}; any other string kept as-is.</li>
 * </ul>
 * Missing fields fall back to defaults (name = "", seed = 0, dayTime = 0, rules empty); no strict
 * validation (schema-free). Parse failures / a non-level compound yield the same defaults. This is a
 * read-only migration prerequisite and uses no Minecraft classes.
 */
public final class LevelDatReader {

    private static final String DATA = "Data";
    private static final String WORLD_GEN_SETTINGS = "WorldGenSettings";
    private static final String GAME_RULES = "GameRules";

    private LevelDatReader() {
    }

    /**
     * 从 {@code level.dat} 字节解析语义核心 {@link LevelDatum}。见类 javadoc 的提取规则。
     * Parses the semantic core {@link LevelDatum} from the {@code level.dat} bytes per the class javadoc.
     */
    public static LevelDatum read(byte[] datBytes) {
        try {
            NbtNode root = NbtTreeReader.readCompressed(datBytes);
            if (root == null || root.tag() != NbtTreeReader.TAG_COMPOUND) {
                return defaults();
            }
            NbtNode data = child((Map<?, ?>) root.payload(), DATA);
            if (data == null || data.tag() != NbtTreeReader.TAG_COMPOUND) {
                return defaults();
            }
            @SuppressWarnings("unchecked")
            Map<String, NbtNode> level = (Map<String, NbtNode>) data.payload();
            String name = string(level, "LevelName");
            long seed = seedOf(level);
            long dayTime = longOr(level, "DayTime", longOr(level, "Time", 0L));
            Map<String, TdValue> rules = gameRules(level);
            return new LevelDatum(name, seed, dayTime, rules);
        } catch (RuntimeException e) {
            return defaults();
        }
    }

    private static LevelDatum defaults() {
        return new LevelDatum("", 0L, 0L, Map.of());
    }

    private static NbtNode child(Map<?, ?> compound, String key) {
        Object v = compound.get(key);
        return v instanceof NbtNode n ? n : null;
    }

    private static String string(Map<String, NbtNode> level, String key) {
        NbtNode n = level.get(key);
        if (n == null || n.tag() != NbtTreeReader.TAG_STRING || !(n.payload() instanceof String s)) {
            return "";
        }
        return s;
    }

    private static long seedOf(Map<String, NbtNode> level) {
        NbtNode randomSeed = level.get("RandomSeed");
        Long s = intLike(randomSeed);
        if (s != null) {
            return s;
        }
        NbtNode wgs = level.get(WORLD_GEN_SETTINGS);
        if (wgs != null && wgs.tag() == NbtTreeReader.TAG_COMPOUND
                && wgs.payload() instanceof Map<?, ?> settingsMap) {
            NbtNode seed = child(settingsMap, "seed");
            Long v = intLike(seed);
            if (v != null) {
                return v;
            }
        }
        return 0L;
    }

    private static long longOr(Map<String, NbtNode> level, String key, long def) {
        Long v = intLike(level.get(key));
        return v != null ? v : def;
    }

    /** 把 LONG/INT/SHORT/BYTE 数字 tag 归一为 long；其它 → null。Normalises numeric tags to long; else null. */
    private static Long intLike(NbtNode n) {
        if (n == null || !(n.payload() instanceof Number num)) {
            return null;
        }
        return num.longValue();
    }

    private static Map<String, TdValue> gameRules(Map<String, NbtNode> level) {
        NbtNode rulesNode = level.get(GAME_RULES);
        if (rulesNode == null || rulesNode.tag() != NbtTreeReader.TAG_COMPOUND
                || !(rulesNode.payload() instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (!(e.getKey() instanceof String k) || !(e.getValue() instanceof NbtNode v)) {
                continue;
            }
            String norm = normalizeRuleName(k);
            TdValue t = toRuleValue(v);
            if (t != null) {
                out.put(norm, t);
            }
        }
        return out;
    }

    private static String normalizeRuleName(String k) {
        return k;
    }

    private static TdValue toRuleValue(NbtNode v) {
        Object p = v.payload();
        if (p instanceof Number num) {
            return TdValue.of(num.longValue());
        }
        if (p instanceof String s) {
            String t = s.trim();
            if (t.equalsIgnoreCase("true")) {
                return TdValue.of(true);
            }
            if (t.equalsIgnoreCase("false")) {
                return TdValue.of(false);
            }
            try {
                return TdValue.of(Long.parseLong(t));
            } catch (NumberFormatException e) {
                return TdValue.str(s);
            }
        }
        if (p instanceof boolean[] barr) { // defensive; NBT has no native bool
            return TdValue.of(barr.length > 0 && barr[0]);
        }
        return null;
    }
}