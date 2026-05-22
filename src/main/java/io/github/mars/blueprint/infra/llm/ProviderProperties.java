package io.github.mars.blueprint.infra.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "blueprint.provider")
public record ProviderProperties(
        Selector chat,
        Selector embedding,
        Map<String, ProviderPreset> providers
) {
    public record Selector(String preset) {
    }
}
