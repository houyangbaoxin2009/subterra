package io.toterra.subterra.runtime.export;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
 * p.2.18.5 — {@code /subterra export <form> [<path>]} — 导出中枢（ExportHub）确定性接线：按 form
 * 从 {@link ExportHub} 取功能性 {@link ExportProducer}，把同一源物产出为 td 文档（{@code <path>/
 * <form>.td}，缺省目录 {@code subterra-export}）与 zd v2 二进制变体（{@code <form>.zd}），并立即
 * 做回水化恒等校验——{@code td.equals(rehydrateTd(td))} 与 {@code Arrays.equals(zd, rehydrateZd(zd))}
 * 须逐字节成立。七个 form（world / save / datapack / language_keys / config / registries /
 * migrate_maps）共享同一确定性核心 {@link #exportForm(String, String)} /
 * {@link #exportAllForms(String)}，命令层与启动钩子（ExportHubRuntime 的
 * {@code subterra.probe.exportHub} 门控）同输出。
 *
 * <p>源物供给：datapack form 用已装载 {@link DatapackRegistrar}（固定加载序首个 pack 为代表源物，
 * 完整多 pack 导出仍走既有 {@code /subterra export [path]} → DatapackExportCommand.exportFrom）；
 * world / save form 本里程碑无活跃 SaveContainer 壳 → 确定性 {@code skip (no save source)}，不写
 * 文件、不判失败；language_keys / config / registries / migrate_maps 用下列硬编码确定性样例源物
 * （固定内容、固定序，producer 内部字典序快照）：
 * <ul>
 *   <li>{@code language_keys}：{@code item.toterra.crystal=Crystal}、{@code item.toterra.dust=Dust}、
 *       {@code block.toterra.moss=Moss}；</li>
 *   <li>{@code config}：global 档 {@code subterra.worldgen.seed_offset=42}、
 *       {@code subterra.entity.activation.range=32.0}，overrides 档 {@code seed_offset=7}；</li>
 *   <li>{@code registries}：{@code subterra:item=[toterra:crystal, toterra:dust]}、
 *       {@code subterra:block=[toterra:moss]}；</li>
 *   <li>{@code migrate_maps}：{@code subterra.old.seed→subterra.worldgen.seed_offset}、
 *       {@code subterra.old.range→subterra.entity.activation.range}。</li>
 * </ul>
 *
 * <p>确定性 marker（命令与启动钩子同输出，禁 sleep、禁时序、禁 stdin）：每 form 一行
 * {@code [Subterra export] export hub <form> ok (tdBytes=.., zdBytes=.., rehydrate=ok)} /
 * {@code ... mismatch (reason)} / {@code ... skip (reason)}；随后汇总 {@code export hub ok (forms=N)}
 * （N = 成功产出的 form 数）或 {@code export hub mismatch}。命令包装层把 markers 转
 * sendSuccess/sendFailure 并把同串打到 logger INFO/ERROR。
 *
 * <p>Brigadier 接线：与 DatapackExportCommand 的 {@code /subterra export [path]} 同根（subterra →
 * export literal）合并并存。form 参数用 form 感知的 {@link ExportFormArgumentType}——parse 期只接受
 * 七 form 之一（未知串抛异常让既有 path 分支独占解析），因此 {@code export <任意非form路径>} 仍走
 * 既有 exportFrom（markers 逐字符不变），{@code export <form>} 与 {@code export <form> <path>} 走本
 * 核心（单参 form 与既有 path 单参同分时，后注册的 form 分支按 Brigadier getBestMatch 的
 * {@code >=} 规则胜出）。
 *
 * <p>p.2.18.5 — {@code /subterra export <form> [<path>]} — deterministic wiring of the export hub:
 * resolves the functional {@link ExportProducer} per form from {@link ExportHub}, renders the same
 * source as a td document ({@code <path>/<form>.td}, default dir {@code subterra-export}) and its zd
 * v2 binary variant ({@code <form>.zd}), then immediately asserts rehydrate identity —
 * {@code td.equals(rehydrateTd(td))} and {@code Arrays.equals(zd, rehydrateZd(zd))} must hold
 * byte-for-byte. All seven forms (world / save / datapack / language_keys / config / registries /
 * migrate_maps) share the one deterministic core {@link #exportForm(String, String)} /
 * {@link #exportAllForms(String)}; the command layer and the startup hook (ExportHubRuntime's
 * {@code subterra.probe.exportHub} gate) emit identical output.
 *
 * <p>Source supply: the datapack form uses the loaded {@link DatapackRegistrar} (the first pack in
 * fixed load order is the representative source; full multi-pack export still goes through the
 * existing {@code /subterra export [path]} → DatapackExportCommand.exportFrom); the world / save
 * forms have no active SaveContainer shell this milestone → deterministic {@code skip (no save
 * source)}, no file written, not a failure; language_keys / config / registries / migrate_maps use
 * the hard-coded deterministic sample sources below (fixed content, fixed order; the producers
 * snapshot in lexicographic order internally):
 * <ul>
 *   <li>{@code language_keys}: {@code item.toterra.crystal=Crystal}, {@code item.toterra.dust=Dust},
 *       {@code block.toterra.moss=Moss};</li>
 *   <li>{@code config}: global layer {@code subterra.worldgen.seed_offset=42},
 *       {@code subterra.entity.activation.range=32.0}, overrides layer {@code seed_offset=7};</li>
 *   <li>{@code registries}: {@code subterra:item=[toterra:crystal, toterra:dust]},
 *       {@code subterra:block=[toterra:moss]};</li>
 *   <li>{@code migrate_maps}: {@code subterra.old.seed→subterra.worldgen.seed_offset},
 *       {@code subterra.old.range→subterra.entity.activation.range}.</li>
 * </ul>
 *
 * <p>Deterministic markers (identical from the command and the startup hook; no sleeps, no timing,
 * no stdin): one line per form {@code [Subterra export] export hub <form> ok (tdBytes=..,
 * zdBytes=.., rehydrate=ok)} / {@code ... mismatch (reason)} / {@code ... skip (reason)}; then the
 * aggregate {@code export hub ok (forms=N)} (N = number of successfully produced forms) or
 * {@code export hub mismatch}. The command wrapper maps the markers to sendSuccess/sendFailure and
 * logs the same strings at INFO/ERROR.
 *
 * <p>Brigadier wiring: coexists with DatapackExportCommand's {@code /subterra export [path]} under
 * the same root (subterra → export literal merged). The form argument uses the form-aware
 * {@link ExportFormArgumentType} — it accepts only one of the seven forms at parse time (an unknown
 * string throws, leaving the existing path branch to parse alone), so {@code export <any-non-form
 * path>} still runs the existing exportFrom (markers character-for-character unchanged), while
 * {@code export <form>} and {@code export <form> <path>} run this core (when the single-argument
 * form ties with the existing single-argument path, the later-registered form branch wins per
 * Brigadier getBestMatch's {@code >=} rule).
 */
public final class ExportHubCommand {

    /** 确定性 marker 前缀。The deterministic marker prefix. */
    public static final String MARKER = "[Subterra export]";

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

    private ExportHubCommand() {
    }

    /**
     * 在 {@code subterra} 根下追加 {@code export <form> [<path>]} 分支（requires op level 2）；与
     * DatapackExportCommand 的同根注册合并并存（本分支不写无参 executes，故既有
     * {@code export [path]} 的 executes 不被覆盖）。
     * Registers the {@code export <form> [<path>]} branch under the {@code subterra} root (requires
     * op level 2); merged with the same root registered by DatapackExportCommand (this branch never
     * attaches a no-argument executes, so the existing {@code export [path]} executes survives).
     *
     * @param event 命令注册事件 / the command registration event.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("subterra")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("export")
                        .then(Commands.argument("form", new ExportFormArgumentType())
                                .suggests(ExportHubCommand::suggestForms)
                                .executes(ctx -> runFormCommand(ctx,
                                        ctx.getArgument("form", ExportKind.class), null))
                                .then(Commands.argument("path", com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(ctx -> runFormCommand(ctx,
                                                ctx.getArgument("form", ExportKind.class),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "path")))))));
    }

    /** Suggests the seven registered forms. */
    private static CompletableFuture<Suggestions> suggestForms(CommandContext<CommandSourceStack> ctx,
                                                               SuggestionsBuilder builder) {
        for (ExportKind k : ExportKind.values()) {
            builder.suggest(k.form());
        }
        return builder.buildFuture();
    }

    private static int runFormCommand(CommandContext<CommandSourceStack> ctx, ExportKind kind, String pathArg) {
        List<String> markers = exportForm(kind.form(), pathArg);
        boolean ok = true;
        StringBuilder feedback = new StringBuilder();
        for (String m : markers) {
            if (m.contains(" mismatch")) {
                ok = false;
                ExportHubRuntime.LOGGER.error("{}", m);
            } else {
                ExportHubRuntime.LOGGER.info("{}", m);
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
     * 共享确定性核心（单 form）：对给定 form 从 ExportHub 取 producer（缺则按源物幂等注册），产出
     * td/zd、做回水化恒等校验、写 {@code <path>/<form>.td} 与 {@code <form>.zd}，返回确定性 marker
     * 行（该 form 一行 + 汇总一行）。命令层与启动钩子共享，输出逐字符一致。
     * <p>
     * The shared deterministic core (single form): resolves the producer for the given form from
     * ExportHub (registering it idempotently when absent and a source exists), renders td/zd,
     * asserts rehydrate identity, writes {@code <path>/<form>.td} and {@code <form>.zd}, and returns
     * the deterministic marker lines (one per form + the aggregate). The command layer and the
     * startup hook share this core, so their output is character-for-character identical.
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
            out.add(MARKER + " export hub " + form + " mismatch (unknown form)");
            out.add(MARKER + " export hub mismatch");
            return out;
        }
        String line = exportFormInternal(kind, pathArg);
        out.add(line);
        out.add(line.contains(" ok (")
                ? MARKER + " export hub ok (forms=1)"
                : MARKER + " export hub mismatch");
        return out;
    }

    /**
     * 共享确定性核心（全部七 form，按 {@link ExportKind} 枚举序）：逐 form 产出/校验/写档打 marker，
     * 最后汇总 {@code export hub ok (forms=N)}（N = ok 的 form 数；world/save 无源物 → skip 不计入，
     * 不判失败）或 {@code export hub mismatch}。启动钩子（{@code subterra.probe.exportHub} 门控）与
     * 命令的七 form 全跑共用。
     * <p>
     * The shared deterministic core (all seven forms, in {@link ExportKind} enum order): per form
     * render/verify/write with a marker line, then the aggregate {@code export hub ok (forms=N)}
     * (N = number of ok forms; the no-source world/save skips do not count and are not failures) or
     * {@code export hub mismatch}. Shared by the startup hook ({@code subterra.probe.exportHub}
     * gate) and any all-forms command path.
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
                ? MARKER + " export hub mismatch"
                : MARKER + " export hub ok (forms=" + ok + ")");
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
            return MARKER + " export hub " + form + " skip (" + skipReason(kind) + ")";
        }

        String td;
        byte[] zd;
        try {
            td = producer.exportTd();
            zd = producer.exportZd();
        } catch (RuntimeException e) {
            return MARKER + " export hub " + form + " mismatch (export failed: " + e + ")";
        }

        boolean identity;
        try {
            identity = td.equals(producer.rehydrateTd(td))
                    && Arrays.equals(zd, producer.rehydrateZd(zd));
        } catch (RuntimeException e) {
            return MARKER + " export hub " + form + " mismatch (rehydrate failed: " + e + ")";
        }
        if (!identity) {
            return MARKER + " export hub " + form + " mismatch (rehydrate identity)";
        }

        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(form + ".td"), td);
            Files.write(dir.resolve(form + ".zd"), zd);
        } catch (IOException e) {
            return MARKER + " export hub " + form + " mismatch (write failed: " + e + ")";
        }
        return MARKER + " export hub " + form + " ok (tdBytes="
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

    /**
     * form 感知参数类型：parse 期只接受七 form 之一；未知串抛 {@link CommandSyntaxException}，使
     * 该分支从候选退出、既有 {@code export [path]} 分支独占解析（保持既有行为与 markers 不变）。
     * <p>
     * Form-aware argument type: accepts only one of the seven forms at parse time; an unknown string
     * throws {@link CommandSyntaxException}, dropping this branch from the candidates so the existing
     * {@code export [path]} branch parses alone (existing behavior and markers unchanged).
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
