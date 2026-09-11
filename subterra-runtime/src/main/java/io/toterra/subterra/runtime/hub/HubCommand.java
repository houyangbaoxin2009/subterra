package io.toterra.subterra.runtime.hub;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleViolationException;
import io.toterra.subterra.engine.hub.FormField;
import io.toterra.subterra.engine.hub.FormStructure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * p.2.23.3 — {@code /subterra hub} — 服务器端 op 配置命令（op 权限 2）：四个子命令共享同一确定性
 * 核心 {@link #runVerb(HubRuntime, String, String, String)}（承接 p.2.17.4 {@code /subterra rule}
 * 的「共享确定性核心 runVerb」范式，marker 前缀为 {@code [Subterra hub]}）：
 * <ul>
 *   <li>{@code view} —— 打印有效规则 render（经 {@code HubConfigEditor.resolve/render}，双层解析
 *       语义与 {@code RuleStore} 逐条一致）；</li>
 *   <li>{@code form <id>} —— 打印固定样例表单结构的确定性 marker（p.2.23.1 {@code HubFormGen}
 *       由定型 td schema 生成：字段数 / 根 kind / 根 widget / 固定文档序字段名列表）；</li>
 *   <li>{@code set <key> <value>} —— 经 {@code HubConfigEditor.editGlobal} 做全局档 key 级覆盖
 *       写（先写前校验：缺参 / 未知键 / 类型违约 → mismatch marker，不写档）；</li>
 *   <li>{@code override <key> <value>} —— 经 {@code HubConfigEditor.editOverrides} 做存档覆盖档
 *       key 级覆盖写（双层解析中胜出）。</li>
 * </ul>
 * 确定性 marker（命令与启动钩子同输出）：成功形如 {@code [Subterra hub] view ok (rules=N,
 * render=…)} / {@code form <id> ok (fields=N, root=<kind>/<widget>, names=…)} /
 * {@code set ok (key=…, value=…)} / {@code override ok (key=…, value=…)}；违约 / 未知表单 /
 * 缺参 → {@code [Subterra hub] <verb> mismatch (reason)}。命令包装层把 markers 转
 * sendSuccess/sendFailure 并把同串打到 logger INFO/ERROR；启动钩子（HubRuntime 的
 * {@code subterra.probe.hub} 门控）直接复用同一核心逐行 INFO。
 * <p>
 * 注册：在 {@code subterra} 根字面量（已由 DatapackExportCommand 注册）下追加 {@code hub} 分支——
 * Brigadier 允许同一根多次 register 合并 children，与 export / rule 命令并存。
 * <p>
 * p.2.23.3 — {@code /subterra hub} — the server-side op config command (op level 2): four
 * sub-commands share one deterministic core {@link #runVerb(HubRuntime, String, String, String)}
 * (carrying over the p.2.17.4 {@code /subterra rule} "shared deterministic runVerb core" pattern,
 * marker prefix {@code [Subterra hub]}):
 * <ul>
 *   <li>{@code view} — prints the effective rules render (via
 *       {@code HubConfigEditor.resolve/render}, two-tier resolve semantics item-identical to
 *       {@code RuleStore});</li>
 *   <li>{@code form <id>} — prints the deterministic marker of a fixed sample form structure
 *       (p.2.23.1 {@code HubFormGen} derived from a settled td schema: field count / root kind /
 *       root widget / the fixed document-order field-name list);</li>
 *   <li>{@code set <key> <value>} — a global-layer key-level overwrite via
 *       {@code HubConfigEditor.editGlobal} (write pre-check first: missing argument / unknown key /
 *       type violation → a mismatch marker, no layer write);</li>
 *   <li>{@code override <key> <value>} — a save-overrides-layer key-level overwrite via
 *       {@code HubConfigEditor.editOverrides} (wins in the two-tier resolve).</li>
 * </ul>
 * Deterministic markers (identical from the command and the startup hook): success lines like
 * {@code [Subterra hub] view ok (rules=N, render=…)} /
 * {@code form <id> ok (fields=N, root=<kind>/<widget>, names=…)} /
 * {@code set ok (key=…, value=…)} / {@code override ok (key=…, value=…)}; a violation / unknown
 * form / missing argument → {@code [Subterra hub] <verb> mismatch (reason)}. The command wrapper
 * maps the markers to sendSuccess/sendFailure and logs the same strings at INFO/ERROR; the startup
 * hook (HubRuntime's {@code subterra.probe.hub} gate) reuses the same core verbatim, INFO per line.
 * <p>
 * Registration: the {@code hub} branch is appended under the {@code subterra} root literal (already
 * registered by DatapackExportCommand) — Brigadier merges children of a re-registered root, so it
 * coexists with the export / rule commands.
 */
public final class HubCommand {

    /** 确定性 marker 前缀。The deterministic marker prefix. */
    public static final String MARKER = "[Subterra hub]";

    private HubCommand() {
    }

    /**
     * 在 {@code subterra} 根下注册 {@code hub} 分支（requires op level 2）；与
     * {@code DatapackExportCommand} 的同根注册合并并存（view / form / set / override 四子命令）。
     * Registers the {@code hub} branch under the {@code subterra} root (requires op level 2);
     * merges with the same root registered by {@code DatapackExportCommand} (the four
     * sub-commands view / form / set / override).
     *
     * @param event 命令注册事件 / the command registration event.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("subterra")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("hub")
                        .then(Commands.literal("view")
                                .executes(ctx -> runVerbCommand(ctx, "view", null, null)))
                        .then(Commands.literal("form")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> runVerbCommand(ctx, "form",
                                                StringArgumentType.getString(ctx, "id"), null))))
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
        HubRuntime store = HubRuntime.active();
        if (store == null) {
            ctx.getSource().sendFailure(Component.literal("hub runtime not active"));
            return 0;
        }
        List<String> markers = runVerb(store, verb, key, value);
        boolean ok = true;
        StringBuilder feedback = new StringBuilder();
        for (String m : markers) {
            if (m.contains(" mismatch")) {
                ok = false;
                HubRuntime.LOGGER.error("{}", m);
            } else {
                HubRuntime.LOGGER.info("{}", m);
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
     * key/value；form 以 key 为表单 id（未知 id → mismatch marker）；set/override 先做写前校验
     * （缺参、未知键、类型违约 → mismatch marker，不写档），通过后经
     * {@code HubConfigEditor.editGlobal/editOverrides} 双层 key 级覆盖写。命令层与启动钩子
     * （HubRuntime 的 {@code subterra.probe.hub} 门控）共享此核心，输出逐字符一致。
     * <p>
     * The shared deterministic core: executes one verb and returns deterministic marker lines (no
     * network, no clock reads, no randomness). view ignores key/value; form treats key as the form
     * id (an unknown id → a mismatch marker); set/override run a write pre-check first (missing
     * argument / unknown key / type violation → a mismatch marker, no layer write), then write by
     * key-level overwrite into the chosen tier via
     * {@code HubConfigEditor.editGlobal/editOverrides}. The command layer and the startup hook
     * (HubRuntime's {@code subterra.probe.hub} gate) share this core, so their output is
     * character-for-character identical.
     *
     * @param store 目标 hub runtime 状态 / the target hub runtime state.
     * @param verb  verb（view/form/set/override）/ the verb (view/form/set/override).
     * @param key   规则键或表单 id（view 传 null）/ the rule key or form id (null for view).
     * @param value 值（view/form 传 null）/ the value (null for view/form).
     * @return 确定性 marker 行 / the deterministic marker lines.
     */
    public static List<String> runVerb(HubRuntime store, String verb, String key, String value) {
        List<String> out = new ArrayList<>();
        switch (verb) {
            case "view" -> {
                Map<String, String> effective = store.editor().resolve();
                out.add(MARKER + " view ok (rules=" + effective.size()
                        + ", render=" + store.editor().render() + ")");
            }
            case "form" -> {
                FormStructure form = store.forms().get(key);
                if (form == null) {
                    out.add(MARKER + " form " + key + " mismatch (unknown form)");
                } else {
                    out.add(MARKER + " form " + key + " ok (fields=" + form.fields().size()
                            + ", root=" + form.rootKind() + "/" + form.rootWidget()
                            + ", names=" + names(form) + ")");
                }
            }
            case "set" -> out.add(edit(store, "set", key, value, true));
            case "override" -> out.add(edit(store, "override", key, value, false));
            default -> out.add(MARKER + " " + verb + " mismatch (unknown verb)");
        }
        return out;
    }

    /**
     * 双层写共享路径（set → 全局档，override → 存档覆盖档）：先写前校验（缺 key → {@code key
     * required}；缺 value → {@code value required for <key>}；键不在 specs → {@code unknown key}；
     * 值未通过 spec 类型 → 类型违约），通过后经 {@code HubConfigEditor.editGlobal/editOverrides}
     * key 级覆盖并入对应档（{@code revision} 自增 1，单调、禁时间戳）。校验层的确定性异常
     * （理论不可达，样例合法）转 mismatch marker，绝不打假 ok。
     * <p>
     * The shared two-tier write path (set → the global layer, override → the save-overrides layer):
     * a write pre-check first (missing key → {@code key required}; missing value → {@code value
     * required for <key>}; a key absent from the specs → {@code unknown key}; a value failing the
     * spec type → a type violation), then a key-level overwrite merge into the chosen tier via
     * {@code HubConfigEditor.editGlobal/editOverrides} ({@code revision} bumps by 1, monotonic, no
     * timestamps). A validation-layer deterministic exception (theoretically unreachable, the
     * samples are legal) maps to a mismatch marker — a false ok is never emitted.
     */
    private static String edit(HubRuntime store, String verb, String key, String value, boolean global) {
        if (key == null || key.isBlank()) {
            return MARKER + " " + verb + " mismatch (key required)";
        }
        if (value == null) {
            return MARKER + " " + verb + " mismatch (value required for " + key + ")";
        }
        String reason = writeViolation(store, key, value);
        if (reason != null) {
            return MARKER + " " + verb + " mismatch (" + reason + ")";
        }
        try {
            if (global) {
                store.editor().editGlobal(Map.of(key, value));
            } else {
                store.editor().editOverrides(Map.of(key, value));
            }
            return MARKER + " " + verb + " ok (key=" + key + ", value=" + value + ")";
        } catch (RuleViolationException e) {
            return MARKER + " " + verb + " mismatch (validation: " + e.getMessage() + ")";
        } catch (IllegalArgumentException e) {
            return MARKER + " " + verb + " mismatch (" + e.getMessage() + ")";
        }
    }

    /**
     * 写前校验：返回违约原因（null = 可写）。键不在 {@code HubConfigEditor.specs} → {@code unknown
     * key}；值未通过 {@code RuleSpec.type.accepts} → 类型违约。
     * Write pre-check: returns the violation reason (null = writable). A key absent from
     * {@code HubConfigEditor.specs} → {@code unknown key}; a value failing
     * {@code RuleSpec.type.accepts} → a type violation.
     */
    private static String writeViolation(HubRuntime store, String key, String value) {
        RuleSpec spec = null;
        for (RuleSpec s : store.editor().specs()) {
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

    /** 固定文档序字段名逗号连接。The fixed document-order field names joined by comma. */
    private static String names(FormStructure form) {
        StringBuilder sb = new StringBuilder();
        for (FormField f : form.fields()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(f.name());
        }
        return sb.toString();
    }
}
