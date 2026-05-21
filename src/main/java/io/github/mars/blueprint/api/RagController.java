package io.github.mars.blueprint.api;

import io.github.mars.blueprint.rag.RagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/ask")
    public RagService.RagAnswer ask(@RequestParam("q") String question) {
        return ragService.ask(question);
    }

    @PostMapping("/ask")
    public RagService.RagAnswer askPost(@RequestBody Map<String, String> req) {
        return ragService.ask(req.getOrDefault("question", ""));
    }
}
