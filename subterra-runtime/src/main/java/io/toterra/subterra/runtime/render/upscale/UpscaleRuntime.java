package io.toterra.subterra.runtime.render.upscale;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.render.upscale.UpscaleModel;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * p.2.32.3 超分辨率接线壳：默认 no-op；门控 {@code subterra.probe.upscale} 下在
 * ServerStarted 打印缺省管线的规范渲染 marker（数据面样例，无 GL 侧效果）。实际
 * GL 后处理实现为文档化接线点（性能敏感，tie 化候选按 p.2.1 桥下沉）。
 *
 * <p>The p.2.32.3 upscale wiring shell: no-op by default; under the
 * {@code subterra.probe.upscale} gate ServerStarted prints the default pipeline's
 * canonical render marker (data-plane sample, no GL-side effect). The actual GL
 * post-process implementation is a documented wiring point (performance-sensitive;
 * the tie offload candidate sinks via the p.2.1 bridge).
 */
public final class UpscaleRuntime {

    /** 确定性日志 marker。 / The deterministic log marker. */
    public static final String MARKER = "[Subterra upscale]";

    /** E2E 门控属性。 / The E2E gate property. */
    public static final String PROBE_GATE = "subterra.probe.upscale";

    public static final Logger LOGGER = LogUtils.getLogger();

    private UpscaleRuntime() {
    }

    /** 从 mod 构造调用。 / Called from the mod constructor. */
    public static void bootstrap(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(UpscaleRuntime::onServerStarted);
        LOGGER.info("{} shell active (default no-op; sample only under {})", MARKER, PROBE_GATE);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty(PROBE_GATE) == null) {
            return;
        }
        UpscaleModel.Pipeline pipeline = UpscaleModel.defaultPipeline();
        LOGGER.info("{} gate=on pipeline ok ({} passCount={})", MARKER, pipeline.render(), pipeline.passes().size());
    }
}
