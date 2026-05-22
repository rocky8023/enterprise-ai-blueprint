package io.github.mars.blueprint.api;

import io.github.mars.blueprint.infra.llm.ProviderPreset;
import io.github.mars.blueprint.infra.llm.ProviderRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderRegistry registry;

    public ProviderController(ProviderRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<String, Object> list() {
        String activeChat = registry.activeChatPreset();
        String activeEmbedding = registry.activeEmbeddingPreset();

        List<Map<String, Object>> items = registry.all().entrySet().stream()
                .map(e -> {
                    String name = e.getKey();
                    ProviderPreset p = e.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", name);
                    row.put("baseUrl", p.baseUrl());
                    row.put("chatModel", p.chatModel());
                    row.put("chatTemperature", p.chatTemperature());
                    row.put("embeddingModel", p.embeddingModel());
                    row.put("docsUrl", p.docsUrl());
                    row.put("notes", p.notes());
                    row.put("activeAsChat", name.equals(activeChat));
                    row.put("activeAsEmbedding", name.equals(activeEmbedding));
                    return row;
                })
                .toList();

        return Map.of(
                "activeChat", activeChat == null ? "" : activeChat,
                "activeEmbedding", activeEmbedding == null ? "" : activeEmbedding,
                "presets", items
        );
    }
}
