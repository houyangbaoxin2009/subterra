package io.toterra.subterra.engine.save.migrate;

import io.toterra.subterra.engine.config.TdValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版 {@code players/<uuid>.dat} 语义提取器（p.2.3.3）。与 level.dat 不同，玩家文件的根 compound 本身
 * 就是玩家数据（无 {@code Data} 外壳）。提取规则：按长期稳定的 NBT key 提取核心字段：
 * <ul>
 *   <li>{@code dimension} ← {@code Dimension}（STRING）；</li>
 *   <li>{@code pos} ← {@code Pos}（LIST DOUBLE ×3）；</li>
 *   <li>{@code rotation} ← {@code Rotation}（LIST FLOAT ×2）；</li>
 *   <li>{@code dataVersion} ← {@code DataVersion}（INT）；</li>
 *   <li>{@code extra} ← 根 compound 其余非核心标量键的扁平视图：数值 → {@link TdValue#of(long)}、
 *       字符串 → {@link TdValue#str}；嵌套结构（COMPOUND/LIST/各数组）按 schema-free 跳过，不进
 *       {@code extra}。</li>
 * </ul>
 * 字段缺失给默认（uuid="", dimension="", pos={0,0,0}, rotation={0,0}, dataVersion=0），不做严格校验
 * （schema-free）。解析失败或非 compound 根 → 同默认。{@code uuid} 不在本提取器职责内（文件名携带）。
 * 只读迁移前提，不使用任何 MC 类。
 * <p>
 * Vanilla {@code players/<uuid>.dat} semantic extractor (p.2.3.3). Unlike level.dat, the root
 * compound of the player file is the player data itself (no {@code Data} wrapper). Extraction rules:
 * <ul>
 *   <li>{@code dimension} ← {@code Dimension} (STRING);</li>
 *   <li>{@code pos} ← {@code Pos} (LIST DOUBLE x3);</li>
 *   <li>{@code rotation} ← {@code Rotation} (LIST FLOAT x2);</li>
 *   <li>{@code dataVersion} ← {@code DataVersion} (INT);</li>
 *   <li>{@code extra} ← flat view of the remaining non-core scalar keys of the root compound:
 *       numerics → {@link TdValue#of(long)}, strings → {@link TdValue#str}; nested structures
 *       (COMPOUND/LIST/arrays) are skipped schema-free and never enter {@code extra}.</li>
 * </ul>
 * Missing fields fall back to defaults (uuid = "", dimension = "", pos = {0,0,0}, rotation = {0,0},
 * dataVersion = 0); no strict validation (schema-free). Parse failures / a non-compound root yield
 * the same defaults. {@code uuid} is out of this extractor's scope (carried by the file name). This is
 * a read-only migration prerequisite and uses no Minecraft classes.
 */
public final class PlayerDatReader {

    private static final String DIMENSION = "Dimension";
    private static final String POS = "Pos";
    private static final String ROTATION = "Rotation";
    private static final String DATA_VERSION = "DataVersion";

    private static final double[] DEF_POS = {0.0, 0.0, 0.0};
    private static final float[] DEF_ROT = {0.0f, 0.0f};

    private PlayerDatReader() {
    }

    /**
     * 从 {@code players/<uuid>.dat} 字节解析语义核心 {@link PlayerDatum}。见类 javadoc 的提取规则。
     * Parses the semantic core {@link PlayerDatum} from the player {@code .dat} bytes per the class
     * javadoc.
     */
    public static PlayerDatum read(byte[] datBytes) {
        try {
            NbtNode root = NbtTreeReader.readCompressed(datBytes);
            if (root == null || root.tag() != NbtTreeReader.TAG_COMPOUND) {
                return defaults();
            }
            @SuppressWarnings("unchecked")
            Map<String, NbtNode> p = (Map<String, NbtNode>) root.payload();
            String dimension = string(p, DIMENSION);
            double[] pos = doubles(p.get(POS));
            float[] rotation = floats(p.get(ROTATION));
            int dataVersion = intAt(p, DATA_VERSION);
            Map<String, TdValue> extra = extraOf(p);
            return new PlayerDatum("", dimension, pos, rotation, dataVersion, extra);
        } catch (RuntimeException e) {
            return defaults();
        }
    }

    private static PlayerDatum defaults() {
        return new PlayerDatum("", "", DEF_POS.clone(), DEF_ROT.clone(), 0, Map.of());
    }

    private static String string(Map<String, NbtNode> p, String key) {
        NbtNode n = p.get(key);
        if (n == null || n.tag() != NbtTreeReader.TAG_STRING || !(n.payload() instanceof String s)) {
            return "";
        }
        return s;
    }

    /** {@code Pos}：LIST DOUBLE ×3；缺失/错型给默认。 */
    private static double[] doubles(NbtNode n) {
        if (n == null || n.tag() != NbtTreeReader.TAG_LIST || !(n.payload() instanceof List<?> list)) {
            return DEF_POS.clone();
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            out[i] = (o instanceof NbtNode nn && nn.payload() instanceof Number num) ? num.doubleValue() : 0.0;
        }
        return out;
    }

    /** {@code Rotation}：LIST FLOAT ×2；缺失/错型给默认。 */
    private static float[] floats(NbtNode n) {
        if (n == null || n.tag() != NbtTreeReader.TAG_LIST || !(n.payload() instanceof List<?> list)) {
            return DEF_ROT.clone();
        }
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            out[i] = (o instanceof NbtNode nn && nn.payload() instanceof Number num) ? num.floatValue() : 0.0f;
        }
        return out;
    }

    private static int intAt(Map<String, NbtNode> p, String key) {
        NbtNode n = p.get(key);
        if (n != null && n.payload() instanceof Number num) {
            return num.intValue();
        }
        return 0;
    }

    /** 根 compound 的扁平标量视图：跳过核心键与嵌套结构，其余标量键归一为 {@link TdValue}。 */
    private static Map<String, TdValue> extraOf(Map<String, NbtNode> p) {
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (Map.Entry<String, NbtNode> e : p.entrySet()) {
            String k = e.getKey();
            if (k.equals(DIMENSION) || k.equals(POS) || k.equals(ROTATION) || k.equals(DATA_VERSION)) {
                continue;
            }
            Object payload = e.getValue().payload();
            if (payload instanceof Number num) {         // 数值 (I/L/S/B/F/D) → long
                out.put(k, TdValue.of(num.longValue()));
            } else if (payload instanceof String s) {    // 字符串 → 原样
                out.put(k, TdValue.str(s));
            }
            // 其余（COMPOUND/LIST/数组）→ schema-free 跳过
        }
        return out;
    }
}