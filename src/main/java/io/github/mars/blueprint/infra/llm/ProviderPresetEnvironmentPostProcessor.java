package io.github.mars.blueprint.infra.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在 Spring 启动早期把 BLUEPRINT_CHAT_PRESET / BLUEPRINT_EMBEDDING_PRESET 解析成
 * spring.ai.openai.chat.* / embedding.* 具体值，使 Spring AI 的 OpenAI autoconfig
 * 在 bean 装配时拿到的就是最终值。
 *
 * 优先级（从高到低）：
 *   显式 BLUEPRINT_*_BASE_URL / BLUEPRINT_*_MODEL  > preset 字典  > 不注入（让 Spring AI 自决）
 *
 * 未知 preset 名称直接抛异常，启动 fail-fast。
 */
public class ProviderPresetEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ProviderPresetEnvironmentPostProcessor.class);
    private static final String PROPERTY_SOURCE_NAME = "providerPresetResolved";
    private static final String PRESET_PREFIX = "blueprint.provider.providers.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Map<String, Object> resolved = new HashMap<>();
        resolveChannel(env, "chat", resolved);
        resolveChannel(env, "embedding", resolved);

        if (!resolved.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, resolved));
        }
    }

    private void resolveChannel(ConfigurableEnvironment env, String channel, Map<String, Object> out) {
        String upper = channel.toUpperCase(Locale.ROOT);
        String explicitBaseUrl = env.getProperty("BLUEPRINT_" + upper + "_BASE_URL");
        String explicitModel = env.getProperty("BLUEPRINT_" + upper + "_MODEL");

        String presetName = env.getProperty("BLUEPRINT_" + upper + "_PRESET");
        if (presetName == null || presetName.isBlank()) {
            presetName = env.getProperty("blueprint.provider." + channel + ".preset");
        }

        String baseUrl = explicitBaseUrl;
        String model = explicitModel;
        Double temperature = null;
        String source;

        if (presetName != null && !presetName.isBlank()) {
            String prefix = PRESET_PREFIX + presetName + ".";
            String presetBaseUrl = env.getProperty(prefix + "base-url");
            if (presetBaseUrl == null) {
                throw new IllegalStateException(
                        "未知 " + channel + " preset: '" + presetName + "'. "
                        + "可用 preset: " + knownPresetNames(env) + ". "
                        + "在 providers.yml 中追加 preset，或通过 BLUEPRINT_" + upper + "_BASE_URL 直接显式指定 base-url。");
            }
            String presetModelKey = "chat".equals(channel) ? "chat-model" : "embedding-model";
            String presetModel = env.getProperty(prefix + presetModelKey);

            if ("chat".equals(channel)) {
                String tempRaw = env.getProperty(prefix + "chat-temperature");
                if (tempRaw != null && !tempRaw.isBlank()) {
                    try {
                        temperature = Double.valueOf(tempRaw);
                    } catch (NumberFormatException e) {
                        log.warn("preset {} 的 chat-temperature 不是合法数字: {}", presetName, tempRaw);
                    }
                }
            }

            if (isBlank(baseUrl)) baseUrl = presetBaseUrl;
            if (isBlank(model)) model = presetModel;
            source = "preset=" + presetName
                    + (isBlank(explicitBaseUrl) ? "" : " (base-url 被 env 显式覆盖)")
                    + (isBlank(explicitModel) ? "" : " (model 被 env 显式覆盖)");
        } else {
            source = "无 preset，仅使用显式 env";
        }

        if (isBlank(baseUrl)) {
            // 既没 preset 也没显式 base-url —— 不注入，让 Spring AI 自己处理（多半会因 api-key 缺失报错）
            return;
        }

        out.put("spring.ai.openai." + channel + ".base-url", baseUrl);
        if (!isBlank(model)) {
            out.put("spring.ai.openai." + channel + ".options.model", model);
        }
        if (temperature != null) {
            out.put("spring.ai.openai." + channel + ".options.temperature", temperature);
        }

        log.info("Provider [{}] 已解析: base-url={}, model={}{}, source={}",
                channel, baseUrl, model,
                temperature != null ? ", temperature=" + temperature : "",
                source);
    }

    private Set<String> knownPresetNames(ConfigurableEnvironment env) {
        Set<String> names = new LinkedHashSet<>();
        env.getPropertySources().forEach(source -> {
            if (source instanceof EnumerablePropertySource<?> eps) {
                for (String key : eps.getPropertyNames()) {
                    if (key.startsWith(PRESET_PREFIX) && key.endsWith(".base-url")) {
                        String name = key.substring(PRESET_PREFIX.length(),
                                key.length() - ".base-url".length());
                        names.add(name);
                    }
                }
            }
        });
        return names;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public int getOrder() {
        // 在 ConfigDataEnvironmentPostProcessor 之后运行，确保 application.yml / providers.yml 已加载
        return Ordered.LOWEST_PRECEDENCE;
    }
}
