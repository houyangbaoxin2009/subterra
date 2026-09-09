package io.toterra.subterra.engine.save.migrate;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.zd.ZdRow;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 玩家混合文档（p.2.3.3）：把 {@link PlayerDatum} 映射为「td 元数据 + zd 载荷」的单一文件，约定
 * 扩展名 {@code .zdt}。文档形状与 {@code LevelZdt} 同构，结构委托 {@link ZdtTransfer}：
 * <pre>{@code
 * type tie<data>
 * player = [
 *   version = 1,
 *   meta = [
 *     uuid = "...", dimension = "...", dataVersion = N,
 *     pos = [ x = ..., y = ..., z = ... ],
 *     rotation = [ yaw = ..., pitch = ... ],
 *     extra = [ [ k = "...", v = ... ], ... ],
 *   ],
 *   zd = "<base64>",
 * ]
 * }</pre>
 * {@code zd} 载荷把 {@link PlayerDatum} 写成一组 {@link ZdRow}（表根 + 标量行，仿 LevelZdt 风格）：
 * {@code meta} 表根（kind 0）+ uuid/dimension（kind 1）、dataVersion（kind 2）、pos×3 与 yaw/pitch
 * （kind 3）、extra（kind 0 child=N 再跟 N 行 {@code extra_<k>}）。经 {@code ZdtTransfer.write} 序列化，
 * Base64 嵌入。往返：{@code parse(toTd(p))==p} 字段级；{@code toTd(parse(s))==s} 逐字节。
 * <p>
 * Player hybrid document (p.2.3.3): maps a {@link PlayerDatum} to a single "td metadata + zd payload"
 * file, extension {@code .zdt}. The shape mirrors {@code LevelZdt} and is delegated to
 * {@link ZdtTransfer}. The {@code zd} payload flattens the {@link PlayerDatum} into rows (table root +
 * scalar rows, LevelZdt style): the {@code meta} root (kind 0) + uuid/dimension (kind 1),
 * dataVersion (kind 2), pos x3 and yaw/pitch (kind 3), extra (a kind-0 child=N row followed by N
 * {@code extra_<k>} rows), serialised via {@code ZdtTransfer.write} and Base64-embedded. Round-trips:
 * {@code parse(toTd(p)) == p} field-level; {@code toTd(parse(s)) == s} byte-for-byte.
 */
public final class PlayerZdt {

    /** 当前文档版本 / current document version. */
    public static final long VERSION = 1;

    private static final String MARKER = "player";
    private static final String ROOT_ZD_KEY = "meta";
    private static final String EXTRA_PREFIX = "extra_";
    private static final double[] DEF_POS = {0.0, 0.0, 0.0};
    private static final float[] DEF_ROT = {0.0f, 0.0f};

    private PlayerZdt() {
    }

    /**
     * 序列化 {@link PlayerDatum} 为单文件 {@code .zdt} td 文档。extra 键按字母序确定性地排序后写入
     * meta 与 zd。Serialises a {@link PlayerDatum} into a single-file {@code .zdt} td document; extra
     * keys are deterministically sorted alphabetically before entering both meta and zd.
     */
    public static String toTd(PlayerDatum d) {
        return ZdtTransfer.write(MARKER, VERSION, metaTable(d), buildZdRows(d));
    }

    /**
     * 从 {@code .zdt} td 文档解码内嵌的 zd 载荷字节（供诊断）。Extracts the embedded zd payload bytes
     * from a {@code .zdt} td document (for diagnostics).
     */
    public static byte[] zdPayload(String tdText) {
        return ZdtTransfer.zdBytes(tdText);
    }

    /**
     * 反解析 {@code .zdt} 文档为 {@link PlayerDatum}。委托 {@link ZdtTransfer} 校验版本与 zd 头，并
     * 交叉断言 zd 载荷解出的裸字段必须与 meta 一致，不一致或头非法抛 {@link IllegalArgumentException}。
     * Parses a {@code .zdt} document back into a {@link PlayerDatum}. It delegates version / zd-header
     * validation to {@link ZdtTransfer} and cross-asserts that the bare fields decoded from the zd
     * payload match the meta; on mismatch or an invalid header it raises
     * {@link IllegalArgumentException}.
     */
    public static PlayerDatum parse(String tdText) {
        ZdtTransfer.TableDoc doc = ZdtTransfer.parse(tdText);
        if (doc.version() != VERSION) {
            throw new IllegalArgumentException("unsupported player zdt version: " + doc.version());
        }
        PlayerDatum meta = datumFromMeta(doc.meta());
        Decoded zd = decodeZd(doc.zdBytes());
        PlayerDatum decoded = new PlayerDatum(zd.uuid, zd.dimension, zd.pos, zd.rotation, zd.dataVersion, zd.extra);
        if (!equal(meta, decoded)) {
            throw new IllegalArgumentException("player zdt zd payload disagrees with td meta");
        }
        return meta;
    }

    // ---- meta table -------------------------------------------------------------

    private static TdTable metaTable(PlayerDatum d) {
        return TdTable.builder()
                .put("uuid", TdValue.str(d.uuid()))
                .put("dimension", TdValue.str(d.dimension()))
                .put("dataVersion", TdValue.of(d.dataVersion()))
                .put("pos", TdTable.builder()
                        .put("x", TdValue.of(posAt(d.pos(), 0)))
                        .put("y", TdValue.of(posAt(d.pos(), 1)))
                        .put("z", TdValue.of(posAt(d.pos(), 2)))
                        .build())
                .put("rotation", TdTable.builder()
                        .put("yaw", TdValue.of((double) rotAt(d.rotation(), 0)))
                        .put("pitch", TdValue.of((double) rotAt(d.rotation(), 1)))
                        .build())
                .put("extra", extraTable(d.extra()))
                .build();
    }

    private static TdTable extraTable(Map<String, TdValue> extra) {
        TdTable.Builder b = TdTable.builder();
        for (Map.Entry<String, TdValue> e : new TreeMap<>(extra).entrySet()) {
            b.element(TdTable.builder().put("k", e.getKey()).put("v", e.getValue()).build());
        }
        return b.build();
    }

    private static PlayerDatum datumFromMeta(TdTable meta) {
        String uuid = strAt(meta, "uuid");
        String dimension = strAt(meta, "dimension");
        int dataVersion = (int) intAt(meta, "dataVersion");
        double[] pos = posFrom(meta.get("pos"));
        float[] rotation = rotationFrom(meta.get("rotation"));
        Map<String, TdValue> extra = extraFrom(meta.get("extra"));
        return new PlayerDatum(uuid, dimension, pos, rotation, dataVersion, extra);
    }

    private static String strAt(TdTable t, String key) {
        return t.get(key) != null ? t.get(key).asString() : "";
    }

    private static long intAt(TdTable t, String key) {
        return t.get(key) != null ? t.get(key).asInt() : 0L;
    }

    private static double[] posFrom(TdValue v) {
        if (!(v instanceof TdTable t)) {
            return DEF_POS.clone();
        }
        return new double[]{
                t.get("x") != null ? t.get("x").asFloat() : 0.0,
                t.get("y") != null ? t.get("y").asFloat() : 0.0,
                t.get("z") != null ? t.get("z").asFloat() : 0.0,
        };
    }

    private static float[] rotationFrom(TdValue v) {
        if (!(v instanceof TdTable t)) {
            return DEF_ROT.clone();
        }
        return new float[]{
                (float) (t.get("yaw") != null ? t.get("yaw").asFloat() : 0.0),
                (float) (t.get("pitch") != null ? t.get("pitch").asFloat() : 0.0),
        };
    }

    private static Map<String, TdValue> extraFrom(TdValue v) {
        if (!(v instanceof TdTable table)) {
            return Map.of();
        }
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (TdValue item : table.elements()) {
            if (!(item instanceof TdTable t)) {
                continue;
            }
            String k = t.get("k") != null ? t.get("k").asString() : "";
            TdValue value = t.get("v");
            if (k.isBlank() || value == null) {
                continue;
            }
            out.put(k, value); // meta already sorted; preserve order
        }
        return out;
    }

    // ---- zd payload -------------------------------------------------------------

    private static List<ZdRow> buildZdRows(PlayerDatum d) {
        Map<String, TdValue> extra = new TreeMap<>(d.extra());
        List<ZdRow> rows = new ArrayList<>();
        rows.add(new ZdRow(0, ROOT_ZD_KEY, 0L, 0.0, "", 6));
        rows.add(new ZdRow(1, "uuid", 0L, 0.0, d.uuid() == null ? "" : d.uuid(), 0));
        rows.add(new ZdRow(1, "dimension", 0L, 0.0, d.dimension() == null ? "" : d.dimension(), 0));
        rows.add(new ZdRow(2, "dataVersion", d.dataVersion(), 0.0, "", 0));
        rows.add(new ZdRow(0, "pos", 0L, 0.0, "", 3));
        rows.add(new ZdRow(3, "pos_x", 0L, posAt(d.pos(), 0), "", 0));
        rows.add(new ZdRow(3, "pos_y", 0L, posAt(d.pos(), 1), "", 0));
        rows.add(new ZdRow(3, "pos_z", 0L, posAt(d.pos(), 2), "", 0));
        rows.add(new ZdRow(0, "rotation", 0L, 0.0, "", 2));
        rows.add(new ZdRow(3, "yaw", 0L, rotAt(d.rotation(), 0), "", 0));
        rows.add(new ZdRow(3, "pitch", 0L, rotAt(d.rotation(), 1), "", 0));
        rows.add(new ZdRow(0, "extra", 0L, 0.0, "", extra.size()));
        for (Map.Entry<String, TdValue> e : extra.entrySet()) {
            rows.add(extraRow(e.getKey(), e.getValue()));
        }
        return rows;
    }

    private static ZdRow extraRow(String key, TdValue v) {
        if (!(v instanceof TdValue.Scalar s)) {
            // extra is documented as a scalar-only view — reject nested values
            throw new IllegalArgumentException("player extra values must be scalars: " + key);
        }
        return switch (s.kind()) {
            case STRING -> new ZdRow(1, EXTRA_PREFIX + key, 0L, 0.0, s.str(), 0);
            case INT -> new ZdRow(2, EXTRA_PREFIX + key, s.i(), 0.0, "", 0);
            case BOOL -> new ZdRow(2, EXTRA_PREFIX + key, s.b() ? 1L : 0L, 0.0, "b", 0);
            case FLOAT -> new ZdRow(3, EXTRA_PREFIX + key, 0L, s.f(), "", 0);
        };
    }

    private static final record Decoded(String uuid, String dimension, int dataVersion,
                                        double[] pos, float[] rotation, Map<String, TdValue> extra) {
    }

    private static Decoded decodeZd(byte[] zdBytes) {
        List<ZdRow> rows = ZdVolume.readRows(zdBytes);
        String uuid = "";
        String dimension = "";
        int dataVersion = 0;
        double[] pos = DEF_POS.clone();
        float[] rotation = DEF_ROT.clone();
        Map<String, TdValue> extra = new LinkedHashMap<>();
        int i = 0;
        while (i < rows.size()) {
            ZdRow r = rows.get(i);
            switch (r.key()) {
                case "uuid" -> uuid = r.valueStr() == null ? "" : r.valueStr();
                case "dimension" -> dimension = r.valueStr() == null ? "" : r.valueStr();
                case "dataVersion" -> dataVersion = (int) r.valueI64();
                case "pos" -> {
                    int n = (int) r.childCount();
                    for (int j = 0; j < n && i + 1 + j < rows.size(); j++) {
                        pos[j] = rows.get(i + 1 + j).valueF64();
                    }
                    i += n;
                }
                case "rotation" -> {
                    int n = (int) r.childCount();
                    for (int j = 0; j < n && i + 1 + j < rows.size(); j++) {
                        rotation[j] = (float) rows.get(i + 1 + j).valueF64();
                    }
                    i += n;
                }
                case "extra" -> {
                    int n = (int) r.childCount();
                    for (int j = 0; j < n && i + 1 + j < rows.size(); j++) {
                        ZdRow cr = rows.get(i + 1 + j);
                        String k = cr.key();
                        if (k != null && k.startsWith(EXTRA_PREFIX)) {
                            extra.put(k.substring(EXTRA_PREFIX.length()), decodeExtraValue(cr));
                        }
                    }
                    i += n;
                }
                default -> { /* header row / container — skipped by key */ }
            }
            i++;
        }
        return new Decoded(uuid, dimension, dataVersion, pos, rotation, extra);
    }

    private static TdValue decodeExtraValue(ZdRow r) {
        switch (r.kind()) {
            case 1:
                return TdValue.str(r.valueStr() == null ? "" : r.valueStr());
            case 3:
                return TdValue.of(r.valueF64());
            case 2:
            default:
                String marker = r.valueStr() == null ? "" : r.valueStr();
                return "b".equals(marker) ? TdValue.of(r.valueI64() != 0) : TdValue.of(r.valueI64());
        }
    }

    // ---- equality ---------------------------------------------------------------

    private static boolean equal(PlayerDatum a, PlayerDatum b) {
        if (!a.uuid().equals(b.uuid()) || !a.dimension().equals(b.dimension())
                || a.dataVersion() != b.dataVersion()) {
            return false;
        }
        double[] pa = a.pos();
        double[] pb = b.pos();
        if (pa.length != pb.length) {
            return false;
        }
        for (int i = 0; i < pa.length; i++) {
            if (Double.doubleToLongBits(pa[i]) != Double.doubleToLongBits(pb[i])) {
                return false;
            }
        }
        float[] ra = a.rotation();
        float[] rb = b.rotation();
        if (ra.length != rb.length) {
            return false;
        }
        for (int i = 0; i < ra.length; i++) {
            if (Float.floatToIntBits(ra[i]) != Float.floatToIntBits(rb[i])) {
                return false;
            }
        }
        if (a.extra().size() != b.extra().size()) {
            return false;
        }
        for (Map.Entry<String, TdValue> e : a.extra().entrySet()) {
            TdValue av = e.getValue();
            TdValue bv = b.extra().get(e.getKey());
            if (bv == null || !extraValueEqual(av, bv)) {
                return false;
            }
        }
        return true;
    }

    /** extra 值语义相等：字符串精确、布尔按真假、数值按 kind 位模式/ i。 */
    private static boolean extraValueEqual(TdValue a, TdValue b) {
        if (a instanceof TdTable || b instanceof TdTable) {
            return false;
        }
        TdValue.Scalar sa = a.scalar();
        TdValue.Scalar sb = b.scalar();
        if (sa.kind() == TdValue.Kind.STRING || sb.kind() == TdValue.Kind.STRING) {
            return sa.kind() == sb.kind() && sa.str().equals(sb.str());
        }
        if (sa.kind() == TdValue.Kind.BOOL || sb.kind() == TdValue.Kind.BOOL) {
            return sa.asBool() == sb.asBool();
        }
        if (sa.kind() == TdValue.Kind.FLOAT || sb.kind() == TdValue.Kind.FLOAT) {
            return sa.kind() == sb.kind()
                    && Double.doubleToLongBits(sa.f()) == Double.doubleToLongBits(sb.f());
        }
        return sa.kind() == sb.kind() && sa.i() == sb.i();
    }

    private static double posAt(double[] pos, int i) {
        return i < pos.length ? pos[i] : 0.0;
    }

    private static float rotAt(float[] rotation, int i) {
        return i < rotation.length ? rotation[i] : 0.0f;
    }
}