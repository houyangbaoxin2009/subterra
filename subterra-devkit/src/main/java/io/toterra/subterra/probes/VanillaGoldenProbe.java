// p.2.15.3: golden-gate regression probe — the frozen vanilla-fidelity td asset
// (vanilla-router-seed44905237.td) becomes a regression gate over the live
// NoiseRouter mirror. Pure JVM: no MC runtime, no wall-clock, no timestamps.
package io.toterra.subterra.probes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

/**
 * p.2.15.3 黄金门禁回归探针 —— 把 p.2.15.2 冻结的 golden td 资产
 * {@code /golden/vanilla-router-seed44905237.td} 变成对实时 {@link NoiseRouter} 镜像的
 * 回归门禁：① 元数据锚定（{@code golden_version=1}、{@code seed=44905237}、{@code minY/maxY}、
 * {@code pointCount=12}、{@code fieldCount=15}、字段序与 {@link NoiseRouter#FIELD_NAMES}
 * 逐字一致）；② 180 项逐位回归（重建 {@code overworld(44905237, -64, 320)} 后每点每字段的
 * {@code Double.doubleToLongBits} 与资产 int64 逐位相等）；③ 确定性再入（连续两次重建镜像、
 * 全量逐位一致）；④ 文档往返（{@code Td.write(Td.parse(text))} 与资产正文逐字节一致）。
 * 每项失败计数 +1 并给出明确诊断（字段名、点、expected/actual 位模式，long 与 hex）；
 * 元数据/字段序漂移单独报错。禁静默吞错。纯 JVM——绝不触碰 Minecraft 类；无时序、无时间戳。
 * 退出码 0 = PASS，1 = FAIL。
 *
 * <p>p.2.15.3 golden-gate regression probe — turns the p.2.15.2 frozen golden td asset
 * {@code /golden/vanilla-router-seed44905237.td} into a regression gate over the live
 * {@link NoiseRouter} mirror: ① metadata anchor ({@code golden_version=1},
 * {@code seed=44905237}, {@code minY/maxY}, {@code pointCount=12}, {@code fieldCount=15},
 * field order verbatim against {@link NoiseRouter#FIELD_NAMES}); ② 180 bit-exact
 * regressions (rebuild {@code overworld(44905237, -64, 320)} and compare every
 * {@code Double.doubleToLongBits} with the stored int64); ③ determinism re-entry (two
 * fresh mirror builds agree on every bit); ④ doc round-trip
 * ({@code Td.write(Td.parse(text))} byte-identical to the asset body). Every failure is
 * counted and diagnosed explicitly (field name, point, expected/actual bit patterns in
 * long and hex); metadata / field-order drift is reported separately. Never silently
 * swallowed. Pure JVM — never touches Minecraft classes; no timing, no timestamps.
 * Exit 0 = PASS, 1 = FAIL.
 */
public final class VanillaGoldenProbe {

    /** Classpath resource of the frozen golden asset (devkit resources on runtimeClasspath). */
    private static final String RESOURCE = "/golden/vanilla-router-seed44905237.td";

    /** td doc type header written by the p.2.15.2 generator (single leading line). */
    private static final String DOC_HEADER = "type tie<data>";

    /** Frozen world seed — must equal the asset's seed metadata. */
    private static final long SEED = 44905237L;

    /** Vanilla overworld block range the asset was frozen with. */
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    /** Frozen schema version — the anchor: a deliberate value change must bump it and re-freeze. */
    private static final int GOLDEN_VERSION = 1;

    /** Expected asset layout (defensive; the asset also declares them). */
    private static final int EXPECTED_POINTS = 12;
    private static final int EXPECTED_FIELDS = 15;

    private static int checks = 0;
    private static int failures = 0;
    private static int bitCompared = 0;
    private static int bitMismatched = 0;

    private VanillaGoldenProbe() {
    }

    public static void main(String[] args) {
        try {
            String text = readResource(RESOURCE);
            TdTable doc = Td.parse(text);
            metadataAnchor(doc);
            fieldOrderAnchor(doc);
            List<long[]> points = new ArrayList<>();
            List<long[]> storedBits = new ArrayList<>();
            parseRows(doc, points, storedBits);
            bitRegression(points, storedBits);
            determinismReentry(points);
            docRoundTrip(text);
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[VanillaGoldenProbe] PASS (" + checks + " checks, "
                    + bitCompared + " bit regressions bit-exact, determinism re-entry, doc round-trip)");
            System.exit(0);
        } else {
            System.out.println("[VanillaGoldenProbe] FAIL (" + failures + " of " + checks
                    + " checks, " + bitMismatched + " bit mismatch(es))");
            System.exit(1);
        }
    }

    // ---------- resource ----------

    /** Reads the golden asset text from the classpath. */
    private static String readResource(String resource) {
        try (InputStream in = VanillaGoldenProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing probe resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read probe resource " + resource, e);
        }
    }

    // ---------- (a) metadata anchor ----------

    private static void metadataAnchor(TdTable doc) {
        checkMeta("golden_version", GOLDEN_VERSION, int64(doc, "golden_version"));
        checkMeta("seed", SEED, int64(doc, "seed"));
        checkMeta("minY", MIN_Y, int64(doc, "minY"));
        checkMeta("maxY", MAX_Y, int64(doc, "maxY"));
        checkMeta("pointCount", EXPECTED_POINTS, int64(doc, "pointCount"));
        checkMeta("fieldCount", EXPECTED_FIELDS, int64(doc, "fieldCount"));
    }

    private static void checkMeta(String what, long expected, Long actual) {
        check("metadata " + what + " == " + expected, actual != null && actual == expected);
    }

    /** Field order must equal {@link NoiseRouter#FIELD_NAMES} verbatim (order included). */
    private static void fieldOrderAnchor(TdTable doc) {
        TdValue fields = doc.get("fields");
        if (!(fields instanceof TdTable ft)) {
            check("metadata fields present (15 names in FIELD_NAMES order)", false);
            return;
        }
        List<TdValue> names = ft.elements();
        boolean same = names.size() == NoiseRouter.FIELD_NAMES.length;
        for (int i = 0; same && i < names.size(); i++) {
            same = NoiseRouter.FIELD_NAMES[i].equals(names.get(i).asString());
        }
        check("metadata fields order == NoiseRouter.FIELD_NAMES (verbatim)", same);
        if (!same) {
            int max = Math.max(names.size(), NoiseRouter.FIELD_NAMES.length);
            for (int i = 0; i < max; i++) {
                String expected = i < NoiseRouter.FIELD_NAMES.length ? NoiseRouter.FIELD_NAMES[i] : "<absent>";
                String actual = i < names.size() ? names.get(i).asString() : "<absent>";
                if (!expected.equals(actual)) {
                    System.out.println("  [VanillaGoldenProbe] fields[" + i + "] expected \""
                            + expected + "\" got \"" + actual + "\"");
                }
            }
        }
    }

    // ---------- rows: points + stored bit patterns ----------

    /** Extracts points and stored int64 bit patterns from the doc rows; structural drift fails loudly. */
    private static void parseRows(TdTable doc, List<long[]> points, List<long[]> storedBits) {
        List<TdValue> rows = doc.elements();
        check("row count == pointCount == " + EXPECTED_POINTS, rows.size() == EXPECTED_POINTS);
        for (int ri = 0; ri < rows.size(); ri++) {
            TdValue v = rows.get(ri);
            if (!(v instanceof TdTable r)) {
                check("row " + ri + " is a table", false);
                continue;
            }
            Long x = int64(r, "x");
            Long y = int64(r, "y");
            Long z = int64(r, "z");
            if (x == null || y == null || z == null) {
                check("row " + ri + " x/y/z present", false);
                continue;
            }
            long[] bits = new long[EXPECTED_FIELDS];
            boolean complete = true;
            for (int i = 0; i < EXPECTED_FIELDS; i++) {
                TdValue fv = r.get(NoiseRouter.FIELD_NAMES[i]);
                if (fv == null) {
                    check("row " + ri + " field " + NoiseRouter.FIELD_NAMES[i] + " present", false);
                    complete = false;
                } else {
                    bits[i] = fv.asInt();
                }
            }
            check("row " + ri + " all " + EXPECTED_FIELDS + " field keys present", complete);
            points.add(new long[] {x, y, z});
            storedBits.add(bits);
        }
    }

    // ---------- (b) bit-exact regression ----------

    private static void bitRegression(List<long[]> points, List<long[]> storedBits) {
        if (points.isEmpty()) {
            return; // structural failures already reported above
        }
        long[][] actual = evaluate(points);
        int mismatches = 0;
        for (int pi = 0; pi < points.size(); pi++) {
            long[] p = points.get(pi);
            long[] stored = storedBits.get(pi);
            for (int i = 0; i < EXPECTED_FIELDS; i++) {
                bitCompared++;
                if (stored[i] != actual[pi][i]) {
                    bitMismatched++;
                    mismatches++;
                    printBitDiag("golden regression", pi, p, i, stored[i], actual[pi][i]);
                }
            }
        }
        check("golden regression: every stored int64 == doubleToLongBits of fresh eval ("
                + points.size() * EXPECTED_FIELDS + " items)", mismatches == 0);
    }

    // ---------- (c) determinism re-entry ----------

    /** Rebuilds the mirror twice from scratch and requires every bit to agree (self-check). */
    private static void determinismReentry(List<long[]> points) {
        if (points.isEmpty()) {
            return;
        }
        long[][] first = evaluate(points);
        long[][] second = evaluate(points);
        int mismatches = 0;
        for (int pi = 0; pi < points.size(); pi++) {
            long[] p = points.get(pi);
            for (int i = 0; i < EXPECTED_FIELDS; i++) {
                bitCompared++;
                if (first[pi][i] != second[pi][i]) {
                    bitMismatched++;
                    mismatches++;
                    printBitDiag("determinism re-entry", pi, p, i, first[pi][i], second[pi][i]);
                }
            }
        }
        check("determinism re-entry: two fresh mirror builds agree on all bits", mismatches == 0);
    }

    /** Evaluates all fields at every point via a fresh {@link NoiseRouter#overworld} mirror. */
    private static long[][] evaluate(List<long[]> points) {
        NoiseRouter router = NoiseRouter.overworld(SEED, MIN_Y, MAX_Y);
        long[][] bits = new long[points.size()][EXPECTED_FIELDS];
        for (int pi = 0; pi < points.size(); pi++) {
            long[] p = points.get(pi);
            for (int i = 0; i < EXPECTED_FIELDS; i++) {
                bits[pi][i] = Double.doubleToLongBits(
                        router.fieldAt(i).eval((double) p[0], (double) p[1], (double) p[2]));
            }
        }
        return bits;
    }

    // ---------- (d) doc round-trip ----------

    /**
     * The asset is {@code DOC_HEADER + "\n" + canonical body}; {@link Td#parse} strips the
     * header, so the round-trip oracle compares {@code Td.write(Td.parse(text))} with the
     * header-stripped body byte-for-byte (canonical-form stability).
     */
    private static void docRoundTrip(String text) {
        if (!text.startsWith(DOC_HEADER)) {
            check("doc header \"" + DOC_HEADER + "\" present", false);
            return;
        }
        if (text.length() <= DOC_HEADER.length() || text.charAt(DOC_HEADER.length()) != '\n') {
            check("doc header line terminated by \\n", false);
            return;
        }
        String body = text.substring(DOC_HEADER.length() + 1);
        String rewritten = Td.write(Td.parse(text));
        check("doc round-trip: Td.write(Td.parse(text)) == canonical body (byte-identical)",
                rewritten.equals(body));
        if (!rewritten.equals(body)) {
            int idx = 0;
            int min = Math.min(rewritten.length(), body.length());
            while (idx < min && rewritten.charAt(idx) == body.charAt(idx)) {
                idx++;
            }
            System.out.println("  [VanillaGoldenProbe] round-trip divergence at offset " + idx
                    + " (body length " + body.length() + ", rewritten length " + rewritten.length() + ")");
        }
    }

    // ---------- helpers ----------

    /** Long value of a key, or null when absent. */
    private static Long int64(TdTable t, String key) {
        TdValue v = t.get(key);
        return v == null ? null : v.asInt();
    }

    /** Prints a full bit-mismatch diagnosis: field name, point, expected/actual in long + hex. */
    private static void printBitDiag(String kind, int pi, long[] p, int fieldIndex, long expected, long actual) {
        System.out.printf("[FAIL] %s row=%d point=(%d,%d,%d) field=%s expectedBits=%d(0x%s) actualBits=%d(0x%s)%n",
                kind, pi, p[0], p[1], p[2], NoiseRouter.FIELD_NAMES[fieldIndex],
                expected, Long.toHexString(expected), actual, Long.toHexString(actual));
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
