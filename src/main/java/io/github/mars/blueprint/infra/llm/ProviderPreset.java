package io.github.mars.blueprint.infra.llm;

public record ProviderPreset(
        String baseUrl,
        String chatModel,
        Double chatTemperature,
        String embeddingModel,
        String docsUrl,
        String notes
) {
}
