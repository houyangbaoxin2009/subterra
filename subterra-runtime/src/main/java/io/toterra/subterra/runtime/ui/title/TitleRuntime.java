package io.toterra.subterra.runtime.ui.title;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.ui.title.TitleHintCore;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * p.2.32.1 标题提示接线壳：默认 no-op（零开销）；门控 {@code subterra.probe.title}
 * 下在 ServerStarted 跑一次确定性样例（双提示文档 + 进入/去重/离开轨迹）并输出
 * marker 供 AsyncE2EProbe 断言。实际标题呈现属客户端 UI 面，接线点文档化（数据面
 * 恒为语言键、无 HUD 系统感元素）。
 *
 * <p>The p.2.32.1 title-hint wiring shell: no-op by default; under the
 * {@code subterra.probe.title} gate ServerStarted runs one deterministic sample
 * (a two-hint doc + the enter/dedup/leave trajectory) and emits markers for
 * AsyncE2EProbe. Actual title presentation is a client-side UI surface with the
 * wiring point documented; the data plane is language keys only, no HUD system-feel.
 */
public final class TitleRuntime {

    /** 确定性日志 marker。 / The deterministic log marker. */
    public static final String MARKER = "[Subterra title]";

    /** E2E 门控属性。 / The E2E gate property. */
    public static final String PROBE_GATE = "subterra.probe.title";

    public static final Logger LOGGER = LogUtils.getLogger();

    private TitleRuntime() {
    }

    /** 从 mod 构造调用。 / Called from the mod constructor. */
    public static void bootstrap(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(TitleRuntime::onServerStarted);
        LOGGER.info("{} shell active (default no-op; sample only under {})", MARKER, PROBE_GATE);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty(PROBE_GATE) == null) {
            return;
        }
        TitleHintCore.HintDoc doc = new TitleHintCore.HintDoc(List.of(
                new TitleHintCore.Hint("region", "overturn:ashen_marches", "region.overturn.ashen_marches.title", "region.overturn.ashen_marches.subtitle"),
                new TitleHintCore.Hint("region", "overturn:verdant_reach", "region.overturn.verdant_reach.title", "")));
        TitleHintCore.Session session = new TitleHintCore.Session(doc);
        TitleHintCore.Display first = session.enter("region", "overturn:ashen_marches");
        TitleHintCore.Display repeat = session.enter("region", "overturn:ashen_marches");
        session.leave("region");
        TitleHintCore.Display again = session.enter("region", "overturn:ashen_marches");
        LOGGER.info("{} gate=on sample ok (title={}, window={}, dedup={}, re-entrant={})",
                MARKER, first != null ? first.titleKey() : "none", TitleHintCore.DISPLAY_TICKS,
                repeat == null, again != null);
    }
}
