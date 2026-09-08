package io.toterra.subterra.worldgen.profiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.FMLPaths;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.api.worldgen.profiler.ProfileCategory;
import io.toterra.subterra.api.worldgen.profiler.ProfileFormat;
import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileSink;
import io.toterra.subterra.api.worldgen.profiler.ProfileStep;
import io.toterra.subterra.api.worldgen.profiler.ProfileWindow;
import io.toterra.subterra.engine.worldgen.profiler.facade.ProfilePlanTd;

/**
 * Config-file loader + runtime gate for the World Profiler (p.1.8.30). Reads
 * {@code config/subterra/worldprofile.td} relative to the game dir, reusing the
 * same td shape the pure-JDK facade parses ({@link ProfilePlanTd}). A missing,
 * blank or malformed file always falls back to the documented default plan
 * (residency off, window center {@code [0,0]} radius {@code 8}, all four
 * categories, step {@code xz=2 y=16}, no slice, format {@code [zd]}, sink
 * {@code run}) and never affects boot.
 * <p>
 * Global Profiler 配置文件加载器与运行开关（p.1.8.30）。按游戏目录相对路径读取
 * {@code config/subterra/worldprofile.td}，复用纯 JDK 门面解析的 td 形状
 * ({@link ProfilePlanTd})。缺失、空白或畸形文件一律回落为文档化默认计划（residency
 * 关闭、window 中心 {@code [0,0]} 半径 {@code 8}、四类全开、step {@code xz=2 y=16}、
 * 无 slice、format {@code [zd]}、sink {@code run}），绝不影响启动。
 */
public final class WorldProfilerConfig {

    /** Relative path (from the game dir) of the profiler option file. */
    private static final String CONFIG_REL_PATH = "config/subterra/worldprofile.td";

    private WorldProfilerConfig() {
    }

    /**
     * The expected td path of the plan file ({@code config/<gamedir>/subterra/
     * worldprofile.td}), for command hints and module documentation.
     * <p>
     * 计划文件的预期 td 路径（{@code config/<gamedir>/subterra/worldprofile.td}），供
     * 命令提示与模块说明使用。
     *
     * @return the absolute expected path of the plan file.
     */
    public static Path defaultPlanSource() {
        return FMLPaths.GAMEDIR.get().resolve(CONFIG_REL_PATH);
    }

    /**
     * Loads the current {@link ProfilePlan} from {@link #defaultPlanSource()}. A
     * missing/blank file returns the default plan; a parse failure is logged at
     * ERROR and also falls back to the default plan. Never throws.
     * <p>
     * 从 {@link #defaultPlanSource()} 加载当前 {@link ProfilePlan}。文件缺失/为空返回默认
     * 计划；解析失败以 ERROR 记录并同样回落默认计划。绝不抛出。
     *
     * @return the parsed plan, or the default plan on any failure.
     */
    public static ProfilePlan load() {
        Path config = defaultPlanSource();
        if (!Files.isRegularFile(config)) {
            return defaultPlan();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return defaultPlan();
            }
            return ProfilePlanTd.fromSource(text);
        } catch (IllegalArgumentException | IOException e) {
            Subterra.LOGGER.error("[subterra_profiler] worldprofile.td {} ignored ({}), using default plan",
                    config, e);
            return defaultPlan();
        }
    }

    /**
     * The documented default plan (p.1.8.30 section defaults) used when no plan
     * file is present or the file is unreadable.
     * <p>
     * 计划文件缺失或不可读时使用的文档化默认计划（p.1.8.30 章节默认值）。
     *
     * @return the default plan.
     */
    static ProfilePlan defaultPlan() {
        Set<ProfileCategory> cats = EnumSet.allOf(ProfileCategory.class);
        return new ProfilePlan(false, new ProfileWindow(0, 0, 8), cats,
                new ProfileStep(2, 16), null, List.of(ProfileFormat.ZD), ProfileSink.RUN);
    }
}