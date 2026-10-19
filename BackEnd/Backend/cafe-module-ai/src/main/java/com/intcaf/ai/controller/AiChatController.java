package com.intcaf.ai.controller;

import com.intcaf.ai.config.AiChatConfig;
import com.intcaf.ai.dto.AiConfigResponse;
import com.intcaf.ai.dto.ChatRequest;
import com.intcaf.ai.dto.ChatResponse;
import com.intcaf.ai.service.AiChatService;
import com.intcaf.ai.service.AiIdentity;
import com.starlink.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI 智能助手接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatConfig aiChatConfig;
    private final Semaphore syncSlots = new Semaphore(8);
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor streamExecutor = new ThreadPoolExecutor(
            4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8),
            task -> {
                Thread thread = new Thread(task, "ai-chat-stream");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

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
        enforceRateLimit();
        if (!syncSlots.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 请求过多，请稍后重试");
        }
        try {
            return Result.ok(aiChatService.chat(request));
        } finally {
            syncSlots.release();
        }
    }

    /**
     * 流式聊天接口（SSE）
     * POST /api/ai/chat/stream
     * 事件类型：step（思考步骤）、delta（答案增量）、done（结束）、error
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        enforceRateLimit();
        // 10 分钟超时，足够 tool calling + 流式输出
        SseEmitter emitter = new SseEmitter(600000L);
        // 有界线程池限制同时运行的外部 AI 请求，并把已验证身份传递给异步任务。
        Runnable task = new DelegatingSecurityContextRunnable(() -> {
            try {
                aiChatService.streamChat(request, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(Map.of("type", "error", "content", "AI 服务暂时不可用，请稍后重试")));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        }, SecurityContextHolder.getContext());
        try {
            streamExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 请求过多，请稍后重试");
        }
        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }

    private void enforceRateLimit() {
        AiIdentity actor = AiIdentity.current();
        String key = actor.userType() + ":" + actor.userId();
        if (!rateWindows.computeIfAbsent(key, ignored -> new RateWindow()).tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 请求过多，请稍后重试");
        }
    }

    private static final class RateWindow {
        private long start = System.currentTimeMillis();
        private int count;

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - start >= TimeUnit.MINUTES.toMillis(1)) {
                start = now;
                count = 0;
            }
            return ++count <= 20;
        }
    }
}
