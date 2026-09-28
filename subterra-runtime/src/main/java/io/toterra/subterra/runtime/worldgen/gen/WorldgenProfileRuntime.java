package io.toterra.subterra.runtime.worldgen.gen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.api.worldgen.WorldgenProfile;

/**
 * p.2.36.2 closing slice — worldgen profile load / validate / snapshot shell,
 * the {@code setAssembly} discipline applied to the {@link WorldgenProfile}
 * config surface: one effective profile is read once from
 * {@code config/subterra/worldgen_profile.data.tie} at
 * {@code ServerAboutToStart} (the earliest guaranteed point, verified against
 * the 1.21.1 sources: {@code DedicatedServer.initServer} fires the event
 * <em>before</em> {@code loadLevel()}/{@code createLevels()}, so this runs
 * strictly before any chunk generation or level construction), parsed through
 * the single api seam ({@link WorldgenProfile#fromTd}), and snapshotted into a
 * {@code volatile} holder for the p.2.36.3+ consumers.
 *
 * <p>Deterministic failure discipline (mirrors the assembly snapshot): a
 * missing / blank / malformed file deterministically rejects the whole
 * document and keeps {@link WorldgenProfile#defaults()} — default boot is
 * byte-identical to a world without this file, and an invalid value never
 * changes behaviour.
 *
 * <p><b>Window-tuning activation gate (p.2.36.3):</b> a profile whose window /
 * sea level differs from the p.2.36.1 defaults is snapshotted verbatim (the
 * config surface is definable) but logged with a loud deterministic warning
 * and <b>not</b> yet honoured by the shipped datapack: the
 * {@code subterra_overworld.json} noise block, the {@code subterra:overworld}
 * dimension type, and the density {@code Kind} window all stay at the
 * four-way-consistent defaults until p.2.36.3 parameterises the density side
 * and opens the pre-world-load injection. 1.21.1 fact (decompiled-source
 * verified): {@code MappedRegistry.register} throws on a duplicate key (Neo
 * added the throw; fixed upstream in 1.21.2), so same-key value rewrite at
 * the {@code DatapackRegistryInjector} surface is impossible — the p.2.36.3
 * injection must go through the value-side holder path or a pack-layer
 * injection; until then a divergent window must not leak into the world
 * (this is exactly the four-way-consistency discipline the 2026-09-16
 * drowning regression taught).
 *
 * <p>p.2.36.2 收尾片——世界生成档案的装载 / 校验 / 快照壳：把 {@code setAssembly}
 * 纪律套到 {@link WorldgenProfile} 配置面上。{@code ServerAboutToStart} 一次性读
 * {@code config/subterra/worldgen_profile.data.tie}（该事件先于
 * {@code loadLevel()}/{@code createLevels()}，严格早于任何区块生成），经
 * {@link WorldgenProfile#fromTd} 单一 seam 解析后落入 {@code volatile} 快照。
 * 缺失 / 空白 / 畸形文件确定性整档拒绝并保持缺省（缺省启动与无此文件逐字节等价）。
 * 窗口调节激活门：与缺省不同的窗口 / 海平面照实入快照（配置面可定义），但打确定性
 * 警告且随附数据包不跟随——在 p.2.36.3 参数化密度侧并开启注入前，不得让分歧窗口漏进
 * 世界（2026-09-16 淹没回归的四方一致纪律）。
 */
public final class WorldgenProfileRuntime {

    /** Relative path (from the game dir) of the worldgen profile file. */
    private static final String CONFIG_REL_PATH = "config/subterra/worldgen_profile.data.tie";

    /** Boot marker prefix (deterministic gate markers, assembly-style). */
    private static final String MARKER = "[Subterra worldgen profile]";

    private static volatile WorldgenProfile profile = WorldgenProfile.defaults();

    private WorldgenProfileRuntime() {
    }

    /**
     * Loads + snapshots the profile at {@code ServerAboutToStart} (wired from
     * {@link SubterraWorldgen}). Deterministic: same file bytes → same snapshot
     * → same log lines, no wall clock, no randomness.
     * <p>
     * 在 {@code ServerAboutToStart} 装载并快照档案（由 {@link SubterraWorldgen} 接线）。
     * 确定性：同文件字节 → 同快照 → 同日志行。
     */
    public static void snapshotAtServerAboutToStart() {
        load(FMLPaths.GAMEDIR.get());
    }

    /**
     * The snapshotted profile (defaults until the first successful snapshot;
     * never null). Consumers (p.2.36.3+) must treat this as a boot-time
     * constant — it is set once per server lifecycle and never mutated in place.
     * <p>
     * 当前快照档案（首次成功快照前为缺省；永不为 null）。消费方（p.2.36.3+）应视其为
     * 启动期常量——每个服务器生命周期置一次，不原地变更。
     */
    public static WorldgenProfile profile() {
        return profile;
    }

    /** True when the snapshot carries the p.2.36.1 default window / sea level. */
    public static boolean isDefaultWindow() {
        WorldgenProfile p = profile;
        return p.minY() == WorldgenProfile.DEFAULT_MIN_Y
                && p.height() == WorldgenProfile.DEFAULT_HEIGHT
                && Double.doubleToLongBits(p.seaLevel())
                == Double.doubleToLongBits(WorldgenProfile.DEFAULT_SEA_LEVEL);
    }

    private static void load(Path gameDir) {
        Path config = gameDir.resolve(CONFIG_REL_PATH);
        WorldgenProfile parsed;
        if (!Files.isRegularFile(config)) {
            parsed = WorldgenProfile.defaults();
            Subterra.LOGGER.info("{} no profile file ({}); defaults in effect", MARKER, config);
        } else {
            try {
                String text = Files.readString(config, StandardCharsets.UTF_8);
                parsed = text.isBlank()
                        ? WorldgenProfile.defaults()
                        : WorldgenProfile.fromTd(text);
                Subterra.LOGGER.info("{} loaded ({})", MARKER, summary(parsed));
            } catch (IllegalArgumentException | IOException e) {
                parsed = WorldgenProfile.defaults();
                Subterra.LOGGER.error("{} reject ({}) -> defaults (no behaviour change)", MARKER, e.toString());
            }
        }
        profile = parsed;
        if (!isDefaultWindow()) {
            // p.2.36.3 activation gate: the snapshot is definable, the shipped
            // datapack + density Kind stay at the consistent defaults until the
            // density side is parameterised. Loud, deterministic, once per boot.
            Subterra.LOGGER.warn("{} window {} not honoured yet: shipped noise_settings / "
                            + "dimension_type / density Kind stay at the p.2.36.1 defaults until "
                            + "p.2.36.3 opens the pre-world-load injection",
                    MARKER, windowSummary(parsed));
        }
    }

    /** One-line deterministic summary (window / sea / edges / non-default fields). */
    private static String summary(WorldgenProfile p) {
        int customFields = 0;
        for (int i = 0; i < p.fields().size(); i++) {
            if (!p.fields().get(i).bypassed()) {
                customFields++;
            }
        }
        return "window " + windowSummary(p) + ", edges " + p.edges().size()
                + ", non-default fields " + customFields;
    }

    /** One-line deterministic window summary: {@code [-128, 592) sea 127.0}. */
    private static String windowSummary(WorldgenProfile p) {
        return "[" + p.minY() + ", " + p.windowTop() + ") sea " + p.seaLevel();
    }
}
