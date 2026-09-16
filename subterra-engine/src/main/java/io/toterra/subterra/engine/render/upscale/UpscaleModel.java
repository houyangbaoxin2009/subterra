package io.toterra.subterra.engine.render.upscale;

import java.util.List;

/**
 * p.2.32.3 超分辨率数据面（clean-room 自研）：后处理管线的确定性模型——升采样、
 * 锐化、色调三类 pass 的有序组合，规范序（首 pass 必为 UPSCALE、UPSCALE 不重复、
 * SHARPEN/TONE 在后），因子界 [1, 4]。纯数据面（不含 GL 实现），runtime 接线点
 * 文档化于门控壳；tie 化候选（性能敏感项按 p.2.1 桥下沉）。同输入同管线。
 *
 * <p>The p.2.32.3 upscale data plane (clean-room): a deterministic model of the
 * post-process pipeline — an ordered composition of UPSCALE / SHARPEN / TONE passes
 * with a canonical order (first pass UPSCALE, no duplicate UPSCALE, SHARPEN/TONE
 * after) and factors bounded to [1, 4]. Data plane only (no GL implementation); the
 * runtime wiring point is documented in the gated shell; a tie-offload candidate
 * (performance-sensitive, per the p.2.1 bridge). Identical inputs give identical
 * pipelines.
 */
public final class UpscaleModel {

    /** pass 类别。 / The pass kind. */
    public enum PassKind {
        UPSCALE,
        SHARPEN,
        TONE
    }

    /** 单个 pass（因子仅 UPSCALE 可 &gt;1）。 / One pass (only UPSCALE may carry factor > 1). */
    public record Pass(PassKind kind, int factor) {
        public Pass {
            if (kind == null) {
                throw new IllegalArgumentException("kind must not be null");
            }
            if (factor < 1 || factor > 4) {
                throw new IllegalArgumentException("factor must be within [1, 4] (got " + factor + ")");
            }
            if (kind != PassKind.UPSCALE && factor != 1) {
                throw new IllegalArgumentException("only UPSCALE may carry factor > 1 (got " + kind + " x" + factor + ")");
            }
        }

        /** 规范渲染片段。 / The canonical render fragment. */
        public String render() {
            return kind().name().toLowerCase(java.util.Locale.ROOT) + (factor() == 1 ? "" : "x" + factor());
        }
    }

    /** 管线（规范序校验 + 规范渲染）。 / The pipeline (canonical-order validation + canonical render). */
    public record Pipeline(List<Pass> passes, String render) {
        public Pipeline {
            if (passes == null || passes.isEmpty()) {
                throw new IllegalArgumentException("passes must not be empty");
            }
            passes = List.copyOf(passes);
            if (passes.get(0).kind() != PassKind.UPSCALE) {
                throw new IllegalArgumentException("first pass must be UPSCALE");
            }
            boolean seenUpscale = false;
            StringBuilder sb = new StringBuilder("upscale pipeline");
            for (Pass p : passes) {
                if (p.kind() == PassKind.UPSCALE) {
                    if (seenUpscale) {
                        throw new IllegalArgumentException("duplicate UPSCALE pass");
                    }
                    seenUpscale = true;
                }
                sb.append(' ').append(p.render());
            }
            render = sb.toString();
        }
    }

    /** 缺省管线：2x 升采样 + 锐化。 / The default pipeline: 2x upscale + sharpen. */
    public static Pipeline defaultPipeline() {
        return new Pipeline(List.of(new Pass(PassKind.UPSCALE, 2), new Pass(PassKind.SHARPEN, 1)),
                "upscale pipeline upscalex2 sharpen");
    }

    private UpscaleModel() {
    }
}
