package io.toterra.subterra.migrate;

import io.toterra.subterra.api.migrate.MigrationReport;
import io.toterra.subterra.api.migrate.SaveMigrator;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * p.2.3.5 migrate 模块的可执行入口（api-only）：解析 {@code --world <dir>}（必填）与
 * {@code --out <dir>}（缺省 {@code worldDir+/migrated}），经 {@link ServiceLoader} 运行时发现
 * {@link SaveMigrator} 实现（engine 提供，本模块零 engine import），调用 {@code migrate()}，
 * 逐行打印报告并返回 {@code ok?0:1}。退出码 2 = 用法/无实现。本类只依赖 {@code java.*} 与
 * {@code io.toterra.subterra.api.*}，符合铁律（migrate 禁 engine）。{@link #run(String[])} 可供
 * 探针/外部直接调用，不加 {@link System#exit} 干扰。
 * <p>
 * p.2.3.5 executable entry of the migrate module (api-only): parses {@code --world <dir>} (required)
 * and {@code --out <dir>} (defaults to {@code worldDir+/migrated}), discovers a
 * {@link SaveMigrator} implementation at runtime via {@link ServiceLoader} (supplied by engine; this
 * module has zero engine imports), calls {@code migrate()}, prints the report line by line and
 * returns {@code ok?0:1}. Exit code 2 = usage / no implementation. This class depends only on
 * {@code java.*} and {@code io.toterra.subterra.api.*}, honouring the iron law (migrate must not
 * import engine). {@link #run(String[])} is callable from probes/callers without {@link System#exit}.
 */
public final class Runner {

    private static final int EXIT_USAGE = 2;

    private Runner() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * 非退出式运行入口：返回退出码（0=成功，1=迁移失败，2=用法错误/无实现），打印到 stdout/stderr，
     * 不调用 {@link System#exit}。Non-exiting run entry: returns an exit code (0 = success, 1 =
     * migration failed, 2 = usage error / no implementation), prints to stdout/stderr and never calls
     * {@link System#exit}.
     */
    public static int run(String[] args) {
        String world = null;
        String out = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--world" -> {
                    if (i + 1 < args.length) {
                        world = args[++i];
                    }
                }
                case "--out" -> {
                    if (i + 1 < args.length) {
                        out = args[++i];
                    }
                }
                default -> { /* ignore unknown flags */ }
            }
        }
        if (world == null) {
            System.err.println("subterra-migrate: missing --world <dir>");
            return EXIT_USAGE;
        }
        Path worldDir = Path.of(world);
        Path outDir = out != null ? Path.of(out) : worldDir.resolve("migrated");

        ServiceLoader<SaveMigrator> loader = ServiceLoader.load(SaveMigrator.class);
        Iterator<SaveMigrator> it = loader.iterator();
        if (!it.hasNext()) {
            System.err.println("subterra-migrate: no SaveMigrator implementation found on classpath");
            return EXIT_USAGE;
        }
        SaveMigrator migrator = it.next();
        System.out.println("subterra-migrate: using " + migrator.name());
        MigrationReport report;
        try {
            report = migrator.migrate(worldDir, outDir);
        } catch (RuntimeException e) {
            System.err.println("subterra-migrate: migrate threw: " + e);
            return 1;
        }
        for (String line : report.lines()) {
            System.out.println(line);
        }
        return report.ok() ? 0 : 1;
    }
}