package io.toterra.subterra.engine.worldgen.pipeline;

import java.util.Set;

/**
 * Deterministic registry for the pipeline faces (p.1.8.2 world-gen pipeline
 * recon): validates folder / registry-id tokens so data packs and MC shells
 * can be checked cheaply, mirroring the hot-spot registry pattern used by
 * {@code SyncLoadGuard}. Pure JDK; immutable.
 */
public final class PipelineFaces {

    private PipelineFaces() {
    }

    /** All registered faces. */
    public static Set<String> faceNames() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (PipelineFace face : PipelineFace.values()) {
            names.add(face.name());
        }
        return java.util.Collections.unmodifiableSet(names);
    }

    /** True when {@code folder} is a known {@code data/.../worldgen/...} folder. */
    public static boolean isKnownDataFolder(String folder) {
        if (folder == null) {
            return false;
        }
        for (PipelineFace face : PipelineFace.values()) {
            if (face.dataFolder().equals(folder)) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code id} is a known registry id (null registry rows skipped). */
    public static boolean isKnownRegistryId(String id) {
        if (id == null) {
            return false;
        }
        for (PipelineFace face : PipelineFace.values()) {
            if (id.equals(face.registryId())) {
                return true;
            }
        }
        return false;
    }

    /** Folder of a named face; null when unknown. */
    public static String dataFolderOf(String faceName) {
        for (PipelineFace face : PipelineFace.values()) {
            if (face.name().equals(faceName)) {
                return face.dataFolder();
            }
        }
        return null;
    }
}