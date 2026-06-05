package io.github.mars.blueprint.infra.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PresetChatRouterTest {

    private final ChatClient defaultClient = mock(ChatClient.class);

    private ProviderRegistry registry() {
        var minimax = new ProviderPreset("https://api.minimaxi.com", "MiniMax-M2.7", 0.7, null, null, null);
        var deepseek = new ProviderPreset("https://api.deepseek.com", "deepseek-chat", 0.7, null, null, null);
        var dashscope = new ProviderPreset("https://dashscope.aliyuncs.com/compatible-mode", null, null,
                "text-embedding-v3", null, null);
        var props = new ProviderProperties(
                new ProviderProperties.Selector("minimax"),
                new ProviderProperties.Selector("dashscope"),
                Map.of("minimax", minimax, "deepseek", deepseek, "dashscope", dashscope));
        return new ProviderRegistry(props);
    }

    private PresetChatRouter router(MockEnvironment env) {
        return new PresetChatRouter(defaultClient, registry(), env);
    }

    @Test
    void preset为空时返回默认client() {
        var r = router(new MockEnvironment()).resolve(null);

        assertThat(r.preset()).isEqualTo("minimax");
        assertThat(r.model()).isEqualTo("MiniMax-M2.7");
        assertThat(r.client()).isSameAs(defaultClient);
    }

    @Test
    void 显式指定默认preset也复用默认client() {
        var r = router(new MockEnvironment()).resolve("minimax");

        assertThat(r.client()).isSameAs(defaultClient);
    }

    @Test
    void 配了key的其它preset现场构建并缓存() {
        var env = new MockEnvironment().withProperty("BLUEPRINT_KEY_DEEPSEEK", "fake-key");
        var router = router(env);

        var r1 = router.resolve("deepseek");
        var r2 = router.resolve("deepseek");

        assertThat(r1.preset()).isEqualTo("deepseek");
        assertThat(r1.model()).isEqualTo("deepseek-chat");
        assertThat(r1.client()).isNotSameAs(defaultClient);
        assertThat(r2.client()).isSameAs(r1.client());   // 命中缓存，同一实例
    }

    @Test
    void 未配key的preset直接fail_fast() {
        assertThatThrownBy(() -> router(new MockEnvironment()).resolve("deepseek"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLUEPRINT_KEY_DEEPSEEK");
    }

    @Test
    void 未知preset_fail_fast() {
        assertThatThrownBy(() -> router(new MockEnvironment()).resolve("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到");
    }

    @Test
    void embedding专用preset不支持chat() {
        var env = new MockEnvironment().withProperty("BLUEPRINT_KEY_DASHSCOPE", "fake-key");
        assertThatThrownBy(() -> router(env).resolve("dashscope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持 chat");
    }

    @Test
    void availablePresets含默认与配key且支持chat的() {
        var env = new MockEnvironment()
                .withProperty("BLUEPRINT_KEY_DEEPSEEK", "fake-key")
                .withProperty("BLUEPRINT_KEY_DASHSCOPE", "fake-key");   // 即便配了 key，无 chat-model 也不计入

        assertThat(router(env).availablePresets())
                .contains("minimax", "deepseek")
                .doesNotContain("dashscope");
    }
}
