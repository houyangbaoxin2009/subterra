package io.toterra.subterra.runtime.weather;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.weather.WeatherCore;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * p.2.32.2 天气接线壳：默认 no-op；门控 {@code subterra.probe.weather} 下在
 * ServerStarted 跑一次确定性预报样例（固定种子 + 固定气候 → 规范渲染 marker）。
 * 实际 vanilla 雨雪档驱动为运行期行为（世界 tick 与档位切换），不作验收断言。
 *
 * <p>The p.2.32.2 weather wiring shell: no-op by default; under the
 * {@code subterra.probe.weather} gate ServerStarted runs one deterministic forecast
 * sample (fixed seed + fixed climate → canonical render marker). Driving the vanilla
 * rain tiers is runtime behaviour (world ticks and tier switches) and is not part
 * of the acceptance assertions.
 */
public final class WeatherRuntime {

    /** 确定性日志 marker。 / The deterministic log marker. */
    public static final String MARKER = "[Subterra weather]";

    /** E2E 门控属性。 / The E2E gate property. */
    public static final String PROBE_GATE = "subterra.probe.weather";

    public static final Logger LOGGER = LogUtils.getLogger();

    private WeatherRuntime() {
    }

    /** 从 mod 构造调用。 / Called from the mod constructor. */
    public static void bootstrap(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(WeatherRuntime::onServerStarted);
        LOGGER.info("{} shell active (default no-op; sample only under {})", MARKER, PROBE_GATE);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty(PROBE_GATE) == null) {
            return;
        }
        WeatherCore.Forecast forecast = new WeatherCore.Forecast(44905237L,
                new WeatherCore.Climate(0.6f, 0.55f));
        LOGGER.info("{} gate=on forecast ok (at0={}, {})", MARKER,
                forecast.at(0), forecast.render());
    }
}
