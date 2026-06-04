package io.github.mars.blueprint.infra.observability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityPropertiesTest {

    private ObservabilityProperties props(List<ObservabilityProperties.ModelPrice> pricing) {
        return new ObservabilityProperties(100, "CNY", pricing);
    }

    @Test
    void 精确匹配模型单价() {
        var props = props(List.of(
                new ObservabilityProperties.ModelPrice("MiniMax-M2.7", 0.001, 0.008),
                new ObservabilityProperties.ModelPrice("default", 0.002, 0.006)));

        var price = props.priceFor("MiniMax-M2.7");

        assertThat(price).isNotNull();
        assertThat(price.input()).isEqualTo(0.001);
        assertThat(price.output()).isEqualTo(0.008);
    }

    @Test
    void 模型名大小写不敏感() {
        var props = props(List.of(
                new ObservabilityProperties.ModelPrice("gpt-4o-mini", 0.0011, 0.0044)));

        assertThat(props.priceFor("GPT-4O-MINI")).isNotNull();
    }

    @Test
    void 未命中时回退到default() {
        var props = props(List.of(
                new ObservabilityProperties.ModelPrice("deepseek-chat", 0.001, 0.002),
                new ObservabilityProperties.ModelPrice("default", 0.002, 0.006)));

        var price = props.priceFor("未知模型");

        assertThat(price).isNotNull();
        assertThat(price.model()).isEqualTo("default");
    }

    @Test
    void 无匹配且无default时返回null() {
        var props = props(List.of(
                new ObservabilityProperties.ModelPrice("glm-4.5", 0.0005, 0.002)));

        assertThat(props.priceFor("unknown")).isNull();
        assertThat(props.priceFor(null)).isNull();
    }

    @Test
    void 默认值兜底() {
        var props = new ObservabilityProperties(null, null, null);

        assertThat(props.maxTracesOrDefault()).isEqualTo(200);
        assertThat(props.currencyOrDefault()).isEqualTo("CNY");
        assertThat(props.priceFor("any")).isNull();
    }
}
