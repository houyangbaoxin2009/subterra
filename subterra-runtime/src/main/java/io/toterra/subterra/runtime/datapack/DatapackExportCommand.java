package io.toterra.subterra.runtime.datapack;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackExportArchive;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * p.2.2.7 — {@code /subterra export [<path>]} — writes every currently loaded
 * td datapack out as a content-level export-archive td document
 * ({@link DatapackExportArchive}), one {@code <pack-name>.td} per pack, under
 * the requested directory (default {@code subterra-export} below the server
 * working directory). Each document is verified immediately by rehydrating it
 * through {@link DatapackExportArchive#rehydrate} and re-exporting — the two
 * documents must be byte-identical (export ∘ rehydrate is the identity on the
 * archive). Ops (permission level 2) may run it on the server console; it is a
 * forward hook to the p.2.18 engine.export capability.
 *
 * <p>Deterministic markers feed the E2E gate: one
 * {@code export cmd <pack> ok/mismatch (... rehydrate=ok/mismatch)} line per
 * pack, then a single aggregate {@code export cmd ok (packs=N, bytes=M)} or
 * {@code export cmd mismatch}.
 */
public final class DatapackExportCommand {

    private DatapackExportCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("subterra")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("export")
                        .executes(ctx -> runExport(ctx, null))
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(ctx ->
                                        runExport(ctx, StringArgumentType.getString(ctx, "path"))))));
    }

    private static int runExport(CommandContext<CommandSourceStack> ctx, String pathArg) {
        DatapackRegistrar reg = DatapackRuntime.activeRegistrar();
        if (reg == null) {
            ctx.getSource().sendFailure(Component.literal("datapack registrar not active"));
            return 0;
        }
        Path dir = pathArg != null && !pathArg.isBlank()
                ? Path.of(pathArg).toAbsolutePath().normalize()
                : Path.of("subterra-export").toAbsolutePath().normalize();

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DatapackRuntime.LOGGER.error("{} export cmd mismatch (dir create failed): {}",
                    DatapackRegistrar.MARKER, e.toString());
            ctx.getSource().sendFailure(Component.literal("subterra export failed: " + e.getMessage()));
            return 0;
        }

        int totalPacks = 0;
        long totalBytes = 0L;
        boolean failed = false;
        for (Datapack pack : reg.packs()) {
            String doc;
            try {
                doc = DatapackExportArchive.export(pack);
            } catch (RuntimeException e) {
                DatapackRuntime.LOGGER.error("{} export cmd mismatch (export failed): {}",
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
                DatapackRuntime.LOGGER.error("{} export cmd {} mismatch (rehydrate failed): {}",
                        DatapackRegistrar.MARKER, pack.name(), e.toString());
                identity = false;
            }
            if (!identity) {
                failed = true;
            }
            try {
                Files.writeString(dir.resolve(safeName(pack.name()) + ".td"), doc);
            } catch (IOException e) {
                DatapackRuntime.LOGGER.error("{} export cmd mismatch (write failed): {}",
                        DatapackRegistrar.MARKER, e.toString());
                failed = true;
                continue;
            }
            totalPacks++;
            totalBytes += (long) doc.getBytes(StandardCharsets.UTF_8).length;
            DatapackRuntime.LOGGER.info("{} export cmd {} {} (entries={}, rehydrate={})",
                    DatapackRegistrar.MARKER, pack.name(), identity ? "ok" : "mismatch",
                    pack.entries().size(), identity ? "ok" : "mismatch");
        }

        if (failed) {
            DatapackRuntime.LOGGER.error("{} export cmd mismatch", DatapackRegistrar.MARKER);
            ctx.getSource().sendFailure(Component.literal("subterra export failed (see log markers)"));
            return 0;
        }
        DatapackRuntime.LOGGER.info("{} export cmd ok (packs={}, bytes={})",
                DatapackRegistrar.MARKER, totalPacks, totalBytes);
        ctx.getSource().sendSuccess(() -> Component.literal("subterra export wrote " + dir), false);
        return 1;
    }

    /** Replaces every char outside {@code [A-Za-z0-9._-]} with {@code _} so the file name stays filesystem-safe. */
    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}