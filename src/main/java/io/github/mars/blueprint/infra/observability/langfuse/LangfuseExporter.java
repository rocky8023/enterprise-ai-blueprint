package io.github.mars.blueprint.infra.observability.langfuse;

import io.github.mars.blueprint.infra.observability.LlmCallTrace;
import io.github.mars.blueprint.infra.observability.TraceSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 把每条 {@link LlmCallTrace} 异步推送到 Langfuse（通过其 ingestion REST API）。
 * <p>
 * 这是「把内存 TraceStore 换成生产级观测后端」的样板：埋点（LlmTracer）不变，
 * 只多注册一个 {@link TraceSink}。默认关闭，配置 enabled=true 且提供密钥后生效。
 * 推送在独立线程异步进行，失败仅记日志，不影响主调用链与内存留痕。
 */
@Component
@ConditionalOnProperty(prefix = "blueprint.observability.langfuse", name = "enabled", havingValue = "true")
public class LangfuseExporter implements TraceSink {

    private static final Logger log = LoggerFactory.getLogger(LangfuseExporter.class);
    private static final String INGESTION_PATH = "/api/public/ingestion";

    private final LangfuseProperties props;
    private final RestClient client;
    private final ExecutorService executor;
    private final boolean ready;

    public LangfuseExporter(LangfuseProperties props) {
        this.props = props;
        this.ready = props.publicKey() != null && !props.publicKey().isBlank()
                && props.secretKey() != null && !props.secretKey().isBlank();
        if (!ready) {
            log.warn("Langfuse 已启用但未配置 publicKey/secretKey，推送将被跳过");
            this.client = null;
            this.executor = null;
            return;
        }
        String basic = Base64.getEncoder().encodeToString(
                (props.publicKey() + ":" + props.secretKey()).getBytes(StandardCharsets.UTF_8));
        this.client = RestClient.builder()
                .baseUrl(props.hostOrDefault())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "langfuse-exporter");
            t.setDaemon(true);
            return t;
        });
        log.info("Langfuse 导出已启用，host={}", props.hostOrDefault());
    }

    @Override
    public void accept(LlmCallTrace trace) {
        if (!ready) {
            return;
        }
        executor.submit(() -> {
            try {
                client.post()
                        .uri(INGESTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildIngestionBatch(trace))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException e) {
                log.warn("Langfuse 推送失败 [{}]: {}", trace.traceId(), e.toString());
            }
        });
    }

    /**
     * 构造 Langfuse ingestion 批次：一个 trace-create + 一个 generation-create（携带 usage 与成本）。
     * 包级可见，便于离线单测断言结构。
     */
    static Map<String, Object> buildIngestionBatch(LlmCallTrace t) {
        Instant end = t.timestamp() != null ? t.timestamp() : Instant.now();
        Instant start = end.minusMillis(Math.max(0, t.latencyMs()));
        boolean ok = "ok".equals(t.status());
        String name = t.promptId() != null ? t.promptId() : "chat";

        Map<String, Object> traceBody = new LinkedHashMap<>();
        traceBody.put("id", t.traceId());
        traceBody.put("timestamp", start.toString());
        traceBody.put("name", name);
        traceBody.put("input", t.renderedPrompt());
        traceBody.put("output", t.response());
        traceBody.put("metadata", Map.of(
                "preset", str(t.preset()), "model", str(t.model()), "type", str(t.type())));

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("input", t.promptTokens());
        usage.put("output", t.completionTokens());
        usage.put("total", t.totalTokens());
        usage.put("unit", "TOKENS");
        if (t.cost() != null) {
            usage.put("totalCost", t.cost());
        }

        Map<String, Object> genBody = new LinkedHashMap<>();
        genBody.put("id", "gen-" + t.traceId());
        genBody.put("traceId", t.traceId());
        genBody.put("type", "GENERATION");
        genBody.put("name", name);
        genBody.put("startTime", start.toString());
        genBody.put("endTime", end.toString());
        genBody.put("model", t.model());
        genBody.put("input", t.renderedPrompt());
        genBody.put("output", t.response());
        genBody.put("usage", usage);
        genBody.put("metadata", Map.of("preset", str(t.preset()), "variables", t.variables() == null ? Map.of() : t.variables()));
        genBody.put("level", ok ? "DEFAULT" : "ERROR");
        if (!ok && t.errorMessage() != null) {
            genBody.put("statusMessage", t.errorMessage());
        }

        return Map.of("batch", List.of(
                event("trace-create", traceBody, start),
                event("generation-create", genBody, start)));
    }

    private static Map<String, Object> event(String type, Map<String, Object> body, Instant ts) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", UUID.randomUUID().toString());
        e.put("type", type);
        e.put("timestamp", ts.toString());
        e.put("body", body);
        return e;
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
