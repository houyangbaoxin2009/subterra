package io.toterra.subterra.engine.network.crypto;

/**
 * Encryption configuration for the enhanced network channel.
 * <p>
 * 中文：增强网络通道的加密配置。在可信局域网环境中可以关闭加密以降低延迟。
 * Default: enabled = true (strong encryption on).
 * <p>
 * 中文：默认开启（enabled = true），启用强加密；可信局域网可设置为 false 关闭，
 * 走透传模式以降低延迟。
 */
public record EncryptionConfig(boolean enabled) {

    /**
     * Create a default configuration (encryption enabled).
     * <p>
     * 中文：创建默认配置（加密开启）。
     *
     * @return default config with encryption enabled
     */
    public static EncryptionConfig on() {
        return new EncryptionConfig(true);
    }

    /**
     * Create a configuration with encryption disabled for trusted LAN.
     * <p>
     * 中文：创建加密关闭的配置（用于可信局域网）。
     *
     * @return config with encryption disabled
     */
    public static EncryptionConfig disabled() {
        return new EncryptionConfig(false);
    }

    /**
     * Create a configuration with the specified enabled state.
     * <p>
     * 中文：创建指定加密状态的配置。
     *
     * @param enabled whether encryption should be enabled
     * @return the configuration
     */
    public static EncryptionConfig of(boolean enabled) {
        return new EncryptionConfig(enabled);
    }
}
