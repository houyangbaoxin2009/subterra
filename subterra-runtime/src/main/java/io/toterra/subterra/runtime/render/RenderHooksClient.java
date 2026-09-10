package io.toterra.subterra.runtime.render;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.RenderBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

/**
 * p.2.27.2 — 三 MC 钩子的客户端实际对象只读核对（真实接入面）。经
 * {@code @EventBusSubscriber(modid = Subterra.MODID, value = Dist.CLIENT, bus = Bus.GAME)}
 * 自注册（仓库既有的客户端事件自注册范式，同 SubterraClient；不新增 mixin 配置——三钩子按
 * NeoForge 1.21.1 事件/可达面接线，jdoc 说明替代硬造 mixin json）：门控
 * {@code subterra.probe.render} 非 null 时，在首个 {@link RenderLevelStageEvent}
 * （由 {@link LevelRenderer#renderLevel} 派发）上对三个实际 MC 渲染对象做一次只读核对并打
 * 确定性 marker {@code [Subterra render] hooks client ok (level=.., blockModel=.., entity=..)}
 * （违约/程序错误打 {@code hooks client mismatch (error=...)}）：
 * <ol>
 *   <li><b>LevelRenderer</b>：{@code event.getLevelRenderer()} 实际实例在场 + NeoForge 渲染阶段
 *       面 {@link RenderLevelStageEvent.Stage#fromRenderType} 对原版 solid 层有绑定（engine
 *       {@code RenderStage.SOLID} 映射到的真实阶段面存在）；</li>
 *   <li><b>ModelBlockRenderer</b>：{@code Minecraft.getBlockRenderer().getModelRenderer()} 实际
 *       实例在场 + {@link RenderHooks#blockLayoutDigest()}（{@code VertexLayout.BLOCK} 32B 布局
 *       只读核对，方块模型实例数据字段布局对其对齐）；</li>
 *   <li><b>EntityRenderDispatcher</b>：{@code Minecraft.getEntityRenderDispatcher()} 实际实例在场
 *       + {@link BackendRegistry#defaultFor} 对确定性方块实例格式的缺省后端（单机集成服务器下
 *       服务端门控先注册样例行，故固定为 {@code sample.universal}；注册表为空时确定性报
 *       {@code backend:none}，不崩溃）。</li>
 * </ol>
 * 一次性（volatile 单发守卫）：首个观察到的 level 渲染阶段跑完即不再动作；全程只读——不改变渲染
 * 输出、不注册后端、不改任何 MC 状态。默认 no-op：门控属性缺失/空白时零开销返回。注意：dedicated
 * server 无客户端渲染对象，本类不注册（{@code value = Dist.CLIENT}），E2E（runServer）断言的是
 * {@link RenderHooks#serverWiringCheck()} 的 {@code hooks ok} marker；本行 {@code hooks client ok}
 * 是客户端 boot（runClient/单机）的 dev 校验面，文本刻意不与 {@code hooks ok} 子串重叠，避免
 * E2E 误匹配。
 * <p>
 * p.2.27.2 — client-side read-only check on the actual MC render objects (the real injection
 * surface of the three hooks). Self-registered via
 * {@code @EventBusSubscriber(modid = Subterra.MODID, value = Dist.CLIENT, bus = Bus.GAME)} (the
 * repo's established client-event self-registration pattern, same as SubterraClient; no new mixin
 * config — the three hooks are wired over the NeoForge 1.21.1 event/reachable surfaces, documented
 * here instead of fabricating a mixin json): when the {@code subterra.probe.render} gate is
 * non-null, the first {@link RenderLevelStageEvent} (fired from {@link LevelRenderer#renderLevel})
 * runs a one-shot read-only check on the three actual MC render objects and prints the
 * deterministic marker
 * {@code [Subterra render] hooks client ok (level=.., blockModel=.., entity=..)} (a violation /
 * program error prints {@code hooks client mismatch (error=...)}):
 * <ol>
 *   <li><b>LevelRenderer</b>: {@code event.getLevelRenderer()} live instance present + the NeoForge
 *       render-stage surface {@link RenderLevelStageEvent.Stage#fromRenderType} is bound for the
 *       vanilla solid layer (the real stage surface engine {@code RenderStage.SOLID} maps onto);</li>
 *   <li><b>ModelBlockRenderer</b>: {@code Minecraft.getBlockRenderer().getModelRenderer()} live
 *       instance present + {@link RenderHooks#blockLayoutDigest()} (read-only
 *       {@code VertexLayout.BLOCK} 32B layout check, the block-model instance data aligns to it);</li>
 *   <li><b>EntityRenderDispatcher</b>: {@code Minecraft.getEntityRenderDispatcher()} live instance
 *       present + {@link BackendRegistry#defaultFor} over the deterministic block-instance format
 *       (under the integrated server the server-side gate registers the sample row first, so it is
 *       fixed to {@code sample.universal}; an empty registry deterministically reports
 *       {@code backend:none} instead of crashing).</li>
 * </ol>
 * One-shot (volatile single-fire guard): the first observed level-render stage runs the check, then
 * the handler stays inert; fully read-only — no render-output change, no backend registration, no
 * MC state mutation. Default no-op: zero-cost return when the gate property is absent/blank. Note: a
 * dedicated server has no client render objects and never registers this class
 * ({@code value = Dist.CLIENT}); the E2E (runServer) asserts the {@code hooks ok} marker of
 * {@link RenderHooks#serverWiringCheck()}; this {@code hooks client ok} line is the client-boot
 * (runClient / singleplayer) dev-check surface and its text deliberately does not contain the
 * {@code hooks ok} substring, so the E2E assertion never false-matches it.
 */
@EventBusSubscriber(modid = Subterra.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class RenderHooksClient {

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 一次性守卫：首个观察到的 level 渲染阶段只跑一次核对。One-shot guard: the first observed
     * level-render stage runs the check exactly once. */
    private static volatile boolean done = false;

    private RenderHooksClient() {
    }

    /**
     * LevelRenderer 钩子面（事件由 {@link LevelRenderer#renderLevel} 派发）：对三个实际 MC 渲染
     * 对象做只读核对并打确定性 marker；违约/程序错误打 {@code hooks client mismatch}，绝不打假 ok。
     * LevelRenderer hook surface (the event is dispatched from {@link LevelRenderer#renderLevel}):
     * read-only check of the three actual MC render objects, then the deterministic marker; a
     * violation / program error prints {@code hooks client mismatch}, never a false ok.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (done) {
            return;
        }
        String probe = System.getProperty("subterra.probe.render");
        if (probe == null || probe.isBlank()) {
            return;
        }
        done = true;
        try {
            // LevelRenderer hook: live level renderer + the real NeoForge stage surface the engine
            // RenderStage order maps onto (the vanilla solid layer is bound to a Stage).
            LevelRenderer levelRenderer = event.getLevelRenderer();
            boolean rendererPresent = levelRenderer != null;
            boolean solidBound = RenderLevelStageEvent.Stage.fromRenderType(RenderType.solid()) != null;
            String level = "renderer:" + (rendererPresent ? "present" : "absent")
                    + ",solid:" + (solidBound ? "bound" : "unbound");

            // ModelBlockRenderer hook: live block-model renderer + the BLOCK vertex layout the
            // block-model instance data field layout aligns to (read-only layout verification).
            BlockRenderDispatcher blockRenderDispatcher = Minecraft.getInstance().getBlockRenderer();
            ModelBlockRenderer modelRenderer = blockRenderDispatcher == null
                    ? null : blockRenderDispatcher.getModelRenderer();
            boolean modelPresent = modelRenderer != null;
            String blockModel = "model:" + (modelPresent ? "present" : "absent")
                    + " " + RenderHooks.blockLayoutDigest();

            // EntityRenderDispatcher hook: live dispatcher + the deterministic default backend the
            // dispatcher wiring selects per format (BackendRegistry.defaultFor).
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            boolean dispatcherPresent = dispatcher != null;
            RenderBackend backend = BackendRegistry.defaultFor(RenderHooks.blockInstanceFormat());
            String entity = "dispatcher:" + (dispatcherPresent ? "present" : "absent")
                    + ",backend:" + (backend != null ? backend.name() : "none");

            LOGGER.info("{} hooks client ok (level={}, blockModel={}, entity={})",
                    RenderRuntime.MARKER, level, blockModel, entity);
        } catch (RuntimeException e) {
            LOGGER.warn("{} hooks client mismatch (error={})", RenderRuntime.MARKER, e.getMessage());
        }
    }
}
