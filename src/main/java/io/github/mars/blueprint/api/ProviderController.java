package io.github.mars.blueprint.api;

import io.github.mars.blueprint.infra.llm.ChatRouter;
import io.github.mars.blueprint.infra.llm.ProviderPreset;
import io.github.mars.blueprint.infra.llm.ProviderRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderRegistry registry;
    private final ChatRouter chatRouter;

    public ProviderController(ProviderRegistry registry, ChatRouter chatRouter) {
        this.registry = registry;
        this.chatRouter = chatRouter;
    }

    @GetMapping
    public Map<String, Object> list() {
        String activeChat = registry.activeChatPreset();
        String activeEmbedding = registry.activeEmbeddingPreset();
        Set<String> routable = chatRouter.availablePresets();

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
                    row.put("routable", routable.contains(name));   // 请求维度可路由到（配了 key 且支持 chat）
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
