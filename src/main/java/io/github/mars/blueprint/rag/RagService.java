package io.github.mars.blueprint.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    private static final String PROMPT_TEMPLATE = """
            你是企业知识助手。请严格基于下方【知识库】内容回答【用户问题】。

            规则：
            1. 答案必须来自知识库内容，禁止编造
            2. 如果知识库不足以回答，明确说"知识库中没有相关信息"
            3. 引用知识时尽量带上来源文件名
            4. 回答简洁、条理化

            【知识库】
            %s

            【用户问题】
            %s
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final int topK;

    public RagService(VectorStore vectorStore,
                      ChatClient chatClient,
                      @Value("${blueprint.rag.top-k:4}") int topK) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.topK = topK;
    }

    public RagAnswer ask(String question) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());

        String context = hits.stream()
                .map(d -> "【来源：" + d.getMetadata().get("source") + "】\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = PROMPT_TEMPLATE.formatted(context, question);
        String rawAnswer = chatClient.prompt(prompt).call().content();
        String answer = stripThinking(rawAnswer);

        List<Source> sources = hits.stream()
                .map(d -> new Source(
                        String.valueOf(d.getMetadata().getOrDefault("source", "unknown")),
                        preview(d.getText())))
                .toList();

        return new RagAnswer(answer, sources);
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

    public record RagAnswer(String answer, List<Source> sources) {}
    public record Source(String filename, String preview) {}
}
