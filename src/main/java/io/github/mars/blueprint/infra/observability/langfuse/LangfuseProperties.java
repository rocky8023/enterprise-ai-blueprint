package io.github.mars.blueprint.infra.observability.langfuse;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Langfuse 接入配置。默认关闭；配好 enabled=true 与密钥后，调用记录会异步推送到 Langfuse。
 */
@ConfigurationProperties(prefix = "blueprint.observability.langfuse")
public record LangfuseProperties(
        boolean enabled,
        String host,
        String publicKey,
        String secretKey
) {

    public String hostOrDefault() {
        return host == null || host.isBlank() ? "https://cloud.langfuse.com" : host.replaceAll("/+$", "");
    }
}
