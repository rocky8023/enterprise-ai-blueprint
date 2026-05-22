package io.github.mars.blueprint.prompt;

import java.util.List;

public record PromptExample(
        String question,
        List<String> expectedKeywords
) {
}
