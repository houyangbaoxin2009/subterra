// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
package io.toterra.subterra.optim.sched.util;

public class Assertions {

    public static void assertTrue(boolean value, String message) {
        if (!value) {
            final AssertionError error = new AssertionError(message);
            error.printStackTrace();
            throw error;
        }
    }

    public static void assertTrue(boolean state, String format, Object... args) {
        if (!state) {
            final AssertionError error = new AssertionError(String.format(format, args));
            error.printStackTrace();
            throw error;
        }
    }

    public static void assertTrue(boolean value) {
        if (!value) {
            final AssertionError error = new AssertionError();
            error.printStackTrace();
            throw error;
        }
    }

}