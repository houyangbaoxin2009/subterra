package io.toterra.subterra.engine.save.migrate;

import io.toterra.subterra.api.migrate.MigrationReport;
import io.toterra.subterra.api.migrate.SaveMigrator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * p.2.3.5 engine 提供的迁移 SPI 实现（经 {@link java.util.ServiceLoader} 注册，见
 * {@code subterra-engine} 的 {@code META-INF/services/io.toterra.subterra.api.migrate.SaveMigrator}）：
 * 把原版存档目录迁移为 {@code .zdt} 混合文档。流程：
 * <ol>
 *   <li>{@code worldDir/level.dat}（缺失 → 直接 fail report，lines 注明）；</li>
 *   <li>读 level.dat → {@link LevelDatum} → {@link LevelZdt#toTd} → 写
 *       {@code outDir/<LevelName 清洗后或固定 level>.zdt}（非法字符替换为 {@code _}，确定性）；</li>
 *   <li>扫 {@code worldDir/players/*.dat} → {@link PlayerDatReader#read} → {@link PlayerZdt#toTd}
 *       → 写 {@code outDir/players/<uuid>.zdt}（uuid 由文件名 stem 取，{@code PlayerDatum.uuid} 非空则用它）；</li>
 *   <li>逐步追加 {@code lines}，汇总 bytesIn/bytesOut/filesMigrated，成功 → {@code ok=true}。</li>
 * </ol>
 * 全程 try/catch：单文件失败记入 {@code lines} 而不整体崩溃（坏文件跳过计数）；输入确定输出确定。
 * <p>
 * p.2.3.5 engine-side SPI implementation registered via {@link java.util.ServiceLoader} (see
 * {@code META-INF/services/io.toterra.subterra.api.migrate.SaveMigrator} in {@code subterra-engine}):
 * migrates a vanilla save directory to {@code .zdt} hybrid documents. Pipeline:
 * <ol>
 *   <li>{@code worldDir/level.dat} (missing → a fail report with the reason in {@code lines});</li>
 *   <li>read level.dat → {@link LevelDatum} → {@link LevelZdt#toTd} → write
 *       {@code outDir/<cleaned LevelName or fixed level>.zdt} (illegal characters → {@code _}, deterministic);</li>
 *   <li>scan {@code worldDir/players/*.dat} → {@link PlayerDatReader#read} → {@link PlayerZdt#toTd}
 *       → write {@code outDir/players/<uuid>.zdt} (uuid from the file-name stem, falling back to
 *       {@code PlayerDatum.uuid} when non-blank);</li>
 *   <li>append each step to {@code lines}, sum bytesIn/bytesOut/filesMigrated, success → {@code ok=true}.</li>
 * </ol>
 * Whole-run try/catch: a single-file failure is recorded in {@code lines} without aborting (a bad
 * file is skipped from the count); deterministic input → deterministic output.
 */
public final class ZdtSaveMigrator implements SaveMigrator {

    /** 迁移器标识 / migrator id. */
    public static final String NAME = "zdt-v1";

    private static final String LEVEL_FILE = "level.dat";
    private static final String PLAYERS_DIR = "players";
    private static final String ZDT_EXT = ".zdt";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public MigrationReport migrate(Path worldDir, Path outDir) {
        if (worldDir == null || !Files.isDirectory(worldDir)) {
            return MigrationReport.fail("world dir not found: " + worldDir);
        }
        Path levelDat = worldDir.resolve(LEVEL_FILE);
        if (!Files.isRegularFile(levelDat)) {
            return MigrationReport.fail("level.dat not found in " + worldDir);
        }
        List<String> lines = new ArrayList<>();
        long[] stats = new long[3]; // [0] bytesIn, [1] bytesOut, [2] filesMigrated
        try {
            Files.createDirectories(outDir);

            // level.dat -> <cleaned-name-or-level>.zdt
            byte[] lvl = Files.readAllBytes(levelDat);
            stats[0] += lvl.length;
            LevelDatum level = LevelDatReader.read(lvl);
            String levelName = sanitize(level.name().isBlank() ? "level" : level.name());
            String levelTd = LevelZdt.toTd(level);
            Path levelTarget = outDir.resolve(levelName + ZDT_EXT);
            Files.writeString(levelTarget, levelTd, StandardCharsets.UTF_8);
            stats[1] += levelTd.getBytes(StandardCharsets.UTF_8).length;
            stats[2]++;
            lines.add("migrated level -> " + levelTarget + " (level.zdt)");

            // players/<uuid>.dat -> players/<uuid>.zdt
            Path playersIn = worldDir.resolve(PLAYERS_DIR);
            if (Files.isDirectory(playersIn)) {
                Path playersOut = outDir.resolve(PLAYERS_DIR);
                Files.createDirectories(playersOut);
                try (Stream<Path> s = Files.list(playersIn)) {
                    for (Path dat : s.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".dat"))
                            .sorted()
                            .collect(Collectors.toList())) {
                        migratePlayer(dat, playersOut, lines, stats);
                    }
                }
            }
            return MigrationReport.ok(stats[0], stats[1], stats[2], lines);
        } catch (IOException e) {
            return MigrationReport.fail("migration failed: " + e);
        }
    }

    private static boolean migratePlayer(Path dat, Path outDir, List<String> lines, long[] stats) {
        String fileName = dat.getFileName().toString();
        String stem = fileName.endsWith(".dat") ? fileName.substring(0, fileName.length() - 4) : fileName;
        try {
            byte[] data = Files.readAllBytes(dat);
            stats[0] += data.length;
            PlayerDatum d = PlayerDatReader.read(data);
            String uuid = d.uuid().isBlank() ? stem : d.uuid();
            // uuid 通常不在 dat 字节里，由文件名注入：重建 datum 以在输出 zdt 的 meta 中带上 uuid。
            PlayerDatum datum = new PlayerDatum(uuid, d.dimension(), d.pos(), d.rotation(),
                    d.dataVersion(), d.extra());
            String playerTd = PlayerZdt.toTd(datum);
            Path target = outDir.resolve(sanitize(uuid) + ZDT_EXT);
            Files.writeString(target, playerTd, StandardCharsets.UTF_8);
            stats[1] += playerTd.getBytes(StandardCharsets.UTF_8).length;
            stats[2]++;
            lines.add("migrated player " + uuid + " -> " + target);
            return true;
        } catch (IOException e) {
            lines.add("failed player " + stem + ": " + e);
            return false;
        }
    }

    /** 文件名清洗：仅字母/数字/点/连字符/下划线保留，其余替换为 {@code _}（确定性）。 */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }
}