package io.toterra.subterra.runtime.ui;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.ui.BookDef;
import io.toterra.subterra.engine.ui.DocBookTd;
import io.toterra.subterra.engine.ui.FoodTooltipRow;
import io.toterra.subterra.engine.ui.FoodValues;
import io.toterra.subterra.engine.ui.HudData;
import io.toterra.subterra.engine.ui.ModListViewCore;
import io.toterra.subterra.engine.ui.ModMetadata;
import io.toterra.subterra.engine.ui.TooltipBook;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * p.2.22.4 — ui runtime 壳：把 p.2.22 的 engine.ui 管理视图数据面核心（p.2.22.1 AppleSkin
 * 食物数据 {@link FoodValues} / {@link FoodTooltipRow} / {@link HudData}，p.2.22.2 td 化
 * tooltip 书籍 {@link TooltipBook} 与文档书籍 {@link DocBookTd}，p.2.22.3 ModMenu 借鉴的模组
 * 视图核心 {@link ModListViewCore} / {@link ModMetadata}）收编进 boot 生命周期的确定性核对。
 * {@link #bootstrap()}（{@code Subterra.java} 构造调用）注册 {@code ServerStartedEvent} 门控；
 * 门控 {@code subterra.probe.ui}（经 gradle -P → runServer system property 转发，与其余探针壳
 * 同模式）非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例消费（固定序、禁时序、禁 sleep）：固定 {@link FoodValues}（{@code hunger=6,
 * saturationModifier=0.6f}）→ {@link FoodTooltipRow#rows} 得固定序三行（hunger/saturation/
 * ratio）；固定 td 文档 → {@link TooltipBook#fromTd} + 固定 item 的 {@link TooltipBook#lookup}；
 * 两个固定 td 文档书籍 → {@link DocBookTd#fromTd}；两个固定 {@link ModMetadata}（故意乱序输入）
 * → {@link ModListViewCore#page}（按 id 字典序确定性排序）。全部对「同一驱动从头重建」做摘要
 * 复验（确定性证明）。全部通过打
 * {@code [Subterra ui] ok (hud=<rows 摘要>, tooltips=N, books=N, mods=<id 列表>, verify=ok)}
 * （hud 摘要 = 固定序行的 {@code label=value} 逗号连接；mods 为视图排序后的 id 逗号连接）；
 * 违约/程序错误（样例合法，正常不可达）打 {@code ui mismatch (error=...)} marker，绝不打假 ok。
 * <p>
 * <b>HUD/tooltip/书籍 GUI 消费接线面（p.2.23 后接线点，本子项只做门控确定性核对）</b>：
 * engine.ui 是纯 JDK 确定性管理视图数据面，真实玩家 HUD 渲染注入留 p.2.23（避免范围膨胀）。
 * 玩家侧交互走 p.2.14（engine.interact，零 HUD 规则）：面板仅管理视图。三面接线面文档化如下
 * （沿用 p.2.27.2 RenderHooks/RenderHooksClient 的「server 侧只读核对 + 客户端事件注入」范式，
 * 均以本壳确定性核对为前置）：
 * <ol>
 *   <li><b>HUD 消费面（hud consume）</b>：p.2.23 在客户端 HUD 渲染层消费 {@link FoodValues}/
 *       {@link FoodTooltipRow}（AppleSkin {@code HUDOverlayHandler} 式的饥饿/饱和度条）+ 只读
 *       {@link HudData}（玩家饥饿/饱和度/疲劳）——后续接线点为玩家 HUD 事件注入真实 overlay
 *       渲染；本子项仅消费 {@link FoodTooltipRow#rows} 数据面，不引玩家 HUD 渲染注入；</li>
 *   <li><b>tooltip 消费面（tooltip consume）</b>：p.2.23 在物品 tooltip 事件
 *       （GatherComponentsTooltipEvent 之类）上消费 {@link TooltipBook#lookup(itemId)} 的行
 *       列表 → 追加 tooltip 行——后续接线点为 tooltip 事件 → 书籍查询 → 行追加；本子项仅消费
 *       {@code fromTd+lookup} 数据面；</li>
 *   <li><b>书籍/面板 GUI 消费面（book & panel GUI consume）</b>：p.2.23 经
 *       {@link DocBookTd#fromTd} 装载 {@link BookDef} → 书籍 GUI 屏（Patchouli 式书视图），
 *       并经 {@link ModListViewCore}/{@code ModDetailViewCore}/{@code LicenseViewCore} 支撑
 *       模组管理面板（admin/management 视图）——后续接线点为 GUI 屏栈打开与视图行渲染；本子项
 *       仅消费 {@code fromTd/page} 数据面。</li>
 * </ol>
 * 门控同 {@code subterra.probe.ui}（默认 no-op）：服务端门控内打 {@code ok (hud=.., tooltips=..,
 * books=.., mods=.., verify=ok)}（供 E2E 断言）。接续表另见仓库 runtime 接线文档；本子项交付
 * 门控 marker 与 engine.ui 管理视图数据面在真实 boot 生命周期上的确定性证明。
 * <p>
 * p.2.22.4 — the ui runtime shell: folds the p.2.22 engine.ui management-view data-plane cores
 * (p.2.22.1 AppleSkin food data {@link FoodValues} / {@link FoodTooltipRow} / {@link HudData},
 * the p.2.22.2 td-ized tooltip book {@link TooltipBook} and doc book {@link DocBookTd}, and the
 * p.2.22.3 ModMenu-borrowed mod view cores {@link ModListViewCore} / {@link ModMetadata}) into
 * the boot lifecycle as a deterministic verification. {@link #bootstrap()} (called from the
 * {@code Subterra.java} constructor) registers the {@code ServerStartedEvent} gate; gated by
 * {@code subterra.probe.ui} (forwarded gradle -P → runServer system property, same pattern as
 * the other probe shells), runs only when non-null — a pure no-op shell by default, zero impact
 * on the boot lifecycle.
 * <p>
 * Deterministic sample consumption (fixed order, no timing, no sleeps): a fixed {@link FoodValues}
 * ({@code hunger=6, saturationModifier=0.6f}) → {@link FoodTooltipRow#rows} yields the fixed
 * three rows (hunger/saturation/ratio); fixed td documents → {@link TooltipBook#fromTd} plus a
 * fixed-item {@link TooltipBook#lookup}; two fixed td doc books → {@link DocBookTd#fromTd}; two
 * fixed {@link ModMetadata} (deliberately unsorted input) → {@link ModListViewCore#page} (sorted
 * deterministically by id). Every digest is re-verified against an identically rebuilt drive from
 * scratch (the determinism proof). On full success it prints
 * {@code [Subterra ui] ok (hud=<rows digest>, tooltips=N, books=N, mods=<id list>, verify=ok)}
 * (hud digest = the fixed-order rows joined as {@code label=value}; mods = the view-sorted ids
 * joined by comma); a load violation / program error (the samples are legal, so normally
 * unreachable) prints a {@code ui mismatch (error=...)} marker instead — a false ok is never
 * emitted.
 * <p>
 * <b>HUD / tooltip / book GUI consumption wiring surfaces (post-p.2.23 wiring points; this
 * sub-item only does the gated deterministic verification)</b>: engine.ui is the pure-JDK
 * deterministic management-view data plane, and the real player-HUD render injection lands with
 * p.2.23 (scope containment). Player-side interaction goes through p.2.14 (engine.interact,
 * zero-HUD rule): the panels are management views only. The three wiring surfaces are documented
 * here (following the p.2.27.2 RenderHooks/RenderHooksClient "server-side read-only check +
 * client-event injection" pattern, each building on this shell's deterministic check):
 * <ol>
 *   <li><b>HUD-consume surface</b>: p.2.23 consumes {@link FoodValues} / {@link FoodTooltipRow}
 *       at the client HUD render layer (AppleSkin {@code HUDOverlayHandler}-style hunger /
 *       saturation bars) plus the read-only {@link HudData} (player hunger / saturation /
 *       exhaustion) — the future wiring point injects the real overlay rendering on the player
 *       HUD event; this sub-item consumes only the {@link FoodTooltipRow#rows} data plane, with
 *       no player-HUD render injection;</li>
 *   <li><b>Tooltip-consume surface</b>: p.2.23 consumes the {@link TooltipBook#lookup(itemId)}
 *       line list on the item-tooltip event (GatherComponentsTooltipEvent and friends) →
 *       appended tooltip lines — the future wiring point is tooltip event → book lookup → line
 *       append; this sub-item consumes only the {@code fromTd+lookup} data plane;</li>
 *   <li><b>Book & panel GUI consume surface</b>: p.2.23 loads {@link BookDef} via
 *       {@link DocBookTd#fromTd} → the book GUI screen (Patchouli-style book viewer), and drives
 *       the mod-management panel (admin/management views) via {@link ModListViewCore} /
 *       {@code ModDetailViewCore} / {@code LicenseViewCore} — the future wiring point is the GUI
 *       screen-stack opening and view-row rendering; this sub-item consumes only the
 *       {@code fromTd/page} data plane.</li>
 * </ol>
 * Same gate {@code subterra.probe.ui} (default no-op): the server-side gate prints
 * {@code ok (hud=.., tooltips=.., books=.., mods=.., verify=ok)} (E2E-asserted). The wiring
 * table also lives in the repository runtime wiring docs; this sub-item delivers the gated
 * marker and the determinism proof of the engine.ui management-view data plane on a real boot
 * lifecycle.
 */
public final class UiRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra ui]";

    public static final Logger LOGGER = LogUtils.getLogger();

    private UiRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(UiRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.22.4 deterministic E2E hook: consume the engine.ui management-view data plane at
        // startup when a probe flag is forwarded (subterra.probe.ui) — mirrors the other
        // probe-shell gates, so no console command round-trips through the gradle-forked server
        // JVM stdin. Default no-op.
        String probe = System.getProperty("subterra.probe.ui");
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            // Deterministic fixed-order consumption (no timing, no randomness); every digest is
            // re-verified against an identical from-scratch rebuild.
            String hud = hudDigest();
            TooltipBook tooltipBook = sampleTooltipBook();
            int tooltips = tooltipBook.size();
            if (!tooltipBook.lookup("minecraft:apple")
                    .equals(List.of("restores 4 hunger", "saturation 2.4"))) {
                throw new IllegalStateException("tooltip lookup mismatch");
            }
            List<BookDef> books = sampleBooks();
            int bookCount = books.size();
            String mods = modIds();
            if (!hud.equals(hudDigest())
                    || tooltips != sampleTooltipBook().size()
                    || !books.equals(sampleBooks())
                    || !mods.equals(modIds())) {
                throw new IllegalStateException("ui view data not deterministic");
            }
            LOGGER.info("{} ok (hud={}, tooltips={}, books={}, mods={}, verify=ok)",
                    MARKER, hud, tooltips, bookCount, mods);
        } catch (RuntimeException e) {
            // the samples are legal, so a mismatch is a program error — never emit a false ok.
            LOGGER.warn("{} ui mismatch (error={})", MARKER, e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as the other shells).
    }

    /** 固定食物样例：hunger=6（3 鸡腿）、saturationModifier=0.6f（增量 7.2、比值 1.2）。
     * Fixed food sample: hunger=6 (3 shanks), saturationModifier=0.6f (increment 7.2, ratio 1.2). */
    private static FoodValues sampleFoodValues() {
        return new FoodValues(6, 0.6f);
    }

    /** HUD 数据面摘要：{@link FoodTooltipRow#rows} 固定序行以 {@code label=value} 逗号连接。
     * HUD data-plane digest: the fixed-order {@link FoodTooltipRow#rows} joined as
     * {@code label=value} by comma. */
    private static String hudDigest() {
        StringBuilder sb = new StringBuilder();
        for (FoodTooltipRow row : FoodTooltipRow.rows(sampleFoodValues())) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(row.label()).append('=').append(row.value());
        }
        return sb.toString();
    }

    /** 固定 td tooltip 书籍文档 → {@link TooltipBook#fromTd}（两条固定规则，首现胜出）。
     * Fixed td tooltip-book document → {@link TooltipBook#fromTd} (two fixed rules, first wins). */
    private static TooltipBook sampleTooltipBook() {
        TdTable doc = TdTable.builder()
                .put("tooltips", TdTable.builder()
                        .put("version", TdValue.of(1L))
                        .put("entries", TdTable.builder()
                                .element(tooltipRow("minecraft:apple",
                                        "restores 4 hunger", "saturation 2.4"))
                                .element(tooltipRow("minecraft:golden_apple",
                                        "restores 8 hunger", "saturation 9.6"))
                                .build())
                        .build())
                .build();
        return TooltipBook.fromTd(doc);
    }

    /** 单条固定 tooltip 行（item + 固定行序 lines）。A single fixed tooltip row
     * (item + fixed-order lines). */
    private static TdTable tooltipRow(String item, String... lines) {
        TdTable.Builder lineTable = TdTable.builder();
        for (String line : lines) {
            lineTable.element(TdValue.str(line));
        }
        return TdTable.builder()
                .put("item", item)
                .put("lines", lineTable.build())
                .build();
    }

    /** 两个固定 td 文档书籍 → {@link DocBookTd#fromTd}（field guide / bestiary，各两页）。
     * Two fixed td doc books → {@link DocBookTd#fromTd} (field guide / bestiary, two pages each). */
    private static List<BookDef> sampleBooks() {
        return List.of(
                DocBookTd.fromTd(bookDoc("subterra:field_guide", "Field Guide",
                        new String[][]{
                                {"text", "Intro", "This handbook documents the Subterra management views."},
                                {"recipe", "Core", "Crafting a subterra:core module."}})),
                DocBookTd.fromTd(bookDoc("subterra:bestiary", "Bestiary",
                        new String[][]{
                                {"entity", "Snail", "Slow but deterministic."},
                                {"text", "Outro", "End of the bestiary."}})));
    }

    /** 固定文档书籍 td 文档（固定字段序：id / title / pages，页内 type / title / content）。
     * Fixed doc-book td document (fixed field order: id / title / pages, page fields
     * type / title / content). */
    private static TdTable bookDoc(String id, String title, String[][] pages) {
        TdTable.Builder pageTable = TdTable.builder();
        for (String[] page : pages) {
            pageTable.element(TdTable.builder()
                    .put("type", page[0])
                    .put("title", page[1])
                    .put("content", page[2])
                    .build());
        }
        return TdTable.builder()
                .put("book", TdTable.builder()
                        .put("id", id)
                        .put("title", title)
                        .put("pages", pageTable.build())
                        .build())
                .build();
    }

    /** 模组视图数据面摘要：两个固定 {@link ModMetadata}（输入乱序）→ {@link ModListViewCore#page}
     * → 按 id 字典序排序的 id 逗号连接（subterra:core,subterra:ui）。
     * Mod-view data-plane digest: two fixed {@link ModMetadata} (unsorted input) →
     * {@link ModListViewCore#page} → the id-sorted ids joined by comma (subterra:core,subterra:ui). */
    private static String modIds() {
        ModListViewCore view = new ModListViewCore(List.of(
                new ModMetadata("subterra:ui", "Subterra UI", "Management view data cores.", "1.0.0", "MIT"),
                new ModMetadata("subterra:core", "Subterra Core", "Deterministic engine core.", "1.0.0", "MIT")));
        StringBuilder sb = new StringBuilder();
        for (ModMetadata mod : view.page(2, 0)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(mod.id());
        }
        return sb.toString();
    }
}
