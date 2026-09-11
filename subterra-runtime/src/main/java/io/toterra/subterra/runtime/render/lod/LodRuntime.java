package io.toterra.subterra.runtime.render.lod;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.api.lod.LodApi;
import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.RenderBackend;
import io.toterra.subterra.engine.render.lod.CacheResult;
import io.toterra.subterra.engine.render.lod.CacheStatus;
import io.toterra.subterra.engine.render.lod.LodCache;
import io.toterra.subterra.engine.render.lod.LodCacheKey;
import io.toterra.subterra.engine.render.lod.LodConfigDoc;
import io.toterra.subterra.engine.render.lod.LodApiMirror;
import io.toterra.subterra.engine.render.lod.LodLevel;
import io.toterra.subterra.engine.render.lod.LodMerger;
import io.toterra.subterra.engine.render.lod.LodPipeline;
import io.toterra.subterra.engine.render.lod.LodPolygonizer;
import io.toterra.subterra.engine.render.lod.LodRenderBackend;
import io.toterra.subterra.engine.render.lod.LodSection;
import io.toterra.subterra.engine.render.lod.LodTieAccelerator;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * p.2.28.6（2/2）— LOD runtime 门控壳：把 engine.render.lod 全套（LodLevel/LodConfigDoc/
 * LodPolygonizer/LodMerger/LodPipeline/LodDistanceSelector/LodViewCuller/LodBudgetScheduler/
 * LodCache/LodTieAccelerator/LodRenderBackend，p.2.28.1-.5）与 api.lod 契约面（p.2.28.6 1/2）
 * 收编进 boot 生命周期，与 {@code RenderRuntime}（p.2.27.1.2，{@code subterra.probe.render}）同范式。
 * {@link #bootstrap()}（由 {@code Subterra.java} 构造调用，挂在 RenderRuntime.bootstrap() 同处）
 * 注册 {@code ServerStartedEvent} 门控；门控 {@code subterra.probe.lod}（经 gradle -P → runServer
 * system property 转发，与其余探针壳同模式）非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 门控内的确定性装载校验（固定序、禁时序、禁 sleep；缺 dll 不失败）：
 * <ol>
 *   <li><b>api.lod ↔ {@link LodApiMirror} 同输入同输出对照</b>：档位序 / 缺省质量档 / 最大层级 /
 *       每区块方块数 / 缺省距离表 / {@code resolveLevel} 投影对若干固定距离平方逐字对照；</li>
 *   <li><b>管线确定性</b>：固定 {@link LodPolygonizer.LodRegionSample} → {@link LodPipeline#generate}
 *       → {@code merge} 链连跑两次逐字节一致（仅 JDK 金样路径，确定性）；</li>
 *   <li><b>LodCache 往返 + 篡改拒</b>：在 {@code build/tmp} staging 目录 {save → load 往返 → 单字节
 *       篡改拒 CORRUPT → 版本不符拒 VERSION_MISMATCH}，写完即删 staging；</li>
 *   <li><b>LodTieAccelerator skip-or-parity</b>：dll 定位复用 {@code TieRuntime} 同款
 *       （{@code subterra.lod.lib} 属性 → 捆绑资源，空则 skip）；无 lib 确定性 skip；有 lib 时与 JDK
 *       内核（{@link LodPolygonizer#profileTop}/{@link LodMerger#mergeCorner}）对照；</li>
 *   <li><b>LodRenderBackend 注册存在性</b>：缺注册时注册，按名查得 + 支持自身两种实例格式。</li>
 * </ol>
 * 全部通过打 {@code [Subterra lod] ok (api=..., pipeline=..., cache=..., tie=.., backend=..., rules=...)}
 * （与 {@code [Subterra render]}/{@code [Subterra tie]} 风格一致）；装载违约/程序错误打
 * {@code lod mismatch (error=...)}（warn、不抛、不失败）。
 * <p>
 * <b>p.2.17 RuleStore LOD 档位读取（收尾接线）</b>：门控内确定性读 LOD 档位。runtime 侧当前没有可直接触达的
 * 全局 {@code RuleStore} 实例面（其为实例类、按注册表装配），故此阶段降级为「读 system property 缺省」——
 * {@code subterra.lod.enabled}（缺省 {@code true}）与 {@code subterra.lod.maxLevel}（缺省
 * {@code LodApi.DEFAULT_MAX_LEVEL}=4，即 api.lod 默认档），把读得值打进 marker 文本。真实的 p.2.17
 * RuleStore 双层配置读取面 / p.2.23 hub 表单热重载编辑为 p.2.28 后接线点（不实现）。
 * <p>
 * 真实 LOD pass 注入（客户端 {@linkplain io.toterra.subterra.runtime.render.RenderHooksClient
 * RenderHooksClient} 的 RenderLevelStageEvent LOD 注入点计数钩子）为 p.2.28 后接线，本子项不做渲染接管。
 * <p>
 * p.2.28.6 (2/2) — the LOD runtime gated shell: folds the whole engine.render.lod core (p.2.28.1-.5)
 * and the api.lod contract surface (p.2.28.6 1/2) into the boot lifecycle, in the same pattern as
 * {@code RenderRuntime} (p.2.27.1.2, {@code subterra.probe.render}). {@link #bootstrap()} (called from
 * the {@code Subterra.java} constructor, next to {@code RenderRuntime.bootstrap()}) registers the
 * {@code ServerStartedEvent} gate; gated by {@code subterra.probe.lod} (forwarded gradle -P → runServer
 * system property, the same pattern as the other probe shells), runs only when non-null — a pure no-op
 * shell by default, zero impact on the boot lifecycle.
 * <p>
 * The deterministic load verification inside the gate (fixed order, no timing, no sleeps; missing dll
 * never fails):
 * <ol>
 *   <li><b>api.lod ↔ {@link LodApiMirror} same-input-same-output</b>: tier order / default quality tier /
 *       max level / blocks per chunk / default distance table / the {@code resolveLevel} projection over
 *       a fixed set of squared distances, compared verbatim;</li>
 *   <li><b>pipeline determinism</b>: a fixed {@link LodPolygonizer.LodRegionSample} → {@link LodPipeline#generate}
 *       → {@code merge} chain run twice, byte-identical (pure JDK golden path, deterministic);</li>
 *   <li><b>LodCache round-trip + tamper rejection</b>: in a {@code build/tmp} staging dir {save → load
 *       round-trip → single-byte-tamper rejection CORRUPT → version-mismatch rejection VERSION_MISMATCH},
 *       the staging dir is deleted afterwards;</li>
 *   <li><b>LodTieAccelerator skip-or-parity</b>: dll location reuses the {@code TieRuntime} pattern
 *       ({@code subterra.lod.lib} property → bundled resource, else skip); a missing lib deterministically
 *       skips; a present lib is compared against the JDK kernels
 *       ({@link LodPolygonizer#profileTop}/{@link LodMerger#mergeCorner});</li>
 *   <li><b>LodRenderBackend registration existence</b>: registers when absent, then lookup-by-name + it
 *       supports both of its own instance formats.</li>
 * </ol>
 * On full success it prints {@code [Subterra lod] ok (api=..., pipeline=..., cache=..., tie=..,
 * backend=..., rules=...)} (style-aligned with {@code [Subterra render]}/{@code [Subterra tie]}); a load
 * violation / program error prints {@code lod mismatch (error=...)} instead (warn, never throws, never
 * fails).
 * <p>
 * <b>p.2.17 RuleStore LOD tier readback (closing wiring)</b>: the gate reads the LOD tier deterministically.
 * The runtime side has no directly reachable global {@code RuleStore} instance surface today (it is an
 * instance class assembled per-registry), so this stage degrades to a deterministic system-property read —
 * {@code subterra.lod.enabled} (default {@code true}) and {@code subterra.lod.maxLevel} (default
 * {@code LodApi.DEFAULT_MAX_LEVEL}=4, the api.lod default tier), folded into the marker text. The real
 * p.2.17 RuleStore two-tier config readback / p.2.23 hub form hot-reload editing are the p.2.28+ wiring
 * points (not implemented here).
 * <p>
 * The real LOD pass injection (the client-side RenderLevelStageEvent LOD injection-point counting hook on
 * {@linkplain io.toterra.subterra.runtime.render.RenderHooksClient RenderHooksClient}) is post-p.2.28
 * wiring; this sub-item does no render takeover.
 */
public final class LodRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra lod]";

    /** 门控属性键 / gate property key. */
    public static final String GATE_PROPERTY = "subterra.probe.lod";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 类路径捆绑的 LOD tiec 动态库资源（devkit 亦捆绑同源 dll，供探针对照）。Bundled LOD tiec dll
     * resource (the devkit also bundles the same-source dll for probe parity). */
    private static final String BUNDLED_DLL = "/tie/subterra_lod_pipeline.dll";

    /** 类路径资源提取后的临时文件名（固定名，进程内单实例，确定性）。Temp filename after extracting
     * the classpath resource (fixed name, single instance per JVM, deterministic). */
    private static final String STAGED_NAME = "subterra_lod_runtime_pipeline.dll";

    private LodRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（{@code Subterra.java} 构造调用，与 RenderRuntime.bootstrap()
     * 同处接线）。Registers the NeoForge lifecycle listeners (called from the {@code Subterra.java}
     * constructor, next to {@code RenderRuntime.bootstrap()}). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(LodRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.28.6 deterministic E2E hook: run the engine.render.lod deterministic load verification
        // at startup when a probe flag is forwarded (subterra.probe.lod) — mirrors the other
        // probe-shell gates, so no console command round-trips through the gradle-forked server
        // JVM stdin. Default no-op.
        String probe = System.getProperty(GATE_PROPERTY);
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            String api = apiMirrorCheck();
            String pipeline = pipelineCheck();
            String cache = cacheCheck();
            String tie = tieCheck();
            String backend = backendCheck();
            String rules = ruleTierReadback();
            LOGGER.info("{} ok (api={}, pipeline={}, cache={}, tie={}, backend={}, rules={})",
                    MARKER, api, pipeline, cache, tie, backend, rules);
        } catch (RuntimeException e) {
            // the samples are legal, so a mismatch is a program error — never emit a false ok.
            LOGGER.warn("{} lod mismatch (error={})", MARKER, e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as the other shells).
    }

    /**
     * ① api.lod ↔ {@link LodApiMirror} 同输入同输出对照：档位序 / 缺省质量档 / 最大层级 / 每区块方块数 /
     * 缺省距离表 / {@code resolveLevel} 投影。违约抛 {@link IllegalStateException}。
     * ① api.lod ↔ {@link LodApiMirror} same-input-same-output: tier order / default quality tier / max
     * level / blocks per chunk / default distance table / {@code resolveLevel} projection. Violations throw
     * {@link IllegalStateException}.
     */
    private static String apiMirrorCheck() {
        List<String> tierForms = List.of("standard", "high", "low");
        if (!tierForms.equals(LodApiMirror.qualityTierForms())) {
            throw new IllegalStateException("mirror qualityTierForms != fixed order");
        }
        if (!LodApiMirror.defaultQualityTier().equals(LodApi.DEFAULT_QUALITY.form())) {
            throw new IllegalStateException("default quality tier mirror mismatch");
        }
        if (LodApiMirror.defaultMaxLevel() != LodApi.DEFAULT_MAX_LEVEL) {
            throw new IllegalStateException("default max level mirror mismatch");
        }
        if (LodApiMirror.blocksPerChunk() != LodApi.BLOCKS_PER_CHUNK) {
            throw new IllegalStateException("blocks per chunk mirror mismatch");
        }
        if (LodApiMirror.defaultDistances().size() != LodApi.defaultDistanceSpec().tiers().size()) {
            throw new IllegalStateException("default distance table size mirror mismatch");
        }
        long[] probes = {0L, 1L, 64L * 64L - 1L, 64L * 64L, 128L * 128L, 512L * 512L, 512L * 512L + 1L};
        for (long d : probes) {
            int eng = LodApiMirror.resolveLevel(LodConfigDoc.DEFAULT_DISTANCES, d);
            int api = LodApi.resolveLevel(LodApi.defaultDistanceSpec(), d);
            if (eng != api) {
                throw new IllegalStateException("resolveLevel mirror mismatch at distSq=" + d
                        + ": engine=" + eng + " api=" + api);
            }
        }
        return "mirror:" + tierForms.size() + "tiers,dists=" + LodApiMirror.defaultDistances().size();
    }

    /**
     * ② 管线确定性：固定 {@link LodPolygonizer.LodRegionSample} → {@link LodPipeline#generate} →
     * {@code merge} 链连跑两次逐字节一致（仅 JDK 金样路径）。The pipeline determinism: a fixed region
     * sample → {@code generate} → {@code merge} chain run twice is byte-identical (JDK golden path).
     */
    private static String pipelineCheck() {
        int span = LodLevel.L0.blockSpan();
        byte[] run1 = chainBytes(span, 44905237L);
        byte[] run2 = chainBytes(span, 44905237L);
        if (!Arrays.equals(run1, run2)) {
            throw new IllegalStateException("pipeline generate->merge chain not deterministic");
        }
        return "chain:" + run1.length + "B";
    }

    /** 一条固定 generate→merge 链的逐字节载荷（L0 生成 ×4 → merge 得 L1）。A fixed generate→merge chain's
     * byte payload (four L0 generates merged into an L1 parent). */
    private static byte[] chainBytes(int span, long seed) {
        LodPipeline pipe = new LodPipeline(LodTieAccelerator.skippedNoOp());
        LodSection a = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed));
        LodSection b = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 1L));
        LodSection c = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 2L));
        LodSection d = pipe.generate(new LodPolygonizer.LodRegionSample(span, seed + 3L));
        LodSection l1 = pipe.merge(0, 0, a, b, c, d);
        return l1.toBytes();
    }

    /**
     * ③ {@link LodCache} 往返 + 篡改拒：在 build/tmp staging 目录 {save → load 往返 → 单字节篡改拒
     * CORRUPT → 版本不符拒 VERSION_MISMATCH}，写完即删 staging。③ {@link LodCache} round-trip +
     * tamper rejection: in a build/tmp staging dir {save → load round-trip → single-byte-tamper reject
     * CORRUPT → version-mismatch reject VERSION_MISMATCH}, the staging dir is deleted afterwards.
     */
    private static String cacheCheck() {
        String base = System.getProperty("subterra.projectDir",
                System.getProperty("user.dir", "."));
        Path staging = Path.of(base, "build", "tmp", "lod-runtime-gate");
        try {
            deleteRecursive(staging);
            LodCache cache = new LodCache(staging);
            LodCacheKey key = new LodCacheKey(LodLevel.L1, 3, -2);
            LodSection section = new LodPipeline(LodTieAccelerator.skippedNoOp())
                    .generate(new LodPolygonizer.LodRegionSample(LodLevel.L0.blockSpan(), 12345L));
            CacheResult saved = cache.save(key, section);
            if (saved.status() != CacheStatus.OK) {
                throw new IllegalStateException("cache save failed: " + saved.status());
            }
            CacheResult loaded = cache.load(key);
            if (loaded.status() != CacheStatus.OK
                    || !Arrays.equals(loaded.section().toBytes(), section.toBytes())) {
                throw new IllegalStateException("cache round-trip not byte-identical");
            }
            if (!cache.hit(key)) {
                throw new IllegalStateException("cache hit() false after save");
            }
            Path file = staging.resolve(key.fileName());
            byte[] tampered = Files.readAllBytes(file);
            tampered[LodCache.HEADER_LEN] ^= 0x01; // single-byte payload tamper
            Files.write(file, tampered);
            if (cache.load(key).status() != CacheStatus.CORRUPT) {
                throw new IllegalStateException("cache tamper not rejected");
            }
            byte[] versioned = Files.readAllBytes(file);
            versioned[7] ^= 0x7F; // bump the zd v2 header version byte[7] (d1) -> VERSION_MISMATCH
            Files.write(file, versioned);
            if (cache.load(key).status() != CacheStatus.VERSION_MISMATCH) {
                throw new IllegalStateException("cache version mismatch not detected");
            }
            return "roundtrip+reject";
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cache staging I/O error: " + e.getMessage(), e);
        } finally {
            deleteRecursive(staging);
        }
    }

    /**
     * ④ {@link LodTieAccelerator} skip-or-parity：dll 定位复用 {@code TieRuntime} 同款
     * （{@code subterra.lod.lib} 属性 → 捆绑资源，空则 skip）；无 lib 确定性 {@code skip}；有 lib 时与
     * JDK 内核对照，违约判 mismatch。④ {@link LodTieAccelerator} skip-or-parity: dll location reuses the
     * {@code TieRuntime} pattern ({@code subterra.lod.lib} property → bundled resource, else skip); no lib
     * is a deterministic {@code skip}; a present lib is compared against the JDK kernels, a violation
     * becomes a mismatch.
     */
    private static String tieCheck() {
        LodDllSource src = locate();
        if (src == null) {
            return "skip (no lod lib)";
        }
        Path dll = src.path();
        try (LodTieAccelerator acc = LodTieAccelerator.fromDll(dll)) {
            if (acc.skipped()) {
                return "skip (" + acc.reason() + ")";
            }
            long[][] profileInputs = {{44905237L, 0L, 0L}, {9L, 3L, 5L}, {1L, 15L, 15L}};
            for (long[] in : profileInputs) {
                Optional<Long> t = acc.profileTop(in[0], in[1], in[2]);
                if (t.isEmpty() || t.get() != LodPolygonizer.profileTop(in[0], in[1], in[2])) {
                    throw new IllegalStateException("profile_top parity mismatch");
                }
            }
            long[][] cornerInputs = {{1L, 2L, 3L, 4L}, {9L, 9L, 1L, 1L}};
            for (long[] in : cornerInputs) {
                Optional<Long> t = acc.mergeCorner(in[0], in[1], in[2], in[3]);
                if (t.isEmpty() || t.get() != LodMerger.mergeCorner(in[0], in[1], in[2], in[3])) {
                    throw new IllegalStateException("merge_corner parity mismatch");
                }
            }
            return "ok (parity)";
        } finally {
            if (src.temp()) {
                try {
                    Files.deleteIfExists(dll);
                } catch (Exception ignored) {
                    // best-effort temp cleanup
                }
            }
        }
    }

    /**
     * ⑤ {@link LodRenderBackend} 注册存在性：缺注册时注册，按名查得且支持自身两种实例格式。⑤
     * {@link LodRenderBackend} registration existence: registers when absent, then lookup-by-name + it
     * supports both of its own instance formats.
     */
    private static String backendCheck() {
        if (BackendRegistry.lookup(LodRenderBackend.NAME) == null) {
            LodRenderBackend.registerInto(); // idempotent-safe, dev-gated registration
        }
        RenderBackend be = BackendRegistry.lookup(LodRenderBackend.NAME);
        if (be == null || !LodRenderBackend.NAME.equals(be.name())) {
            throw new IllegalStateException("lod backend not registered");
        }
        if (!be.supports(LodRenderBackend.QUAD_INSTANCE_FORMAT)
                || !be.supports(LodRenderBackend.COLUMN_INSTANCE_FORMAT)) {
            throw new IllegalStateException("lod backend does not support its instance formats");
        }
        return "present:" + LodRenderBackend.NAME;
    }

    /**
     * p.2.17 RuleStore LOD 档位读取（收尾接线，降级版）：读 system property 缺省
     * {@code subterra.lod.enabled}/{@code subterra.lod.maxLevel}，读得值打进 marker。真实 p.2.17
     * RuleStore 双层配置面 / p.2.23 hub 表单热重载编辑为 p.2.28 后接线点（不实现）。
     * p.2.17 RuleStore LOD tier readback (closing wiring, degraded): reads the system-property defaults
     * {@code subterra.lod.enabled}/{@code subterra.lod.maxLevel}, folded into the marker. The real p.2.17
     * RuleStore two-tier config surface / p.2.23 hub form hot-reload editing are the p.2.28+ wiring points
     * (not implemented here).
     */
    private static String ruleTierReadback() {
        boolean enabled = Boolean.parseBoolean(
                System.getProperty("subterra.lod.enabled", "true"));
        int maxLevel = Integer.parseInt(
                System.getProperty("subterra.lod.maxLevel", String.valueOf(LodApi.DEFAULT_MAX_LEVEL)));
        return "enabled=" + enabled + ",maxLevel=" + maxLevel;
    }

    /** dll 定位解析结果：路径 + 是否临时提取（临时文件由调用方负责清理）。Resolved dll source: the path +
     * whether it is a temp extraction (the temp file is cleaned up by the caller). */
    private record LodDllSource(Path path, boolean temp) {
    }

    /** 定位 LOD tiec 动态库（固定序：{@code subterra.lod.lib} 属性 → 类路径捆绑资源）；两者皆缺返回
     * null。Locates the LOD tiec dll (fixed order: {@code subterra.lod.lib} property → bundled classpath
     * resource); returns null when neither is present. */
    private static LodDllSource locate() {
        String external = System.getProperty("subterra.lod.lib");
        if (external != null && !external.isBlank()) {
            Path p = Path.of(external);
            if (Files.isRegularFile(p)) {
                return new LodDllSource(p, false);
            }
        }
        Path staged = Path.of(System.getProperty("java.io.tmpdir"), STAGED_NAME);
        try (InputStream in = LodRuntime.class.getResourceAsStream(BUNDLED_DLL)) {
            if (in == null) {
                return null;
            }
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            return new LodDllSource(staged, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** 递归删除目录（尽力而为）. Best-effort recursive directory delete. */
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