package io.toterra.subterra.api.migrate;

import java.util.List;

/**
 * p.2.3.5 迁移结果载体（不可变 record）：确定性汇总一次迁移。{@code ok} 表示整体成功；
 * {@code bytesIn}/{@code bytesOut} 为读写字节数；{@code filesMigrated} 为成功迁移文件计数；
 * {@code lines} 为逐步日志（每行一个确定性文本，含成功/失败的说明）。提供 {@link #ok} /
 * {@link #fail} 便捷工厂。保持纯 JDK + 本 api 包（无 engine 依赖）。
 * <p>
 * p.2.3.5 migration-result carrier (an immutable record) summarising one migration
 * deterministically. {@code ok} marks overall success; {@code bytesIn}/{@code bytesOut} the bytes
 * read/written; {@code filesMigrated} counts successfully migrated files; {@code lines} the step
 * log (one deterministic line each, covering both successes and failures). {@link #ok} and
 * {@link #fail} are convenience factories. Stays pure JDK + this api package (no engine dependency).
 *
 * @param ok            整体成功 / overall success.
 * @param bytesIn       读入总字节 / total bytes read.
 * @param bytesOut      写出总字节 / total bytes written.
 * @param filesMigrated 成功迁移文件数 / number of files successfully migrated.
 * @param lines         逐步日志（确定性文本，保序）/ the step log (deterministic lines, in order).
 */
public record MigrationReport(boolean ok, long bytesIn, long bytesOut, long filesMigrated,
                              List<String> lines) {

    /**
     * 成功报告（不可变视图）。A success report (immutable view).
     */
    public static MigrationReport ok(long bytesIn, long bytesOut, long filesMigrated, List<String> lines) {
        return new MigrationReport(true, bytesIn, bytesOut, filesMigrated, List.copyOf(lines));
    }

    /**
     * 失败报告（单行原因，字节/计数归零）。A failure report (a single reason line; bytes/count zeroed).
     */
    public static MigrationReport fail(String reason) {
        return new MigrationReport(false, 0L, 0L, 0L, List.of(reason));
    }
}