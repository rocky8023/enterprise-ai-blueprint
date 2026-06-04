package io.github.mars.blueprint.api;

import io.github.mars.blueprint.infra.observability.LlmCallTrace;
import io.github.mars.blueprint.infra.observability.TraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用链查询端点。
 * <p>
 * 列表只回摘要（避免 prompt 全文刷屏），详情按 traceId 回完整记录，stats 给聚合视图。
 * 这就是「看得见每次调用花了多少钱」的入口。
 */
@RestController
@RequestMapping("/api/traces")
public class TraceController {

    private final TraceStore traceStore;

    public TraceController(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    /** 最近调用摘要列表。 */
    @GetMapping
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "50") int limit) {
        return traceStore.recent(limit).stream().map(TraceController::toSummary).toList();
    }

    /** 单条调用完整记录（含 prompt 全文与返回正文）。 */
    @GetMapping("/{traceId}")
    public ResponseEntity<LlmCallTrace> detail(@PathVariable String traceId) {
        return traceStore.byId(traceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 聚合统计：总调用数、总 token、总成本、平均耗时、错误数。 */
    @GetMapping("/stats")
    public TraceStore.Stats stats() {
        return traceStore.stats();
    }

    /** 清空（仅 demo 便利）。 */
    @DeleteMapping
    public Map<String, String> clear() {
        traceStore.clear();
        return Map.of("status", "cleared");
    }

    private static Map<String, Object> toSummary(LlmCallTrace t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId", t.traceId());
        m.put("timestamp", t.timestamp());
        m.put("type", t.type());
        m.put("promptId", t.promptId());
        m.put("preset", t.preset());
        m.put("model", t.model());
        m.put("promptTokens", t.promptTokens());
        m.put("completionTokens", t.completionTokens());
        m.put("totalTokens", t.totalTokens());
        m.put("cost", t.cost());
        m.put("latencyMs", t.latencyMs());
        m.put("status", t.status());
        m.put("responsePreview", preview(t.response()));
        return m;
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "..." : oneLine;
    }
}
