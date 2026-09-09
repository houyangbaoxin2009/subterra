package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackRules;
import io.toterra.subterra.engine.save.migrate.LevelDatReader;
import io.toterra.subterra.engine.save.migrate.LevelDatum;
import io.toterra.subterra.engine.save.migrate.LevelZdt;
import io.toterra.subterra.engine.save.migrate.NbtNode;
import io.toterra.subterra.engine.save.migrate.NbtTreeReader;
import io.toterra.subterra.engine.zd.ZdHeader;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * p.2.3.2 level.dat → zdt 迁移确定性探针（纯 JVM，无 MC 运行时）。自造最小 level.dat 夹具（纯 JDK
 * NBT 写入助手，支持 COMPOUND/STRING/LONG/INT/BYTE 子集 + gzip/未压缩）→ 断言链：
 * {@link NbtTreeReader} 读出树（name/seed/time/gamerule 无损）→ {@link LevelDatReader.read} 字段无损
 * → {@code LevelZdt.toTd} 文档形状（version/meta/zd + zd 头合法 + parseVersion==2）
 * → {@code LevelZdt.parse} 字段级相等 + {@code toTd(parse(s))==s} 逐字节恒等。反向变体：缺 GameRules、
 * 缺 DayTime、含中文世界名、WorldGenSettings.seed、未压缩路径；规则渲染确定性断言。
 * 退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.3.2 level.dat → zdt migration determinism probe (pure JVM, no MC runtime). Builds a minimal
 * level.dat fixture (a pure-JDK NBT writer helper covering the COMPOUND/STRING/LONG/INT/BYTE subset +
 * gzip/uncompressed) → assertion chain: {@link NbtTreeReader} yields the tree (name/seed/time/gamerule
 * lossless) → {@link LevelDatReader.read} fields lossless → {@code LevelZdt.toTd} document shape
 * (version/meta/zd + a valid zd header with parseVersion == 2) → {@code LevelZdt.parse} field-identical
 * and {@code toTd(parse(s))==s} byte-for-byte. Reverse fixtures: missing GameRules, missing DayTime, a
 * Chinese world name, a WorldGenSettings.seed variant and the uncompressed path; deterministic rules
 * rendering. Exit 0 = PASS, 1 = FAIL.
 */
public final class SaveMigrateProbe {

    private SaveMigrateProbe() {
    }

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
        baseFixtureGzip();
        baseFixtureUncompressed();
        missingRulesAndTime();
        chineseName();
        worldGenSettingsSeed();
        rulesRenderDeterminism();
        tamperedZdRejected();

        if (failures == 0) {
            System.out.println("[SaveMigrateProbe] PASS (level.dat -> zdt, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SaveMigrateProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- fixtures -------------------------------------------------------------

    private static byte[] gzipped(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    /** 最小 level.dat：根 compound{Data{LevelName/String, RandomSeed/LONG, DayTime/LONG, GameRules}}. */
    private static byte[][] dataChildren(String levelName, Long randomSeed, Long dayTime, byte[][] rulesExtra) {
        java.util.List<byte[]> kids = new java.util.ArrayList<>();
        kids.add(wString("LevelName", levelName));
        if (randomSeed != null) {
            kids.add(wLong("RandomSeed", randomSeed));
        }
        if (dayTime != null) {
            kids.add(wLong("DayTime", dayTime));
        }
        kids.add(wString("Version", "1.21.1"));
        for (byte[] e : rulesExtra) {
            kids.add(e);
        }
        return kids.toArray(new byte[0][]);
    }

    private static byte[] gameRules(Map<String, Object> entries) {
        java.util.List<byte[]> kids = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s) {
                kids.add(wString(e.getKey(), s));
            } else if (v instanceof Long l) {
                kids.add(wLong(e.getKey(), l));
            } else if (v instanceof Integer i) {
                kids.add(wInt(e.getKey(), i));
            } else if (v instanceof Byte b) {
                kids.add(wByte(e.getKey(), b));
            }
        }
        return wCompound("GameRules", kids.toArray(new byte[0][]));
    }

    /** 基线夹具（主断言链）：经典 LevelName + RandomSeed + DayTime + GameRules. */
    private static void baseFixtureGzip() throws Exception {
        Map<String, Object> gr = new LinkedHashMap<>();
        gr.put("doFireTick", "true");          // bool string
        gr.put("doLimitedCrafting", "false");  // bool string
        gr.put("randomTickSpeed", "3");        // numeric string
        gr.put("keepInventory", (byte) 1);     // numeric byte -> long 1
        gr.put("someNum", 7L);                 // numeric long
        byte[] nbt = NbtFixture.root(
                wCompound("Data", dataChildren("P1", 123L, 1000L,
                        new byte[][]{gameRules(gr)})));
        byte[] p1 = gzipped(nbt);

        // NbtTreeReader tree read
        NbtNode root = NbtTreeReader.readCompressed(p1);
        check("nbt: gzip 读根为 COMPOUND", root != null && root.tag() == NbtTreeReader.TAG_COMPOUND);
        @SuppressWarnings("unchecked")
        Map<String, NbtNode> rootMap = (Map<String, NbtNode>) root.payload();
        NbtNode data = rootMap.get("Data");
        boolean dataOk = data != null && data.tag() == NbtTreeReader.TAG_COMPOUND;
        check("nbt: 根内含 Data compound", dataOk);
        if (dataOk) {
            @SuppressWarnings("unchecked")
            Map<String, NbtNode> level = (Map<String, NbtNode>) data.payload();
            check("nbt: LevelName=P1",
                    level.get("LevelName") != null
                            && "P1".equals(level.get("LevelName").payload()));
            check("nbt: RandomSeed=123",
                    level.get("RandomSeed") instanceof NbtNode r && ((Number) r.payload()).longValue() == 123);
            check("nbt: DayTime=1000",
                    level.get("DayTime") instanceof NbtNode d2 && ((Number) d2.payload()).longValue() == 1000);
            check("nbt: GameRules 存在且含 doFireTick",
                    level.get("GameRules") != null &&
                            ((Map<?, ?>) level.get("GameRules").payload()).containsKey("doFireTick"));
        }

        // LevelDatReader field fidelity
        LevelDatum d = LevelDatReader.read(p1);
        check("read: name", "P1".equals(d.name()));
        check("read: seed", d.seed() == 123);
        check("read: dayTime", d.dayTime() == 1000);
        check("read: rules=true/num 归一",
                d.rules().get("doFireTick") != null && d.rules().get("doFireTick").asBool()
                        && d.rules().get("randomTickSpeed") != null && d.rules().get("randomTickSpeed").asInt() == 3
                        && d.rules().get("keepInventory") != null && d.rules().get("keepInventory").asInt() == 1
                        && d.rules().get("someNum") != null && d.rules().get("someNum").asInt() == 7);

        // LevelZdt round-trip
        String s = LevelZdt.toTd(d);
        TdValue shape = Td.parse(s).get("level");
        check("zdt: 文档形状 level 表", shape instanceof io.toterra.subterra.engine.config.TdTable
                && Td.parse(s).get("level") != null);
        LevelDatum d2 = LevelZdt.parse(s);
        check("zdt: parse 字段级相等",
                d2.name().equals(d.name()) && d2.seed() == d.seed() && d2.dayTime() == d.dayTime()
                        && DatapackRules.render(d2.rules()).equals(DatapackRules.render(d.rules())));
        check("zdt: toTd(parse(s))==s 逐字节", LevelZdt.toTd(d2).equals(s));
        byte[] zd = LevelZdt.zdPayload(s);
        check("zdt: zd 头合法 version=2",
                ZdHeader.isZd(zd) && ZdHeader.parseVersion(zd, 0) == 2);
    }

    /** 未压缩路径：raw NBT（首字节 0x0A，非 gzip/zlib 魔数）也应可解析. */
    private static void baseFixtureUncompressed() throws Exception {
        Map<String, Object> gr = new LinkedHashMap<>();
        gr.put("doFireTick", "true");
        byte[] nbt = NbtFixture.root(
                wCompound("Data", dataChildren("Raw", 9L, 5L, new byte[][]{gameRules(gr)})));
        LevelDatum d = LevelDatReader.read(nbt); // raw, uncompressed
        check("read: 未压缩路径解析", "Raw".equals(d.name()) && d.seed() == 9 && d.dayTime() == 5);
    }

    /** 缺 GameRules / 缺 DayTime → 默认. */
    private static void missingRulesAndTime() throws Exception {
        byte[] noRules = NbtFixture.root(
                wCompound("Data", dataChildren("P2", 77L, null, new byte[0][])));
        LevelDatum d = LevelDatReader.read(gzipped(noRules));
        check("read: 缺 GameRules → rules 空", d.rules().isEmpty());
        check("read: 缺 DayTime → time 0", d.name().equals("P2") && d.dayTime() == 0);
    }

    /** 中文世界名 + 全空 gamerule 集 → 结构往返. */
    private static void chineseName() throws Exception {
        Map<String, Object> gr = new LinkedHashMap<>();
        gr.put("doMobLoot", "true");
        byte[] nbt = NbtFixture.root(
                wCompound("Data", dataChildren("雨林城市", 20240607L, 24000L, new byte[][]{gameRules(gr)})));
        LevelDatum d = LevelDatReader.read(gzipped(nbt));
        String s = LevelZdt.toTd(d);
        LevelDatum d2 = LevelZdt.parse(s);
        check("中文世界名 往返", "雨林城市".equals(d2.name())
                && LevelZdt.toTd(d2).equals(s)
                && d2.rules().get("doMobLoot").asBool());
    }

    /** 新版种子：无 RandomSeed，改为 WorldGenSettings.seed（LONG）. */
    private static void worldGenSettingsSeed() throws Exception {
        byte[] wgs = wCompound("WorldGenSettings",
                new byte[][]{wLong("seed", 424242L)});
        byte[] nbt = NbtFixture.root(
                wCompound("Data", dataChildren("Modern", null, 8L,
                        new byte[][]{wgs})));
        LevelDatum d = LevelDatReader.read(gzipped(nbt));
        check("read: WorldGenSettings.seed 回退", "Modern".equals(d.name()) && d.seed() == 424242L);
    }

    /** 规则渲染确定性：两次 render + 排序. */
    private static void rulesRenderDeterminism() throws Exception {
        Map<String, Object> gr = new LinkedHashMap<>();
        gr.put("wild", "true");
        gr.put("tick", "40");
        gr.put("alpha", "false");
        byte[] nbt = NbtFixture.root(
                wCompound("Data", dataChildren("Rules", 1L, 1L, new byte[][]{gameRules(gr)})));
        LevelDatum d = LevelDatReader.read(gzipped(nbt));
        String r1 = DatapackRules.render(d.rules());
        String r2 = DatapackRules.render(d.rules());
        check("rules: render 确定性 + 排序",
                r1.equals(r2) && r1.equals("alpha=false; tick=40; wild=true"));
    }

    /** 篡改 zd 与 meta 不一致 → parse 必须抛. */
    private static void tamperedZdRejected() throws Exception {
        Map<String, Object> gr = new LinkedHashMap<>();
        gr.put("a", "true");
        byte[] nbt = NbtFixture.root(
                wCompound("Data", dataChildren("T", 5L, 5L, new byte[][]{gameRules(gr)})));
        LevelDatum d = LevelDatReader.read(gzipped(nbt));
        String good = LevelZdt.toTd(d);
        // 篡改 meta.seed 使其与 zd 不一致
        String tampered = good.replace("seed = 5,", "seed = 999,");
        boolean threw = false;
        try {
            LevelZdt.parse(tampered);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("zdt: 交叉断言不一致抛 IllegalArgumentException", threw);
    }

    // ---- helpers: NBT writer lifted into the shared {@link NbtFixture} (p.2.3.3
    // read-only refactor; these thin delegates keep the call sites unchanged). ----

    private static byte[] wString(String name, String val) {
        return NbtFixture.wString(name, val);
    }

    private static byte[] wLong(String name, long val) {
        return NbtFixture.wLong(name, val);
    }

    private static byte[] wInt(String name, int val) {
        return NbtFixture.wInt(name, val);
    }

    private static byte[] wByte(String name, int val) {
        return NbtFixture.wByte(name, val);
    }

    private static byte[] wCompound(String name, byte[][] kids) {
        return NbtFixture.wCompound(name, kids);
    }

    /** 根：具名 COMPOUND（名字留空，与 level.dat 惯例一致）后接 payload. */
    private static byte[] root(byte[] namedCompound) {
        return NbtFixture.root(namedCompound);
    }
}