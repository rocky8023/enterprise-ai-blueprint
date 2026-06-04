package io.github.mars.blueprint.infra.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 可观测性配置。
 * <p>
 * 关键点是<b>定价表外置为配置</b>：成本核算不该硬编码在代码里，各厂商单价会变，
 * 不同企业拿到的折扣价也不同。这里用一张 model -> 单价 的表，运行时按响应里的真实模型名匹配。
 */
@ConfigurationProperties(prefix = "blueprint.observability")
public record ObservabilityProperties(
        Integer maxTraces,
        String currency,
        List<ModelPrice> pricing
) {

    public int maxTracesOrDefault() {
        return maxTraces == null || maxTraces <= 0 ? 200 : maxTraces;
    }

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "CNY" : currency;
    }

    /**
     * 单条模型定价。input / output 单位为「货币 / 1K tokens」。
     * model 取 "default" 作为未命中时的兜底单价。
     */
    public record ModelPrice(String model, double input, double output) {
    }

    /** 按模型名匹配单价（大小写不敏感），未命中则回退到 default，再无则返回 null。 */
    public ModelPrice priceFor(String model) {
        if (pricing == null || pricing.isEmpty()) {
            return null;
        }
        ModelPrice fallback = null;
        for (ModelPrice p : pricing) {
            if (p.model() == null) {
                continue;
            }
            if (model != null && p.model().equalsIgnoreCase(model)) {
                return p;
            }
            if ("default".equalsIgnoreCase(p.model())) {
                fallback = p;
            }
        }
        return fallback;
    }
}
