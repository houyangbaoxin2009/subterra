package io.toterra.subterra.engine.ui.kit;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bottom-layer multilingual support of the Subterra UI kit (p.2.37.1, user decision):
 * localization is a first-class engine-core citizen, not a runtime patch. A thread-safe
 * table of {@code (key, locale) -> text} with a deterministic fallback chain
 * ({@code requested -> en_us}); widgets only carry translation keys, never final language
 * text. Same key + same locale resolve to the same bytes. Locale ids are normalized
 * ({@code [a-z]{2,3}(_[a-z0-9]{2,8})?}) with deterministic rejection; td declarability and
 * the api surface land in p.2.37.3/p.2.37.5 — this core fixes the resolution semantics.
 *
 * <p>Subterra UI 库的底层多语言支持（p.2.37.1，用户定案）：本地化是引擎核心的一等公民，
 * 不是 runtime 侧补丁。线程安全的 {@code (key, locale) -> text} 表 + 确定性 fallback 链
 * （{@code 请求 locale -> en_us}）；控件只持 translation key，不持最终语言文本。同 key 同
 * locale 解析同字节。locale id 规范化（{@code [a-z]{2,3}(_[a-z0-9]{2,8})?}）、非法确定性
 * 拒绝；td 声明与 api 面随 p.2.37.3/p.2.37.5 落——本核心钉死解析语义。
 */
public final class LocaleTable {

    /** The terminal fallback locale. / 末端回退 locale。 */
    public static final String FALLBACK = "en_us";

    private static final String LOCALE_PATTERN = "[a-z]{2,3}(_[a-z0-9]{2,8})?";

    private final Map<String, Map<String, String>> byKey = new ConcurrentHashMap<>();

    /**
     * Canonicalizes a locale form: trim + lowercase + pattern-validated
     * ({@code [a-z]{2,3}(_[a-z0-9]{2,8})?}). Pure; same-input-same-output.
     * / 规范化 locale 形式：trim + 小写 + 模式校验。纯函数；同输入同输出。
     *
     * @param form the locale form (non-null).
     * @return the canonical lowercase locale id.
     * @throws NullPointerException     if {@code form} is null.
     * @throws IllegalArgumentException if the form is illegal.
     */
    public static String normalizeLocale(String form) {
        Objects.requireNonNull(form, "locale form must not be null");
        String lowered = form.trim().toLowerCase(Locale.ROOT);
        if (!lowered.matches(LOCALE_PATTERN)) {
            throw new IllegalArgumentException("illegal locale id: " + form);
        }
        return lowered;
    }

    /**
     * The deterministic fallback chain: the requested locale first, then {@link #FALLBACK}
     * when different. Pure. / 确定性回退链：请求 locale 在前，不同时补 {@link #FALLBACK}。纯函数。
     *
     * @param locale the requested locale (normalized).
     * @return the ordered fallback chain.
     */
    public static List<String> fallbackChain(String locale) {
        String normalized = normalizeLocale(locale);
        return normalized.equals(FALLBACK) ? List.of(FALLBACK) : List.of(normalized, FALLBACK);
    }

    /** An empty table. / 空表。 */
    public static LocaleTable empty() {
        return new LocaleTable();
    }

    private LocaleTable() {
    }

    /**
     * Registers one (key, locale, text) entry; re-registering overwrites deterministically.
     * / 注册一条 (key, locale, text)；重复注册确定性覆盖。
     *
     * @param key    the translation key (non-blank).
     * @param locale the locale form (normalized via {@link #normalizeLocale}).
     * @param text   the localized text (non-null).
     * @throws NullPointerException     if any argument is null.
     * @throws IllegalArgumentException if the key is blank or the locale is illegal.
     */
    public void put(String key, String locale, String text) {
        Objects.requireNonNull(key, "translation key must not be null");
        Objects.requireNonNull(text, "localized text must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("translation key must not be blank");
        }
        String normalizedLocale = normalizeLocale(locale);
        byKey.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(normalizedLocale, text);
    }

    /**
     * Resolves the text for the key along the deterministic fallback chain; empty when no
     * entry exists on the whole chain. Pure read, lock-free.
     * / 沿确定性回退链解析文本；整链无条目时为空。纯读，无锁。
     *
     * @param key    the translation key (non-blank).
     * @param locale the requested locale form.
     * @return the resolved text, or empty.
     * @throws NullPointerException     if any argument is null.
     * @throws IllegalArgumentException if the key is blank or the locale is illegal.
     */
    public Optional<String> resolve(String key, String locale) {
        Objects.requireNonNull(key, "translation key must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("translation key must not be blank");
        }
        Map<String, String> texts = byKey.get(key);
        if (texts == null) {
            return Optional.empty();
        }
        for (String l : fallbackChain(locale)) {
            String text = texts.get(l);
            if (text != null) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    /** Whether the key has any entry. / 该 key 是否有条目。 */
    public boolean has(String key) {
        Objects.requireNonNull(key, "translation key must not be null");
        return byKey.containsKey(key);
    }

    /** The sorted key snapshot (deterministic). / 排序的 key 快照（确定性）。 */
    public List<String> keys() {
        return byKey.keySet().stream().sorted().toList();
    }

    /** The sorted locale snapshot carrying any entry for the key (deterministic). /
     *  该 key 携带条目的排序 locale 快照（确定性）。 */
    public List<String> localesOf(String key) {
        Map<String, String> texts = byKey.get(Objects.requireNonNull(key, "key must not be null"));
        return texts == null ? List.of() : texts.keySet().stream().sorted().toList();
    }

    /** Removes every entry. / 清空全部条目。 */
    public void clear() {
        byKey.clear();
    }
}
