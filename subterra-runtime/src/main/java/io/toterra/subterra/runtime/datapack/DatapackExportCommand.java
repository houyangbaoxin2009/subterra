package io.toterra.subterra.runtime.datapack;

import io.toterra.subterra.runtime.export.ExportCommandCore;

/**
 * p.2.19.5 — 委托壳：p.2.2.7 的 datapack 全量导出核心（{@link #exportFrom}，markers
 * {@code export cmd ...} 逐字符不变）已收编进统一 {@link ExportCommandCore}（runtime.export，同一
 * {@code /subterra export} 命令树单点注册）；本类保留 {@link #exportFrom} 公开面作为兼容委托入口，
 * 命令注册与启动钩子均收敛到 ExportCommandCore.onRegisterCommands / ExportRuntime。
 *
 * <p>p.2.19.5 — delegating shell: the p.2.2.7 datapack all-packs export core ({@link #exportFrom},
 * markers {@code export cmd ...} character-for-character unchanged) is folded into the unified
 * {@link ExportCommandCore} (runtime.export, single-point registration of the one
 * {@code /subterra export} command tree); this class keeps the {@link #exportFrom} public surface as
 * a compatibility delegate, while command registration and the startup hooks live in
 * ExportCommandCore.onRegisterCommands / ExportRuntime.
 */
public final class DatapackExportCommand {

    private DatapackExportCommand() {
    }

    /**
     * 委托统一导出核心（p.2.19.5）。
     * Delegates to the unified export core (p.2.19.5).
     *
     * @param reg     活跃 datapack 注册器 / the active datapack registrar.
     * @param pathArg 目标目录（null/空白 → {@code subterra-export}）/ target directory (null/blank →
     *                {@code subterra-export}).
     * @return {@code true} 若每个 pack 导出且回水化逐字节恒等 / {@code true} if every pack exported and
     *         rehydrated byte-identical.
     */
    public static boolean exportFrom(DatapackRegistrar reg, String pathArg) {
        return ExportCommandCore.exportFrom(reg, pathArg);
    }
}
