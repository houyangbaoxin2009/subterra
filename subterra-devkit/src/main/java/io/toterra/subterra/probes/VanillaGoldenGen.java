package io.toterra.subterra.probes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

/**
 * p.2.15.2 one-time golden asset generator: freezes the pure-JDK mirror
 * {@link NoiseRouter#overworld(long, int, int)} (seed 44905237, range [-64, 320))
 * into a td golden document for the p.2.15.3 {@code VanillaGoldenProbe} baseline.
 * Pure JVM — never touches Minecraft classes.
 * <p>
 * The twelve fixed sample points mirror {@code SameSeedCompare.samplePoints()}
 * (columns {(0,0),(123,-456),(9999,3)} × y {-40,0,16,64}, columns outer / y inner),
 * and each of the fifteen fields ({@link NoiseRouter#FIELD_NAMES}, vanilla record
 * order) is evaluated at raw block coordinates. Every value is stored as its
 * <b>IEEE-754 bit pattern</b> ({@code Double.doubleToLongBits}) in td's first-class
 * int64 type, so the file round-trips bit-exactly with no float formatting loss.
 * <p>
 * Determinism contract: fixed field order ({@code FIELD_NAMES}), fixed point order,
 * fixed metadata ({@code golden_version}, {@code seed}, ranges), and no timestamps —
 * the same input always produces the same byte stream. Self-check inside {@link #main}
 * re-reads the written file, re-parses it, rebuilds the router from scratch and
 * asserts every stored bit matches a fresh evaluation (cross-build determinism).
 * <p>
 * Output: {@code subterra-devkit/src/main/resources/golden/vanilla-router-seed44905237.td}
 * (a classpath resource at runtime), absolute path + stats printed to stdout, exit 0.
 *
 * <p>p.2.15.2 一次性资产生成器：把纯 JDK 镜像 {@link NoiseRouter#overworld(long,int,int)}
 * （种子 44905237、范围 [-64, 320)）冻结为 td golden 文档，作为 p.2.15.3
 * {@code VanillaGoldenProbe} 的基线。纯 JVM——绝不触碰 Minecraft 类。
 * <p>
 * 十二个固定采样点与 {@code SameSeedCompare.samplePoints()} 完全一致（列 {(0,0),
 * (123,-456),(9999,3)} × y {-40,0,16,64}，列外循环 / y 内循环），每个字段
 * （{@link NoiseRouter#FIELD_NAMES}，原生 record 顺序）在原始方块坐标处求值。每个值
 * 以其 <b>IEEE-754 位模式</b>（{@code Double.doubleToLongBits}）存入 td 一等类型
 * int64，保证文件往返逐位无损、无浮点格式化损耗。
 * <p>
 * 确定性契约：字段顺序固定（{@code FIELD_NAMES}）、点序固定、元数据固定
 * （{@code golden_version}、{@code seed}、范围），且不含时间戳——同一输入必然产出
 * 同一字节流。{@link #main} 内置自检：重读已写文件、重新解析、从零重建路由器并逐位
 * 断言每个存储位与一次全新求值一致（跨构建确定性）。
 * <p>
 * 输出：{@code subterra-devkit/src/main/resources/golden/vanilla-router-seed44905237.td}
 * （运行时为 classpath 资源），stdout 打印绝对路径与统计行，exit 0。
 */
public final class VanillaGoldenGen {

    /** Frozen world seed (same default as the p.1.8.17 compare bridge). */
    private static final long SEED = 44905237L;

    /** Vanilla overworld block range (inclusive min, exclusive max). */
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    /** Golden schema version; bump on any layout change (fixed, no timestamp). */
    private static final int GOLDEN_VERSION = 1;

    /** Expected canonical field count (vanilla 1.21.1 router record). */
    private static final int EXPECTED_FIELDS = 15;

    /** Relative output path (JavaExec workingDir = the devkit project dir). */
    private static final Path GOLDEN_REL = Path.of("src", "main", "resources", "golden",
            "vanilla-router-seed44905237.td");

    private VanillaGoldenGen() {
    }

    public static void main(String[] args) {
        int fieldCount = NoiseRouter.FIELD_NAMES.length;
        if (fieldCount != EXPECTED_FIELDS) {
            throw new IllegalStateException("expected " + EXPECTED_FIELDS + " canonical fields, got " + fieldCount);
        }
        List<long[]> points = samplePoints();
        long[][] bits = evaluate(SEED, points, fieldCount);

        TdTable golden = buildDoc(points, bits, fieldCount);
        String text = "type tie<data>\n" + Td.write(golden);

        Path out = GOLDEN_REL;
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write golden td " + out, e);
        }

        selfCheck(out, text, golden, points, fieldCount);
        printStats(points, bits, fieldCount, out);
        System.out.println("[VanillaGoldenGen] golden td written: " + out.toAbsolutePath());
    }

    // ---------- evaluation ----------

    /** The twelve fixed sample points: columns {(0,0),(123,-456),(9999,3)} × y {-40,0,16,64}. */
    private static List<long[]> samplePoints() {
        long[][] columns = { {0L, 0L}, {123L, -456L}, {9999L, 3L} };
        int[] ys = {-40, 0, 16, 64};
        List<long[]> out = new ArrayList<>();
        for (long[] c : columns) {
            for (int y : ys) {
                out.add(new long[] {c[0], y, c[1]});
            }
        }
        return out;
    }

    /** Evaluates all fifteen fields at every point, storing {@code doubleToLongBits} patterns. */
    private static long[][] evaluate(long seed, List<long[]> points, int fieldCount) {
        NoiseRouter router = NoiseRouter.overworld(seed, MIN_Y, MAX_Y);
        long[][] bits = new long[points.size()][fieldCount];
        for (int pi = 0; pi < points.size(); pi++) {
            long[] p = points.get(pi);
            for (int i = 0; i < fieldCount; i++) {
                bits[pi][i] = Double.doubleToLongBits(
                        router.fieldAt(i).eval((double) p[0], (double) p[1], (double) p[2]));
            }
        }
        return bits;
    }

    // ---------- document ----------

    /** Builds the single-table golden doc: metadata + 12 row elements in fixed point order. */
    private static TdTable buildDoc(List<long[]> points, long[][] bits, int fieldCount) {
        TdTable.Builder fields = TdTable.builder();
        for (String name : NoiseRouter.FIELD_NAMES) {
            fields.element(TdValue.str(name));
        }
        TdTable.Builder doc = TdTable.builder()
                .put("golden_version", TdValue.of(GOLDEN_VERSION))
                .put("seed", TdValue.of(SEED))
                .put("minY", TdValue.of(MIN_Y))
                .put("maxY", TdValue.of(MAX_Y))
                .put("pointCount", TdValue.of(points.size()))
                .put("fieldCount", TdValue.of(fieldCount))
                .put("fields", fields.build());
        for (int pi = 0; pi < points.size(); pi++) {
            long[] p = points.get(pi);
            TdTable.Builder row = TdTable.builder()
                    .put("x", TdValue.of(p[0]))
                    .put("y", TdValue.of(p[1]))
                    .put("z", TdValue.of(p[2]));
            for (int i = 0; i < fieldCount; i++) {
                row.put(NoiseRouter.FIELD_NAMES[i], TdValue.of(bits[pi][i]));
            }
            doc.element(row.build());
        }
        return doc.build();
    }

    // ---------- self-check ----------

    /** Re-reads the file, re-parses, rebuilds the router and asserts every bit matches. */
    private static void selfCheck(Path out, String text, TdTable golden, List<long[]> points, int fieldCount) {
        boolean ok = true;
        ok &= check("text round-trip byte-identical", Td.write(Td.parse(text)).equals(Td.write(golden)));
        String onDisk;
        try {
            onDisk = Files.readString(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot re-read golden td " + out, e);
        }
        ok &= check("file bytes == emitted text", onDisk.equals(text));

        TdTable back = Td.parse(onDisk);
        ok &= check("golden_version == " + GOLDEN_VERSION, back.get("golden_version").asInt() == GOLDEN_VERSION);
        ok &= check("seed == " + SEED, back.get("seed").asInt() == SEED);
        ok &= check("pointCount == " + points.size(), back.get("pointCount").asInt() == points.size());
        ok &= check("fieldCount == " + fieldCount, back.get("fieldCount").asInt() == fieldCount);
        List<TdValue> rows = back.elements();
        ok &= check("row count == " + points.size(), rows.size() == points.size());
        ok &= check("fields order == FIELD_NAMES", fieldsOrder(back));

        // Cross-build determinism: a fresh router must reproduce every stored bit.
        long[][] fresh = evaluate(SEED, points, fieldCount);
        boolean bitsOk = true;
        for (int pi = 0; pi < rows.size(); pi++) {
            TdTable row = (TdTable) rows.get(pi);
            long x = row.get("x").asInt();
            long y = row.get("y").asInt();
            long z = row.get("z").asInt();
            long[] p = points.get(pi);
            bitsOk &= check("row " + pi + " x/y/z", x == p[0] && y == p[1] && z == p[2]);
            for (int i = 0; i < fieldCount; i++) {
                long stored = row.get(NoiseRouter.FIELD_NAMES[i]).asInt();
                bitsOk &= check("row " + pi + " field " + NoiseRouter.FIELD_NAMES[i],
                        stored == fresh[pi][i]);
            }
        }
        ok &= bitsOk;
        if (!ok) {
            System.out.println("[VanillaGoldenGen] SELF-CHECK FAIL");
            System.exit(1);
        }
        System.out.println("[VanillaGoldenGen] SELF-CHECK PASS (bit-exact round-trip + cross-build determinism)");
    }

    private static boolean fieldsOrder(TdTable back) {
        TdValue fields = back.get("fields");
        if (!(fields instanceof TdTable ft)) {
            return false;
        }
        List<TdValue> names = ft.elements();
        if (names.size() != NoiseRouter.FIELD_NAMES.length) {
            return false;
        }
        for (int i = 0; i < names.size(); i++) {
            if (!NoiseRouter.FIELD_NAMES[i].equals(names.get(i).asString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean check(String what, boolean ok) {
        if (!ok) {
            System.out.println("[VanillaGoldenGen] FAIL " + what);
        }
        return ok;
    }

    // ---------- stats ----------

    /** Prints deterministic per-field stats over the 12 points (nonzero / distinct bit patterns). */
    private static void printStats(List<long[]> points, long[][] bits, int fieldCount, Path out) {
        System.out.println("[VanillaGoldenGen] points=" + points.size() + " fields=" + fieldCount
                + " golden_version=" + GOLDEN_VERSION + " seed=" + SEED);
        for (int i = 0; i < fieldCount; i++) {
            int nonzero = 0;
            java.util.Set<Long> distinct = new java.util.LinkedHashSet<>();
            for (int pi = 0; pi < points.size(); pi++) {
                if (bits[pi][i] != 0L) {
                    nonzero++;
                }
                distinct.add(bits[pi][i]);
            }
            System.out.printf("[VanillaGoldenGen] field %-30s nonzero %2d/%-2d  distinctBits %d%n",
                    NoiseRouter.FIELD_NAMES[i], nonzero, points.size(), distinct.size());
        }
        System.out.println("[VanillaGoldenGen] bytes=" + fileSize(out));
    }

    private static long fileSize(Path out) {
        try {
            return Files.size(out);
        } catch (IOException e) {
            return -1L;
        }
    }
}
