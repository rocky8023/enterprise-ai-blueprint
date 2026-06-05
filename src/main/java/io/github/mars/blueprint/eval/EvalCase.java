package io.github.mars.blueprint.eval;

import java.util.List;

/**
 * 单条评测用例结果：一个 example 问题跑完后的命中情况。
 */
public record EvalCase(
        String question,
        List<String> expectedKeywords,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        boolean passed,
        String answer
) {
}
