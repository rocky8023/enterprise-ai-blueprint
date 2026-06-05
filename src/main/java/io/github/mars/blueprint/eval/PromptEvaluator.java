package io.github.mars.blueprint.eval;

import io.github.mars.blueprint.prompt.PromptDefinition;
import io.github.mars.blueprint.prompt.PromptExample;
import io.github.mars.blueprint.prompt.PromptRegistry;
import io.github.mars.blueprint.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt 评测器：把 prompt frontmatter 里自带的 {@code examples}（问题 + 期望关键词）
 * 当作回归测试用例，逐条跑过 RAG 链路，断言答案是否命中全部期望关键词。
 * <p>
 * 关键词匹配采用大小写不敏感的“包含”判定——朴素但够用，目的是给 prompt 改动一个快速回归信号，
 * 而非追求 NLP 级评分。需要更强评测（如语义相似度、LLM 裁判）时可在此扩展。
 */
@Service
public class PromptEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PromptEvaluator.class);

    private final PromptRegistry promptRegistry;
    private final RagService ragService;

    public PromptEvaluator(PromptRegistry promptRegistry, RagService ragService) {
        this.promptRegistry = promptRegistry;
        this.ragService = ragService;
    }

    /** 评测某个 prompt key 的指定版本（version 为空则取活跃版本）。 */
    public EvalReport evaluate(String key, String version) {
        PromptDefinition def = promptRegistry.get(key, version);
        List<PromptExample> examples = def.metadata().examples();

        List<EvalCase> cases = new ArrayList<>();
        int passed = 0;
        for (PromptExample ex : examples) {
            String answer = ragService.ask(ex.question(), def.version()).answer();
            Match m = match(answer, ex.expectedKeywords());
            boolean ok = m.missing().isEmpty();
            if (ok) {
                passed++;
            }
            cases.add(new EvalCase(ex.question(), ex.expectedKeywords(),
                    m.matched(), m.missing(), ok, answer));
        }

        int total = examples.size();
        double rate = total == 0 ? 0.0 : Math.round((double) passed / total * 1000) / 10.0;
        log.info("评测 {}@{}: {}/{} 通过，pass率 {}%", key, def.version(), passed, total, rate);
        return new EvalReport(key, def.version(), Instant.now(), total, passed, rate, cases);
    }

    /** 评测某个 prompt key 的所有版本，用于版本回归对比。 */
    public List<EvalReport> evaluateAll(String key) {
        Map<String, PromptDefinition> versions = promptRegistry.all().get(key);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("Prompt key 未找到: " + key);
        }
        return versions.keySet().stream().sorted()
                .map(v -> evaluate(key, v))
                .toList();
    }

    /** 关键词命中判定：大小写不敏感的包含。包级可见，便于单测。 */
    static Match match(String answer, List<String> expected) {
        String norm = answer == null ? "" : answer.toLowerCase();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (expected != null) {
            for (String kw : expected) {
                if (kw != null && !kw.isBlank() && norm.contains(kw.toLowerCase())) {
                    matched.add(kw);
                } else {
                    missing.add(kw);
                }
            }
        }
        return new Match(matched, missing);
    }

    record Match(List<String> matched, List<String> missing) {
    }
}
