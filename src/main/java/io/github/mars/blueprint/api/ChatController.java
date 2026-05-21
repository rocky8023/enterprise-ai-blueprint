package io.github.mars.blueprint.api;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/hello")
    public Map<String, String> hello(@RequestParam(defaultValue = "你好，用一句话介绍你自己") String message) {
        String reply = chatClient.prompt(message).call().content();
        return Map.of(
                "model", "MiniMax-M2.7",
                "request", message,
                "reply", reply
        );
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> req) {
        String message = req.getOrDefault("message", "");
        String reply = chatClient.prompt(message).call().content();
        return Map.of("reply", reply);
    }
}
