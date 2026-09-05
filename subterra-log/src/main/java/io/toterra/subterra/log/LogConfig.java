package io.toterra.subterra.log;

import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.config.TdValue;

import java.nio.file.Path;

/**
 * Logging configuration, loaded from td (see {@code log.td}) via
 * subterra-config. Unknown or invalid fields fall back to defaults so a broken
 * config file can never take the logging subsystem down.
 *
 * <pre>{@code
 * type tie<data>
 * log = [
 *   level = "INFO",
 *   ring_size = 1024,
 *   file = [
 *     enabled = true,
 *     dir = "logs/subterra",
 *     keep = 10,
 *   ],
 *   analysis = [
 *     enabled = false,   // local AI error analysis (Intel GPU/NPU), opt-in
 *     device = "auto",   // auto | GPU | NPU | CPU
 *     model = "mini",
 *   ],
 * ]
 * }</pre>
 */
public final class LogConfig {

    public static final LogLevel DEFAULT_LEVEL = LogLevel.INFO;
    public static final int DEFAULT_RING_SIZE = 1024;
    public static final String DEFAULT_FILE_DIR = "logs/subterra";
    public static final int DEFAULT_FILE_KEEP = 10;

    private final LogLevel level;
    private final int ringSize;
    private final boolean fileEnabled;
    private final Path fileDir;
    private final int fileKeep;
    private final boolean analysisEnabled;
    private final String analysisDevice;
    private final String analysisModel;

    private LogConfig(LogLevel level, int ringSize, boolean fileEnabled, Path fileDir,
                      int fileKeep, boolean analysisEnabled, String analysisDevice,
                      String analysisModel) {
        this.level = level;
        this.ringSize = ringSize;
        this.fileEnabled = fileEnabled;
        this.fileDir = fileDir;
        this.fileKeep = fileKeep;
        this.analysisEnabled = analysisEnabled;
        this.analysisDevice = analysisDevice;
        this.analysisModel = analysisModel;
    }

    public static LogConfig defaults() {
        return new LogConfig(DEFAULT_LEVEL, DEFAULT_RING_SIZE, false,
                Path.of(DEFAULT_FILE_DIR), DEFAULT_FILE_KEEP,
                false, "auto", "mini");
    }

    /**
     * Parses td config into a LogConfig. Accepts either a bare table
     * ({@code [ level = ... ]}) or a named {@code log = [...]} subtree.
     */
    public static LogConfig fromTd(TdTable root) {
        TdTable t = root;
        TdValue named = root.get("log");
        if (named instanceof TdTable logTable) {
            t = logTable;
        }

        LogLevel level = optionalLevel(t.get("level"), DEFAULT_LEVEL);
        int ringSize = optionalInt(t.get("ring_size"), DEFAULT_RING_SIZE);
        if (ringSize < 1) {
            ringSize = DEFAULT_RING_SIZE;
        }

        boolean fileEnabled = false;
        Path fileDir = Path.of(DEFAULT_FILE_DIR);
        int fileKeep = DEFAULT_FILE_KEEP;
        TdValue file = t.get("file");
        if (file instanceof TdTable f) {
            fileEnabled = optionalBool(f.get("enabled"), false);
            String dir = optionalString(f.get("dir"), DEFAULT_FILE_DIR);
            if (!dir.isBlank()) {
                fileDir = Path.of(dir);
            }
            fileKeep = optionalInt(f.get("keep"), DEFAULT_FILE_KEEP);
            if (fileKeep < 1) {
                fileKeep = 1;
            }
        }

        boolean analysisEnabled = false;
        String analysisDevice = "auto";
        String analysisModel = "mini";
        TdValue analysis = t.get("analysis");
        if (analysis instanceof TdTable a) {
            analysisEnabled = optionalBool(a.get("enabled"), false);
            analysisDevice = optionalString(a.get("device"), "auto");
            analysisModel = optionalString(a.get("model"), "mini");
        }

        return new LogConfig(level, ringSize, fileEnabled, fileDir, fileKeep,
                analysisEnabled, analysisDevice, analysisModel);
    }

    private static LogLevel optionalLevel(TdValue v, LogLevel dflt) {
        if (v != null) {
            LogLevel lv = LogLevel.of(v.asString());
            if (lv != null) {
                return lv;
            }
        }
        return dflt;
    }

    private static int optionalInt(TdValue v, int dflt) {
        return v != null ? (int) v.asInt() : dflt;
    }

    private static boolean optionalBool(TdValue v, boolean dflt) {
        return v != null ? v.asBool() : dflt;
    }

    private static String optionalString(TdValue v, String dflt) {
        if (v != null) {
            String s = v.asString();
            if (!s.isBlank()) {
                return s;
            }
        }
        return dflt;
    }

    public LogLevel level() {
        return level;
    }

    public int ringSize() {
        return ringSize;
    }

    public boolean fileEnabled() {
        return fileEnabled;
    }

    public Path fileDir() {
        return fileDir;
    }

    public int fileKeep() {
        return fileKeep;
    }

    public boolean analysisEnabled() {
        return analysisEnabled;
    }

    public String analysisDevice() {
        return analysisDevice;
    }

    public String analysisModel() {
        return analysisModel;
    }

    @Override
    public String toString() {
        return "LogConfig{level=" + level + ", ringSize=" + ringSize
                + ", file=" + (fileEnabled ? fileDir + "(keep " + fileKeep + ")" : "off")
                + ", analysis=" + (analysisEnabled ? analysisDevice + "/" + analysisModel : "off")
                + '}';
    }
}