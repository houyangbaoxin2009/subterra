package io.toterra.subterra.runtime.rules;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.toterra.subterra.engine.config.rules.RuleDocument;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * p.2.17.4 — {@code /subterra rule} — 配置规则壳命令（op 权限 2）：三个子命令共享同一确定性核心
 * {@link #runVerb(RuleStore, String, String, String)}：
 * <ul>
 *   <li>{@code view} —— 打印有效规则 render（global putAll overrides 后按 key 字典序）与各档摘要；</li>
 *   <li>{@code set <key> <value>} —— 设置全局档值（经 {@link RuleDocument#toTd} 生成规范单行文档后
 *   走 {@link RuleStore#loadGlobalText} 同一装载通道，与引擎 putIfAbsent 语义一致：同 key 重复装载以
 *   首次为准）；</li>
 *   <li>{@code override <key> <value>} —— 设存档覆盖档值（{@link RuleStore#loadOverridesText}，双层
 *   解析中胜出）。</li>
 * </ul>
 * 确定性 marker（命令与启动钩子同输出）：成功形如 {@code [Subterra rule] view ok (rules=N,
 * render=…, global=M, overrides=K)} / {@code set ok (key=…, value=…)} / {@code override ok (…)}；
 * 未知键 / 类型违约 / 缺参 → {@code [Subterra rule] <verb> mismatch (reason)}。命令包装层把
 * markers 转 sendSuccess/sendFailure 并把同串打到 logger INFO/ERROR；启动钩子（
 * {@code subterra.probe.rule} 门控）直接复用同一核心逐行 INFO。
 * <p>
 * 注册：在 {@code subterra} 根字面量（已由 DatapackExportCommand 注册）下追加 {@code rule} 分支——
 * Brigadier 允许同一根多次 register 合并 children，两命令并存。
 * <p>
 * p.2.17.4 — {@code /subterra rule} — the config-rules shell command (op level 2): three
 * sub-commands share one deterministic core {@link #runVerb(RuleStore, String, String, String)}:
 * <ul>
 *   <li>{@code view} — prints the effective rules render (global putAll overrides, keys in
 *   lexicographic order) plus per-layer summaries;</li>
 *   <li>{@code set <key> <value>} — sets a global-layer value (a canonical single-rule document via
 *   {@link RuleDocument#toTd} goes through the same {@link RuleStore#loadGlobalText} load channel,
 *   consistent with the engine putIfAbsent semantics: a reloaded key keeps its first value);</li>
 *   <li>{@code override <key> <value>} — sets a save-overrides-layer value (
 *   {@link RuleStore#loadOverridesText}; wins in the two-tier resolve).</li>
 * </ul>
 * Deterministic markers (identical from the command and the startup hook): success lines like
 * {@code [Subterra rule] view ok (rules=N, render=…, global=M, overrides=K)} /
 * {@code set ok (key=…, value=…)} / {@code override ok (…)}; an unknown key / type violation /
 * missing argument → {@code [Subterra rule] <verb> mismatch (reason)}. The command wrapper maps the
 * markers to sendSuccess/sendFailure and logs the same strings at INFO/ERROR; the startup hook
 * (gated by {@code subterra.probe.rule}) reuses the same core verbatim, INFO per line.
 * <p>
 * Registration: the {@code rule} branch is appended under the {@code subterra} root literal
 * (already registered by DatapackExportCommand) — Brigadier merges children of a re-registered
 * root, so both commands coexist.
 */
public final class RuntimeRuleCommand {

    /** 确定性 marker 前缀。The deterministic marker prefix. */
    public static final String MARKER = "[Subterra rule]";

    private RuntimeRuleCommand() {
    }

    /**
     * 在 {@code subterra} 根下注册 {@code rule} 分支（requires op level 2）；与
     * {@code DatapackExportCommand} 的同根注册合并并存。
     * Registers the {@code rule} branch under the {@code subterra} root (requires op level 2);
     * merges with the same root registered by {@code DatapackExportCommand}.
     *
     * @param event 命令注册事件 / the command registration event.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("subterra")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("rule")
                        .then(Commands.literal("view")
                                .executes(ctx -> runVerbCommand(ctx, "view", null, null)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .then(Commands.argument("value", StringArgumentType.string())
                                                .executes(ctx -> runVerbCommand(ctx, "set",
                                                        StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value"))))))
                        .then(Commands.literal("override")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .then(Commands.argument("value", StringArgumentType.string())
                                                .executes(ctx -> runVerbCommand(ctx, "override",
                                                        StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value"))))))));
    }

    private static int runVerbCommand(CommandContext<CommandSourceStack> ctx, String verb, String key, String value) {
        RuleStore store = RulesRuntime.activeStore();
        if (store == null) {
            ctx.getSource().sendFailure(Component.literal("rules store not active"));
            return 0;
        }
        List<String> markers = runVerb(store, verb, key, value);
        boolean ok = true;
        StringBuilder feedback = new StringBuilder();
        for (String m : markers) {
            if (m.contains(" mismatch")) {
                ok = false;
                RulesRuntime.LOGGER.error("{}", m);
            } else {
                RulesRuntime.LOGGER.info("{}", m);
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
     * 共享确定性核心：执行单个 verb 并返回确定性 marker 行（不触网、不读时、无随机）。view 忽略
     * key/value；set/override 先做写前校验（缺参、未知键、类型违约 → mismatch marker，不写档），
     * 通过后以 {@link RuleDocument#toTd} 生成规范单行文档走 {@code loadGlobalText/loadOverridesText}
     * 装载。命令层与启动钩子（RulesRuntime 的 {@code subterra.probe.rule} 门控）共享此核心，输出
     * 逐字符一致。
     * <p>
     * The shared deterministic core: executes one verb and returns deterministic marker lines (no
     * network, no clock reads, no randomness). view ignores key/value; set/override run a write
     * pre-check first (missing argument / unknown key / type violation → a mismatch marker, no
     * layer write), then load a canonical single-rule document (via {@link RuleDocument#toTd})
     * through {@code loadGlobalText/loadOverridesText}. The command layer and the startup hook
     * (RulesRuntime's {@code subterra.probe.rule} gate) share this core, so their output is
     * character-for-character identical.
     *
     * @param store 目标规则存储 / the target rule store.
     * @param verb  verb（view/set/override）/ the verb (view/set/override).
     * @param key   规则键（view 传 null）/ the rule key (null for view).
     * @param value 值（view 传 null）/ the value (null for view).
     * @return 确定性 marker 行 / the deterministic marker lines.
     */
    public static List<String> runVerb(RuleStore store, String verb, String key, String value) {
        List<String> out = new ArrayList<>();
        switch (verb) {
            case "view" -> {
                Map<String, String> effective = store.resolve();
                out.add(MARKER + " view ok (rules=" + effective.size()
                        + ", render=" + store.render()
                        + ", global=" + store.global().size()
                        + ", overrides=" + store.overrides().size() + ")");
            }
            case "set" -> {
                String reason = writeViolation(store, key, value);
                if (reason != null) {
                    out.add(MARKER + " set mismatch (" + reason + ")");
                } else {
                    store.loadGlobalText(RuleDocument.toTd(store.specs(), Map.of(key, value)));
                    out.add(MARKER + " set ok (key=" + key + ", value=" + value + ")");
                }
            }
            case "override" -> {
                String reason = writeViolation(store, key, value);
                if (reason != null) {
                    out.add(MARKER + " override mismatch (" + reason + ")");
                } else {
                    store.loadOverridesText(RuleDocument.toTd(store.specs(), Map.of(key, value)));
                    out.add(MARKER + " override ok (key=" + key + ", value=" + value + ")");
                }
            }
            default -> out.add(MARKER + " " + verb + " mismatch (unknown verb)");
        }
        return out;
    }

    /**
     * 写前校验：返回违约原因（null = 可写）。缺 key → {@code key required}；缺 value → {@code value
     * required}；键不在 specs → {@code unknown key}；值未通过 spec 类型 → 类型违约。
     * Write pre-check: returns the violation reason (null = writable). A missing key → {@code key
     * required}; a missing value → {@code value required}; a key absent from the specs → {@code
     * unknown key}; a value failing the spec type → a type violation.
     */
    private static String writeViolation(RuleStore store, String key, String value) {
        if (key == null || key.isBlank()) {
            return "key required";
        }
        if (value == null) {
            return "value required for " + key;
        }
        RuleSpec spec = null;
        for (RuleSpec s : store.specs()) {
            if (s.key().form().equals(key)) {
                spec = s;
                break;
            }
        }
        if (spec == null) {
            return "unknown key: " + key;
        }
        if (!spec.type().accepts(value)) {
            return "value \"" + value + "\" does not match type " + spec.type().form();
        }
        return null;
    }
}
