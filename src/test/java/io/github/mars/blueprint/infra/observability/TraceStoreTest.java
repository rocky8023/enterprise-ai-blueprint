package io.github.mars.blueprint.infra.observability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceStoreTest {

    private TraceStore store(int maxTraces) {
        return new TraceStore(new ObservabilityProperties(maxTraces, "CNY", List.of()));
    }

    private LlmCallTrace trace(String id, Integer totalTokens, Double cost, long latencyMs, String status) {
        return new LlmCallTrace(
                id, Instant.now(), "chat", "rag.company-qa@v1", "minimax", "MiniMax-M2.7",
                Map.of("question", "q"), "rendered prompt", "answer",
                totalTokens == null ? null : 10, totalTokens == null ? null : totalTokens - 10, totalTokens,
                cost, latencyMs, status, status.equals("error") ? "boom" : null);
    }

    @Test
    void 最新记录在最前() {
        var store = store(10);
        store.record(trace("a", 100, 0.01, 50, "ok"));
        store.record(trace("b", 200, 0.02, 60, "ok"));

        List<LlmCallTrace> recent = store.recent(10);

        assertThat(recent).extracting(LlmCallTrace::traceId).containsExactly("b", "a");
    }

    @Test
    void 超出容量丢弃最旧() {
        var store = store(2);
        store.record(trace("a", 100, 0.01, 50, "ok"));
        store.record(trace("b", 100, 0.01, 50, "ok"));
        store.record(trace("c", 100, 0.01, 50, "ok"));

        assertThat(store.recent(10)).extracting(LlmCallTrace::traceId).containsExactly("c", "b");
        assertThat(store.byId("a")).isEmpty();
    }

    @Test
    void byId能取到完整记录() {
        var store = store(10);
        store.record(trace("x", 100, 0.01, 50, "ok"));

        assertThat(store.byId("x")).isPresent();
        assertThat(store.byId("missing")).isEmpty();
    }

    @Test
    void 聚合统计正确() {
        var store = store(10);
        store.record(trace("a", 100, 0.010, 40, "ok"));
        store.record(trace("b", 300, 0.020, 60, "ok"));
        store.record(trace("c", null, null, 80, "error"));   // token/cost 缺失不计入求和

        TraceStore.Stats stats = store.stats();

        assertThat(stats.count()).isEqualTo(3);
        assertThat(stats.totalTokens()).isEqualTo(400);
        assertThat(stats.totalCost()).isEqualTo(0.03);
        assertThat(stats.avgLatencyMs()).isEqualTo(60);   // (40+60+80)/3
        assertThat(stats.errorCount()).isEqualTo(1);
    }

    @Test
    void 清空后无记录() {
        var store = store(10);
        store.record(trace("a", 100, 0.01, 50, "ok"));
        store.clear();

        assertThat(store.recent(10)).isEmpty();
        assertThat(store.stats().count()).isZero();
    }
}
