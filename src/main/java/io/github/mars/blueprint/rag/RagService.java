package io.github.mars.blueprint.rag;

import io.github.mars.blueprint.prompt.PromptDefinition;
import io.github.mars.blueprint.prompt.PromptRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String PROMPT_KEY = "rag.company-qa";
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final PromptRegistry promptRegistry;
    private final int topK;

    public RagService(VectorStore vectorStore,
                      ChatClient chatClient,
                      PromptRegistry promptRegistry,
                      @Value("${blueprint.rag.top-k:4}") int topK) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.promptRegistry = promptRegistry;
        this.topK = topK;
    }

    public RagAnswer ask(String question) {
        return ask(question, null);
    }

    public RagAnswer ask(String question, String promptVersion) {
        PromptDefinition promptDef = promptRegistry.get(PROMPT_KEY, promptVersion);

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());

        String context = hits.stream()
                .map(d -> "【来源：" + d.getMetadata().get("source") + "】\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = promptDef.render(Map.of("context", context, "question", question));

        log.info("RAG 调用: prompt={}, question={}", promptDef.fullId(), question);

        String rawAnswer = chatClient.prompt(prompt).call().content();
        String answer = stripThinking(rawAnswer);

        List<Source> sources = hits.stream()
                .map(d -> new Source(
                        String.valueOf(d.getMetadata().getOrDefault("source", "unknown")),
                        preview(d.getText())))
                .toList();

        return new RagAnswer(answer, promptDef.fullId(), sources);
    }

    private static String stripThinking(String text) {
        if (text == null) {
            return "";
        }
        return THINK_BLOCK.matcher(text).replaceAll("").trim();
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    public record RagAnswer(String answer, String promptUsed, List<Source> sources) {}
    public record Source(String filename, String preview) {}
}
