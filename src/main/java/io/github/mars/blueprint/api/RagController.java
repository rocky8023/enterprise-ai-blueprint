package io.github.mars.blueprint.api;

import io.github.mars.blueprint.prompt.PromptDefinition;
import io.github.mars.blueprint.prompt.PromptRegistry;
import io.github.mars.blueprint.rag.RagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final PromptRegistry promptRegistry;

    public RagController(RagService ragService, PromptRegistry promptRegistry) {
        this.ragService = ragService;
        this.promptRegistry = promptRegistry;
    }

    @GetMapping("/ask")
    public RagService.RagAnswer ask(
            @RequestParam("q") String question,
            @RequestParam(value = "promptVersion", required = false) String promptVersion,
            @RequestParam(value = "preset", required = false) String preset) {
        return ragService.ask(question, promptVersion, preset);
    }

    @PostMapping("/ask")
    public RagService.RagAnswer askPost(@RequestBody Map<String, String> req) {
        return ragService.ask(
                req.getOrDefault("question", ""),
                req.get("promptVersion"),
                req.get("preset"));
    }

    @GetMapping("/prompts")
    public List<Map<String, Object>> listPrompts() {
        List<Map<String, Object>> out = new ArrayList<>();
        promptRegistry.all().forEach((key, versions) -> {
            for (Map.Entry<String, PromptDefinition> e : versions.entrySet()) {
                PromptDefinition def = e.getValue();
                out.add(Map.of(
                        "key", def.key(),
                        "version", def.version(),
                        "status", def.metadata().status() == null ? "unknown" : def.metadata().status(),
                        "description", def.metadata().description() == null ? "" : def.metadata().description(),
                        "author", def.metadata().author() == null ? "" : def.metadata().author(),
                        "created", def.metadata().created() == null ? "" : def.metadata().created(),
                        "tags", def.metadata().tags() == null ? List.of() : def.metadata().tags()
                ));
            }
        });
        return out;
    }
}
