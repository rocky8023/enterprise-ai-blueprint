package io.github.mars.blueprint.infra.observability;

import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 调用记录存储——内存环形缓冲，最新的在最前，超出容量丢弃最旧的。
 * <p>
 * 仅用于 blueprint 演示与本地排查；生产环境请替换为数据库 / 时序库 / Langfuse 等持久化方案，
 * 否则重启即丢、且无法跨实例聚合。
 */
@Component
public class TraceStore {

    private final Deque<LlmCallTrace> traces = new ConcurrentLinkedDeque<>();
    private final int maxTraces;

    public TraceStore(ObservabilityProperties properties) {
        this.maxTraces = properties.maxTracesOrDefault();
    }

    public void record(LlmCallTrace trace) {
        traces.addFirst(trace);
        while (traces.size() > maxTraces) {
            traces.pollLast();
        }
    }

    public List<LlmCallTrace> recent(int limit) {
        return traces.stream().limit(Math.max(1, limit)).toList();
    }

    public Optional<LlmCallTrace> byId(String traceId) {
        return traces.stream().filter(t -> t.traceId().equals(traceId)).findFirst();
    }

    public void clear() {
        traces.clear();
    }

    public Stats stats() {
        int count = 0;
        long totalTokens = 0;
        double totalCost = 0;
        long latencySum = 0;
        int errorCount = 0;
        for (LlmCallTrace t : traces) {
            count++;
            if (t.totalTokens() != null) {
                totalTokens += t.totalTokens();
            }
            if (t.cost() != null) {
                totalCost += t.cost();
            }
            latencySum += t.latencyMs();
            if ("error".equals(t.status())) {
                errorCount++;
            }
        }
        long avgLatency = count == 0 ? 0 : latencySum / count;
        double cost = Math.round(totalCost * 1_000_000) / 1_000_000.0;
        return new Stats(count, totalTokens, cost, avgLatency, errorCount);
    }

    public record Stats(int count, long totalTokens, double totalCost, long avgLatencyMs, int errorCount) {
    }
}
