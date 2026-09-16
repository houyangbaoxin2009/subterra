package io.toterra.subterra.probes;

import io.toterra.subterra.engine.weather.WeatherCore;

/**
 * p.2.32.2 确定性探针（纯 JVM）：天气数据面 —— 气候界校验、同种子同气候同 tick
 * 恒同天气（再入深度相等）、不同种子/气候分布差异、周期取档、负 tick 拒绝、
 * 规范渲染、壳接线盘点。禁时序断言。
 *
 * <p>The p.2.32.2 deterministic probe (pure JVM): the weather data plane — climate
 * bounds, same seed+climate+tick identity (deep re-entry), cross-seed/climate spread,
 * periodic lookup, negative-tick rejection, canonical render, and the shell wiring
 * inventory. No timing assertions.
 */
public final class WeatherProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        climateValidation();
        determinism();
        periodicityAndSpread();
        wiringInventory();
        System.out.println("[WeatherProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void climateValidation() {
        reject("temperature above 1", () -> new WeatherCore.Climate(1.1f, 0.5f));
        reject("humidity below 0", () -> new WeatherCore.Climate(0.5f, -0.1f));
        WeatherCore.Climate c = new WeatherCore.Climate(0.5f, 0.5f);
        check("climate: canonical render", c.render().equals("temperature=0.5 humidity=0.5"));
    }

    private static void determinism() {
        WeatherCore.Forecast a = new WeatherCore.Forecast(44905237L, new WeatherCore.Climate(0.6f, 0.55f));
        WeatherCore.Forecast b = new WeatherCore.Forecast(44905237L, new WeatherCore.Climate(0.6f, 0.55f));
        check("determinism: canonical render deep-equal", a.render().equals(b.render()));
        boolean sameAll = true;
        for (long tick = 0; tick <= WeatherCore.SLOT_TICKS * WeatherCore.SLOT_COUNT; tick += WeatherCore.SLOT_TICKS / 2) {
            if (a.at(tick) != b.at(tick)) {
                sameAll = false;
            }
        }
        check("determinism: identical trajectory over two full periods", sameAll);
        // 湿度 0 ⇒ 恒 CLEAR（无雨）。
        WeatherCore.Forecast dry = new WeatherCore.Forecast(44905237L, new WeatherCore.Climate(0.5f, 0f));
        boolean allClear = true;
        for (long tick = 0; tick < WeatherCore.SLOT_TICKS * WeatherCore.SLOT_COUNT; tick += WeatherCore.SLOT_TICKS) {
            allClear &= dry.at(tick) == WeatherCore.Weather.CLEAR;
        }
        check("climate: zero humidity forces CLEAR everywhere", allClear);
        // 湿度 1 ⇒ 无 CLEAR 档。
        WeatherCore.Forecast wet = new WeatherCore.Forecast(44905237L, new WeatherCore.Climate(0.5f, 1f));
        boolean noneClear = true;
        for (long tick = 0; tick < WeatherCore.SLOT_TICKS * WeatherCore.SLOT_COUNT; tick += WeatherCore.SLOT_TICKS) {
            noneClear &= wet.at(tick) != WeatherCore.Weather.CLEAR;
        }
        check("climate: full humidity removes CLEAR", noneClear);
    }

    private static void periodicityAndSpread() {
        WeatherCore.Forecast f = new WeatherCore.Forecast(44905237L, new WeatherCore.Climate(0.6f, 0.55f));
        check("period: at(0) == at(period)",
                f.at(0) == f.at((long) WeatherCore.SLOT_TICKS * WeatherCore.SLOT_COUNT));
        try {
            f.at(-1);
            check("reject: negative tick", false);
        } catch (IllegalArgumentException e) {
            check("reject: negative tick", true);
        }
        boolean spread = false;
        for (long seed = 1; seed <= 40 && !spread; seed++) {
            WeatherCore.Forecast g = new WeatherCore.Forecast(seed, new WeatherCore.Climate(0.6f, 0.55f));
            spread = g.at(0) != f.at(0);
        }
        check("spread: seeds differ across the grid", spread);
        check("render: canonical prefix", f.render().startsWith("forecast "));
    }

    private static void wiringInventory() {
        check("wiring: WeatherRuntime present (load-only)",
                present("io.toterra.subterra.runtime.weather.WeatherRuntime"));
        check("wiring: marker literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.weather.WeatherRuntime", "[Subterra weather]"));
        check("wiring: gate literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.weather.WeatherRuntime", "subterra.probe.weather"));
    }

    private static void reject(String name, Runnable r) {
        try {
            r.run();
            check("reject: " + name, false);
        } catch (IllegalArgumentException e) {
            check("reject: " + name, true);
        }
    }

    private static boolean present(String fqcn) {
        try {
            Class.forName(fqcn, false, WeatherProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        try (java.io.InputStream in = WeatherProbe.class.getResourceAsStream("/" + fqcn.replace('.', '/') + ".class")) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(literal);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
