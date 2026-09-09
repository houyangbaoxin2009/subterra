package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.save.migrate.PlayerDatReader;
import io.toterra.subterra.engine.save.migrate.PlayerDatum;
import io.toterra.subterra.engine.save.migrate.PlayerZdt;
import io.toterra.subterra.engine.zd.ZdHeader;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * p.2.3.3 players/&lt;uuid&gt;.dat → zdt 玩家数据迁移确定性探针（纯 JVM，无 MC 运行时）。复用
 * {@link NbtFixture} 自造玩家夹具（根 compound 即玩家数据，无 Data 外壳；含 Dimension/Pos/DataVersion
 * 等核心键 + 无关键如 Abilities COMPOUND / Inventory LIST 验证 schema-free 跳过）→ 断言链：
 * {@link PlayerDatReader#read} 核心字段无损 + {@code extra} 只含标量键（嵌套结构被跳过）
 * → {@code PlayerZdt.toTd} 文档（version/meta/zd + zd 头合法 parseVersion==2）
 * → {@code PlayerZdt.parse} 字段级相等 + {@code toTd(parse(s))==s} 逐字节恒等。变体夹具：完整字段 /
 * 缺 Rotation / 缺 DataVersion / 中文名附近数据 / 直接构造 datum 验证 uuid 往返。独立于
 * {@link SaveMigrateProbe}（level）的非 fork 纯 JVM 探针。退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.3.3 players/&lt;uuid&gt;.dat → zdt player-data migration determinism probe (pure JVM, no MC
 * runtime). Reuses {@link NbtFixture} to build player fixtures (the root compound is the player data
 * itself, no {@code Data} wrapper; core keys Dimension/Pos/DataVersion etc. plus schema-free keys such
 * as Abilities COMPOUND / Inventory LIST to prove they are skipped) → assertion chain:
 * {@link PlayerDatReader#read} core fields lossless + {@code extra} scalar-keys only (nested structures
 * skipped) → {@code PlayerZdt.toTd} document (version/meta/zd + a valid zd header parseVersion==2)
 * → {@code PlayerZdt.parse} field-identical and {@code toTd(parse(s))==s} byte-for-byte. Variant
 * fixtures: full fields / missing Rotation / missing DataVersion / data near a Chinese name / a
 * directly-constructed datum to verify the uuid round-trip. This is a non-forking pure-JVM probe,
 * independent of {@link SaveMigrateProbe} (level). Exit 0 = PASS, 1 = FAIL.
 */
public final class SavePlayerProbe {

    private SavePlayerProbe() {
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
        fullFixture();
        missingRotation();
        missingDataVersion();
        chineseDimensionNested();
        uuidRoundTrip();
        levelZdtUnchangedAfterGeneralisation();

        if (failures == 0) {
            System.out.println("[SavePlayerProbe] PASS (players/<uuid>.dat -> zdt, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SavePlayerProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- fixtures & assertions -------------------------------------------------

    /** 完整字段：Dimension + Pos + Rotation + DataVersion + 若干无关键 + 嵌套跳过. */
    private static void fullFixture() throws Exception {
        byte[] player = NbtFixture.root(playerCompound(
                new byte[][]{
                        NbtFixture.wString("Dimension", "minecraft:overworld"),
                        NbtFixture.wListDoubles("Pos", new double[]{100.5, 64.0, -200.25}),
                        NbtFixture.wListFloats("Rotation", new float[]{45.5f, 13.25f}),
                        NbtFixture.wInt("DataVersion", 4319),
                        NbtFixture.wInt("foodLevel", 20),
                        NbtFixture.wInt("playerGameType", 1),
                        NbtFixture.wString("seenCredits", "true"),
                        // 无关键嵌套结构 → 必须被 schema-free 跳过（不进 extra）
                        NbtFixture.wCompound("Abilities", new byte[][]{
                                NbtFixture.wByte("invulnerable", 0),
                                NbtFixture.wByte("flying", 0),
                        }),
                        NbtFixture.wList("Inventory", 10, new byte[][]{}),
                }));
        byte[] p = NbtFixture.gzipped(player);

        PlayerDatum d = PlayerDatReader.read(p);
        check("read: dimension", "minecraft:overworld".equals(d.dimension()));
        check("read: pos 三元素", d.pos().length == 3 && d.pos()[0] == 100.5 && d.pos()[1] == 64.0 && d.pos()[2] == -200.25);
        check("read: rotation 两元素", d.rotation().length == 2 && d.rotation()[0] == 45.5f && d.rotation()[1] == 13.25f);
        check("read: dataVersion", d.dataVersion() == 4319);
        check("read: uuid 默认空", "".equals(d.uuid()));
        check("read: extra 含标量键 + 嵌套跳过",
                d.extra().get("foodLevel") != null && d.extra().get("foodLevel").asInt() == 20
                        && d.extra().get("playerGameType") != null && d.extra().get("playerGameType").asInt() == 1
                        && d.extra().get("seenCredits") != null && "true".equals(d.extra().get("seenCredits").asString())
                        && !d.extra().containsKey("Abilities") && !d.extra().containsKey("Inventory"));
        check("read: extra 只含标量键", extraScalarOnly(d));

        String s = PlayerZdt.toTd(d);
        byte[] zd = PlayerZdt.zdPayload(s);
        check("zdt: zd 头合法 version=2", ZdHeader.isZd(zd) && ZdHeader.parseVersion(zd, 0) == 2);
        PlayerDatum d2 = PlayerZdt.parse(s);
        check("zdt: parse 字段级相等", datumEqual(d, d2));
        check("zdt: toTd(parse(s))==s 逐字节", PlayerZdt.toTd(d2).equals(s));
    }

    /** 缺 Rotation → 默认 {0,0}. */
    private static void missingRotation() throws Exception {
        byte[] player = NbtFixture.root(playerCompound(new byte[][]{
                NbtFixture.wString("Dimension", "minecraft:the_nether"),
                NbtFixture.wListDoubles("Pos", new double[]{10.0, 20.0, 30.0}),
                NbtFixture.wInt("DataVersion", 3700),
        }));
        PlayerDatum d = PlayerDatReader.read(NbtFixture.gzipped(player));
        PlayerDatum p2 = roundTrip(d);
        check("缺 Rotation → 默认 {0,0} 且往返", p2.rotation()[0] == 0f && p2.rotation()[1] == 0f);
    }

    /** 缺 DataVersion → 默认 0. */
    private static void missingDataVersion() throws Exception {
        byte[] player = NbtFixture.root(playerCompound(new byte[][]{
                NbtFixture.wString("Dimension", "minecraft:the_end"),
                NbtFixture.wListDoubles("Pos", new double[]{1.0, 2.0, 3.0}),
                NbtFixture.wListFloats("Rotation", new float[]{0.0f, 90.0f}),
        }));
        PlayerDatum d = PlayerDatReader.read(NbtFixture.gzipped(player));
        check("缺 DataVersion → 默认 0",
                d.dataVersion() == 0 && roundTrip(d).dataVersion() == 0);
    }

    /** 中文名附近数据（中文长度 UTF-8 多字节）→ 往返无损. */
    private static void chineseDimensionNested() throws Exception {
        byte[] player = NbtFixture.root(playerCompound(new byte[][]{
                NbtFixture.wString("Dimension", "toterra:雨林城市"),
                NbtFixture.wListDoubles("Pos", new double[]{-10.5, 0.0, 10.5}),
                NbtFixture.wListFloats("Rotation", new float[]{180.0f, 0.0f}),
                NbtFixture.wInt("DataVersion", 4319),
                NbtFixture.wString("displayName", "玩家甲"),
        }));
        PlayerDatum d = PlayerDatReader.read(NbtFixture.gzipped(player));
        PlayerDatum p2 = roundTrip(d);
        check("中文维度/显示名往返",
                "toterra:雨林城市".equals(p2.dimension())
                        && "玩家甲".equals(p2.extra().get("displayName").asString())
                        && p2.pos()[0] == -10.5 && p2.pos()[1] == 0.0 && p2.pos()[2] == 10.5);
    }

    /** uuid 直接构造（通常由文件名注入，不来自 dat 字节）→ meta 往返无损. */
    private static void uuidRoundTrip() throws Exception {
        Map<String, TdValue> extra = new LinkedHashMap<>();
        extra.put("foodLevel", TdValue.of(18));
        extra.put("flag", TdValue.of(true));
        PlayerDatum d = new PlayerDatum(
                "1f2e3d4c-5b6a-7f8e-9d0c-1a2b3c4d5e6f",
                "minecraft:overworld",
                new double[]{0.0, 70.0, 0.0},
                new float[]{0.0f, 0.0f},
                4319,
                extra);
        PlayerDatum p2 = roundTrip(d);
        check("uuid + bool extra 往返", d.uuid().equals(p2.uuid()) && datumEqual(d, p2));
    }

    private static void levelZdtUnchangedAfterGeneralisation() {
        // 抽象在 ZdtTransfer.parse 之上校验版本时对齐 LevelZdt 语义：缺失/非法版本必抛。
        // 此处再钉一层：player 文档带非法版本被拒。
        String bad = "type tie<data>\nplayer = [ version = 0, meta = [ uuid = \"u\" ], zd = \"\" ]";
        boolean threw = false;
        try {
            PlayerZdt.parse(bad);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("zdt: 非法版本抛 IllegalArgumentException", threw);
    }

    // ---- helpers ---------------------------------------------------------------

    /** 根 compound 的孩子们：玩家根无 Data 外壳，孩子即玩家数据键（调用方再以 NbtFixture.root 包根）. */
    private static byte[] playerCompound(byte[][] kids) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] k : kids) {
            o.writeBytes(k);
        }
        return o.toByteArray();
    }

    /** read → toTd → parse 往返，返回 parse 结果；并校验 toTd(parse(toTd(d)))==toTd(d) 逐字节. */
    private static PlayerDatum roundTrip(PlayerDatum d) {
        String s = PlayerZdt.toTd(d);
        PlayerDatum p2 = PlayerZdt.parse(s);
        check("zdt: toTd(parse(toTd(d)))==toTd(d) 逐字节", PlayerZdt.toTd(p2).equals(s));
        return p2;
    }

    private static boolean extraScalarOnly(PlayerDatum d) {
        for (TdValue v : d.extra().values()) {
            if (v == null || v instanceof io.toterra.subterra.engine.config.TdTable) {
                return false;
            }
        }
        return true;
    }

    private static boolean datumEqual(PlayerDatum a, PlayerDatum b) {
        if (!a.uuid().equals(b.uuid()) || !a.dimension().equals(b.dimension())
                || a.dataVersion() != b.dataVersion()) {
            return false;
        }
        double[] pa = a.pos();
        double[] pb = b.pos();
        if (pa.length != pb.length) {
            return false;
        }
        for (int i = 0; i < pa.length; i++) {
            if (Double.doubleToLongBits(pa[i]) != Double.doubleToLongBits(pb[i])) {
                return false;
            }
        }
        float[] ra = a.rotation();
        float[] rb = b.rotation();
        if (ra.length != rb.length) {
            return false;
        }
        for (int i = 0; i < ra.length; i++) {
            if (Float.floatToIntBits(ra[i]) != Float.floatToIntBits(rb[i])) {
                return false;
            }
        }
        if (a.extra().size() != b.extra().size()) {
            return false;
        }
        for (Map.Entry<String, TdValue> e : a.extra().entrySet()) {
            TdValue av = e.getValue();
            TdValue bv = b.extra().get(e.getKey());
            if (bv == null || !av.toString().equals(bv.toString())) {
                return false;
            }
        }
        return true;
    }
}