package io.github.mars.blueprint.infra.observability;

/**
 * 调用记录的去向（sink）。
 * <p>
 * {@link LlmTracer} 采到一条 {@link LlmCallTrace} 后扇出给所有 sink。内置 {@link TraceStore}（内存）
 * 始终存在；接入 Langfuse 等外部观测后端时，再实现一个 sink 即可——不改埋点、不动调用方。
 * 单个 sink 失败不应影响其它 sink 与主调用链。
 */
public interface TraceSink {

    void accept(LlmCallTrace trace);
}
