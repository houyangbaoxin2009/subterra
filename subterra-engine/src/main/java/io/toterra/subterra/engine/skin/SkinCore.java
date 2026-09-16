package io.toterra.subterra.engine.skin;

import java.util.List;
import java.util.Locale;

/**
 * p.2.32.4 皮肤补丁数据面（clean-room 自研）：确定性皮肤解析——td 声明的
 * 规格序（UUID / NAME / DEFAULT）首个命中者胜出，未命中回落缺省皮肤；
 * 纹理尺寸校验（64x64 或 64x32 传统布局）。纯 JDK、无 MC 引用、同输入同结果。
 *
 * <p>The p.2.32.4 skin-patch data plane (clean-room): deterministic skin resolution —
 * the first matching spec in the declared td order (UUID / NAME / DEFAULT) wins,
 * falling back to the default skin; texture-dimension validation (64x64 or the
 * legacy 64x32 layout). Pure JDK, no MC references, identical inputs give identical
 * results.
 */
public final class SkinCore {

    /** 规格类别。 / The spec kind. */
    public enum Kind {
        UUID,
        NAME,
        DEFAULT
    }

    /** 缺省纹理 id。 / The fallback texture id. */
    public static final String DEFAULT_TEXTURE = "subterra:skin_default";

    /** 单条规格（构造期确定性校验）。 / One spec (validated at construction). */
    public record SkinSpec(Kind kind, String matchValue, String textureId) {
        public SkinSpec {
            if (kind == null) {
                throw new IllegalArgumentException("kind must not be null");
            }
            if (kind != Kind.DEFAULT && (matchValue == null || matchValue.isBlank())) {
                throw new IllegalArgumentException("match_value must not be blank for " + kind);
            }
            if (textureId == null || !textureId.contains(":") || textureId.startsWith(":") || textureId.endsWith(":")) {
                throw new IllegalArgumentException("texture_id must be namespaced (got " + textureId + ")");
            }
            matchValue = kind == Kind.DEFAULT ? "" : matchValue;
            if (kind == Kind.UUID) {
                matchValue = matchValue.toLowerCase(Locale.ROOT);
            }
        }
    }

    /** 解析结果。 / The resolution outcome. */
    public record Resolution(String playerKey, String textureId, String source) {
    }

    private SkinCore() {
    }

    /** 确定性解析：规格序首个命中者胜出，未命中回落 DEFAULT 规格或缺省纹理。 /
     *  Resolves deterministically: first match in spec order, else the DEFAULT spec, else the default texture. */
    public static Resolution resolve(List<SkinSpec> specs, String playerUuid, String playerName) {
        if (specs == null) {
            throw new IllegalArgumentException("specs must not be null");
        }
        if (playerUuid == null || playerUuid.isBlank() || playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("player identity must not be blank");
        }
        String uuid = playerUuid.toLowerCase(Locale.ROOT);
        String key = uuid + "/" + playerName;
        SkinSpec defaultSpec = null;
        for (SkinSpec spec : specs) {
            switch (spec.kind()) {
                case UUID -> {
                    if (spec.matchValue().equals(uuid)) {
                        return new Resolution(key, spec.textureId(), "uuid");
                    }
                }
                case NAME -> {
                    if (spec.matchValue().equals(playerName)) {
                        return new Resolution(key, spec.textureId(), "name");
                    }
                }
                case DEFAULT -> {
                    if (defaultSpec == null) {
                        defaultSpec = spec;
                    }
                }
                default -> throw new IllegalArgumentException("unknown spec kind");
            }
        }
        if (defaultSpec != null) {
            return new Resolution(key, defaultSpec.textureId(), "default-spec");
        }
        return new Resolution(key, DEFAULT_TEXTURE, "built-in");
    }

    /** 纹理尺寸校验：64x64 或 64x32。 / The texture-dimension check: 64x64 or 64x32. */
    public static boolean validTextureDims(int width, int height) {
        return width == 64 && (height == 64 || height == 32);
    }
}
