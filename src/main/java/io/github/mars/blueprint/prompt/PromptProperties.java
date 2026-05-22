package io.github.mars.blueprint.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "blueprint.prompts")
public record PromptProperties(
        String location,
        Map<String, String> active
) {
}
