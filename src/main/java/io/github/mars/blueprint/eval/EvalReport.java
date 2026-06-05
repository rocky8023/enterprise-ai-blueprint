package io.github.mars.blueprint.eval;

import java.time.Instant;
import java.util.List;

/**
 * 一个 prompt 版本的评测报告。
 * <p>
 * 这是「Prompt 即代码 → 就该有回归测试」的落点：改完 prompt 跑一遍，
 * 用 pass 率直观看出这次改动有没有把原本答得对的问题改坏。
 */
public record EvalReport(
        String promptKey,
        String version,
        Instant timestamp,
        int total,
        int passed,
        double passRate,   // 百分比，保留一位小数
        List<EvalCase> cases
) {
}
