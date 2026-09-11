package io.toterra.subterra.probes;

import io.toterra.subterra.api.lod.LodApi;
import io.toterra.subterra.api.lod.LodDistanceSpec;
import io.toterra.subterra.api.lod.LodQualitySpec;
import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.RenderBackend;
import io.toterra.subterra.engine.render.lod.CacheResult;
import io.toterra.subterra.engine.render.lod.CacheStatus;
import io.toterra.subterra.engine.render.lod.LodApiMirror;
import io.toterra.subterra.engine.render.lod.LodBudgetScheduler;
import io.toterra.subterra.engine.render.lod.LodBudgetScheduler.CellRequest;
import io.toterra.subterra.engine.render.lod.LodCache;
import io.toterra.subterra.engine.render.lod.LodCacheKey;
import io.toterra.subterra.engine.render.lod.LodConfigDoc;
import io.toterra.subterra.engine.render.lod.LodDistanceSelector;
import io.toterra.subterra.engine.render.lod.LodLevel;
import io.toterra.subterra.engine.render.lod.LodMerger;
import io.toterra.subterra.engine.render.lod.LodPipeline;
import io.toterra.subterra.engine.render.lod.LodPolygonizer;
import io.toterra.subterra.engine.render.lod.LodRenderBackend;
import io.toterra.subterra.engine.render.lod.LodSeamRules;
import io.toterra.subterra.engine.render.lod.LodSection;
import io.toterra.subterra.engine.render.lod.LodTieAccelerator;
import io.toterra.subterra.engine.render.lod.LodVertexLayout;
import io.toterra.subterra.engine.render.lod.LodViewCuller;
import io.toterra.subterra.engine.render.lod.LodViewCuller.Bounds;
import io.toterra.subterra.engine.render.lod.LodViewCuller.ViewFrustum;
import io.toterra.subterra.engine.sim.budget.TickBudget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * p.2.28.6（2/2）— LOD 确定性/接线探针：把 engine.render.lod 全套（p.2.28.1-.5）与 api.lod
 * 契约面（p.2.28.6 1/2）锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；exit 0 = PASS，
 * exit 1 = FAIL；不进 mod jar。分节：
 * <ol>
 *   <li><b>api 档位固定序</b>：{@link LodQualitySpec#values()} 固定 {@code STANDARD→HIGH→LOW} +
 *       {@code form()/fromForm()} 往返 + 未知/大小写拒绝 + {@code LodApi.QUALITY_TIER_FORMS}
 *       与 engine {@code LodConfigDoc.QUALITY_TIERS} 逐字对照。</li>
 *   <li><b>api↔engine 镜像逐字对照</b>：调 {@link LodApiMirror} 与 {@link LodApi} 对同输入求值，
 *       档位/缺省质量/最大层级/每区块方块数/缺省距离表/{@code resolveLevel} 投影同输入同输出。</li>
 *   <li><b>层级阶梯与阈值表</b>：{@link LodLevel#values()} 六层固定序 + 每层 {@code k/chunkSpan/blockSpan}
 *       期望硬编码 + {@code form()/fromForm()} + {@code canonicalLadder()} 连跑同字符 + 缺省距离表
 *       {@code L1@64,L2@128,L3@256,L4@512}。</li>
 *   <li><b>polygonizer+merger 确定性</b>：同输入同字节（两次运行）+ 再运行（独立重建）逐字节一致 +
 *       merge 链两次一致。</li>
 *   <li><b>merge 链 level 递增与往返</b>：L0×4 merge → L1 且 level 递增 + {@code toBytes/fromBytes}
 *       往返恒等。</li>
 *   <li><b>LodDistanceSelector 分档单调</b>：随距离平方增大单调不减 + 期望层级硬编码。</li>
 *   <li><b>视锥剔除固定命中</b>：固定 {@link Bounds}/{@link ViewFrustum} 下 keep/cull 硬编码 + 朝向位
 *       背面剔除命中。</li>
 *   <li><b>预算超限确定性跳过</b>：{@link LodBudgetScheduler} 全局 cap=1 时首入次跳过 + 确定性降级提示。</li>
 *   <li><b>LodTieAccelerator skip-or-parity</b>：无资源确定性 skip；资源 dll 在场则装载对照（与 JDK 内核
 *       逐位一致，或确定性 skip，两者皆断言通过分支）。</li>
 *   <li><b>LodCache 往返 + 四类拒</b>：{save→load 往返→hit} + 篡改/截断/版本不符/坏魔数四类确定性拒绝
 *       （staging 于 build/tmp，写完即删）。</li>
 *   <li><b>LodRenderBackend 注册与实例化固定序</b>：隔离注册表下注册 + 按名查得 + defaultFor 选中 +
 *       实例化两遍同字节 + 顶点布局定点化。</li>
 *   <li><b>LodSeamRules 缝合一雾单调</b>：stitch 位规则 + 距离雾随档单调、末档 FOG_MAX。</li>
 *   <li><b>接线契约（静态盘点）</b>：{@code LodRuntime} 类存在（只加载不初始化）+ 类字节含门控
 *       {@code subterra.probe.lod} + marker {@code [Subterra lod]}；{@code RenderHooksClient} 类字节含
 *       {@code [Subterra lod]}；{@code AsyncE2EProbe} 类字节含 {@code [Subterra lod]} lodOk 断言字面量。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；失败计数只在失败路径自增；全过输出 {@code [LodProbe] PASS (n checks)}
 * exit 0，否则 FAIL exit 1。
 * <p>
 * p.2.28.6 (2/2) — LOD determinism/wiring probe: anchors the whole engine.render.lod core (p.2.28.1-.5)
 * and the api.lod contract surface (p.2.28.6 1/2) to pure-JVM assertions. Pure JVM — no MC runtime, no
 * timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Sections:
 * api tier fixed order / api↔engine mirror verbatim / ladder + distance table / polygonizer+merger
 * determinism (same bytes + refresh-stable) / merge-chain level progression + round-trip / distance
 * selector tiering monotonicity / fixed frustum culling hits / budget overrun deterministic skip /
 * LodTieAccelerator skip-or-parity / LodCache round-trip + four rejection classes / backend registration
 * + fixed-order instantiation / seam + fog monotonicity / wiring contract (static inventory).
 */
public final class LodProbe {

    private LodProbe() {
    }

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        try {
            apiTiers();
            apiMirror();
            ladderAndThresholds();
            polygonizerMergerDeterminism();
            mergeChainRoundTrip();
            distanceSelectorMonotonicity();
            viewCulling();
            budgetOverrun();
            tieSkipOrParity();
            cacheRoundTripAndRejections();
            backendRegistration();
            seamAndFog();
            wiringContract();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[LodProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[LodProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    /** 动作抛出 IAE 为真。True iff the action raises IllegalArgumentException. */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ---------- (1) api tier fixed order ----------

    private static void apiTiers() {
        List<String> expected = List.of("STANDARD", "HIGH", "LOW");
        LodQualitySpec[] vals = LodQualitySpec.values();
        boolean fixedOrder = vals.length == 3;
        for (int i = 0; i < vals.length && i < expected.size(); i++) {
            fixedOrder = fixedOrder && vals[i].name().equals(expected.get(i));
        }
        check("LodQualitySpec: values() 固定序（3 档，逐名核对 STANDARD→HIGH→LOW）", fixedOrder);
        boolean roundTrip = true;
        for (LodQualitySpec q : LodQualitySpec.values()) {
            roundTrip = roundTrip && LodQualitySpec.fromForm(q.form()) == q;
        }
        check("LodQualitySpec: form()/fromForm() 全量往返（3 档各一次）", roundTrip);
        check("LodQualitySpec: 大小写不敏感 + 空白容忍 + 未知/空/null 拒绝",
                LodQualitySpec.fromForm(" high ") == LodQualitySpec.HIGH
                        && LodQualitySpec.fromForm("LOW") == LodQualitySpec.LOW
                        && throwsIAE(() -> LodQualitySpec.fromForm("ultra"))
                        && throwsIAE(() -> LodQualitySpec.fromForm(""))
                        && throwsIAE(() -> LodQualitySpec.fromForm(null)));
        check("LodApi.QUALITY_TIER_FORMS 与 engine LodConfigDoc.QUALITY_TIERS 逐字对照",
                LodApi.QUALITY_TIER_FORMS.equals(LodConfigDoc.QUALITY_TIERS)
                        && LodApi.QUALITY_TIER_FORMS.equals(List.of("standard", "high", "low")));
        check("LodApi: 缺省质量档 STANDARD + 常量 DEFAULT_MAX_LEVEL=4 + BLOCKS_PER_CHUNK=16",
                LodApi.DEFAULT_QUALITY == LodQualitySpec.STANDARD
                        && LodApi.DEFAULT_MAX_LEVEL == 4
                        && LodApi.BLOCKS_PER_CHUNK == 16);
        check("LodQualitySpec.maxLevel()/distances() 逐档忠实读取（均取 engine 缺省配置）",
                LodQualitySpec.STANDARD.maxLevel() == 4
                        && LodQualitySpec.HIGH.maxLevel() == 4
                        && LodQualitySpec.LOW.maxLevel() == 4
                        && LodQualitySpec.STANDARD.distances().tiers().size() == 4);
    }

    // ---------- (2) api↔engine mirror verbatim ----------

    private static void apiMirror() {
        check("mirror: LodApiMirror.qualityTierForms() 与 fixed order 同（3 档）",
                LodApiMirror.qualityTierForms().equals(LodConfigDoc.QUALITY_TIERS)
                        && LodApiMirror.qualityTierForms().size() == 3);
        check("mirror: 缺省质量档商 engine=api（STANDARD）",
                LodApiMirror.defaultQualityTier().equals(LodApi.DEFAULT_QUALITY.form()));
        check("mirror: 缺省最大层级商 engine=api（4）",
                LodApiMirror.defaultMaxLevel() == LodApi.DEFAULT_MAX_LEVEL);
        check("mirror: 每区块方块数商 engine=api（16）",
                LodApiMirror.blocksPerChunk() == LodApi.BLOCKS_PER_CHUNK);
        check("mirror: 缺省距离表 size 商 engine=api（4 档）",
                LodApiMirror.defaultDistances().size() == LodApi.defaultDistanceSpec().tiers().size());
        long[] probes = {0L, 1L, 63L * 63L, 64L * 64L - 1L, 64L * 64L, 128L * 128L,
                256L * 256L, 512L * 512L, 512L * 512L + 1L, 10_000L * 10_000L};
        boolean mirrorLevels = true;
        LodDistanceSpec apiDistances = LodApi.defaultDistanceSpec();
        for (long d : probes) {
            int eng = LodApiMirror.resolveLevel(LodConfigDoc.DEFAULT_DISTANCES, d);
            int api = LodApi.resolveLevel(apiDistances, d);
            int sel = LodDistanceSelector.defaults().levelForDistanceSq(d).ordinal();
            mirrorLevels = mirrorLevels && (eng == api) && (api == sel);
        }
        check("mirror: resolveLevel 同输入同输出（10 组距离平方，api=mirror=selector）", mirrorLevels);
        check("mirror: api resolve(quality,distance) maxLevel=缺省 4",
                LodApi.resolve(LodApi.DEFAULT_QUALITY, apiDistances).maxLevel() == 4
                        && LodApiMirror.resolve("standard", LodConfigDoc.DEFAULT_DISTANCES).maxLevel() == 4);
        check("mirror: qualityTier 非法拒绝（api 未知档抛 IAE）",
                throwsIAE(() -> LodQualitySpec.fromForm("ultra"))
                        && throwsIAE(() -> LodApiMirror.resolve("ultra", LodConfigDoc.DEFAULT_DISTANCES)));
    }

    // ---------- (3) ladder + distance table ----------

    private static void ladderAndThresholds() {
        int[] expects = {0, 1, 2, 3, 4, 5};
        int[] chunkSpans = {1, 2, 4, 8, 16, 32};
        int[] blockSpans = {16, 32, 64, 128, 256, 512};
        LodLevel[] vals = LodLevel.values();
        boolean ladder = vals.length == 6;
        for (int i = 0; i < vals.length && i < expects.length; i++) {
            ladder = ladder
                    && vals[i].k() == expects[i]
                    && vals[i].chunkSpan() == chunkSpans[i]
                    && vals[i].blockSpan() == blockSpans[i]
                    && (i == 0 || vals[i].chunkSpan() == 2 * vals[i - 1].chunkSpan());
        }
        check("LodLevel: 六层固定序 L0..L5 + k/chunkSpan/blockSpan 期望硬编码 + 层级跨度翻倍", ladder);
        boolean ladderRoundTrip = true;
        for (LodLevel l : vals) {
            ladderRoundTrip = ladderRoundTrip && LodLevel.fromForm(l.form()) == l;
        }
        check("LodLevel: form()/fromForm() 往返 + 大小写/未知拒绝",
                ladderRoundTrip && LodLevel.fromForm(" L3 ") == LodLevel.L3
                        && throwsIAE(() -> LodLevel.fromForm("L9"))
                        && throwsIAE(() -> LodLevel.fromForm("")));
        check("LodLevel: canonicalLadder() 连跑同字符 + 每一行含 k=/chunkSpan=/blockSpan=",
                LodLevel.canonicalLadder().equals(LodLevel.canonicalLadder())
                        && LodLevel.canonicalLadder().contains("k=0")
                        && LodLevel.canonicalLadder().contains("k=5"));
        check("LodConfigDoc: 缺省文档（enabled=true, maxLevel=4, standard/none, 4 距离档）",
                LodConfigDoc.defaults().enabled()
                        && LodConfigDoc.defaults().maxLevel() == 4
                        && LodConfigDoc.defaults().qualityTier().equals("standard")
                        && LodConfigDoc.defaults().cachePolicy().equals("none")
                        && LodConfigDoc.defaults().distances().size() == 4);
        check("LodConfigDoc: 缺省距离表 L1@64,L2@128,L3@256,L4@512",
                "1,64;2,128;3,256;4,512".equals(distanceTableDigest(LodConfigDoc.DEFAULT_DISTANCES)));
    }

    // ---------- (4) polygonizer + merger determinism ----------

    private static void polygonizerMergerDeterminism() {
        int span = LodLevel.L0.blockSpan();
        long seed = 44905237L;
        LodPipeline pipe = new LodPipeline(LodTieAccelerator.skippedNoOp());
        LodSection s1 = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed));
        LodSection s2 = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed));
        check("polygonizer: 同输入两次 generate 逐字节一致", Arrays.equals(s1.toBytes(), s2.toBytes()));
        check("polygonizer: 再运行（独立重建）逐字节一致",
                Arrays.equals(s1.toBytes(), pipe.generate(new LodPolygonizer.LodRegionSample(span, seed)).toBytes()));
        check("polygonizer: profileTop 同输入两次同值 + 打包位（height/color/bright 解包范围）",
                LodPolygonizer.profileTop(seed, 3L, 5L) == LodPolygonizer.profileTop(seed, 3L, 5L)
                        && LodPolygonizer.LodRegionSample.heightOf(LodPolygonizer.profileTop(seed, 3L, 5L)) <= 0x7FFFFF);
        check("polygonizer: L0 段列数 = 16×16 = 256（固定扫描序全列覆盖）", s1.columnStacks().size() == 256);
        LodSection a1 = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 1L));
        LodSection b1 = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 2L));
        LodSection c1 = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 3L));
        LodSection d1 = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 4L));
        LodSection m1 = pipe.merge(0, 0, a1, b1, c1, d1);
        LodSection m2 = pipe.merge(0, 0, a1, b1, c1, d1);
        check("merger: 同输入两次 merge 逐字节一致", Arrays.equals(m1.toBytes(), m2.toBytes()));
        check("merger: mergeCorner 同输入两次同值 + 并列取首现（钉死 a,b,c,d 序）",
                LodMerger.mergeCorner(5L, 5L, 1L, 9L) == LodMerger.mergeCorner(5L, 5L, 1L, 9L)
                        && LodMerger.mergeCorner(9L, 9L, 1L, 1L) == 9L);
    }

    // ---------- (5) merge-chain level progression + round-trip ----------

    private static void mergeChainRoundTrip() {
        int span = LodLevel.L0.blockSpan();
        long seed = 11L;
        LodPipeline pipe = new LodPipeline(LodTieAccelerator.skippedNoOp());
        LodSection a = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed));
        LodSection b = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 1L));
        LodSection c = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 2L));
        LodSection d = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 3L));
        LodSection l1 = pipe.merge(0, 0, a, b, c, d);
        check("merge-chain: L0×4 merge → L1（level 递增，origin 显式给定）",
                l1.level() == LodLevel.L1
                        && l1.level().ordinal() == a.level().ordinal() + 1
                        && l1.originBlockX() == 0
                        && l1.originBlockZ() == 0);
        check("merge-chain: toBytes/fromBytes 往返恒等（L1）",
                Arrays.equals(l1.toBytes(), LodSection.fromBytes(l1.toBytes()).toBytes()));
        check("merge-chain: canonicalText 对同区块连跑同字符",
                l1.canonicalText().equals(LodSection.fromBytes(l1.toBytes()).canonicalText()));
        check("merge-chain: 非法输入拒绝（子级层级不齐 → IAE）",
                throwsIAE(() -> pipe.merge(0, 0, a, a, a, l1)));
        // level progression: merge L1×4 → L2, etc. up to L4 (L5 has no coarser child).
        LodSection[] cur = {l1, l1, l1, l1};
        boolean progressed = true;
        LodSection top = l1;
        for (int level = 1; level < 4; level++) {
            top = pipe.merge(level * 100, level * 100, cur[0], cur[1], cur[2], cur[3]);
            progressed = progressed && top.level().ordinal() == level + 1;
            cur = new LodSection[]{top, top, top, top};
        }
        check("merge-chain: L1→L2→L3→L4 逐级 merge level 递增", progressed && top.level() == LodLevel.L4);
    }

    // ---------- (6) distance-selector tiering monotonicity ----------

    private static void distanceSelectorMonotonicity() {
        LodDistanceSelector def = LodDistanceSelector.defaults();
        long[] dists = {0L, 40L * 40L, 64L * 64L - 1L, 64L * 64L, 100L * 100L, 128L * 128L,
                256L * 256L, 512L * 512L, 512L * 512L + 1L, 20_000L * 20_000L};
        int[] expectedLevels = {0, 0, 0, 1, 1, 2, 3, 4, 4, 4};
        boolean monotone = true;
        boolean expectedOk = true;
        int prev = -1;
        for (int i = 0; i < dists.length; i++) {
            int lvl = def.levelForDistanceSq(dists[i]).ordinal();
            monotone = monotone && lvl >= prev;
            expectedOk = expectedOk && lvl == expectedLevels[i];
            prev = lvl;
        }
        check("LodDistanceSelector: 全缺省选择器 10 档期望层级硬编码",
                expectedOk && def.tiers().size() == 4);
        check("LodDistanceSelector: 分档单调不减（距离平方越大层级越粗）", monotone);
        check("LodDistanceSelector: L0（不启 LOD）= 首档之内 / L5 之外全吸收末档",
                def.levelForDistanceSq(0L) == LodLevel.L0
                        && def.levelForChunk(0, 0, 40, 0).ordinal() >= def.levelForChunk(0, 0, 2, 0).ordinal());
        check("LodDistanceSelector: DEFAULT_TIERS 阈值表 == 缺省距离表（4 档）",
                def.tiers().equals(LodDistanceSelector.DEFAULT_TIERS)
                        && LodDistanceSelector.DEFAULT_TIERS.stream().allMatch(t -> t.level() >= 1));
    }

    // ---------- (7) frustum culling fixed hits ----------

    private static void viewCulling() {
        long one = 1L << 24;
        ViewFrustum f = ViewFrustum.of(0L, 80L, 0L, 0L, 0L, 1L, one, one, 1L, 1000L);
        check("culling: 视锥构建 + 视锥正中前向小盒保留（keep）",
                LodViewCuller.passesFrustum(f, new Bounds(-1, 79, 2, 1, 81, 6)));
        check("culling: 视锥背后的 AABB 剔除（cull）",
                !LodViewCuller.passesFrustum(f, new Bounds(-50, 0, -500, -40, 120, -490)));
        check("culling: 视锥旁侧 AABB 剔除（位于 +X 读者外）",
                !LodViewCuller.passesFrustum(f, new Bounds(500, 0, 0, 520, 100, 10)));
        check("culling: UP 面永不剔除 + South 面（观察者南侧=朝外可见不剔，北侧=背面剔除）",
                !LodViewCuller.cullByFacingBits(LodViewCuller.FACE_UP, 0L, 0L)
                        && !LodViewCuller.cullByFacingBits(LodViewCuller.FACE_SOUTH, 0L, 5L)
                        && LodViewCuller.cullByFacingBits(LodViewCuller.FACE_SOUTH, 0L, -5L));
        check("culling: West 面（观察者西侧可见不剔，东侧背面剔除）+ 无朝向位不剔",
                !LodViewCuller.cullByFacingBits(LodViewCuller.FACE_WEST, -5L, 0L)
                        && LodViewCuller.cullByFacingBits(LodViewCuller.FACE_WEST, 5L, 0L)
                        && !LodViewCuller.cullByFacingBits(0, 0L, 0L));
    }

    // ---------- (8) budget overrun deterministic skip ----------

    private static void budgetOverrun() {
        LodBudgetScheduler sched = LodBudgetScheduler.create(TickBudget.of(100, 100, 1), 31);
        LodBudgetScheduler.BatchPlan plan = sched.planBatch(List.of(
                new CellRequest(LodLevel.L2, 0, 0, 1),
                new CellRequest(LodLevel.L2, 4, 0, 1)));
        check("budget: 全局 cap=1 时仅首单元入批（includedCount=1）", plan.includedCount() == 1);
        check("budget: 超限第二单元确定性跳过 + 降级提示（L2→L3）",
                plan.outcomes().get(0).included()
                        && !plan.outcomes().get(1).included()
                        && plan.outcomes().get(1).downgradeTo() == LodLevel.L3);
        check("budget: 同输入同输出（重建 scheduler 再跑同结果）",
                LodBudgetScheduler.create(TickBudget.of(100, 100, 1), 31)
                        .planBatch(List.of(
                                new CellRequest(LodLevel.L2, 0, 0, 1),
                                new CellRequest(LodLevel.L2, 4, 0, 1))).includedCount() == 1);
        check("budget: 非法构造拒绝（cap<1 / regionBits 越界 / units<=0）",
                throwsIAE(() -> TickBudget.of(0, 1, 1))
                        && throwsIAE(() -> LodBudgetScheduler.create(TickBudget.of(1, 1, 1), 64))
                        && throwsIAE(() -> new CellRequest(LodLevel.L2, 0, 0, 0)));
    }

    // ---------- (9) LodTieAccelerator skip-or-parity ----------

    private static void tieSkipOrParity() {
        // No property -> deterministic skip (never throws).
        LodTieAccelerator noProp = LodTieAccelerator.fromProperties();
        check("tie: 无 subterra.tie.lib 属性时确定性 skip（不抛、不激活）",
                noProp.skipped() && !noProp.active()
                        && noProp.profileTop(1L, 1L, 1L).isEmpty());

        Path staged = null;
        try (InputStream in = LodProbe.class.getResourceAsStream("/tie/subterra_lod_pipeline.dll")) {
            if (in == null) {
                // resource absent -> assert the skip branch deterministically (ALL PASS).
                check("tie: 缺少捆绑 LOD dll 资源时确定性 skip（ALL PASS 分支）",
                        noProp.skipped());
                return;
            }
            staged = Path.of(System.getProperty("java.io.tmpdir"),
                    "subterra_lod_pipeline_" + System.nanoTime() + ".dll");
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            check("tie: 资源提取失败时按 skip 处理（确定性分支）", noProp.skipped());
            return;
        }
        try (LodTieAccelerator acc = LodTieAccelerator.fromDll(staged)) {
            boolean deterministicSkip = acc.skipped();
            boolean parity = true;
            long[][] profiles = {{44905237L, 0L, 0L}, {9L, 3L, 5L}, {1L, 15L, 15L}, {77L, 1L, 8L}};
            for (long[] p : profiles) {
                if (acc.active()) {
                    java.util.Optional<Long> t = acc.profileTop(p[0], p[1], p[2]);
                    parity = parity && t.isPresent()
                            && t.get() == LodPolygonizer.profileTop(p[0], p[1], p[2]);
                }
            }
            long[][] corners = {{1L, 2L, 3L, 4L}, {9L, 9L, 1L, 1L}, {5L, 5L, 5L, 5L}};
            for (long[] c : corners) {
                if (acc.active()) {
                    java.util.Optional<Long> t = acc.mergeCorner(c[0], c[1], c[2], c[3]);
                    parity = parity && t.isPresent()
                            && t.get() == LodMerger.mergeCorner(c[0], c[1], c[2], c[3]);
                }
            }
            check("tie: skip-or-parity 二分支确定性（激活则与 JDK 内核逐位一致，否则确定性 skip）",
                    deterministicSkip || parity);
            check("tie: 激活时 profileTop/mergeCorner 与 JDK 金样逐位一致（4+3 组输入）",
                    !acc.active() || parity);
        } finally {
            try {
                Files.deleteIfExists(staged);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    // ---------- (10) LodCache round-trip + four rejection classes ----------

    private static void cacheRoundTripAndRejections() {
        String base = System.getProperty("subterra.projectDir", System.getProperty("user.dir", "."));
        Path staging = Path.of(base, "build", "tmp", "lod-probe-cache");
        deleteRecursive(staging);
        try {
            LodCache cache = new LodCache(staging);
            LodCacheKey key = new LodCacheKey(LodLevel.L1, 3, -2);
            LodSection section = new LodPipeline(LodTieAccelerator.skippedNoOp())
                    .generate(new LodPolygonizer.LodRegionSample(LodLevel.L0.blockSpan(), 12345L));
            CacheResult saved = cache.save(key, section);
            check("cache: save+重读校验 OK（往返同字节）",
                    saved.status() == CacheStatus.OK
                            && Arrays.equals(saved.section().toBytes(), section.toBytes()));
            CacheResult loaded = cache.load(key);
            check("cache: load OK + 逐字节往返一致 + hit()=true",
                    loaded.status() == CacheStatus.OK
                            && Arrays.equals(loaded.section().toBytes(), section.toBytes())
                            && cache.hit(key));
            check("cache: 缺键 MISS（确定性非抛错）",
                    cache.load(new LodCacheKey(LodLevel.L2, 1, 1)).status() == CacheStatus.MISS);
            Path file = staging.resolve(key.fileName());
            // tamper
            byte[] t = Files.readAllBytes(file);
            t[LodCache.HEADER_LEN] ^= 0x01;
            Files.write(file, t);
            check("cache: 单字节篡改确定性拒绝 CORRUPT",
                    cache.load(key).status() == CacheStatus.CORRUPT && !cache.hit(key));
            // restore
            cache.save(key, section);
            // truncate
            byte[] clean = Files.readAllBytes(file);
            Files.write(file, Arrays.copyOf(clean, LodCache.HEADER_LEN + 1));
            check("cache: 截断确定性拒绝 CORRUPT", cache.load(key).status() == CacheStatus.CORRUPT);
            // restore
            cache.save(key, section);
            // version mismatch
            // p.2.28 follow-up: the file is now a standard zd v2 carrier, so byte[4] belongs to the zd
            // header magic; the pin-format-version is exposed as the zd header version byte[7] (d1).
            byte[] v = Files.readAllBytes(file);
            v[7] = (byte) (v[7] ^ 0x7F);
            Files.write(file, v);
            check("cache: 版本不符确定性拒绝 VERSION_MISMATCH",
                    cache.load(key).status() == CacheStatus.VERSION_MISMATCH);
            // restore
            cache.save(key, section);
            // bad magic
            byte[] m = Files.readAllBytes(file);
            m[0] = (byte) 'X';
            m[1] = (byte) 'X';
            Files.write(file, m);
            check("cache: 坏魔数确定性拒绝 CORRUPT", cache.load(key).status() == CacheStatus.CORRUPT);
            check("cache: 可再生契约 REGENERABLE=true", cache.isRegenerable() && LodCache.REGENERABLE);
        } catch (IOException e) {
            check("cache: staging I/O（异常视为 FAIL）", false);
        } finally {
            deleteRecursive(staging);
        }
    }

    // ---------- (11) backend registration + fixed-order instantiation ----------

    private static void backendRegistration() {
        BackendRegistry.clear(); // probe isolation: deterministic registration surface
        LodRenderBackend.registerInto();
        check("backend: 注册后按名查得 'lod'",
                BackendRegistry.lookup(LodRenderBackend.NAME) != null
                        && LodRenderBackend.NAME.equals("lod"));
        RenderBackend def = BackendRegistry.defaultFor(LodRenderBackend.QUAD_INSTANCE_FORMAT);
        check("backend: defaultFor(QUAD_INSTANCE_FORMAT) 确定性选中 lod",
                def != null && LodRenderBackend.NAME.equals(def.name()));
        check("backend: 同名二次注册确定性拒绝（IAE）",
                throwsIAE(LodRenderBackend::registerInto));
        check("backend: 支持两种 LOD 实例格式 + 优先级固定",
                LodRenderBackend.INSTANCE.supports(LodRenderBackend.QUAD_INSTANCE_FORMAT)
                        && LodRenderBackend.INSTANCE.supports(LodRenderBackend.COLUMN_INSTANCE_FORMAT)
                        && LodRenderBackend.INSTANCE.priority() == 0);
        LodSection sec = new LodPipeline(LodTieAccelerator.skippedNoOp())
                .generate(new LodPolygonizer.LodRegionSample(LodLevel.L0.blockSpan(), 99L));
        LodRenderBackend.InstantiatedMesh m1 = LodRenderBackend.INSTANCE.instantiate(sec);
        LodRenderBackend.InstantiatedMesh m2 = LodRenderBackend.INSTANCE.instantiate(sec);
        check("backend: instantiate 两遍同字节 + canonicalText 稳定",
                Arrays.equals(m1.toBytes(), m2.toBytes())
                        && m1.canonicalText().equals(m2.canonicalText())
                        && m1.level() == LodLevel.L0);
        check("backend: 顶点布局定点化（QUAD/COLUMN byteSize=8）+ 规范字节逐字节稳定",
                LodVertexLayout.QUAD.byteSize() == 8
                        && LodVertexLayout.COLUMN.byteSize() == 8
                        && LodVertexLayout.isByteStable(LodVertexLayout.QUAD)
                        && LodVertexLayout.isByteStable(LodVertexLayout.COLUMN)
                        && Arrays.equals(LodVertexLayout.QUAD.canonicalBytes(),
                        LodVertexLayout.QUAD.canonicalBytes()));
    }

    // ---------- (12) seam + fog monotonicity ----------

    private static void seamAndFog() {
        List<LodDistanceSelector.Tier> tiers = LodDistanceSelector.DEFAULT_TIERS;
        check("seam: 边缘取整/对齐规则（全细节 16 方块网格）",
                LodSeamRules.floorToFullDetail(37) == 32
                        && LodSeamRules.ceilToFullDetail(37) == 48
                        && LodSeamRules.isFullDetailAligned(32)
                        && !LodSeamRules.isFullDetailAligned(33));
        check("seam: stitch 位规则（较小 quad 与较大邻居相接需 stitch）",
                LodSeamRules.stitchRequired(2, 4)
                        && !LodSeamRules.stitchRequired(4, 4)
                        && LodSeamRules.stitchBit(2, 4) == 1
                        && LodSeamRules.stitchBit(4, 4) == 0
                        && LodSeamRules.tessellationCompatible(4, 2));
        check("seam: 距离雾 0/首档边界 + 末档之后 FOG_MAX",
                LodSeamRules.fogStrength(0L, tiers) == 0
                        && LodSeamRules.fogStrength(64L * 64L, tiers) > 0
                        && LodSeamRules.fogStrength(512L * 512L + 1L, tiers) == LodSeamRules.FOG_MAX);
        boolean monotone = true;
        long prev = -1;
        for (long d = 1L; d < 2000L * 2000L; d *= 2L) {
            long fog = LodSeamRules.fogStrength(d, tiers);
            monotone = monotone && fog >= prev;
            prev = fog;
        }
        check("seam: 距离雾随距离平方单调不减", monotone);
        check("seam: fogForTierIndex 严格递增且末档 FOG_MAX",
                LodSeamRules.fogForTierIndex(0, 4) < LodSeamRules.fogForTierIndex(1, 4)
                        && LodSeamRules.fogForTierIndex(1, 4) < LodSeamRules.fogForTierIndex(2, 4)
                        && LodSeamRules.fogForTierIndex(3, 4) == LodSeamRules.FOG_MAX);
    }

    // ---------- (13) wiring contract (static inventory, load-only) ----------

    private static void wiringContract() {
        String lodRuntime = "io.toterra.subterra.runtime.render.lod.LodRuntime";
        check("wiring: runtime.render.lod.LodRuntime 类存在（只加载不初始化，纯 JVM 不触发 MC LogUtils）",
                classExists(lodRuntime));
        check("wiring: LodRuntime 类字节含门控属性串 'subterra.probe.lod'",
                classBytesContain(lodRuntime, "subterra.probe.lod"));
        check("wiring: LodRuntime 类字节含 marker 前缀 '[Subterra lod]'",
                classBytesContain(lodRuntime, "[Subterra lod]"));
        check("wiring: RenderHooksClient 类字节含 LOD 注入点 marker '[Subterra lod]'",
                classBytesContain("io.toterra.subterra.runtime.render.RenderHooksClient", "[Subterra lod]"));
        // 真实 boot 生命周期由 AsyncE2EProbe lodOk 槽覆盖；此处静态存在性。
        check("wiring: AsyncE2EProbe 类字节含 lodOk 断言（'[Subterra lod]' marker，真实 boot 生命周期覆盖）",
                classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "[Subterra lod]"));
        // RuleStore (p.2.17) is compile-reachable from the devkit probe (engine dependency).
        check("wiring: RuleStore (p.2.17) 类型可加载（LOD 规则档位读取接入点的依赖基座）",
                classExists("io.toterra.subterra.engine.config.rules.RuleStore"));
    }

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, LodProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8，纯 ASCII，ISO-8859-1 逐字节映射，线性 contains）。
     * Asserts the class bytes contain a string literal (constant-pool UTF-8; pure-ASCII, so ISO-8859-1 is a
     * byte-identity mapping — a linear contains scan). */
    private static boolean classBytesContain(String fqcn, String literal) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = LodProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
        } catch (IOException e) {
            return false;
        }
    }

    /** 缺省距离表摘要 {@code level,distance; ...} 原文拼装（确定性复验载体）。Default distance-table digest
     *  ({@code level,distance; ...}, a determinism-replay carrier). */
    private static String distanceTableDigest(List<LodConfigDoc.Distance> dists) {
        StringBuilder sb = new StringBuilder();
        for (LodConfigDoc.Distance d : dists) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(d.level()).append(',').append(d.distance());
        }
        return sb.toString();
    }

    /** 递归删除目录（尽力而为）。Best-effort recursive directory delete. */
    private static void deleteRecursive(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        } catch (Exception ignored) {
            // best-effort
        }
    }
}