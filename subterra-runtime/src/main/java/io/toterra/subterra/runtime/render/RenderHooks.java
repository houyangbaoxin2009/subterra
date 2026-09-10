package io.toterra.subterra.runtime.render;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.render.RenderStage;
import io.toterra.subterra.engine.render.VertexLayout;
import io.toterra.subterra.engine.render.VertexLayout.VertexField;
import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.InstanceFormat;
import io.toterra.subterra.engine.render.instancing.InstanceFormat.ScalarType;
import io.toterra.subterra.engine.render.instancing.RenderBackend;
import org.slf4j.Logger;

import java.util.List;

/**
 * p.2.27.2 — 三 MC 钩子的确定性校验接线核心（server 侧可跑，供 E2E 断言）。
 * 把 p.2.27.1 的 engine.render 模型（{@link RenderStage} / {@link VertexLayout#BLOCK}）与
 * p.2.27.1.1 的 engine.render.instancing（{@link InstanceFormat} / {@link BackendRegistry}）按
 * 三个 MC 钩子的接线面做只读核对，并打出确定性 marker {@code [Subterra render] hooks ok (level=..,
 * blockModel=.., entity=..)}（违约/程序错误打 {@code hooks mismatch (error=...)}，绝不打假 ok）：
 * <ol>
 *   <li><b>LevelRenderer 钩子面</b>：{@link RenderStage} 固定序（10 阶段）逐项核对
 *       （{@link #levelDigest()}，level 摘要 = {@code stages:10}）——区块级实例化渲染编译/装载面
 *       的缓冲/阶段选择数据保持 p.2.27.1 权威序；</li>
 *   <li><b>ModelBlockRenderer 钩子面</b>：{@link VertexLayout#BLOCK} 32B 布局逐字段核对
 *       （{@link #blockLayoutDigest()}，blockModel 摘要 = {@code BLOCK:32B@0/12/16/20/24/28}）——
 *       方块模型实例数据字段布局与偏移对齐该布局；</li>
 *   <li><b>EntityRenderDispatcher 钩子面</b>：{@link BackendRegistry#defaultFor} 对
 *       {@link #blockInstanceFormat()}（确定性方块实例格式）的缺省后端选择（entity 摘要 =
 *       {@code backend:<name>}，门控样例行中为 {@code sample.universal}）。</li>
 * </ol>
 * 全部只读（不注册、不注入、不改任何 MC 状态，不改变渲染输出）；缺省 no-op 语义由调用方
 * （{@link RenderRuntime} 的 {@code subterra.probe.render} 门控）保证。真实 MC 渲染对象上的
 * 客户端只读核对见 {@link RenderHooksClient}（本类只做模型面校验，dedicated server 也可跑）。
 * <p>
 * p.2.27.2 — deterministic check-wiring core of the three MC hooks (server-runnable, so the E2E
 * probe can assert it). It read-only verifies the p.2.27.1 engine.render models ({@link RenderStage}
 * / {@link VertexLayout#BLOCK}) and the p.2.27.1.1 engine.render.instancing surface
 * ({@link InstanceFormat} / {@link BackendRegistry}) against the three MC hook wiring surfaces and
 * prints the deterministic marker {@code [Subterra render] hooks ok (level=.., blockModel=..,
 * entity=..)} (a violation / program error prints {@code hooks mismatch (error=...)} instead — a
 * false ok is never emitted):
 * <ol>
 *   <li><b>LevelRenderer hook surface</b>: {@link RenderStage} fixed order (10 stages) checked
 *       field-by-field ({@link #levelDigest()}, level digest = {@code stages:10}) — the
 *       chunk-level instanced-render compile/load surface keeps the p.2.27.1 authoritative
 *       buffer/stage selection order;</li>
 *   <li><b>ModelBlockRenderer hook surface</b>: {@link VertexLayout#BLOCK} 32B layout checked
 *       field-by-field ({@link #blockLayoutDigest()}, blockModel digest =
 *       {@code BLOCK:32B@0/12/16/20/24/28}) — the block-model instance data field layout/offsets
 *       align to this layout;</li>
 *   <li><b>EntityRenderDispatcher hook surface</b>: {@link BackendRegistry#defaultFor} over
 *       {@link #blockInstanceFormat()} (a deterministic block-instance format) selects the default
 *       backend (entity digest = {@code backend:<name>}, {@code sample.universal} under the gated
 *       sample row).</li>
 * </ol>
 * Everything is read-only (no registration, no injection, no MC state mutation, no render-output
 * change); the default no-op semantics are guaranteed by the caller ({@link RenderRuntime}'s
 * {@code subterra.probe.render} gate). The client-side read-only check on the actual MC render
 * objects lives in {@link RenderHooksClient} (this class only verifies the model surface, so it
 * runs on a dedicated server too).
 */
public final class RenderHooks {

    /** 探针 marker 前缀（与 {@link RenderRuntime#MARKER} 同源）。Probe marker prefix (same
     * source as {@link RenderRuntime#MARKER}). */
    public static final String MARKER = RenderRuntime.MARKER;

    public static final Logger LOGGER = LogUtils.getLogger();

    /** p.2.27.1 权威序：10 个渲染阶段的固定 form 序（与 {@link RenderStage#values()} 序一致）。
     * p.2.27.1 authoritative order: the fixed form order of the 10 render stages (matching
     * {@link RenderStage#values()}). */
    private static final String[] EXPECTED_STAGE_FORMS = {
            "solid", "cutout", "cutout_mipped", "translucent", "tripwire",
            "beacon_beam", "clouds", "lightning", "water_mask", "translucent_moving_block"};

    /** p.2.27.1 内建方块布局的规范字段序与偏移（position@0 / color@12 / uv@16 / light@20 /
     * overlay@24 / normal@28，总 32B）。Canonical field order and offsets of the built-in block
     * layout (position@0 / color@12 / uv@16 / light@20 / overlay@24 / normal@28, 32B total). */
    private static final String[] EXPECTED_BLOCK_FIELDS = {
            "position", "color", "uv", "light", "overlay", "normal"};
    private static final int[] EXPECTED_BLOCK_OFFSETS = {0, 12, 16, 20, 24, 28};
    private static final int EXPECTED_BLOCK_BYTE_SIZE = 32;

    private RenderHooks() {
    }

    /**
     * 三钩子接线校验（门控内调用）：逐面只读核对并打确定性 {@code hooks ok} marker；违约/程序错误
     * 打 {@code hooks mismatch (error=...)}。Three-hook wiring check (called inside the gate):
     * read-only verification of each surface, then the deterministic {@code hooks ok} marker; a
     * violation / program error prints {@code hooks mismatch (error=...)} instead.
     */
    public static void serverWiringCheck() {
        try {
            String level = levelDigest();
            String blockModel = blockLayoutDigest();
            RenderBackend backend = BackendRegistry.defaultFor(blockInstanceFormat());
            if (backend == null) {
                throw new IllegalStateException("no deterministic default instancing backend for the block instance format");
            }
            LOGGER.info("{} hooks ok (level={}, blockModel={}, entity=backend:{})",
                    MARKER, level, blockModel, backend.name());
        } catch (RuntimeException e) {
            LOGGER.warn("{} hooks mismatch (error={})", MARKER, e.getMessage());
        }
    }

    /**
     * 固定序阶段摘要：核对 {@link RenderStage} 数量与 form 权威序，返回 {@code stages:N}。
     * Fixed-order stage digest: verifies the {@link RenderStage} count and the authoritative form
     * order, returns {@code stages:N}.
     */
    static String levelDigest() {
        RenderStage[] stages = RenderStage.values();
        if (stages.length != EXPECTED_STAGE_FORMS.length) {
            throw new IllegalStateException("RenderStage count changed: " + stages.length
                    + " != " + EXPECTED_STAGE_FORMS.length);
        }
        for (int i = 0; i < stages.length; i++) {
            if (!stages[i].form().equals(EXPECTED_STAGE_FORMS[i])) {
                throw new IllegalStateException("RenderStage fixed order violated at index " + i
                        + ": " + stages[i].form() + " != " + EXPECTED_STAGE_FORMS[i]);
            }
        }
        return "stages:" + stages.length;
    }

    /**
     * 方块布局摘要：核对 {@link VertexLayout#BLOCK} 总字节、字段序与偏移，返回
     * {@code BLOCK:32B@0/12/16/20/24/28}。Block-layout digest: verifies
     * {@link VertexLayout#BLOCK} byte size, field order and offsets, returns
     * {@code BLOCK:32B@0/12/16/20/24/28}.
     */
    static String blockLayoutDigest() {
        VertexLayout block = VertexLayout.BLOCK;
        if (block.byteSize() != EXPECTED_BLOCK_BYTE_SIZE) {
            throw new IllegalStateException("VertexLayout.BLOCK byte size changed: "
                    + block.byteSize() + " != " + EXPECTED_BLOCK_BYTE_SIZE);
        }
        List<VertexField> fields = block.fields();
        if (fields.size() != EXPECTED_BLOCK_FIELDS.length) {
            throw new IllegalStateException("VertexLayout.BLOCK field count changed: "
                    + fields.size() + " != " + EXPECTED_BLOCK_FIELDS.length);
        }
        for (int i = 0; i < EXPECTED_BLOCK_FIELDS.length; i++) {
            VertexField field = fields.get(i);
            if (!field.name().equals(EXPECTED_BLOCK_FIELDS[i])
                    || field.byteOffset() != EXPECTED_BLOCK_OFFSETS[i]) {
                throw new IllegalStateException("VertexLayout.BLOCK field order/offset violated at index "
                        + i + ": " + field.name() + "@" + field.byteOffset()
                        + " != " + EXPECTED_BLOCK_FIELDS[i] + "@" + EXPECTED_BLOCK_OFFSETS[i]);
            }
        }
        StringBuilder digest = new StringBuilder("BLOCK:").append(block.byteSize()).append("B@");
        for (int i = 0; i < EXPECTED_BLOCK_OFFSETS.length; i++) {
            if (i > 0) {
                digest.append('/');
            }
            digest.append(EXPECTED_BLOCK_OFFSETS[i]);
        }
        return digest.toString();
    }

    /**
     * 确定性方块实例格式（声明序固定）：model 索引 + 位置 + 颜色 + UV——EntityRenderDispatcher
     * 钩子面的缺省后端选择输入。Deterministic block-instance format (fixed declaration order):
     * model index + position + color + UV — the input to the EntityRenderDispatcher hook surface's
     * default-backend selection.
     */
    static InstanceFormat blockInstanceFormat() {
        return InstanceFormat.builder()
                .scalar("model_index", ScalarType.U32)
                .vector("position", ScalarType.F32, 3)
                .vector("color", ScalarType.U8, 4)
                .vector("uv", ScalarType.U16, 2)
                .build();
    }
}
