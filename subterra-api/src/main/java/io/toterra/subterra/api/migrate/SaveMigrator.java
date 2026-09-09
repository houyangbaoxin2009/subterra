package io.toterra.subterra.api.migrate;

import java.nio.file.Path;

/**
 * p.2.3.5 迁移 SPI（服务提供商接口）：把一份原版存档目录（{@code worldDir}）转换为新文档格式，
 * 写入 {@code outDir}，返回一份确定性 {@link MigrationReport}。这是 migrate 侧「只依赖 api」的
 * 运行时契约——实现由 engine 提供，migrate 模块经 {@link java.util.ServiceLoader} 在运行时发现，
 * 自身仅依赖本 api 包。接口保持纯 JDK（{@code java.nio.file.Path}），不引用任何 MC 类。
 * <p>
 * p.2.3.5 migration SPI: translates a vanilla save directory ({@code worldDir}) into the new
 * document format under {@code outDir}, returning a deterministic {@link MigrationReport}. This is
 * the runtime contract that keeps migrate "api-only" — implementations are supplied by engine and
 * discovered at runtime via {@link java.util.ServiceLoader}, while the migrate module only depends
 * on this api package. The interface stays pure JDK ({@code java.nio.file.Path}) with no Minecraft
 * dependency.
 */
public interface SaveMigrator {

    /**
     * 迁移器标识（确定性，如 {@code "zdt-v1"}）；用于日志与选择。The migrator's identifier
     * (deterministic, e.g. {@code "zdt-v1"}); used for logging and selection.
     */
    String name();

    /**
     * 执行迁移。实现必须保持确定性（同一输入同一输出）；单文件失败记入 {@code lines} 而不整体
     * 崩溃。Runs the migration. Implementations must be deterministic (same input → same output);
     * a single-file failure is recorded in {@code lines} without aborting the whole run.
     *
     * @param worldDir 原版存档根目录 / the vanilla save root directory.
     * @param outDir   输出目录（不存在则创建）/ the output directory (created if absent).
     * @return 迁移报告 / the migration report.
     */
    MigrationReport migrate(Path worldDir, Path outDir);
}