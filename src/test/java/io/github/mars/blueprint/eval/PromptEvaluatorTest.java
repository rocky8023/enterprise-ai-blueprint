package io.github.mars.blueprint.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptEvaluatorTest {

    @Test
    void 全部关键词命中则无缺失() {
        var m = PromptEvaluator.match("入职满一年享受 5 天年假，来源：leave_policy.md",
                List.of("5 天", "leave_policy"));

        assertThat(m.matched()).containsExactly("5 天", "leave_policy");
        assertThat(m.missing()).isEmpty();
    }

    @Test
    void 部分缺失被记录() {
        var m = PromptEvaluator.match("入职满一年享受 5 天年假",
                List.of("5 天", "leave_policy"));

        assertThat(m.matched()).containsExactly("5 天");
        assertThat(m.missing()).containsExactly("leave_policy");
    }

    @Test
    void 关键词大小写不敏感() {
        var m = PromptEvaluator.match("来源：LEAVE_POLICY.md", List.of("leave_policy"));

        assertThat(m.missing()).isEmpty();
    }

    @Test
    void 空答案则全部缺失() {
        var m = PromptEvaluator.match(null, List.of("5 天", "leave_policy"));

        assertThat(m.matched()).isEmpty();
        assertThat(m.missing()).containsExactly("5 天", "leave_policy");
    }

    @Test
    void 无期望关键词则无缺失() {
        var m = PromptEvaluator.match("任意答案", List.of());

        assertThat(m.matched()).isEmpty();
        assertThat(m.missing()).isEmpty();
    }
}
