package io.toterra.subterra.probes;

import io.toterra.subterra.api.migrate.MigrationReport;
import io.toterra.subterra.api.migrate.SaveMigrator;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackRules;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveSlot;
import io.toterra.subterra.engine.save.migrate.LevelDatum;
import io.toterra.subterra.engine.save.migrate.LevelZdt;
import io.toterra.subterra.engine.save.migrate.PlayerDatum;
import io.toterra.subterra.engine.save.migrate.PlayerZdt;
import io.toterra.subterra.engine.save.migrate.ZdtSaveMigrator;
import io.toterra.subterra.migrate.Runner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * p.2.3.5 双层配置 + subterra-migrate SPI 真实迁移管线 探针（纯 JVM，无 MC 运行时）。
 * 两个 section：
 * <ol>
 *   <li>双层配置：{@link DatapackRules#resolveTiered} 语义（global 全入内，include overrides 冲突
 *       key 胜出）+ {@link SaveContainer#effectiveTiered}/{@link #renderTiered} 确定性（两次相等、
 *       key 排序）+ 既有 {@code effectiveRules} 语义不变复测 + 空参边界。</li>
 *   <li>迁移管线（临时目录示例世界 → {@code .zdt}）：先 {@link ServiceLoader} 断言能发现 1 个
 *       {@link SaveMigrator} 且 name()=={@code "zdt-v1"}（engine 实现注册 + migrate 侧零 engine
 *       import 的运行时契约）→ 探针直接调 engine 实现（devkit classpath 全模块）migrate() →
 *       断言 ok / 文件数（1 level + 2 players）/ uuid 对应 / 输出 level.zdt 存在并经
 *       {@code ZdtTransfer/LevelZdt.parse} 回读字段级等于源 → 再经 {@link Runner#run} 走一遍断言
 *       返回 0 → 可选坏文件（截断字节）验证「不整体崩溃」。</li>
 * </ol>
 * 退出码 0 = PASS，1 = FAIL；临时目录测毕清理。
 * <p>
 * p.2.3.5 two-tier config + subterra-migrate SPI real migration pipeline probe (pure JVM, no MC
 * runtime). Two sections:
 * <ol>
 *   <li>Two-tier config: {@link DatapackRules#resolveTiered} semantics (global enters, the save
 *       override wins on a conflicting key) + {@link SaveContainer#effectiveTiered}/{@link #renderTiered}
 *       determinism (twice equal, sorted keys) + the existing {@code effectiveRules} semantics
 *       re-checked unchanged + empty-input boundaries.</li>
 *   <li>Migration pipeline (a fixture world in a temp dir → {@code .zdt}): first {@link ServiceLoader}
 *       asserts exactly one {@link SaveMigrator} is found with name() == {@code "zdt-v1"} (the runtime
 *       contract of the engine-side registration + migrate's zero-engine-import side), then the probe
 *       calls the engine implementation directly (devkit classpath carries every module) migrate() →
 *       assert ok / file count (1 level + 2 players) / uuid mapping / output level.zdt present and
 *       read back via {@code ZdtTransfer/LevelZdt.parse} field-identical to the source → then rerun
 *       through {@link Runner#run} asserting exit 0 → and an optional truncated "bad" file proving the
 *       run does not crash.</li>
 * </ol>
 * Exit 0 = PASS, 1 = FAIL; temp dirs are cleaned up.
 */
public final class SavePipelineProbe {

    private SavePipelineProbe() {
    }

    private static final String UUID_1 = "1f2e3d4c-5b6a-7f8e-9d0c-1a2b3c4d5e6f";
    private static final String UUID_2 = "aabbccdd-0011-2233-4455-66778899aabb";

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        twoTierConfig();
        migrationPipeline();

        if (failures == 0) {
            System.out.println("[SavePipelineProbe] PASS (tiered config + migrate SPI pipeline, "
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SavePipelineProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- Section 1: two-tier config ----------------------------------------------

    private static void twoTierConfig() {
        Map<String, TdValue> global = new LinkedHashMap<>();
        global.put("a", TdValue.of(1L));
        global.put("b", TdValue.of(2L));
        Map<String, TdValue> overlay = new LinkedHashMap<>();
        overlay.put("b", TdValue.of(3L));

        SaveContainer sc = new SaveContainer().globalRules(global).overlay(SaveSlot.CONFIG, overlay);
        Map<String, TdValue> eff = sc.effectiveTiered(SaveSlot.CONFIG);
        check("tiered: effectiveTiered == {a=1, b=3}",
                eff.size() == 2 && scalarInt(eff.get("a")) == 1 && scalarInt(eff.get("b")) == 3);
        check("tiered: 保序 (a,b)", "[a, b]".equals(eff.keySet().toString()));
        check("tiered: 无 overlay 槽 → 全局层", "a=1; b=2".equals(sc.renderTiered(SaveSlot.WORLD)));
        String r1 = sc.renderTiered(SaveSlot.CONFIG);
        String r2 = sc.renderTiered(SaveSlot.CONFIG);
        check("tiered: renderTiered 确定性 + key 排序", r1.equals(r2) && "a=1; b=3".equals(r1));
        check("tiered: 两层互不影响 — globalRules() 仍是 {a=1,b=2}",
                scalarInt(sc.globalRules().get("a")) == 1 && scalarInt(sc.globalRules().get("b")) == 2 && !sc.globalRules().containsKey("c"));

        // 既有 effectiveRules（doc 内嵌 rules + 槽 overlay）语义不变复测
        TdTable doc = TdTable.builder().put("rules", TdTable.builder()
                .element(rulesKv("a", 10L))
                .element(rulesKv("c", 5L))
                .build()).build();
        Map<String, TdValue> o2 = new LinkedHashMap<>();
        o2.put("b", TdValue.of(20L));
        SaveContainer sc2 = new SaveContainer().globalRules(global).attach(SaveSlot.CONFIG, doc)
                .overlay(SaveSlot.CONFIG, o2);
        Map<String, TdValue> eff2 = sc2.effectiveRules(SaveSlot.CONFIG);
        check("tiered: 既有 effectiveRules 语义不变 (doc a=10,c=5 + overlay b=20)",
                eff2.size() == 3 && scalarInt(eff2.get("a")) == 10
                        && scalarInt(eff2.get("b")) == 20 && scalarInt(eff2.get("c")) == 5);
        check("tiered: effectiveRules 与 effectiveTiered 正交 (doc 层不泄漏进 global 层)",
                "a=1; b=20".equals(DatapackRules.render(sc2.effectiveTiered(SaveSlot.CONFIG))));

        // resolveTiered 空参边界
        check("resolveTiered: 空-空 → 空", DatapackRules.resolveTiered(Map.of(), Map.of()).isEmpty());
        check("resolveTiered: 仅 global → 原样", globalEquals(DatapackRules.resolveTiered(global, Map.of()), Map.of("a", 1L, "b", 2L)));
        check("resolveTiered: 仅 overrides → 原样", globalEquals(DatapackRules.resolveTiered(Map.of(), o2), Map.of("b", 20L)));
        check("resolveTiered: overrides 覆盖 global",
                globalEquals(DatapackRules.resolveTiered(global, overlay), Map.of("a", 1L, "b", 3L)));
    }

    private static TdTable rulesKv(String k, long v) {
        return TdTable.builder().put("k", TdValue.str(k)).put("v", TdValue.of(v)).build();
    }

    private static long scalarInt(TdValue v) {
        return v == null ? Long.MIN_VALUE : v.asInt();
    }

    private static boolean globalEquals(Map<String, TdValue> m, Map<String, Long> expected) {
        if (m.size() != expected.size()) {
            return false;
        }
        for (Map.Entry<String, Long> e : expected.entrySet()) {
            TdValue v = m.get(e.getKey());
            if (v == null || v.asInt() != e.getValue()) {
                return false;
            }
        }
        return true;
    }

    // ---- Section 2: migration pipeline -------------------------------------------

    private static void migrationPipeline() throws Exception {
        Path tmp = Files.createTempDirectory("subterra-savepipe-");
        try {
            // fixture world: level.dat + 2 players, all valid
            Path world = tmp.resolve("world");
            Path players = Files.createDirectories(world.resolve("players"));
            Files.write(world.resolve("level.dat"), levelDat("TestWorld", 123L, 1000L));
            Files.write(players.resolve(UUID_1 + ".dat"), playerDat("Alice", 1.5, 64.0, -2.5));
            Files.write(players.resolve(UUID_2 + ".dat"), playerDat("Bob", -10.0, 70.0, 300.5));

            // ServiceLoader 契约：能发现 1 个且 name()==zdt-v1
            ServiceLoader<SaveMigrator> loader = ServiceLoader.load(SaveMigrator.class);
            Iterator<SaveMigrator> it = loader.iterator();
            check("spi: ServiceLoader 发现至少 1 个 SaveMigrator", it.hasNext());
            String foundName = it.hasNext() ? it.next().name() : "<none>";
            check("spi: name() == \"zdt-v1\"", "zdt-v1".equals(foundName));

            // 探针直接调 engine 实现：确定性管线（devkit classpath 全模块）
            Path out = tmp.resolve("out");
            MigrationReport report = new ZdtSaveMigrator().migrate(world, out);
            check("migrate: ok == true", report.ok());
            check("migrate: lines 有 3 条 (1 level + 2 player)",
                    report.lines().size() == 3
                            && report.lines().get(0).startsWith("migrated level -> ")
                            && report.lines().get(1).startsWith("migrated player ")
                            && report.lines().get(2).startsWith("migrated player "));
            check("migrate: filesMigrated == 3", report.filesMigrated() == 3);
            check("migrate: bytesIn/bytesOut > 0", report.bytesIn() > 0 && report.bytesOut() > 0);

            // 输出 level.zdt 存在且字段级回读等于源
            Path levelOut = out.resolve("TestWorld.zdt");
            check("migrate: <LevelName>.zdt 存在", Files.isRegularFile(levelOut));
            LevelDatum expectedLevel = new LevelDatum("TestWorld", 123L, 1000L,
                    Map.of("doFireTick", TdValue.of(true), "randomTickSpeed", TdValue.of(3L)));
            LevelDatum readLevel = LevelZdt.parse(Files.readString(levelOut));
            check("migrate: level.zdt 回读字段级等于源",
                    expectedLevel.name().equals(readLevel.name())
                            && expectedLevel.seed() == readLevel.seed()
                            && expectedLevel.dayTime() == readLevel.dayTime()
                            && DatapackRules.render(expectedLevel.rules()).equals(DatapackRules.render(readLevel.rules())));

            // 输出 players/<uuid>.zdt 存在且回读等于源
            Path p1 = out.resolve("players").resolve(UUID_1 + ".zdt");
            Path p2 = out.resolve("players").resolve(UUID_2 + ".zdt");
            check("migrate: players/(uuid).zdt 存在且 UUID 对应",
                    Files.isRegularFile(p1) && Files.isRegularFile(p2));
            PlayerDatum readU1 = PlayerZdt.parse(Files.readString(p1));
            check("migrate: player.zdt 回读 uuid + 核心字段",
                    UUID_1.equals(readU1.uuid())
                            && "minecraft:overworld".equals(readU1.dimension())
                            && readU1.pos()[0] == 1.5 && readU1.dataVersion() == 4319);

            // Runner.run API 走一遍（另一个夹具目录）→ 返回 0
            Path world2 = tmp.resolve("world2");
            Files.createDirectories(world2.resolve("players"));
            Files.write(world2.resolve("level.dat"), levelDat("CliWorld", 55L, 7L));
            Files.write(world2.resolve("players").resolve(UUID_1 + ".dat"), playerDat("Carl", 0.0, 0.0, 0.0));
            Path out2 = tmp.resolve("out2");
            int rc = Runner.run(new String[]{"--world", world2.toString(), "--out", out2.toString()});
            check("runner: Runner.run 返回 0", rc == 0);
            check("runner: 输出目录已生成 level.zdt", Files.isRegularFile(out2.resolve("CliWorld.zdt")));
            check("runner: 缺 --world → 返回 2", Runner.run(new String[]{}) == 2);

            // 可选：坏文件（截断字节）→ 不整体崩溃，处理继续
            Path world3 = tmp.resolve("world3");
            Files.createDirectories(world3.resolve("players"));
            Files.write(world3.resolve("level.dat"), levelDat("RobustWorld", 99L, 9L));
            Files.write(world3.resolve("players").resolve(UUID_1 + ".dat"), playerDat("Ok", 1.0, 1.0, 1.0));
            Files.write(world3.resolve("players").resolve("00000000-0000-0000-0000-000000000bad.dat"),
                    new byte[]{1, 2, 3}); // truncated -> not a valid level compound
            Path out3 = tmp.resolve("out3");
            MigrationReport robustness = new ZdtSaveMigrator().migrate(world3, out3);
            check("robust: 坏文件不整体崩溃 — ok == true", robustness.ok());
            check("robust: 好玩家仍迁移", Files.isRegularFile(out3.resolve("players").resolve(UUID_1 + ".zdt")));
        } finally {
            deleteRecursively(tmp);
        }
    }

    // ---- fixtures (reuses the shared {@link NbtFixture} NBT writer) --------------

    /** 最小 level.dat：根 compound{Data{LevelName, RandomSeed, DayTime, GameRules}}，gzip 压缩. */
    private static byte[] levelDat(String name, long seed, long dayTime) {
        byte[] nbt = NbtFixture.root(NbtFixture.wCompound("Data", new byte[][]{
                NbtFixture.wString("LevelName", name),
                NbtFixture.wLong("RandomSeed", seed),
                NbtFixture.wLong("DayTime", dayTime),
                NbtFixture.wString("Version", "1.21.1"),
                NbtFixture.wCompound("GameRules", new byte[][]{
                        NbtFixture.wString("doFireTick", "true"),
                        NbtFixture.wString("randomTickSpeed", "3"),
                }),
        }));
        try {
            return NbtFixture.gzipped(nbt);
        } catch (Exception e) {
            throw new IllegalStateException("level fixture gzip failed", e);
        }
    }

    /** 最小玩家 dat：根 compound 即玩家数据（Dimension + Pos + Rotation + DataVersion），gzip 压缩. */
    private static byte[] playerDat(String displayName, double x, double y, double z) {
        byte[] player = NbtFixture.root(playerChildren(
                NbtFixture.wString("Dimension", "minecraft:overworld"),
                NbtFixture.wListDoubles("Pos", new double[]{x, y, z}),
                NbtFixture.wListFloats("Rotation", new float[]{90.0f, 0.0f}),
                NbtFixture.wInt("DataVersion", 4319),
                NbtFixture.wInt("foodLevel", 20),
                NbtFixture.wString("displayName", displayName)));
        try {
            return NbtFixture.gzipped(player);
        } catch (Exception e) {
            throw new IllegalStateException("player fixture gzip failed", e);
        }
    }

    private static byte[] playerChildren(byte[]... kids) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] k : kids) {
            o.writeBytes(k);
        }
        return o.toByteArray();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.deleteIfExists(p);
            }
        }
    }
}