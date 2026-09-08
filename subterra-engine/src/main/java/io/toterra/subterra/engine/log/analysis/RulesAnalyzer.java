package io.toterra.subterra.engine.log.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Zero-dependency rule-based crash analyzer. Patterned after the common
 * "known crash reasons" checklists (Crash Assistant / hs_err / Fabric analysis
 * guides): each rule matches a regex over the crash text and yields a stable
 * category, a fixed confidence and a remediation hint. Rules are checked in
 * declaration order; the first hit wins. No match falls through to
 * {@code unknown} with a generic next-steps hint.
 */
public final class RulesAnalyzer implements ErrorAnalyzer {

    private record Rule(Pattern pattern, double confidence, String category, String suggestion) {
    }

    private static final List<Rule> RULES = new ArrayList<>();
    private static final String UNKNOWN_SUGGESTION =
            "Check the full stack above the failure, verify installed versions and "
            + "dependencies, then reproduce with a clean mod set.";

    static {
        add(".*OutOfMemoryError.*", 0.95, "out_of_memory",
                "OutOfMemoryError: increase the JVM heap (see Subterra JvmLaunchArgs) and "
                + "reduce loaded mods; check hs_err files for native memory exhaustion.");
        add("(BindException.*(already in use|Address already in use)).*", 0.9, "port_in_use",
                "The server port is already taken. Stop the other process or change the port.");
        add("(.*Invalid mixin.*|.*Mixin.*cannot be cast to.*|.*class_\\d+ cannot be cast to.*)",
                0.85, "mixin_conflict",
                "A mixin conflict / class transform issue between mods. "
                + "Check the mixin audit log and remove the conflicting mods.");
        add(".*NullPointerException.*", 0.7, "null_pointer",
                "Unhandled null dereference; inspect the frames above the exception for the culprit.");
        add(".*StackOverflowError.*", 0.8, "stack_overflow",
                "Deep or recursive call path exhausted the stack; look for unbounded recursion.");
        add("(NoClassDefFoundError|NoSuchMethodError).*", 0.85, "classpath_mismatch",
                "A class or method is missing at runtime — mod/loader version mismatch or "
                + "an omitted dependency. Compare the loaded-module versions against requirements.");
        add(".*(hs_err_pid\\d+\\.log|# A fatal error has been detected by the Java Runtime Environment).*",
                0.9, "jvm_native",
                "JVM-level (native) crash. Collect hs_err_pid*.log and the log tail; "
                + "check GPU/driver status and JVM flags.");
        add(".*(Failed to load mod|Could not find required mod|cannot be found for version).*",
                0.85, "mod_load",
                "A mod failed to load (missing dependency or bad state). "
                + "Inspect the declaration order and the dependency block above.");
    }

    private static void add(String regex, double confidence, String category, String suggestion) {
        RULES.add(new Rule(Pattern.compile(regex, Pattern.DOTALL), confidence, category, suggestion));
    }

    @Override
    public String name() {
        return "rules";
    }

    @Override
    public ErrorAnalyzer.Result analyze(String crashText) {
        String hay = crashText == null ? "" : crashText;
        for (Rule r : RULES) {
            if (r.pattern().matcher(hay).find()) {
                return new ErrorAnalyzer.Result(r.category(), r.confidence(), r.suggestion());
            }
        }
        return ErrorAnalyzer.Result.unknown(UNKNOWN_SUGGESTION);
    }
}