package io.github.mars.blueprint.infra.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ChatRouter} 的默认实现。
 * <p>
 * 默认 preset 直接复用 Spring 自动装配的 {@link ChatClient}（一阶段已就绪，不重复构建）；
 * 其它 preset 在首次被请求时，用 preset 字典里的 base-url/model + 环境变量
 * <code>BLUEPRINT_KEY_&lt;PRESET&gt;</code> 里的 key 现场构建 ChatClient 并缓存。
 * <p>
 * 三种情况 fail-fast：未知 preset、preset 未配 key、preset 不支持 chat（无 chat-model）。
 */
@Component
public class PresetChatRouter implements ChatRouter {

    private static final Logger log = LoggerFactory.getLogger(PresetChatRouter.class);

    private final ChatClient defaultClient;
    private final ProviderRegistry registry;
    private final Environment env;
    private final String defaultPreset;
    private final Map<String, ChatClient> cache = new ConcurrentHashMap<>();

    public PresetChatRouter(ChatClient defaultClient, ProviderRegistry registry, Environment env) {
        this.defaultClient = defaultClient;
        this.registry = registry;
        this.env = env;
        this.defaultPreset = registry.activeChatPreset();
    }

    @Override
    public String defaultPreset() {
        return defaultPreset;
    }

    @Override
    public Resolved resolve(String preset) {
        if (isBlank(preset) || preset.equals(defaultPreset)) {
            String model = defaultPreset == null ? null : registry.get(defaultPreset).chatModel();
            return new Resolved(defaultPreset, model, defaultClient);
        }
        ProviderPreset p = registry.get(preset); // 未知 preset 在此 fail-fast（附可用列表）
        if (isBlank(p.chatModel())) {
            throw new IllegalArgumentException(
                    "preset '" + preset + "' 不支持 chat（无 chat-model），它可能是 embedding 专用 preset。");
        }
        ChatClient client = cache.computeIfAbsent(preset, name -> build(name, p));
        return new Resolved(preset, p.chatModel(), client);
    }

    @Override
    public Set<String> availablePresets() {
        Set<String> out = new LinkedHashSet<>();
        if (defaultPreset != null) {
            out.add(defaultPreset);
        }
        registry.all().forEach((name, p) -> {
            if (!isBlank(p.chatModel()) && keyOf(name) != null) {
                out.add(name);
            }
        });
        return out;
    }

    private ChatClient build(String preset, ProviderPreset p) {
        String key = keyOf(preset);
        if (key == null) {
            throw new IllegalArgumentException(
                    "preset '" + preset + "' 未配置 API key，请设置环境变量 " + envKeyName(preset) + " 后重试。");
        }
        OpenAiApi api = OpenAiApi.builder().baseUrl(p.baseUrl()).apiKey(key).build();
        OpenAiChatOptions.Builder opts = OpenAiChatOptions.builder().model(p.chatModel());
        if (p.chatTemperature() != null) {
            opts.temperature(p.chatTemperature());
        }
        OpenAiChatModel model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(opts.build()).build();
        log.info("为 preset '{}' 构建 ChatClient（model={}, base-url={}）", preset, p.chatModel(), p.baseUrl());
        return ChatClient.create(model);
    }

    private String keyOf(String preset) {
        String v = env.getProperty(envKeyName(preset));
        return isBlank(v) ? null : v;
    }

    /** preset 名 -> 环境变量名，如 deepseek -> BLUEPRINT_KEY_DEEPSEEK。 */
    private static String envKeyName(String preset) {
        return "BLUEPRINT_KEY_" + preset.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
