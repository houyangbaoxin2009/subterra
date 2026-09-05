package io.toterra.subterra.log.analysis;

import io.toterra.subterra.log.LogConfig;

/**
 * Analyzer selection. The mini-AI path is opt-in via td config
 * ({@code log.analysis.enabled}); when off (default) the zero-dependency
 * {@link RulesAnalyzer} handles diagnostics with deterministic probe coverage.
 */
public final class Analyzers {

    private Analyzers() {
    }

    public static ErrorAnalyzer resolve(LogConfig cfg) {
        return cfg.analysisEnabled() ? new AiAnalyzer(cfg) : new RulesAnalyzer();
    }
}