package io.toterra.subterra.engine.weather;

import io.toterra.subterra.engine.pcg.PcgSource;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.32.2 天气数据面（clean-room 自研）：气候 → 天气的确定性映射。以共享种子
 * 经 {@link PcgSource} fork 派生固定长度时段表：湿度加权降雨概率、温度带调制雷暴
 * （仅雨档内出现）；同种子同气候同 tick 恒同天气。纯 JDK、无墙钟、无 MC 引用。
 *
 * <p>The p.2.32.2 weather data plane (clean-room): a deterministic climate → weather
 * mapping. A fixed-length slot schedule is derived from the shared seed via a
 * {@link PcgSource} fork: humidity weights the rain probability and the temperature
 * band modulates thunder (thunder only inside rain slots); the same seed, climate
 * and tick always yield the same weather. Pure JDK, no wall clock, no MC references.
 */
public final class WeatherCore {

    /** 天气档（世界观内： vanilla 雨雪面）。 / The weather tiers (vanilla rain surface). */
    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER
    }

    /** 归一化气候（0..1）。 / The normalized climate (0..1). */
    public record Climate(float temperature, float humidity) {
        public Climate {
            if (temperature < 0f || temperature > 1f) {
                throw new IllegalArgumentException("temperature must be within [0, 1] (got " + temperature + ")");
            }
            if (humidity < 0f || humidity > 1f) {
                throw new IllegalArgumentException("humidity must be within [0, 1] (got " + humidity + ")");
            }
        }

        /** 规范渲染（无时间戳）。 / The canonical render (no timestamps). */
        public String render() {
            return "temperature=" + temperature + " humidity=" + humidity;
        }
    }

    /** 派生盐。 / The fork salt. */
    public static final String SALT = "subterra.weather";

    /** 时段数与时段长（tick）。 / The slot count and slot length (ticks). */
    public static final int SLOT_COUNT = 16;
    public static final int SLOT_TICKS = 2400;

    /** 确定性预报：时段表 + at(tick) 周期取档。 / The deterministic forecast: slot schedule + periodic at(tick). */
    public static final class Forecast {
        private final Climate climate;
        private final List<Weather> slots;

        public Forecast(long worldSeed, Climate climate) {
            if (climate == null) {
                throw new IllegalArgumentException("climate must not be null");
            }
            this.climate = climate;
            PcgSource rng = new PcgSource(worldSeed).fork(SALT);
            this.slots = new ArrayList<>(SLOT_COUNT);
            for (int i = 0; i < SLOT_COUNT; i++) {
                // 湿度即时段降雨概率（0 恒晴、1 恒雨、单调）；雷暴仅在雨档内按温度带门控。
                // Humidity IS the per-slot rain probability (0 always clear, 1 always rain, monotone);
                // thunder only inside rain slots, gated by the temperature band.
                boolean rain = rng.nextDouble() < climate.humidity();
                if (rain) {
                    double thunderGate = StrictMath.max(0.0, StrictMath.min(0.6, 0.2 + climate.temperature() * 0.3));
                    slots.add(rng.nextDouble() < thunderGate ? Weather.THUNDER : Weather.RAIN);
                } else {
                    slots.add(Weather.CLEAR);
                }
            }
        }

        /** 周期取档：tick → 时段序（负 tick 确定性拒绝）。 / Periodic lookup; negative ticks rejected. */
        public Weather at(long tick) {
            if (tick < 0) {
                throw new IllegalArgumentException("tick must be >= 0 (got " + tick + ")");
            }
            int idx = (int) ((tick / SLOT_TICKS) % slots.size());
            return slots.get(idx);
        }

        public Climate climate() {
            return climate;
        }

        /** 规范渲染（时段缩写串，无时间戳）。 / The canonical render (slot abbreviation string, no timestamps). */
        public String render() {
            StringBuilder sb = new StringBuilder("forecast ");
            for (Weather w : slots) {
                sb.append(w == Weather.CLEAR ? 'C' : w == Weather.RAIN ? 'R' : 'T');
            }
            return sb.append(" (").append(climate.render()).append(')').toString();
        }
    }

    private WeatherCore() {
    }
}
