package io.toterra.subterra.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic thread snapshot for crash diagnostics: each live thread's
 * name, state and the top stack frames, sorted by name. Captures worker pools
 * (C2ME/FlowSched workers, storage I/O, lighting, vanilla io) so a crash dump
 * shows the concurrency picture at failure time. Pure JDK.
 */
public final class ThreadsSnapshot {

    private ThreadsSnapshot() {
    }

    /**
     * Renders a thread summary.
     *
     * @param maxFramesPerThread top frames kept per thread (>= 0)
     * @return textual block, e.g.
     *         {@code main RUNNABLE: java.base/java.lang.Thread.dumpThreads...}
     */
    public static String describe(int maxFramesPerThread) {
        int frames = Math.max(0, maxFramesPerThread);
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        List<Map.Entry<Thread, StackTraceElement[]>> sorted = new ArrayList<>(all.entrySet());
        sorted.sort((a, b) -> a.getKey().getName().compareTo(b.getKey().getName()));

        StringBuilder sb = new StringBuilder(512);
        sb.append("Threads (").append(sorted.size()).append("):");
        for (Map.Entry<Thread, StackTraceElement[]> e : sorted) {
            Thread t = e.getKey();
            sb.append("\n  ").append(t.getName()).append(' ')
              .append(t.getState().name());
            StackTraceElement[] stack = e.getValue();
            for (int i = 0; i < frames && i < stack.length; i++) {
                sb.append("\n    at ").append(stack[i]);
            }
        }
        return sb.toString();
    }
}