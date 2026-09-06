// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.optim.server.item_control.shell;

import io.toterra.subterra.optim.server.item_control.ItemControlRule;
import io.toterra.subterra.optim.server.item_control.ItemControlRuleSet;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /itemban} command tree (OP level 2). Registers as a listener via
 * {@link ItemControl#bootstrap} on {@code NeoForge.EVENT_BUS}.
 */
public final class ItemControlCommands {

    static final class IdAndNbt {
        final String id;
        final String nbt;

        IdAndNbt(String id, String nbt) {
            this.id = id;
            this.nbt = nbt;
        }
    }

    static IdAndNbt parseIdAndNbt(String input) {
        int braceIndex = input.indexOf('{');
        if (braceIndex > 0) {
            return new IdAndNbt(input.substring(0, braceIndex), input.substring(braceIndex));
        }
        return new IdAndNbt(input, null);
    }

    private ItemControlCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("itemban")
            .requires(source -> source.hasPermission(2)) // OP level 2
            .then(Commands.literal("add")
                .then(Commands.argument("item", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        IdAndNbt parsed = parseIdAndNbt(StringArgumentType.getString(ctx, "item"));
                        ItemControlConfig.addToBlacklist(parsed.id, parsed.nbt);
                        int stripped = RecipeStripper.applyFromSnapshot(ctx.getSource().getServer());
                        String msg = parsed.nbt != null
                            ? "added blacklist item: " + parsed.id + " (with NBT), recipe table updated (removed " + stripped + ")"
                            : "added blacklist item: " + parsed.id + ", recipe table updated (removed " + stripped + ")";
                        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                        return 1;
                    })))
            .then(Commands.literal("remove")
                .then(Commands.argument("item", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        IdAndNbt parsed = parseIdAndNbt(StringArgumentType.getString(ctx, "item"));
                        ItemControlConfig.removeFromBlacklist(parsed.id, parsed.nbt);
                        int stripped = RecipeStripper.applyFromSnapshot(ctx.getSource().getServer());
                        String msg = parsed.nbt != null
                            ? "removed blacklist item: " + parsed.id + " (NBT-specific), recipes restored (still filtering " + stripped + ")"
                            : "removed blacklist item: " + parsed.id + " (all rules), recipes restored (still filtering " + stripped + ")";
                        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                        return 1;
                    })))
            .then(Commands.literal("list")
                .executes(ctx -> {
                    StringBuilder sb = new StringBuilder("Current blacklist:\n");
                    for (ItemControlRule rule : ItemControlConfig.getBlacklistRules().rules()) {
                        if (rule.nbtSnbt() != null) {
                            sb.append("  - ").append(rule.id()).append(rule.nbtSnbt()).append("\n");
                        } else {
                            sb.append("  - ").append(rule.id()).append("\n");
                        }
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), true);
                    return 1;
                }))
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    ItemControlConfig.reload();
                    int stripped = RecipeStripper.applyFromSnapshot(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("blacklist reloaded, recipe table updated (removed " + stripped + ")"), true);
                    return 1;
                }))
            .then(Commands.literal("announce")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        ItemControlConfig.setPublicAnnounce(true);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aannouncement enabled"), true);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ItemControlConfig.setPublicAnnounce(false);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7cannouncement disabled"), true);
                        return 1;
                    })))
            .then(Commands.literal("autoban")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        ItemControlConfig.setAutoBanOnViolation(true);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aauto-ban enabled"), true);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ItemControlConfig.setAutoBanOnViolation(false);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7cauto-ban disabled"), true);
                        return 1;
                    })))
            .then(Commands.literal("dropdetect")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        ItemControlConfig.setDetectDroppedItems(true);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7adropped-item detection enabled"), true);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ItemControlConfig.setDetectDroppedItems(false);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7cdropped-item detection disabled"), true);
                        return 1;
                    })))
            .then(Commands.literal("blockscan")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        ItemControlConfig.setDetectWorldBlocks(true);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aworld-block detection enabled"), true);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ItemControlConfig.setDetectWorldBlocks(false);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7cworld-block detection disabled"), true);
                        return 1;
                    })))
            .then(Commands.literal("block")
                .then(Commands.literal("add")
                    .then(Commands.argument("block", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            IdAndNbt parsed = parseIdAndNbt(StringArgumentType.getString(ctx, "block"));
                            ItemControlConfig.addToBlockBlacklist(parsed.id, parsed.nbt);
                            ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aadded block " + StringArgumentType.getString(ctx, "block") + " to the block blacklist"), true);
                            return 1;
                        })))
                .then(Commands.literal("remove")
                    .then(Commands.argument("block", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            IdAndNbt parsed = parseIdAndNbt(StringArgumentType.getString(ctx, "block"));
                            ItemControlConfig.removeFromBlockBlacklist(parsed.id, parsed.nbt);
                            ctx.getSource().sendSuccess(() -> Component.literal("\u00a7cremoved block " + StringArgumentType.getString(ctx, "block") + " from the block blacklist"), true);
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        StringBuilder sb = new StringBuilder("\u00a76Block blacklist:\n");
                        for (var rule : ItemControlConfig.getBlockBlacklistRules().rules()) {
                            if (rule.nbtSnbt() != null && !rule.nbtSnbt().isEmpty()) {
                                sb.append("  - ").append(rule.id()).append(rule.nbtSnbt()).append("\n");
                            } else {
                                sb.append("  - ").append(rule.id()).append("\n");
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), true);
                        return 1;
                    })))
            .then(Commands.literal("logexclude")
                .then(Commands.literal("add")
                    .then(Commands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String itemId = parseIdAndNbt(StringArgumentType.getString(ctx, "item")).id;
                            ItemControlConfig.addExcludeFromLog(itemId);
                            ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aadded " + itemId + " to the audit exclusion list"), true);
                            return 1;
                        })))
                .then(Commands.literal("remove")
                    .then(Commands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String itemId = parseIdAndNbt(StringArgumentType.getString(ctx, "item")).id;
                            ItemControlConfig.removeExcludeFromLog(itemId);
                            ctx.getSource().sendSuccess(() -> Component.literal("\u00a7cremoved " + itemId + " from the audit exclusion list"), true);
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        StringBuilder sb = new StringBuilder("\u00a76Audit exclusion list:\n");
                        for (String id : ItemControlConfig.getExcludeFromLog()) {
                            sb.append("  - ").append(id).append("\n");
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), true);
                        return 1;
                    })))
        );
    }
}