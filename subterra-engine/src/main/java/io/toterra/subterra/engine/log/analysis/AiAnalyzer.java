package io.toterra.subterra.engine.log.analysis;

import io.toterra.subterra.engine.log.LogConfig;

/**
 * Opt-in local mini-AI crash analyzer (Intel platform GPU/NPU acceleration,
 * onnxruntime-openvino execution provider). Disabled by default
 * ({@code log.analysis.enabled = false} in {@code log.td}).
 * <p>
 * This release wires the lifecycle contract: configuration parsing, the
 * analyzer id and a clear fallback result. The actual inference endpoint (model
 * + ONNX Runtime) slots in behind {@link #analyze} in a later step; until then
 * analysis degrades to "unknown" with a pointer — never blocks, never throws.
 */
public final class AiAnalyzer implements ErrorAnalyzer {

    private static final String NOT_WIRED =
            "Local AI analysis is enabled but the inference endpoint is not wired in this "
            + "build; falling back to the rules analyzer. Check the model/device setting.";

    private final String device;
    private final String model;

    public AiAnalyzer(LogConfig cfg) {
        this.device = cfg.analysisDevice();
        this.model = cfg.analysisModel();
    }

    public String device() {
        return device;
    }

    public String model() {
        return model;
    }

    @Override
    public String name() {
        return "ai";
    }

    @Override
    public ErrorAnalyzer.Result analyze(String crashText) {
        return ErrorAnalyzer.Result.unknown(NOT_WIRED);
    }
}