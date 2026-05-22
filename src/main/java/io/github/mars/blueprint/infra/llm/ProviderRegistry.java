package io.github.mars.blueprint.infra.llm;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class ProviderRegistry {

    private final ProviderProperties properties;

    public ProviderRegistry(ProviderProperties properties) {
        this.properties = properties;
    }

    public Map<String, ProviderPreset> all() {
        return properties.providers() == null
                ? Map.of()
                : Collections.unmodifiableMap(properties.providers());
    }

    public ProviderPreset get(String name) {
        ProviderPreset p = all().get(name);
        if (p == null) {
            throw new IllegalArgumentException(
                    "Provider preset 未找到: '" + name + "'，可用: " + all().keySet());
        }
        return p;
    }

    public String activeChatPreset() {
        return properties.chat() != null ? properties.chat().preset() : null;
    }

    public String activeEmbeddingPreset() {
        return properties.embedding() != null ? properties.embedding().preset() : null;
    }
}
