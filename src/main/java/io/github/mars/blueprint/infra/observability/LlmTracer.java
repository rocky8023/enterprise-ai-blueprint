package io.github.mars.blueprint.infra.observability;

import io.github.mars.blueprint.infra.llm.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * LLM 调用埋点器：在调用点包一层，自动采集耗时、token、成本，落入 {@link TraceStore}。
 * <p>
 * 用「显式包裹」而非 AOP / Advisor，是为了让 blueprint 读者一眼看清观测发生在哪、采了什么；
 * 调用方只需把原来的 {@code call().chatResponse()} 包进 {@link #traceChat} 即可。
 */
@Component
public class LlmTracer {

    private static final Logger log = LoggerFactory.getLogger(LlmTracer.class);

    private final TraceStore store;
    private final ObservabilityProperties properties;
    private final ProviderRegistry providerRegistry;

    public LlmTracer(TraceStore store,
                     ObservabilityProperties properties,
                     ProviderRegistry providerRegistry) {
        this.store = store;
        this.properties = properties;
        this.providerRegistry = providerRegistry;
    }

    /** chat 调用的语义上下文：哪个 prompt、传了什么变量、最终全文。 */
    public record ChatTraceContext(String promptId, Map<String, Object> variables, String renderedPrompt) {
    }

    /**
     * 包裹一次 chat 调用，记录观测数据后原样返回响应（异常也会记录并继续抛出）。
     */
    public ChatResponse traceChat(ChatTraceContext ctx, Supplier<ChatResponse> call) {
        long start = System.nanoTime();
        String preset = providerRegistry.activeChatPreset();
        try {
            ChatResponse response = call.get();
            store.record(buildOk(ctx, preset, response, elapsedMs(start)));
            return response;
        } catch (RuntimeException e) {
            store.record(buildError(ctx, preset, elapsedMs(start), e));
            throw e;
        }
    }

    private LlmCallTrace buildOk(ChatTraceContext ctx, String preset, ChatResponse response, long latencyMs) {
        ChatResponseMetadata meta = response.getMetadata();
        String model = meta != null ? meta.getModel() : null;

        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        Usage usage = meta != null ? meta.getUsage() : null;
        if (usage != null) {
            promptTokens = usage.getPromptTokens();
            completionTokens = usage.getCompletionTokens();
            totalTokens = usage.getTotalTokens();
        }

        String responseText = "";
        try {
            responseText = response.getResult().getOutput().getText();
        } catch (RuntimeException ignore) {
            // 某些异常响应可能没有正文，记录为空即可
        }

        Double cost = computeCost(model, promptTokens, completionTokens);

        LlmCallTrace trace = new LlmCallTrace(
                newId(), Instant.now(), "chat",
                ctx.promptId(), preset, model,
                ctx.variables(), ctx.renderedPrompt(), responseText,
                promptTokens, completionTokens, totalTokens, cost,
                latencyMs, "ok", null);

        log.info("LLM trace [{}] prompt={} model={} tokens={}/{}/{} cost={}{} latency={}ms",
                trace.traceId(), ctx.promptId(), model,
                promptTokens, completionTokens, totalTokens,
                cost == null ? "?" : cost, properties.currencyOrDefault(), latencyMs);

        return trace;
    }

    private LlmCallTrace buildError(ChatTraceContext ctx, String preset, long latencyMs, RuntimeException e) {
        log.warn("LLM trace [error] prompt={} latency={}ms error={}", ctx.promptId(), latencyMs, e.toString());
        return new LlmCallTrace(
                newId(), Instant.now(), "chat",
                ctx.promptId(), preset, null,
                ctx.variables(), ctx.renderedPrompt(), null,
                null, null, null, null,
                latencyMs, "error", e.getMessage());
    }

    /** cost = input单价 * promptTokens/1000 + output单价 * completionTokens/1000。缺定价或缺 token 则返回 null。 */
    private Double computeCost(String model, Integer promptTokens, Integer completionTokens) {
        ObservabilityProperties.ModelPrice price = properties.priceFor(model);
        if (price == null || promptTokens == null || completionTokens == null) {
            return null;
        }
        double cost = price.input() * promptTokens / 1000.0
                + price.output() * completionTokens / 1000.0;
        return Math.round(cost * 1_000_000) / 1_000_000.0;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
