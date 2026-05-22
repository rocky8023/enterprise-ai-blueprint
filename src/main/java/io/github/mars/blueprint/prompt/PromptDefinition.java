package io.github.mars.blueprint.prompt;

import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

public record PromptDefinition(
        String key,
        String version,
        String body,
        PromptMetadata metadata
) {

    public String render(Map<String, Object> variables) {
        return new PromptTemplate(body).render(variables);
    }

    public String fullId() {
        return key + "@" + version;
    }
}
