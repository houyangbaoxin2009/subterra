package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.tie.TieLibrary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * p.2.2 tie-logic loader: turns the datapack's {@code pack.td} tie declarations
 * into a {@link TieLogicBundle} of loaded libraries. {@code dll} paths are
 * resolved relative to the pack directory; a missing file or a load failure is
 * reported eagerly (never a silent no-op).
 */
public final class TieLogicLoader {

    private TieLogicLoader() {
    }

    public static TieLogicBundle load(Datapack datapack, Path packDir) {
        Path root = packDir.toAbsolutePath().normalize();
        Map<String, TieLibrary> libraries = new LinkedHashMap<>();
        for (TieLibDecl decl : datapack.tieLibraries()) {
            if (libraries.containsKey(decl.lib())) {
                throw new IllegalArgumentException("datapack " + datapack.name()
                        + ": duplicate tie lib declaration '" + decl.lib() + "'");
            }
            Path dll = decl.dll().startsWith("/") || decl.dll().indexOf(':') > 0
                    ? Path.of(decl.dll())
                    : root.resolve(decl.dll()).normalize();
            if (!Files.isRegularFile(dll)) {
                throw new IllegalArgumentException("datapack " + datapack.name()
                        + ": tie lib '" + decl.lib() + "' dll not found: " + dll);
            }
            libraries.put(decl.lib(), TieLibrary.load(dll));
        }
        return new TieLogicBundle(datapack, libraries);
    }
}