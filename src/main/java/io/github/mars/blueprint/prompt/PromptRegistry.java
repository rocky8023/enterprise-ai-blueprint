package io.github.mars.blueprint.prompt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptRegistry {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);
    private static final String DEFAULT_LOCATION = "classpath:prompts/*.md";
    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);

    private final PromptProperties properties;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final Map<String, Map<String, PromptDefinition>> registry = new HashMap<>();

    public PromptRegistry(PromptProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void load() throws IOException {
        String location = properties.location() != null ? properties.location() : DEFAULT_LOCATION;
        Resource[] resources = resolver.getResources(location);
        log.info("发现 {} 个 prompt 模板文件，开始加载...", resources.length);

        for (Resource resource : resources) {
            try {
                PromptDefinition def = parse(resource);
                registry.computeIfAbsent(def.key(), k -> new HashMap<>())
                        .put(def.version(), def);
                log.info("加载 prompt: {} ({})", def.fullId(), def.metadata().status());
            } catch (Exception e) {
                log.error("解析 prompt 模板失败: {} - {}", resource.getFilename(), e.getMessage());
            }
        }

        if (properties.active() != null) {
            properties.active().forEach((key, version) ->
                    log.info("活跃版本配置: {} -> {}", key, version));
        }
        log.info("prompt 注册表加载完成：共 {} 个 prompt key", registry.size());
    }

    public PromptDefinition get(String key, String version) {
        Map<String, PromptDefinition> versions = registry.get(key);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("Prompt key 未找到: " + key);
        }

        String resolvedVersion = version != null
                ? version
                : (properties.active() != null ? properties.active().get(key) : null);

        if (resolvedVersion == null) {
            throw new IllegalStateException(
                    "未配置 prompt 活跃版本，且请求未指定 version: " + key);
        }

        PromptDefinition def = versions.get(resolvedVersion);
        if (def == null) {
            throw new IllegalArgumentException(
                    "Prompt 版本未找到: " + key + "@" + resolvedVersion
                    + "，可用版本: " + versions.keySet());
        }
        return def;
    }

    public PromptDefinition get(String key) {
        return get(key, null);
    }

    public Map<String, Map<String, PromptDefinition>> all() {
        return Collections.unmodifiableMap(registry);
    }

    @SuppressWarnings("unchecked")
    private PromptDefinition parse(Resource resource) throws IOException {
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        Matcher m = FRONTMATTER.matcher(content);
        if (!m.matches()) {
            throw new IllegalArgumentException("缺少 YAML frontmatter: " + resource.getFilename());
        }

        String yamlText = m.group(1);
        String body = m.group(2);

        Map<String, Object> meta = new Yaml().load(yamlText);
        if (meta == null) {
            throw new IllegalArgumentException("frontmatter 为空: " + resource.getFilename());
        }

        String key = (String) meta.get("key");
        String version = (String) meta.get("version");
        if (key == null || version == null) {
            throw new IllegalArgumentException(
                    "frontmatter 缺少 key 或 version: " + resource.getFilename());
        }

        PromptMetadata metadata = new PromptMetadata(
                (String) meta.get("author"),
                (String) meta.get("description"),
                String.valueOf(meta.getOrDefault("created", "")),
                (String) meta.getOrDefault("status", "unknown"),
                (List<String>) meta.getOrDefault("tags", List.of()),
                parseExamples(meta.get("examples")),
                (String) meta.get("based_on")
        );

        return new PromptDefinition(key, version, body, metadata);
    }

    @SuppressWarnings("unchecked")
    private List<PromptExample> parseExamples(Object examples) {
        if (!(examples instanceof List<?> list)) {
            return List.of();
        }
        List<PromptExample> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String question = (String) map.get("question");
                Object keywords = map.get("expected_keywords");
                List<String> kw = keywords instanceof List<?>
                        ? (List<String>) keywords
                        : List.of();
                result.add(new PromptExample(question, kw));
            }
        }
        return result;
    }
}
