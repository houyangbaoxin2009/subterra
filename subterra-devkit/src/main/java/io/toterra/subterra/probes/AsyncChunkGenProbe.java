// Deterministic acceptance probe for the p.2.6.3 async chunk-generation core
// (io.toterra.subterra.engine.worldgen.async ChunkProducer/ChunkConsumer/AsyncChunkPipeline).
// NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.async.AsyncChunkPipeline;
import io.toterra.subterra.engine.worldgen.async.ChunkKey;
import io.toterra.subterra.engine.worldgen.async.ChunkProducer;
import io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher.ChunkResult;
import io.toterra.subterra.engine.worldgen.pipeline.chunkgrid.DensityGrid;
import io.toterra.subterra.engine.worldgen.pipeline.chunkgrid.GridSettings;
import io.toterra.subterra.engine.worldgen.pipeline.chunkgrid.HeightMapper;
import io.toterra.subterra.engine.worldgen.pipeline.density.Densities;
import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.OverworldBounds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic acceptance probe for the p.2.6.3 async chunk-generation core
 * (io.toterra.subterra.engine.worldgen.async ChunkProducer / ChunkConsumer /
 * AsyncChunkPipeline). Builds a fixed chunkgrid worldgen recipe once and pushes it
 * through the SAME producer on both the serial golden path and the parallel dispatch
 * path, asserting: parallel-vs-serial byte equality (per chunk and list order — the
 * milestone's block-for-block determinism contract), determinism across repeat runs,
 * cross-chunk isolation (no contamination when a chunk is generated in a batch), the
 * deterministic consumer key order (the merge contract), dispatch integrity
 * (submitted==executed==N, idempotent close), and parallelism-1-vs-8 merged equality.
 * Every check is a final-state assertion — never a wall-clock/timing assertion.
 * Exit 0 = PASS, 1 = FAIL (not shipped in the mod jar).
 *
 * <p>p.2.6.3 异步区块生成核心（io.toterra.subterra.engine.worldgen.async 的
 * ChunkProducer / ChunkConsumer / AsyncChunkPipeline）的确定性验收探针。仅构建一次固定
 * chunkgrid 世界生成配方，并让同一个 producer 同时走上串行金样路径与并行分派路径，断言：
 * 并行 vs 串行字节等价（逐区块且列表有序——本里程碑的"逐块一致"确定性契约）；重复运行间的
 * 确定性；跨区块隔离（某区块放入批次生成与单独生成字节一致，无相互污染）；确定性消费者键序
 * （归并契约）；分派完整性（submitted==executed==N、close 幂等）；以及并行度 1 与 8 归并结果
 * 一致。所有检查皆为终态断言，绝无墙钟/时序断言。退出码 0 = PASS，1 = FAIL（不随 mod jar 发布）。
 */
public final class AsyncChunkGenProbe {

    private AsyncChunkGenProbe() {
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

    /** Fixed world seed shared by every probe scenario (matches the S-2 probe). */
    private static final long WORLD_SEED = 44905237L;

    /**
     * The producer recipe, constructed ONCE and driven by both the serial and parallel
     * paths. Fresh-constructs every working object (GridSettings, Density, DensityGrid,
     * HeightMapper) inside produce — zero shared mutable state, so it is unambiguously
     * safe under concurrent bank threads. The fingerprint serializes a per-column
     * heightmap (DensityGrid + HeightMapper surface Y over the chunk's 16×16 blocks)
     * plus 4 deterministic salt bytes drawn from the isolated per-chunk random.
     */
    private static ChunkProducer makeProducer(final long worldSeed) {
        return (cx, cz, ctx) -> {
            // One immutable chunkgrid recipe, fresh per chunk (their origins move with the chunk).
            int ox = (int) (cx * 16);
            int oz = (int) (cz * 16);
            GridSettings s = new GridSettings(
                    GridSettings.DEFAULT_CELL_WIDTH,
                    GridSettings.DEFAULT_CELL_HEIGHT,
                    OverworldBounds.vanilla().minY(),
                    OverworldBounds.vanilla().height(),
                    OverworldBounds.vanilla().seaLevel(),
                    GridSettings.DEFAULT_CELL_COUNT_XZ,
                    ox,
                    oz);
            Density density = Densities.valueNoise(worldSeed, 28.0);
            DensityGrid grid = new DensityGrid(s, density);
            HeightMapper hm = new HeightMapper(s);

            // Heightmap fingerprint: 256 surface-Y ints (big-endian) = 1024 bytes.
            byte[] out = new byte[1024 + 4];
            int idx = 0;
            for (int bx = ox; bx < ox + 16; bx++) {
                for (int bz = oz; bz < oz + 16; bz++) {
                    int v = hm.getSurfaceY(grid, bx, bz);
                    out[idx++] = (byte) (v >> 24);
                    out[idx++] = (byte) (v >> 16);
                    out[idx++] = (byte) (v >> 8);
                    out[idx++] = (byte) v;
                }
            }
            // Fold the isolated per-chunk random into 4 salt bytes (deterministic per chunk).
            int salt = 0;
            for (int i = 0; i < 4; i++) {
                salt = (salt << 8) | (ctx.random().nextInt(256) & 0xFF);
            }
            out[1024] = (byte) (salt >> 24);
            out[1025] = (byte) (salt >> 16);
            out[1026] = (byte) (salt >> 8);
            out[1027] = (byte) salt;
            return out;
        };
    }

    /** The canonical 16-chunk coordinate set: a 4×4 window around the origin. */
    private static List<ChunkKey> window16() {
        List<ChunkKey> keys = new ArrayList<>(16);
        for (long x = -2; x <= 1; x++) {
            for (long z = -2; z <= 1; z++) {
                keys.add(new ChunkKey(x, z));
            }
        }
        return keys;
    }

    /** Runs a parallel generate and returns both the merged results and the consumer key order. */
    private static List<ChunkResult> runGenerate(int parallelism, ChunkProducer producer,
                                                 List<ChunkKey> keys, List<ChunkKey> orderOut) {
        AsyncChunkPipeline p = AsyncChunkPipeline.of(producer, r -> orderOut.add(new ChunkKey(r.x(), r.z())),
                WORLD_SEED, parallelism);
        try {
            return p.generate(keys);
        } finally {
            p.close();
        }
    }

    /** Runs a serial generate and returns both the merged results and the consumer key order. */
    private static List<ChunkResult> runSerial(ChunkProducer producer, List<ChunkKey> keys,
                                               List<ChunkKey> orderOut) {
        AsyncChunkPipeline p = AsyncChunkPipeline.of(producer, r -> orderOut.add(new ChunkKey(r.x(), r.z())),
                WORLD_SEED, 1);
        try {
            return p.generateSerial(keys);
        } finally {
            p.close();
        }
    }

    /** True when two merged result lists are element-wise byte-identical (same keys, same order). */
    private static boolean listsEqual(List<ChunkResult> a, List<ChunkResult> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ChunkResult ra = a.get(i);
            ChunkResult rb = b.get(i);
            if (ra.x() != rb.x() || ra.z() != rb.z()
                    || !Arrays.equals(ra.payload(), rb.payload())) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        ChunkProducer producer = makeProducer(WORLD_SEED);
        List<ChunkKey> coords = window16();

        // ---- b. serial golden vs parallel (P=4): block-for-block identity + order ----
        List<ChunkKey> serialOrder = new ArrayList<>();
        List<ChunkKey> parallelOrder = new ArrayList<>();
        List<ChunkResult> serial = runSerial(producer, coords, serialOrder);
        List<ChunkResult> parallel = runGenerate(4, producer, coords, parallelOrder);
        check("b block-for-block: serial vs parallel (P=4) byte-identical per chunk and order",
                listsEqual(serial, parallel) && serial.size() == coords.size());

        // ---- c. determinism repeat: generate twice -> identical ----
        List<ChunkKey> parOrder2 = new ArrayList<>();
        List<ChunkResult> parallelAgain = runGenerate(4, producer, coords, parOrder2);
        check("c determinism: generate (P=4) run twice byte-identical", listsEqual(parallel, parallelAgain));

        // ---- d. cross-chunk isolation: (5,3) standalone vs inside a batch ----
        List<ChunkKey> only = List.of(new ChunkKey(5, 3));
        byte[] standalone = findPayload(runSerial(producer, only, new ArrayList<>()), 5, 3);
        List<ChunkKey> bigBatch = new ArrayList<>();
        for (long x = 0; x < 12; x++) {
            for (long z = 0; z < 12; z++) {
                bigBatch.add(new ChunkKey(x, z));
            }
        }
        byte[] inBatch = findPayload(runGenerate(4, producer, bigBatch, new ArrayList<>()), 5, 3);
        check("d isolation: chunk (5,3) standalone byte-identical to same chunk in a 144-key batch",
                Arrays.equals(standalone, inBatch));

        // ---- e. consumer key order = sorted Z-then-X (deterministic merge contract) ----
        List<ChunkKey> mergeOrder = new ArrayList<>();
        runGenerate(4, producer, coords, mergeOrder);
        check("e consumer: parallel run delivers every result once in sorted Z-then-X order",
                mergeOrder.size() == coords.size()
                        && isSortedZTX(mergeOrder) && keysMatch(mergeOrder, coords));

        // ---- f. dispatch integrity + idempotent close ----
        AsyncChunkPipeline p = AsyncChunkPipeline.of(producer, r -> {
        }, WORLD_SEED, 4);
        List<ChunkResult> res = p.generate(coords);
        check("f dispatch: generate returns N merged results", res.size() == coords.size());
        p.close();
        boolean threwAfterClose = false;
        try {
            p.generate(coords);
        } catch (IllegalStateException e) {
            threwAfterClose = true;
        }
        boolean secondCloseOk;
        try {
            p.close(); // idempotent: repeated close() must not throw
            secondCloseOk = true;
        } catch (Exception e) {
            secondCloseOk = false;
        }
        check("f dispatch+close: generate-after-close rejected, repeated close() idempotent",
                threwAfterClose && secondCloseOk && p.isClosed());

        // ---- g. parallelism sanity (final-state only): P=1 vs P=8 identical merged bytes ----
        List<ChunkResult> p1 = runGenerate(1, producer, coords, new ArrayList<>());
        List<ChunkResult> p8 = runGenerate(8, producer, coords, new ArrayList<>());
        check("g parallelism: P=1 vs P=8 merged byte-identical", listsEqual(p1, p8));

        if (failures == 0) {
            System.out.println("[AsyncChunkGenProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[AsyncChunkGenProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** Finds a result's payload by key, or null if absent. */
    private static byte[] findPayload(List<ChunkResult> results, long x, long z) {
        for (ChunkResult r : results) {
            if (r.x() == x && r.z() == z) {
                return r.payload();
            }
        }
        return null;
    }

    /** True when the key list is strictly ascending by ChunkKey natural order (Z then X). */
    private static boolean isSortedZTX(List<ChunkKey> keys) {
        for (int i = 1; i < keys.size(); i++) {
            if (keys.get(i).compareTo(keys.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    /** True when the observed key multiset exactly equals the expected key multiset. */
    private static boolean keysMatch(List<ChunkKey> observed, List<ChunkKey> expected) {
        List<ChunkKey> a = new ArrayList<>(observed);
        List<ChunkKey> b = new ArrayList<>(expected);
        a.sort(ChunkKey::compareTo);
        b.sort(ChunkKey::compareTo);
        return a.equals(b);
    }
}