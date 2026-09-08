package io.toterra.subterra.worldgen.gen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.api.worldgen.GeneratorOption;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

/**
 * Config-file loader + runtime gate for the Subterra-generator option
 * (p.1.8.22). Reads {@code config/subterra/worldgen.td} at mod construction,
 * reusing the same td parser the repo uses for {@code config/subterra/servercore.td}
 * (subterra-config {@link Td}). Missing / blank / malformed files fall back to
 * {@link GeneratorOption#defaults()} (feature OFF) and never affect boot.
 *
 * <pre>{@code
 * [
 *   use_subterra_generator = true,   // make Subterra the default generator
 * ]
 * }</pre>
 *
 * Boot logging (deterministic gate markers):
 * <ul>
 *   <li>enabled: {@code [subterra_worldgen] Subterra generator enabled as default
 *       (world type: subterra:subterra)}</li>
 *   <li>off (default): {@code [subterra_worldgen] Subterra generator off (select
 *       world type subterra:subterra to use it)}</li>
 * </ul>
 *
 * <b>Default-preset injection caveat (1.21.1):</b> enabling this switch makes the
 * {@code subterra:subterra} preset the documented default generator of this mod,
 * but Minecraft's {@code CreateWorldScreen} hard-codes {@code WorldPresets.NORMAL}
 * as the client-side default preset on the fresh-create path, and there is no clean
 * vanilla/NeoForge public hook to repoint it to a datapack-registered preset. Per
 * project constraint we do <em>not</em> reach for a fragile mixin/reflection; the
 * default remains {@code WorldPresets.NORMAL} and users explicitly pick the
 * "Subterra" world type instead. Honest, documented caveat.
 *
 * <b>默认预设注入说明（1.21.1）：</b>开启此开关后，本 mod 把 {@code subterra:subterra}
 * 预设记录为"默认生成器"，但 Minecraft 的 {@code CreateWorldScreen} 在新建世界流程
 * 中把 {@code WorldPresets.NORMAL} 硬编码为客户端默认预设，且原生/NeoForge 没有干净
 * 的公开口子可把它指到数据包注册的预设。按项目约束<em>不</em>使用脆弱的接口层/mixin；
 * 创建界面默认仍为 {@code WorldPresets.NORMAL}，用户显式选择 "Subterra" 世界类型。
 * 此为如实记录的限制。
 */
public final class WorldgenConfig {

    /** Relative path (from the game dir) of the generator option file. */
    private static final String CONFIG_REL_PATH = "config/subterra/worldgen.td";

    /** The boot marker when Subterra is the default generator. */
    public static final String ENABLED_LOG =
            "[subterra_worldgen] Subterra generator enabled as default (world type: subterra:subterra)";
    /** The boot marker when Subterra is off (the default case). */
    public static final String DISABLED_LOG =
            "[subterra_worldgen] Subterra generator off (select world type subterra:subterra to use it)";

    private static volatile GeneratorOption option = GeneratorOption.defaults();

    private WorldgenConfig() {
    }

    /**
     * Loads the option once at mod construction (same point the repo loads the
     * servercore td configs), then logs the boot gate marker. Zero impact when the
     * file is absent: only a single INFO log line.
     */
    public static void bootstrap() {
        load(FMLPaths.GAMEDIR.get());
    }

    /** The current generator option (defaults until {@link #bootstrap()} runs). */
    public static GeneratorOption option() {
        return option;
    }

    /** True when the Subterra generator is the default (config or API enabled). */
    public static boolean useSubterraAsDefault() {
        return option.useSubterraAsDefault();
    }

    /**
     * RUNTIME API for hosts/plugins: sets the in-memory option to use Subterra as
     * the default generator. Idempotent. Does <em>not</em> persist to disk — a
     * restart re-reads {@code config/subterra/worldgen.td}.
     * <p>
     * 运行期 API（供宿主/插件调用）：把内存中的选项设为使用 Subterra 作为默认生成器。
     * 幂等，不落盘——重启会重新读取 {@code config/subterra/worldgen.td}。
     */
    public static void enableDefaultGenerator() {
        synchronized (WorldgenConfig.class) {
            GeneratorOption current = option;
            if (!current.useSubterraAsDefault()) {
                option = current.withUseSubterraAsDefault(true);
            }
        }
    }

    private static void load(Path gameDir) {
        Path config = gameDir.resolve(CONFIG_REL_PATH);
        option = read(config);
        if (option.useSubterraAsDefault()) {
            Subterra.LOGGER.info(ENABLED_LOG);
        } else {
            Subterra.LOGGER.info(DISABLED_LOG);
        }
    }

    private static GeneratorOption read(Path config) {
        if (!Files.isRegularFile(config)) {
            return GeneratorOption.defaults();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return GeneratorOption.defaults();
            }
            // Reuse the repo's td parser (servercore.td uses the same one).
            // Unknown/malformed input raises; caught below -> defaults.
            TdTable root = Td.parse(text);
            TdValue value = root.get("use_subterra_generator");
            if (value == null) {
                return GeneratorOption.defaults();
            }
            return GeneratorOption.defaults().withUseSubterraAsDefault(value.asBool());
        } catch (IllegalArgumentException | IOException e) {
            Subterra.LOGGER.warn("subterra worldgen: {} ignored ({}), using defaults", config, e);
            return GeneratorOption.defaults();
        }
    }
}