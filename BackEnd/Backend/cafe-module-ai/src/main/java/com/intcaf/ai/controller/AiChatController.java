package com.intcaf.ai.controller;

import com.intcaf.ai.config.AiChatConfig;
import com.intcaf.ai.dto.AiConfigResponse;
import com.intcaf.ai.dto.ChatRequest;
import com.intcaf.ai.dto.ChatResponse;
import com.intcaf.ai.service.AiChatService;
import com.starlink.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 智能助手接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatConfig aiChatConfig;

    /**
     * 获取当前 AI 配置（不含 apiKey 明文）
     * GET /api/ai/config
     */
    @GetMapping("/config")
    public Result<AiConfigResponse> getConfig() {
        AiConfigResponse resp = new AiConfigResponse(
                aiChatConfig.getBaseUrl(),
                aiChatConfig.getModel(),
                aiChatConfig.getTemperature(),
                aiChatConfig.getApiKey() != null && !aiChatConfig.getApiKey().isBlank()
                        && !aiChatConfig.getApiKey().equals("your-api-key-here")
        );
        return Result.ok(resp);
    }

    /**
     * 聊天接口（统一入口）
     * POST /api/ai/chat
     * 同步完成全部 SQL 循环后返回最终答案
     */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = aiChatService.chat(request);
        return Result.ok(response);
    }

    /**
     * 流式聊天接口（SSE）
     * POST /api/ai/chat/stream
     * 事件类型：step（思考步骤）、delta（答案增量）、done（结束）、error
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        // 10 分钟超时，足够 tool calling + 流式输出
        SseEmitter emitter = new SseEmitter(600000L);
        // 异步推送，避免阻塞 Tomcat 线程
        new Thread(() -> {
            try {
                aiChatService.streamChat(request, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(Map.of("type", "error", "content", e.getMessage())));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        }).start();
        return emitter;
    }
}
