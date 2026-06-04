package io.github.mars.blueprint.infra.observability;

import java.time.Instant;
import java.util.Map;

/**
 * 一次 LLM 调用的完整观测记录。
 * <p>
 * 这是可观测性增强的核心数据结构：把每一次大模型调用拆开看清楚——
 * 用的哪个 prompt、渲染后的全文、传入的变量、命中的模型、消耗的 token、折算的成本、耗时与结果。
 * <p>
 * 生产环境应落到 APM / Langfuse / 数据库；本 blueprint 用内存环形缓冲（{@link TraceStore}）做演示。
 */
public record LlmCallTrace(
        String traceId,
        Instant timestamp,
        String type,                 // chat / embedding
        String promptId,             // 如 rag.company-qa@v1；自由对话为 null
        String preset,               // 当前生效的厂商 preset（minimax / deepseek ...）
        String model,                // 响应元数据中解析出的真实模型名
        Map<String, Object> variables,   // 渲染 prompt 时传入的变量
        String renderedPrompt,       // 发给模型的最终 prompt 全文
        String response,             // 模型返回正文
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Double cost,                 // 折算成本（货币单位见 ObservabilityProperties.currency）
        long latencyMs,
        String status,               // ok / error
        String errorMessage
) {
}
