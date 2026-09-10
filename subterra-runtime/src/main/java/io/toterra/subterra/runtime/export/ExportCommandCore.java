package io.toterra.subterra.runtime.export;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackExportArchive;
import io.toterra.subterra.engine.export.ConfigProducer;
import io.toterra.subterra.engine.export.DatapackArchiveProducer;
import io.toterra.subterra.engine.export.ExportHub;
import io.toterra.subterra.engine.export.ExportKind;
import io.toterra.subterra.engine.export.ExportProducer;
import io.toterra.subterra.engine.export.LanguageKeysProducer;
import io.toterra.subterra.engine.export.MigrateMapsProducer;
import io.toterra.subterra.engine.export.RegistriesProducer;
import io.toterra.subterra.runtime.datapack.DatapackRegistrar;
import io.toterra.subterra.runtime.datapack.DatapackRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * p.2.19.5 — 统一 export 指令核心：把 p.2.2.7 的 datapack 全量导出核心
 * （DatapackExportCommand.exportFrom，marker {@code export cmd ...}）与 p.2.18.5 的七 form 导出
 * 中枢核心（ExportHubCommand.exportForm/exportAllForms，marker {@code export hub ...}）收编进同一
 * {@code /subterra export} 命令树（单点注册，仅一处 RegisterCommandsEvent 注册）与同一核心类。
 * 两套 marker 前缀逐字符不变——datapack 侧 {@code [Subterra datapack] export cmd <pack> ok ...} /
 * {@code export cmd ok (packs=N, bytes=M)}，hub 侧 {@code [Subterra export] export hub <form> ok ...} /
 * {@code export hub ok (forms=N)}——E2E 断言（DatapackE2EProbe）零改动。
 *
 * <p>统一命令树（op level 2）：{@code /subterra export [<form>] [<path>]}。无参走 datapack 全量导出
 * （缺省目录 {@code subterra-export}）；单参 {@code path}（非 form 词）走既有 datapack 分支（markers
 * 逐字符不变）；单参/双参 {@code form}（form 感知的 {@link ExportFormArgumentType}，parse 期只接受七
 * form 之一，未知串抛异常使 path 分支独占解析）走既有 hub 分支（markers 逐字符不变）。form 词同时命中
 * 两分支时由 Brigadier getBestMatch 决议，与收编前的合并树行为一致（本注册保持 path 先于 form 的子
 * 节点序）。命令注册、datapack 核心与 hub 核心原先分处 runtime.datapack / runtime.export 两包两类，
 * 现统一收敛于本类；启动钩子收敛于 {@link ExportRuntime}（{@code subterra.probe.export} 与
 * {@code subterra.probe.exportHub} 双门控，markers 不变）。
 *
 * <p>确定性范式（与收编前逐字符一致）：固定序、禁时序、事件驱动 marker、不赌 sleep、不依赖 stdin。
 * datapack 核心 {@link #exportFrom} 每 pack 一行 {@code export cmd <pack> ok/mismatch (... rehydrate=
 * ok/mismatch)} + 汇总 {@code export cmd ok (packs=N, bytes=M)} / {@code export cmd mismatch}（写
 * {@code <pack-name>.td}，export ∘ rehydrate 字节恒等）；hub 核心 {@link #exportForm} /
 * {@link #exportAllForms} 每 form 一行 {@code export hub <form> ok/skip/mismatch (...)} + 汇总
 * {@code export hub ok (forms=N)} / {@code export hub mismatch}（写 {@code <form>.td} + {@code <form>.zd}，
 * td/zd 双格式回水化恒等）。
 *
 * <p>p.2.19.5 — unified export command core: folds the p.2.2.7 datapack all-packs export core
 * (DatapackExportCommand.exportFrom, marker {@code export cmd ...}) and the p.2.18.5 seven-form
 * export-hub core (ExportHubCommand.exportForm/exportAllForms, marker {@code export hub ...}) into
 * the one {@code /subterra export} command tree (single-point registration — exactly one
 * RegisterCommandsEvent registration) and one core class. Both marker prefixes stay
 * character-for-character unchanged — the datapack side {@code [Subterra datapack] export cmd <pack>
 * ok ...} / {@code export cmd ok (packs=N, bytes=M)}, the hub side {@code [Subterra export]
 * export hub <form> ok ...} / {@code export hub ok (forms=N)} — so the E2E assertions
 * (DatapackE2EProbe) are untouched.
 *
 * <p>Unified command tree (op level 2): {@code /subterra export [<form>] [<path>]}. No argument runs
 * the datapack all-packs export (default dir {@code subterra-export}); a single {@code path}
 * argument (a non-form word) runs the existing datapack branch (markers character-for-character
 * unchanged); a single/double {@code form} argument (form-aware {@link ExportFormArgumentType} —
 * accepts only one of the seven forms at parse time, an unknown string throws so the path branch
 * parses alone) runs the existing hub branch (markers unchanged). When a form word hits both
 * branches Brigadier's getBestMatch resolves it, identical to the pre-fold-in merged tree (this
 * registration keeps the path child before the form child, the same order the merge produced).
 * Command registration, the datapack core and the hub core previously lived split across
 * runtime.datapack / runtime.export two classes; they now converge on this class, and the startup
 * hooks converge on {@link ExportRuntime} (the {@code subterra.probe.export} and
 * {@code subterra.probe.exportHub} gates, markers unchanged).
 *
 * <p>Deterministic paradigm (character-for-character identical to the pre-fold-in behavior):
 * fixed order, no timing, event-driven markers, no sleeps, no stdin. The datapack core
 * {@link #exportFrom} emits one {@code export cmd <pack> ok/mismatch (... rehydrate=ok/mismatch)}
 * line per pack plus the aggregate {@code export cmd ok (packs=N, bytes=M)} /
 * {@code export cmd mismatch} (writes {@code <pack-name>.td}, export ∘ rehydrate byte identity);
 * the hub cores {@link #exportForm} / {@link #exportAllForms} emit one
 * {@code export hub <form> ok/skip/mismatch (...)} line per form plus the aggregate
 * {@code export hub ok (forms=N)} / {@code export hub mismatch} (writes {@code <form>.td} +
 * {@code <form>.zd}, td/zd dual-format rehydrate identity).
 */
public final class ExportCommandCore {

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 确定性 hub marker 前缀。The deterministic hub marker prefix. */
    public static final String HUB_MARKER = "[Subterra export]";

    /** 缺省导出目录。Default export directory. */
    private static final String DEFAULT_DIR = "subterra-export";

    // ---------- hard-coded deterministic sample sources (p.2.18.5) ----------

    /** 语言键样例（固定内容）。Sample language-keys table (fixed content). */
    private static final Map<String, String> SAMPLE_KEYS = Map.of(
            "item.toterra.crystal", "Crystal",
            "item.toterra.dust", "Dust",
            "block.toterra.moss", "Moss");

    /** 双层配置样例：global 档（含覆盖前值）。Sample two-tier config: the global layer. */
    private static final Map<String, String> SAMPLE_CONFIG_GLOBAL = Map.of(
            "subterra.worldgen.seed_offset", "42",
            "subterra.entity.activation.range", "32.0");

    /** 双层配置样例：overrides 档（胜出值）。Sample two-tier config: the overrides layer. */
    private static final Map<String, String> SAMPLE_CONFIG_OVERRIDES = Map.of(
            "subterra.worldgen.seed_offset", "7");

    /** 注册表快照样例（注册表名 → 条目 id 列表，固定内容）。Sample registries snapshot. */
    private static final Map<String, List<String>> SAMPLE_REGISTRIES = Map.of(
            "subterra:item", List.of("toterra:crystal", "toterra:dust"),
            "subterra:block", List.of("toterra:moss"));

    /** 迁移映射样例（源键 → 目标键，固定内容）。Sample migration map (source key → target key). */
    private static final Map<String, String> SAMPLE_MIGRATE_MAPS = Map.of(
            "subterra.old.seed", "subterra.worldgen.seed_offset",
            "subterra.old.range", "subterra.entity.activation.range");

    private ExportCommandCore() {
    }

    /**
     * 单点注册统一 {@code /subterra export [<form>] [<path>]} 命令树（op level 2）。收编
     * DatapackExportCommand.onRegisterCommands（datapack 分支：无参 executes + 单参 path executes）与
     * ExportHubCommand.onRegisterCommands（hub 分支：form 感知参数 + 建议 + 单/双参 executes），子节点
     * 序与收编前合并树一致（path 先于 form），保证 RegisterCommandsEvent 下 /subterra 根只注册一次、
     * 解析行为与 markers 不变。
     * <p>
     * Registers the unified {@code /subterra export [<form>] [<path>]} command tree (op level 2) from
     * a single point. Absorbs DatapackExportCommand.onRegisterCommands (datapack branch: no-argument
     * executes + single-argument path executes) and ExportHubCommand.onRegisterCommands (hub branch:
     * form-aware argument + suggestions + single/double-argument executes); the child order matches
     * the pre-fold-in merged tree (path before form), so the /subterra root is registered exactly once
     * per RegisterCommandsEvent and the parse behavior and markers are unchanged.
     *
     * @param event 命令注册事件 / the command registration event.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("subterra")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("export")
                        .executes(ctx -> execute(ctx, null, null))
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(ctx -> execute(ctx, null,
                                        StringArgumentType.getString(ctx, "path"))))
                        .then(Commands.argument("form", new ExportFormArgumentType())
                                .suggests(ExportCommandCore::suggestForms)
                                .executes(ctx -> execute(ctx,
                                        ctx.getArgument("form", ExportKind.class), null))
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .executes(ctx -> execute(ctx,
                                                ctx.getArgument("form", ExportKind.class),
                                                StringArgumentType.getString(ctx, "path")))))));
    }

    /**
     * 统一命令入口：{@code form != null} → 既有 hub 分支（{@code export hub ...} markers 逐字符不变）；
     * {@code form == null} → 既有 datapack 分支（{@code export cmd ...} markers 逐字符不变）。form 判定
     * 在 parse 期由 {@link ExportFormArgumentType} 完成——path 分支的字符串永不重解释为 form。
     * <p>
     * The unified command entry: {@code form != null} → the existing hub branch (the
     * {@code export hub ...} markers character-for-character unchanged); {@code form == null} → the
     * existing datapack branch (the {@code export cmd ...} markers unchanged). Form-ness is decided at
     * parse time by {@link ExportFormArgumentType} — the path branch's string is never re-interpreted
     * as a form.
     *
     * @param ctx     命令上下文 / the command context.
     * @param form    parse 期命中的 form（null = datapack 分支）/ the form hit at parse time (null = the
     *                datapack branch).
     * @param pathArg 目标目录（null/空白 → {@code subterra-export}）/ target directory (null/blank →
     *                {@code subterra-export}).
     * @return 1 = 成功 / 0 = 失败。1 on success, 0 on failure.
     */
    public static int execute(CommandContext<CommandSourceStack> ctx, ExportKind form, String pathArg) {
        if (form != null) {
            return runHubCommand(ctx, form.form(), pathArg);
        }
        return runDatapackCommand(ctx, pathArg);
    }

    // ---------- datapack branch (p.2.2.7, moved from DatapackExportCommand, markers verbatim) ----------

    /** datapack 分支命令包装层（原 DatapackExportCommand.runExport，行为与反馈逐字符不变）。 */
    private static int runDatapackCommand(CommandContext<CommandSourceStack> ctx, String pathArg) {
        DatapackRegistrar reg = DatapackRuntime.activeRegistrar();
        if (reg == null) {
            ctx.getSource().sendFailure(Component.literal("datapack registrar not active"));
            return 0;
        }
        String dir = pathArg != null && !pathArg.isBlank()
                ? pathArg
                : DEFAULT_DIR;
        boolean ok = exportFrom(reg, dir);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal("subterra export wrote "
                    + Path.of(dir).toAbsolutePath().normalize()), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("subterra export failed (see log markers)"));
        }
        return ok ? 1 : 0;
    }

    /**
     * datapack 全量导出共享核心（p.2.2.7，markers 逐字符不变）：对每个已装载 pack 写内容级
     * export-archive td 文档（{@code <pack-name>.td}，目录 {@code pathArg}，缺省
     * {@code subterra-export}）并立即做 export ∘ rehydrate 字节恒等校验；每 pack 一行
     * {@code export cmd <pack> ok/mismatch (... rehydrate=ok/mismatch)}，随后汇总
     * {@code export cmd ok (packs=N, bytes=M)} 或 {@code export cmd mismatch}。命令分支与启动钩子
     * （{@code subterra.probe.export} 门控，现居 ExportRuntime）共享。
     * <p>
     * The shared datapack all-packs export core (p.2.2.7, markers character-for-character unchanged):
     * for every loaded pack writes a content-level export-archive td document
     * ({@code <pack-name>.td}, dir {@code pathArg}, default {@code subterra-export}) and immediately
     * asserts export ∘ rehydrate byte identity; one {@code export cmd <pack> ok/mismatch (... rehydrate=
     * ok/mismatch)} line per pack, then the aggregate {@code export cmd ok (packs=N, bytes=M)} or
     * {@code export cmd mismatch}. Shared by the command branch and the startup hook (the
     * {@code subterra.probe.export} gate, now in ExportRuntime).
     *
     * @param reg    活跃 datapack 注册器 / the active datapack registrar.
     * @param pathArg 目标目录（null/空白 → {@code subterra-export}）/ target directory (null/blank →
     *                {@code subterra-export}).
     * @return {@code true} 若每个 pack 导出且回水化逐字节恒等 / {@code true} if every pack exported and
     *         rehydrated byte-identical.
     */
    public static boolean exportFrom(DatapackRegistrar reg, String pathArg) {
        Path dir = (pathArg == null || pathArg.isBlank()
                ? Path.of(DEFAULT_DIR)
                : Path.of(pathArg)).toAbsolutePath().normalize();

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("{} export cmd mismatch (dir create failed): {}",
                    DatapackRegistrar.MARKER, e.toString());
            return false;
        }

        int totalPacks = 0;
        long totalBytes = 0L;
        boolean failed = false;
        for (Datapack pack : reg.packs()) {
            String doc;
            try {
                doc = DatapackExportArchive.export(pack);
            } catch (RuntimeException e) {
                LOGGER.error("{} export cmd mismatch (export failed): {}",
                        DatapackRegistrar.MARKER, e.toString());
                failed = true;
                continue;
            }
            String reExported;
            boolean identity;
            try {
                reExported = DatapackExportArchive.export(DatapackExportArchive.rehydrate(doc));
                identity = doc.equals(reExported);
            } catch (RuntimeException e) {
                LOGGER.error("{} export cmd {} mismatch (rehydrate failed): {}",
                        DatapackRegistrar.MARKER, pack.name(), e.toString());
                identity = false;
            }
            if (!identity) {
                failed = true;
            }
            try {
                Files.writeString(dir.resolve(safeName(pack.name()) + ".td"), doc);
            } catch (IOException e) {
                LOGGER.error("{} export cmd mismatch (write failed): {}",
                        DatapackRegistrar.MARKER, e.toString());
                failed = true;
                continue;
            }
            totalPacks++;
            totalBytes += (long) doc.getBytes(StandardCharsets.UTF_8).length;
            LOGGER.info("{} export cmd {} {} (entries={}, rehydrate={})",
                    DatapackRegistrar.MARKER, pack.name(), identity ? "ok" : "mismatch",
                    pack.entries().size(), identity ? "ok" : "mismatch");
        }

        if (failed) {
            LOGGER.error("{} export cmd mismatch", DatapackRegistrar.MARKER);
            return false;
        }
        LOGGER.info("{} export cmd ok (packs={}, bytes={})",
                DatapackRegistrar.MARKER, totalPacks, totalBytes);
        return true;
    }

    // ---------- hub branch (p.2.18.5, moved from ExportHubCommand, markers verbatim) ----------

    /** hub 分支命令包装层（原 ExportHubCommand.runFormCommand，行为与反馈逐字符不变）。 */
    private static int runHubCommand(CommandContext<CommandSourceStack> ctx, String form, String pathArg) {
        List<String> markers = exportForm(form, pathArg);
        boolean ok = true;
        StringBuilder feedback = new StringBuilder();
        for (String m : markers) {
            if (m.contains(" mismatch")) {
                ok = false;
                LOGGER.error("{}", m);
            } else {
                LOGGER.info("{}", m);
            }
            if (feedback.length() > 0) {
                feedback.append(" / ");
            }
            feedback.append(m);
        }
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal(feedback.toString()), false);
        } else {
            ctx.getSource().sendFailure(Component.literal(feedback.toString()));
        }
        return ok ? 1 : 0;
    }

    /**
     * hub 共享确定性核心（单 form，p.2.18.5，markers 逐字符不变）：对给定 form 从 ExportHub 取
     * producer（缺则按源物幂等注册），产出 td/zd、做双格式回水化恒等校验、写
     * {@code <path>/<form>.td} 与 {@code <form>.zd}，返回确定性 marker 行（该 form 一行 + 汇总一行）。
     * 命令分支与启动钩子共享，输出逐字符一致。
     * <p>
     * The shared deterministic hub core (single form, p.2.18.5, markers character-for-character
     * unchanged): resolves the producer for the given form from ExportHub (registering it idempotently
     * when absent and a source exists), renders td/zd, asserts dual-format rehydrate identity, writes
     * {@code <path>/<form>.td} and {@code <form>.zd}, and returns the deterministic marker lines (one
     * per form + the aggregate). Shared by the command branch and the startup hook, so their output is
     * character-for-character identical.
     *
     * @param form    form 名（七 form 之一）/ the form name (one of the seven forms).
     * @param pathArg 目标目录（null/空白 → {@code subterra-export}）/ target directory (null/blank →
     *                {@code subterra-export}).
     * @return 确定性 marker 行 / the deterministic marker lines.
     */
    public static List<String> exportForm(String form, String pathArg) {
        List<String> out = new ArrayList<>();
        ExportKind kind = matchForm(form);
        if (kind == null) {
            out.add(HUB_MARKER + " export hub " + form + " mismatch (unknown form)");
            out.add(HUB_MARKER + " export hub mismatch");
            return out;
        }
        String line = exportFormInternal(kind, pathArg);
        out.add(line);
        out.add(line.contains(" ok (")
                ? HUB_MARKER + " export hub ok (forms=1)"
                : HUB_MARKER + " export hub mismatch");
        return out;
    }

    /**
     * hub 共享确定性核心（全部七 form，按 {@link ExportKind} 枚举序，p.2.18.5，markers 逐字符不变）：
     * 逐 form 产出/校验/写档打 marker，最后汇总 {@code export hub ok (forms=N)}（N = ok 的 form 数；
     * world/save 无源物 → skip 不计入，不判失败）或 {@code export hub mismatch}。启动钩子
     * （{@code subterra.probe.exportHub} 门控，现居 ExportRuntime）与命令的七 form 全跑共用。
     * <p>
     * The shared deterministic hub core (all seven forms, in {@link ExportKind} enum order, p.2.18.5,
     * markers character-for-character unchanged): per form render/verify/write with a marker line, then
     * the aggregate {@code export hub ok (forms=N)} (N = number of ok forms; the no-source world/save
     * skips do not count and are not failures) or {@code export hub mismatch}. Shared by the startup
     * hook (the {@code subterra.probe.exportHub} gate, now in ExportRuntime) and any all-forms command
     * path.
     *
     * @param pathArg 目标目录（null/空白 → {@code subterra-export}）/ target directory (null/blank →
     *                {@code subterra-export}).
     * @return 确定性 marker 行 / the deterministic marker lines.
     */
    public static List<String> exportAllForms(String pathArg) {
        List<String> out = new ArrayList<>();
        int ok = 0;
        boolean failed = false;
        for (ExportKind kind : ExportKind.values()) {
            String line = exportFormInternal(kind, pathArg);
            out.add(line);
            if (line.contains(" ok (")) {
                ok++;
            } else if (line.contains(" mismatch")) {
                failed = true;
            }
        }
        out.add(failed
                ? HUB_MARKER + " export hub mismatch"
                : HUB_MARKER + " export hub ok (forms=" + ok + ")");
        return out;
    }

    /** Runs the per-form export + rehydrate-identity + write, returning its single marker line. */
    private static String exportFormInternal(ExportKind kind, String pathArg) {
        String form = kind.form();
        Path dir = (pathArg == null || pathArg.isBlank()
                ? Path.of(DEFAULT_DIR)
                : Path.of(pathArg)).toAbsolutePath().normalize();

        ExportProducer producer = producerFor(kind);
        if (producer == null) {
            return HUB_MARKER + " export hub " + form + " skip (" + skipReason(kind) + ")";
        }

        String td;
        byte[] zd;
        try {
            td = producer.exportTd();
            zd = producer.exportZd();
        } catch (RuntimeException e) {
            return HUB_MARKER + " export hub " + form + " mismatch (export failed: " + e + ")";
        }

        boolean identity;
        try {
            identity = td.equals(producer.rehydrateTd(td))
                    && Arrays.equals(zd, producer.rehydrateZd(zd));
        } catch (RuntimeException e) {
            return HUB_MARKER + " export hub " + form + " mismatch (rehydrate failed: " + e + ")";
        }
        if (!identity) {
            return HUB_MARKER + " export hub " + form + " mismatch (rehydrate identity)";
        }

        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(form + ".td"), td);
            Files.write(dir.resolve(form + ".zd"), zd);
        } catch (IOException e) {
            return HUB_MARKER + " export hub " + form + " mismatch (write failed: " + e + ")";
        }
        return HUB_MARKER + " export hub " + form + " ok (tdBytes="
                + td.getBytes(StandardCharsets.UTF_8).length + ", zdBytes=" + zd.length
                + ", rehydrate=ok)";
    }

    /**
     * 取（必要时幂等注册）某种类的功能性 producer；无源物 form（world/save）返回 {@code null} →
     * 调用方打 skip。注册是一次性的（ExportHub 静态生命周期）：样例 form 用固定常量源物，datapack
     * form 用注册时的活跃 registrar 首个 pack。
     * <p>
     * Resolves (registering idempotently when absent) the functional producer of a kind; forms
     * without a source (world/save) return {@code null} → the caller emits skip. Registration is
     * one-shot (ExportHub's static lifecycle): the sample forms use the fixed constant sources, the
     * datapack form uses the first pack of the registrar active at registration time.
     */
    private static ExportProducer producerFor(ExportKind kind) {
        ExportProducer existing = ExportHub.producer(kind);
        if (existing != null) {
            return existing;
        }
        ExportProducer fresh = buildProducer(kind);
        if (fresh == null) {
            return null;
        }
        ExportHub.register(kind, fresh);
        return fresh;
    }

    /** Builds a fresh producer for a kind from the deterministic sources ({@code null} = no source). */
    private static ExportProducer buildProducer(ExportKind kind) {
        return switch (kind) {
            case WORLD_PACK, SAVE -> null; // no active SaveContainer shell in this milestone
            case DATAPACK -> {
                DatapackRegistrar reg = DatapackRuntime.activeRegistrar();
                yield reg != null && !reg.packs().isEmpty()
                        ? new DatapackArchiveProducer(reg.packs().get(0))
                        : null;
            }
            case LANGUAGE_KEYS -> new LanguageKeysProducer(SAMPLE_KEYS);
            case CONFIG -> new ConfigProducer(SAMPLE_CONFIG_GLOBAL, SAMPLE_CONFIG_OVERRIDES);
            case REGISTRIES -> new RegistriesProducer(SAMPLE_REGISTRIES);
            case MIGRATE_MAPS -> new MigrateMapsProducer(SAMPLE_MIGRATE_MAPS);
        };
    }

    /** The deterministic skip reason of a kind (only reached when its producer is {@code null}). */
    private static String skipReason(ExportKind kind) {
        return switch (kind) {
            case WORLD_PACK, SAVE -> "no save source";
            case DATAPACK -> "no active datapack registrar";
            default -> "no producer";
        };
    }

    /** Matches a form name against the seven {@link ExportKind} forms ({@code null} = unknown). */
    private static ExportKind matchForm(String form) {
        if (form == null) {
            return null;
        }
        for (ExportKind k : ExportKind.values()) {
            if (k.form().equals(form)) {
                return k;
            }
        }
        return null;
    }

    /** Suggests the seven registered forms. */
    private static CompletableFuture<Suggestions> suggestForms(CommandContext<CommandSourceStack> ctx,
                                                               SuggestionsBuilder builder) {
        for (ExportKind k : ExportKind.values()) {
            builder.suggest(k.form());
        }
        return builder.buildFuture();
    }

    /** Replaces every char outside {@code [A-Za-z0-9._-]} with {@code _} so the file name stays filesystem-safe. */
    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * form 感知参数类型（保留自 p.2.18.5）：parse 期只接受七 form 之一；未知串抛
     * {@link CommandSyntaxException}，使该分支从候选退出、既有 {@code export [path]} 分支独占解析
     * （保持既有行为与 markers 不变）。
     * <p>
     * Form-aware argument type (kept from p.2.18.5): accepts only one of the seven forms at parse
     * time; an unknown string throws {@link CommandSyntaxException}, dropping this branch from the
     * candidates so the existing {@code export [path]} branch parses alone (existing behavior and
     * markers unchanged).
     */
    public static final class ExportFormArgumentType implements ArgumentType<ExportKind> {

        @Override
        public ExportKind parse(StringReader reader) throws CommandSyntaxException {
            String s = reader.readUnquotedString();
            ExportKind kind = matchForm(s);
            if (kind == null) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                        .dispatcherUnknownArgument()
                        .createWithContext(reader);
            }
            return kind;
        }
    }
}
