package io.toterra.subterra.runtime.skin;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.skin.SkinCore;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * p.2.32.4 皮肤补丁接线壳：默认 no-op；门控 {@code subterra.probe.skin} 下在
 * ServerStarted 跑一次确定性解析样例（UUID 命中 / NAME 命中 / 回落）并输出
 * marker。实际纹理载入与客户端应用为文档化接线点。
 *
 * <p>The p.2.32.4 skin-patch wiring shell: no-op by default; under the
 * {@code subterra.probe.skin} gate ServerStarted runs one deterministic resolution
 * sample (UUID hit / NAME hit / fallback) and emits markers. Actual texture loading
 * and the client-side application are documented wiring points.
 */
public final class SkinRuntime {

    /** 确定性日志 marker。 / The deterministic log marker. */
    public static final String MARKER = "[Subterra skin]";

    /** E2E 门控属性。 / The E2E gate property. */
    public static final String PROBE_GATE = "subterra.probe.skin";

    public static final Logger LOGGER = LogUtils.getLogger();

    private SkinRuntime() {
    }

    /** 从 mod 构造调用。 / Called from the mod constructor. */
    public static void bootstrap(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(SkinRuntime::onServerStarted);
        LOGGER.info("{} shell active (default no-op; sample only under {})", MARKER, PROBE_GATE);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty(PROBE_GATE) == null) {
            return;
        }
        List<SkinCore.SkinSpec> specs = List.of(
                new SkinCore.SkinSpec(SkinCore.Kind.UUID, "0f2a4b6c-8d9e-0f1a-2b3c-4d5e6f708192", "subterra:skin_elder"),
                new SkinCore.SkinSpec(SkinCore.Kind.NAME, "Jiro", "subterra:skin_founder"),
                new SkinCore.SkinSpec(SkinCore.Kind.DEFAULT, null, "subterra:skin_default"));
        SkinCore.Resolution byUuid = SkinCore.resolve(specs,
                "0F2A4B6C-8D9E-0F1A-2B3C-4D5E6F708192", "Someone");
        SkinCore.Resolution byName = SkinCore.resolve(specs,
                "11111111-2222-3333-4444-555555555555", "Jiro");
        SkinCore.Resolution fallback = SkinCore.resolve(specs,
                "99999999-8888-7777-6666-555555555555", "Nobody");
        LOGGER.info("{} gate=on resolve ok (uuid={} name={} fallback={})",
                MARKER, byUuid.textureId(), byName.textureId(), fallback.textureId());
    }
}
