// Ported from Inventory Advancement Accelerator (github.com/vicuna-main/InventoryAdvancementAccelerator), MIT (c) vicuna.
package io.toterra.subterra.optim.server.invadvopt.command;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import io.toterra.subterra.optim.server.invadvopt.InventoryAdvancementAccelerator;
import io.toterra.subterra.optim.server.invadvopt.OptimizationMode;
import io.toterra.subterra.optim.server.invadvopt.metrics.StatsCollector;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class InvAdvOptCommand {
    private InvAdvOptCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("invadvopt")
                .requires(source -> source.hasPermission(4))
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(literal("stats").executes(context -> stats(context.getSource())))
                .then(literal("verify").executes(context -> verify(context.getSource())))
                .then(literal("mode")
                        .then(literal("vanilla").executes(context -> mode(context.getSource(), OptimizationMode.VANILLA)))
                        .then(literal("exact").executes(context -> mode(context.getSource(), OptimizationMode.EXACT))))
                .then(literal("reset-stats").executes(context -> reset(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[invadvopt] " + InventoryAdvancementAccelerator.runtime().status()), false);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        StatsCollector.Snapshot stats = InventoryAdvancementAccelerator.runtime().stats().snapshot();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[invadvopt] triggers=%d rawListeners=%d candidates=%d reduction=%.2f%% predicateTests=%d fullScans=%d fallbacks=%d mismatches=%d",
                stats.triggers(), stats.rawListeners(), stats.candidateListeners(), stats.reductionPercent(),
                stats.predicateTests(), stats.fullScans(), stats.fallbacks(), stats.mismatches())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[invadvopt] trigger avg=%.2f us p95=%.2f us max=%.2f us total=%.2f ms",
                stats.averageMicros(), stats.p95Nanos() / 1_000.0D, stats.maxNanos() / 1_000.0D,
                stats.totalNanos() / 1_000_000.0D)), false);
        source.sendSuccess(() -> Component.literal("[invadvopt] fallbackReasons=" + stats.fallbackReasons()), false);
        source.sendSuccess(() -> Component.literal("[invadvopt] indexConditions=" + stats.indexConditions()), false);
        source.sendSuccess(() -> Component.literal("[invadvopt] playerHotspots=" + stats.playerHotspots()), false);
        source.sendSuccess(() -> Component.literal("[invadvopt] itemHotspots=" + stats.itemHotspots()), false);
        return 1;
    }

    private static int verify(CommandSourceStack source) {
        InventoryAdvancementAccelerator.runtime().requestVerification();
        source.sendSuccess(() -> Component.literal("[invadvopt] Every indexed player's next trigger will be shadow-verified."), true);
        return 1;
    }

    private static int mode(CommandSourceStack source, OptimizationMode mode) {
        InventoryAdvancementAccelerator.runtime().setMode(mode);
        if (mode == OptimizationMode.EXACT) InventoryAdvancementAccelerator.runtime().resetPlayerCircuitBreakers();
        source.sendSuccess(() -> Component.literal("[invadvopt] Runtime mode set to " + mode + " (configuration file unchanged)."), true);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        InventoryAdvancementAccelerator.runtime().stats().reset();
        source.sendSuccess(() -> Component.literal("[invadvopt] Statistics reset."), true);
        return 1;
    }
}
