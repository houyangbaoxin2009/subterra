package io.toterra.subterra.probes;

import io.toterra.subterra.engine.render.upscale.UpscaleModel;

import java.util.List;

/**
 * p.2.32.3 确定性探针（纯 JVM）：超分辨率数据面 —— pass 因子界、非升采样档因子
 * 拒绝、首 pass 必 UPSCALE、UPSCALE 不重复、缺省管线规范渲染逐字节、空管线拒绝、
 * 壳接线盘点。禁时序断言。
 *
 * <p>The p.2.32.3 deterministic probe (pure JVM): the upscale data plane — factor
 * bounds, non-upscale factor rejection, first-pass-UPSCALE, no duplicate UPSCALE,
 * byte-identical default-pipeline render, empty-pipeline rejection, and the shell
 * wiring inventory. No timing assertions.
 */
public final class UpscaleProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        passValidation();
        pipelineRules();
        wiringInventory();
        System.out.println("[UpscaleProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void passValidation() {
        reject("factor zero", () -> new UpscaleModel.Pass(UpscaleModel.PassKind.UPSCALE, 0));
        reject("factor five", () -> new UpscaleModel.Pass(UpscaleModel.PassKind.UPSCALE, 5));
        reject("sharpen with factor 2", () -> new UpscaleModel.Pass(UpscaleModel.PassKind.SHARPEN, 2));
        check("pass: canonical render fragment",
                new UpscaleModel.Pass(UpscaleModel.PassKind.UPSCALE, 2).render().equals("upscalex2")
                        && new UpscaleModel.Pass(UpscaleModel.PassKind.SHARPEN, 1).render().equals("sharpen"));
    }

    private static void pipelineRules() {
        UpscaleModel.Pipeline d = UpscaleModel.defaultPipeline();
        check("default: canonical render byte-identical",
                d.render().equals("upscale pipeline upscalex2 sharpen"));
        check("default: re-entry identical", d.render().equals(UpscaleModel.defaultPipeline().render()));
        reject("pipeline: empty rejected", () -> new UpscaleModel.Pipeline(List.of(), "x"));
        reject("pipeline: first pass not UPSCALE", () -> new UpscaleModel.Pipeline(
                List.of(new UpscaleModel.Pass(UpscaleModel.PassKind.SHARPEN, 1)), "x"));
        reject("pipeline: duplicate UPSCALE", () -> new UpscaleModel.Pipeline(
                List.of(new UpscaleModel.Pass(UpscaleModel.PassKind.UPSCALE, 2),
                        new UpscaleModel.Pass(UpscaleModel.PassKind.UPSCALE, 3)), "x"));
        UpscaleModel.Pipeline full = new UpscaleModel.Pipeline(List.of(
                new UpscaleModel.Pass(UpscaleModel.PassKind.UPSCALE, 2),
                new UpscaleModel.Pass(UpscaleModel.PassKind.SHARPEN, 1),
                new UpscaleModel.Pass(UpscaleModel.PassKind.TONE, 1)), "x");
        check("pipeline: three-pass composition renders canonically",
                full.render().equals("upscale pipeline upscalex2 sharpen tone"));
    }

    private static void wiringInventory() {
        check("wiring: UpscaleRuntime present (load-only)",
                present("io.toterra.subterra.runtime.render.upscale.UpscaleRuntime"));
        check("wiring: marker literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.render.upscale.UpscaleRuntime", "[Subterra upscale]"));
        check("wiring: gate literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.render.upscale.UpscaleRuntime", "subterra.probe.upscale"));
    }

    private static void reject(String name, Runnable r) {
        try {
            r.run();
            check("reject: " + name, false);
        } catch (IllegalArgumentException e) {
            check("reject: " + name, true);
        }
    }

    private static boolean present(String fqcn) {
        try {
            Class.forName(fqcn, false, UpscaleProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        try (java.io.InputStream in = UpscaleProbe.class.getResourceAsStream("/" + fqcn.replace('.', '/') + ".class")) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(literal);
        } catch (java.io.IOException e) {
            return false;
        }
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
