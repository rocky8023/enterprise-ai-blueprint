package io.github.mars.blueprint.infra.observability.langfuse;

import io.github.mars.blueprint.infra.observability.LlmCallTrace;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LangfuseExporterTest {

    private LlmCallTrace trace(String status, String error) {
        return new LlmCallTrace(
                "abc12345", Instant.parse("2026-06-05T08:00:00Z"), "chat",
                "rag.company-qa@v1", "minimax", "MiniMax-M2.7",
                Map.of("question", "年假怎么算"), "渲染后的 prompt", "答案正文",
                1595, 368, 1963, 0.004539,
                2560, status, error);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 批次含trace与generation两个事件() {
        Map<String, Object> batch = LangfuseExporter.buildIngestionBatch(trace("ok", null));

        var events = (List<Map<String, Object>>) batch.get("batch");
        assertThat(events).hasSize(2);
        assertThat(events).extracting(e -> e.get("type"))
                .containsExactly("trace-create", "generation-create");
    }

    @SuppressWarnings("unchecked")
    @Test
    void generation携带模型_token_成本与正确层级() {
        Map<String, Object> batch = LangfuseExporter.buildIngestionBatch(trace("ok", null));
        var events = (List<Map<String, Object>>) batch.get("batch");
        var genBody = (Map<String, Object>) events.get(1).get("body");

        assertThat(genBody.get("model")).isEqualTo("MiniMax-M2.7");
        assertThat(genBody.get("level")).isEqualTo("DEFAULT");
        assertThat(genBody.get("traceId")).isEqualTo("abc12345");

        var usage = (Map<String, Object>) genBody.get("usage");
        assertThat(usage.get("input")).isEqualTo(1595);
        assertThat(usage.get("output")).isEqualTo(368);
        assertThat(usage.get("total")).isEqualTo(1963);
        assertThat(usage.get("totalCost")).isEqualTo(0.004539);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 异常调用映射为ERROR层级并带statusMessage() {
        Map<String, Object> batch = LangfuseExporter.buildIngestionBatch(trace("error", "boom"));
        var events = (List<Map<String, Object>>) batch.get("batch");
        var genBody = (Map<String, Object>) events.get(1).get("body");

        assertThat(genBody.get("level")).isEqualTo("ERROR");
        assertThat(genBody.get("statusMessage")).isEqualTo("boom");
    }

    @Test
    void host去除尾部斜杠() {
        var p = new LangfuseProperties(true, "https://lf.example.com/", "pk", "sk");
        assertThat(p.hostOrDefault()).isEqualTo("https://lf.example.com");
    }
}
