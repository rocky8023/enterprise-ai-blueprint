package io.github.mars.blueprint.infra.llm;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Set;

/**
 * Chat 路由：把「选哪个厂商」从启动期下沉到<b>请求维度</b>。
 * <p>
 * 一阶段（{@link ProviderPresetEnvironmentPostProcessor}）在启动早期把默认 preset 解析进
 * Spring AI 的 OpenAI autoconfig，全进程只有一个 chat 实例；二阶段在它之上加一层自家接口，
 * 让一次请求可以临时切到别的 preset（前提是该 preset 配了 key）。
 */
public interface ChatRouter {

    /** 解析出该 preset 对应的 ChatClient；preset 为空则用默认。未知/无 key/不支持 chat 时 fail-fast。 */
    Resolved resolve(String preset);

    /** 启动时选定的默认 chat preset。 */
    String defaultPreset();

    /** 当前可被请求路由到的 preset 集合（默认 preset + 配了 key 且支持 chat 的）。 */
    Set<String> availablePresets();

    /** 解析结果：实际使用的 preset、模型名与对应的 ChatClient。 */
    record Resolved(String preset, String model, ChatClient client) {
    }
}
