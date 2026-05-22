package io.github.mars.blueprint.prompt;

import java.util.List;

public record PromptMetadata(
        String author,
        String description,
        String created,
        String status,
        List<String> tags,
        List<PromptExample> examples,
        String basedOn
) {
}
