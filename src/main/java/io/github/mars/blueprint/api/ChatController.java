package io.github.mars.blueprint.api;

import io.github.mars.blueprint.infra.llm.ChatRouter;
import io.github.mars.blueprint.infra.observability.LlmTracer;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatRouter chatRouter;
    private final LlmTracer tracer;

    public ChatController(ChatRouter chatRouter, LlmTracer tracer) {
        this.chatRouter = chatRouter;
        this.tracer = tracer;
    }

    @GetMapping("/hello")
    public Map<String, String> hello(
            @RequestParam(defaultValue = "你好，用一句话介绍你自己") String message,
            @RequestParam(value = "preset", required = false) String preset) {
        ChatRouter.Resolved chat = chatRouter.resolve(preset);
        ChatResponse response = tracer.traceChat(
                new LlmTracer.ChatTraceContext(null, chat.preset(), Map.of("message", message), message),
                () -> chat.client().prompt(message).call().chatResponse());
        // 模型名从响应元数据读取，随 preset 变化，不再硬编码
        String model = response.getMetadata() != null ? response.getMetadata().getModel() : "unknown";
        Map<String, String> out = new LinkedHashMap<>();
        out.put("preset", chat.preset());
        out.put("model", model);
        out.put("request", message);
        out.put("reply", response.getResult().getOutput().getText());
        return out;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> req) {
        String message = req.getOrDefault("message", "");
        ChatRouter.Resolved chat = chatRouter.resolve(req.get("preset"));
        ChatResponse response = tracer.traceChat(
                new LlmTracer.ChatTraceContext(null, chat.preset(), Map.of("message", message), message),
                () -> chat.client().prompt(message).call().chatResponse());
        return Map.of("reply", response.getResult().getOutput().getText());
    }
}
