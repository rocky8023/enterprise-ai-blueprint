package io.github.mars.blueprint.api;

import io.github.mars.blueprint.eval.EvalReport;
import io.github.mars.blueprint.eval.PromptEvaluator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prompt 评测端点。
 * <p>
 * 注意：评测会真实调用大模型（每条 example 一次），消耗 token，需配置好 API key。
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final PromptEvaluator evaluator;

    public EvalController(PromptEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /** 评测某 prompt key 的全部版本（回归对比视角）。 */
    @GetMapping("/{key}")
    public List<EvalReport> evaluateAll(@PathVariable String key) {
        return evaluator.evaluateAll(key);
    }

    /** 评测某 prompt key 的指定版本。 */
    @GetMapping("/{key}/{version}")
    public EvalReport evaluate(@PathVariable String key, @PathVariable String version) {
        return evaluator.evaluate(key, version);
    }
}
