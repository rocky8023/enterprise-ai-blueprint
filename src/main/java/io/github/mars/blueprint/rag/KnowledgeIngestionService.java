package io.github.mars.blueprint.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final String KNOWLEDGE_PATTERN = "classpath:knowledge/*.md";

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public KnowledgeIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void ingest() throws IOException {
        Resource[] resources = resolver.getResources(KNOWLEDGE_PATTERN);
        log.info("发现 {} 个知识库文档，开始灌入向量库...", resources.length);

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> allChunks = new ArrayList<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            Document raw = new Document(content, Map.of("source", filename == null ? "unknown" : filename));
            List<Document> chunks = splitter.apply(List.of(raw));
            allChunks.addAll(chunks);
            log.debug("文档 {} 切片为 {} 个 chunk", filename, chunks.size());
        }

        vectorStore.add(allChunks);
        log.info("知识库灌入完成：共 {} 个 chunk 进入向量库", allChunks.size());
    }
}
