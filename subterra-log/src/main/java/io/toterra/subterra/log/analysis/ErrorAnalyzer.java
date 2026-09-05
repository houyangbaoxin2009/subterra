package io.toterra.subterra.log.analysis;

/**
 * Plug-in point for crash diagnostics: given the crash text (exception, stack
 * and recent log excerpt), produce a structured finding. Implementations are
 * registered for a configuration via {@link Analyzers#resolve}; the built-in
 * {@link RulesAnalyzer} is the zero-dependency default, and the opt-in
 * {@link AiAnalyzer} is the local Intel GPU/NPU-backed mini-AI path
 * (disabled by default).
 */
public interface ErrorAnalyzer {

    /**
     * One analysis finding.
     *
     * @param category   stable category id (e.g. {@code out_of_memory})
     * @param confidence [0,1]; unknown analyses report 0
     * @param suggestion human-readable remediation hint (may be empty)
     */
    record Result(String category, double confidence, String suggestion) {

        public static Result unknown(String suggestion) {
            return new Result("unknown", 0.0, suggestion);
        }
    }

    /** Stable analyzer id ({@code rules} / {@code ai}). */
    String name();

    /** Analyzes crash text; must never throw. */
    Result analyze(String crashText);
}