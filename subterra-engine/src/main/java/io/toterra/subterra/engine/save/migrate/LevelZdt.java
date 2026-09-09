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
 * 混合文档本体（p.2.3.2）：把 {@link LevelDatum} 映射为「td 元数据 + zd 载荷」的单一文件，约定
 * 扩展名 {@code .zdt}。文档形状与 {@code DatapackExportArchive} 同构，具体结构委托
 * {@link ZdtTransfer}（p.2.3.3 泛化）：
 * <pre>{@code
 * type tie<data>
 * level = [
 *   version = 1,
 *   meta = [ name = "...", seed = ..., time = ..., rules = [ [ k = "...", v = ... ], ... ] ],
 *   zd = "<base64>",
 * ]
 * }</pre>
 * {@code zd} 载荷把 {@link LevelDatum} 写成一组 {@link ZdRow}：1 行表根（kind 0、key=meta、
 * child=4）+ 4 行标量（seed/time 为 kind 2 i64、name 为 kind 1 字符串、rules 为 kind 0 child=N 再
 * 跟 N 行 {@code rule_<k>}），经 {@code ZdtTransfer.write} 得字节后 Base64 嵌入。本子项取舍：zd
 * 压缩/列式/字典变体不开，flags=0（最小子集）。对外行为与产出文本逐字节不变（p.2.3.3 重构，21
 * 断言钉住）。
 * <p>
 * Hybrid-document core (p.2.3.2): maps a {@link LevelDatum} to a single file of "td metadata + zd
 * payload", with the conventional extension {@code .zdt}. The document shape mirrors
 * {@code DatapackExportArchive} and its structure is delegated to {@link ZdtTransfer} (generalised
 * in p.2.3.3). The {@code zd} payload flattens the {@link LevelDatum} into {@link ZdRow}s: a 1-row
 * table root (kind 0, key=meta, child=4) + 4 scalar rows (seed/time as kind-2 i64, name as kind-1
 * string, rules as a kind-0 child=N row followed by N {@code rule_<k>} rows), embedded via
 * {@code ZdtTransfer.write}. This sub-item keeps flags = 0 (the minimal subset). The external
 * behaviour and the emitted text are byte-identical after the p.2.3.3 refactor (pinned by 21
 * assertions).
 */
public final class LevelZdt {

    /** 当前文档版本 / current document version. */
    public static final long VERSION = 1;

    private static final String MARKER = "level";
    private static final String ROOT_ZD_KEY = "meta";
    private static final String RULE_PREFIX = "rule_";

    private LevelZdt() {
    }

    /**
     * 序列化 {@link LevelDatum} 为单文件 {@code .zdt} td 文档。rules 键按字母序确定性地排序后写入
     * meta 与 zd。Serialises a {@link LevelDatum} into a single-file {@code .zdt} td document; rule
     * keys are deterministically sorted alphabetically before entering both meta and zd.
     */
    public static String toTd(LevelDatum d) {
        Map<String, TdValue> sorted = sorted(d.rules());
        TdTable.Builder metaB = TdTable.builder()
                .put("name", TdValue.str(d.name()))
                .put("seed", TdValue.of(d.seed()))
                .put("time", TdValue.of(d.dayTime()))
                .put("rules", rulesTable(sorted));
        return ZdtTransfer.write(MARKER, VERSION, metaB.build(),
                buildZdRows(d.rules(), d.seed(), d.dayTime(), d.name()));
    }

    /**
     * 从 {@code .zdt} td 文档解码内嵌的 zd 载荷字节（供 runtime 壳打印 zd 头等诊断）。
     * Extracts the embedded zd payload bytes from a {@code .zdt} td document (for the runtime shell
     * to report the zd header etc.).
     */
    public static byte[] zdPayload(String tdText) {
        return ZdtTransfer.zdBytes(tdText);
    }

    /**
     * 反解析 {@code .zdt} 文档为 {@link LevelDatum}。委托 {@link ZdtTransfer} 校验版本与 zd 头，并
     * 交叉断言 zd 载荷解出的裸字段（name/seed/time/rules）必须与 meta 一致，不一致或头非法抛
     * {@link IllegalArgumentException}。Parses a {@code .zdt} document back into a
     * {@link LevelDatum}. It delegates version / zd-header validation to {@link ZdtTransfer} and
     * cross-asserts that the bare fields decoded from the zd payload (name/seed/time/rules) match
     * the meta; on mismatch or an invalid header raises {@link IllegalArgumentException}.
     */
    public static LevelDatum parse(String tdText) {
        ZdtTransfer.TableDoc doc = ZdtTransfer.parse(tdText);
        if (doc.version() != VERSION) {
            throw new IllegalArgumentException("unsupported level zdt version: " + doc.version());
        }
        TdTable meta = doc.meta();
        String name = meta.get("name") != null ? meta.get("name").asString() : "";
        long seed = meta.get("seed") != null ? meta.get("seed").asInt() : 0L;
        long time = meta.get("time") != null ? meta.get("time").asInt() : 0L;
        Map<String, TdValue> rules = rulesFrom(meta.get("rules"));

        Decoded zd = decodeZd(doc.zdBytes());
        // 交叉断言 / cross-assert: zd 裸字段必须与 meta 一致
        if (!zd.name.equals(name) || zd.seed != seed || zd.time != time || !rulesEqual(zd.rules, rules)) {
            throw new IllegalArgumentException("level zdt zd payload disagrees with td meta");
        }
        return new LevelDatum(name, seed, time, rules);
    }

    // ---- writing/decode helpers -------------------------------------------------

    private static TdTable rulesTable(Map<String, TdValue> rules) {
        TdTable.Builder b = TdTable.builder();
        for (Map.Entry<String, TdValue> e : rules.entrySet()) {
            b.element(TdTable.builder().put("k", e.getKey()).put("v", e.getValue()).build());
        }
        return b.build();
    }

    private static Map<String, TdValue> rulesFrom(TdValue v) {
        if (!(v instanceof TdTable table)) {
            return Map.of();
        }
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (TdValue item : table.elements()) {
            if (!(item instanceof TdTable t)) {
                continue;
            }
            String k = t.get("k") != null ? t.get("k").asString() : "";
            TdValue rule = t.get("v");
            if (k.isBlank() || rule == null) {
                continue;
            }
            out.put(k, rule); // meta already sorted; preserve order
        }
        return out;
    }

    private static List<ZdRow> buildZdRows(Map<String, TdValue> rules, long seed, long time, String name) {
        List<ZdRow> rows = new ArrayList<>();
        rows.add(new ZdRow(0, ROOT_ZD_KEY, 0L, 0.0, "", 4));
        rows.add(new ZdRow(2, "seed", seed, 0.0, "", 0));
        rows.add(new ZdRow(2, "time", time, 0.0, "", 0));
        rows.add(new ZdRow(1, "name", 0L, 0.0, name == null ? "" : name, 0));
        // rules 行（kind 0 child=N）+ N 行 rule_<k>
        Map<String, TdValue> sorted = sorted(rules);
        rows.add(new ZdRow(0, "rules", 0L, 0.0, "", sorted.size()));
        for (Map.Entry<String, TdValue> e : sorted.entrySet()) {
            rows.add(ruleRow(e.getKey(), e.getValue()));
        }
        return rows;
    }

    private static ZdRow ruleRow(String key, TdValue v) {
        if (!(v instanceof TdValue.Scalar s)) {
            // nested-table rule value: encode opaquely as its string form via a kind-1 row
            return new ZdRow(1, RULE_PREFIX + key, 0L, 0.0, "", 0);
        }
        return switch (s.kind()) {
            case STRING -> new ZdRow(1, RULE_PREFIX + key, 0L, 0.0, s.str(), 0);
            case INT -> new ZdRow(2, RULE_PREFIX + key, s.i(), 0.0, "", 0);
            case BOOL -> new ZdRow(2, RULE_PREFIX + key, s.b() ? 1L : 0L, 0.0, "b", 0);
            case FLOAT -> new ZdRow(3, RULE_PREFIX + key, 0L, s.f(), "", 0);
        };
    }

    private static Map<String, TdValue> sorted(Map<String, TdValue> rules) {
        return new TreeMap<>(rules);
    }

    private static final record Decoded(String name, long seed, long time, Map<String, TdValue> rules) {
    }

    private static Decoded decodeZd(byte[] zdBytes) {
        List<ZdRow> rows = ZdVolume.readRows(zdBytes);
        String name = "";
        long seed = 0L;
        long time = 0L;
        Map<String, TdValue> rules = new LinkedHashMap<>();
        int i = 0;
        while (i < rows.size()) {
            ZdRow r = rows.get(i);
            switch (r.key()) {
                case "seed" -> seed = r.valueI64();
                case "time" -> time = r.valueI64();
                case "name" -> name = r.valueStr() == null ? "" : r.valueStr();
                case "rules" -> {
                    int n = (int) r.childCount();
                    for (int j = 0; j < n && i + 1 + j < rows.size(); j++) {
                        ZdRow cr = rows.get(i + 1 + j);
                        String k = cr.key();
                        if (k != null && k.startsWith(RULE_PREFIX)) {
                            rules.put(k.substring(RULE_PREFIX.length()), decodeRuleValue(cr));
                        }
                    }
                    i += n; // skip consumed rule rows
                }
                default -> { /* header row / container — skipped by key */ }
            }
            i++;
        }
        return new Decoded(name, seed, time, rules);
    }

    private static TdValue decodeRuleValue(ZdRow r) {
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

    /** 规则集语义相等：key 集相同且每个值按 kind（i=ints/bools、str、f 位模式）相等。 */
    private static boolean rulesEqual(Map<String, TdValue> a, Map<String, TdValue> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, TdValue> e : a.entrySet()) {
            TdValue av = e.getValue();
            TdValue bv = b.get(e.getKey());
            if (bv == null || !valueEqual(av, bv)) {
                return false;
            }
        }
        return true;
    }

    private static boolean valueEqual(TdValue a, TdValue b) {
        if (a instanceof TdTable || b instanceof TdTable) {
            return false;
        }
        TdValue.Scalar sa = a.scalar();
        TdValue.Scalar sb = b.scalar();
        if (sa.kind() == TdValue.Kind.STRING || sb.kind() == TdValue.Kind.STRING) {
            return sa.kind() == sb.kind() && sa.str().equals(sb.str());
        }
        if (sa.kind() == TdValue.Kind.FLOAT || sb.kind() == TdValue.Kind.FLOAT) {
            return sa.kind() == sb.kind()
                    && Double.doubleToLongBits(sa.f()) == Double.doubleToLongBits(sb.f());
        }
        return sa.kind() == sb.kind() && sa.i() == sb.i();
    }
}