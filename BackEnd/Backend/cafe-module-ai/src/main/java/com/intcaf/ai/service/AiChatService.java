package com.intcaf.ai.service;

import com.intcaf.ai.config.AiChatConfig;
import com.intcaf.ai.context.AiContextLoader;
import com.intcaf.ai.dto.ChatMessage;
import com.intcaf.ai.dto.ChatRequest;
import com.intcaf.ai.dto.ChatResponse;
import com.intcaf.ai.dto.ThinkingStep;
import com.intcaf.ai.tool.AiToolDefinitions;
import com.intcaf.ai.tool.AiToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiChatConfig config;
    private final AiToolDefinitions toolDefinitions;
    private final AiToolExecutor toolExecutor;
    private final AiContextLoader contextLoader;

    private static final int MAX_ROUNDS = 12;

    /** 单次大模型调用失败时的最大尝试次数（含首次；应对 Connection reset 等网络抖动） */
    private static final int LLM_MAX_ATTEMPTS = 3;

    /** 判断是否需要数据查询的关键词（用于决定是否发送 tools） */
    private static final List<String> DATA_KEYWORDS = List.of(
            "查", "统计", "分析", "多少", "几个", "几台", "几号", "哪些", "列出", "显示",
            "今天", "昨天", "本月", "上月", "本周", "上周",
            "营收", "收入", "利润", "销量", "会员", "上机", "下机", "充值", "余额",
            "库存", "商品", "订单", "账单", "报表", "员工", "电脑", "机子", "座位",
            "谁在", "开了", "关了", "赚", "花", "卖", "买",
            "SELECT", "SQL", "数据库", "表"
    );

    // === WebClient ===

    private WebClient buildWebClient(ChatRequest req) {
        String base = isNotBlank(req.getBaseUrl()) ? req.getBaseUrl() : config.getBaseUrl();
        String key  = isNotBlank(req.getApiKey())  ? req.getApiKey()  : config.getApiKey();
        return WebClient.builder().baseUrl(base)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key).build();
    }

    private String model(ChatRequest req) { return isNotBlank(req.getModel()) ? req.getModel() : config.getModel(); }
    private double temp(ChatRequest req) { return req.getTemperature() != null ? req.getTemperature() : config.getTemperature(); }

    // === 聊天（统一入口，返回含思考过程的完整响应） ===

    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String m = model(request);
        List<ThinkingStep> steps = new ArrayList<>();
        try {
            // 判断是否需要数据查询（决定是否注入数据库 schema 到系统提示词）
            boolean needSchema = needsDataQuery(request.getMessage());
            List<Map<String, Object>> msgs = buildMessages(request, needSchema);
            // tools 总是发送（SQLCalling 查数据 + KnowledgeSearch 查规则），AI 自己判断该用哪个
            String reply = fullResolve(buildWebClient(request), msgs, m, temp(request), true, steps);
            reply = cleanArtifacts(reply);
            long costMs = System.currentTimeMillis() - start;
            return new ChatResponse(reply, m, costMs, steps);
        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage(), e);
            long costMs = System.currentTimeMillis() - start;
            return new ChatResponse("AI 服务暂时不可用: " + e.getMessage(), m, costMs, steps);
        }
    }

    /**
     * 判断用户消息是否涉及数据查询，决定是否发送 tools
     */
    private boolean needsDataQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String msg = message.trim();
        for (String kw : DATA_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    // === 核心：Tool Calling 循环（带思考步骤收集） ===

    private String fullResolve(WebClient wc, List<Map<String, Object>> msgs,
                               String modelName, double temperature, boolean sendTools,
                               List<ThinkingStep> steps) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("messages", msgs);
            body.put("max_tokens", config.getMaxTokens());
            body.put("temperature", temperature);
            body.put("stream", false);
            if (sendTools) body.put("tools", toolDefinitions.getToolDefinitions());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = wc.post().uri("/v1/chat/completions")
                    .bodyValue(body).retrieve().bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds())).block();
            if (result == null) return "AI 返回为空";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            if (choices == null || choices.isEmpty()) return "AI 响应无 choices";

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) return "AI 响应无 message";

            // 1) 检查 API 级别的 tool_calls
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                // 记录 assistant 的 tool_call 消息
                msgs.add(message);
                for (Map<String, Object> tc : toolCalls) {
                    String tcId = (String) tc.get("id");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                    String name = (String) fn.get("name");
                    String args = (String) fn.get("arguments");
                    log.info("Tool call [r={}]: {}", round, name);

                    steps.add(new ThinkingStep("tool", "调用工具: " + name + "\n参数: " + args, "调用 " + name));
                    String toolResult = toolExecutor.executeTool(name, args);
                    steps.add(new ThinkingStep("tool_result", toolResult, name + " 返回结果"));

                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("role", "tool"); tm.put("tool_call_id", tcId); tm.put("content", enforceCompleteListing(toolResult));
                    msgs.add(tm);
                }
                continue;
            }

            // 2) 无 tool_calls → 直接作为最终回复返回
            String content = (String) message.get("content");
            if (content == null) return "AI 未返回文本内容";
            return content;
        }
        return "AI 工具调用轮次超出上限，已终止。";
    }

    // === 流式聊天（SSE） ===

    /**
     * 流式聊天：先同步完成 tool calling 循环，最后流式输出最终答案。
     * 事件类型：step（思考步骤）、delta（答案增量）、done（结束）、error
     */
    public void streamChat(ChatRequest request, SseEmitter emitter) {
        try {
            boolean needSchema = needsDataQuery(request.getMessage());
            List<Map<String, Object>> msgs = buildMessages(request, needSchema);
            WebClient wc = buildWebClient(request);
            String m = model(request);

            // 客户端断开 / 超时 / 出错 → 置取消标志，工具循环与 LLM 调用立即停止
            AtomicBoolean cancelled = new AtomicBoolean(false);
            emitter.onCompletion(() -> cancelled.set(true));
            emitter.onTimeout(() -> cancelled.set(true));
            emitter.onError(e -> cancelled.set(true));

            // 先跑 tool calling 循环（可取消），推送思考步骤事件
            String finalText = runToolLoopAndStreamAnswer(wc, msgs, m, temp(request), emitter, cancelled);

            if (cancelled.get()) {
                // 客户端已断开（用户点击停止），不再推送任何内容，静默收尾
                safeComplete(emitter);
                return;
            }

            // 推送结束事件
            safeSend(emitter, SseEmitter.event().data(Map.of("type", "done",
                    "content", finalText == null ? "" : cleanArtifacts(finalText))));
            safeComplete(emitter);
        } catch (LlmCallException e) {
            // 后端 → 大模型出站调用失败（重试后仍失败）：必须通知前端，不能静默挂起
            log.error("AI 大模型调用失败: {}", e.getMessage());
            sendErrorAndComplete(emitter, "AI 服务暂时不可用: " + e.getMessage());
        } catch (AsyncRequestNotUsableException e) {
            // 客户端断开（点停止 / 关页面 / 刷新）：SSE 正常现象，不打错误日志
            log.debug("客户端断开 SSE 连接（忽略）: {}", e.getMessage());
            safeComplete(emitter);
        } catch (Exception e) {
            if (isClientDisconnect(e)) {
                log.debug("客户端断开 SSE 连接（忽略）: {}", e.getMessage());
                safeComplete(emitter);
                return;
            }
            log.error("AI stream chat error: {}", e.getMessage(), e);
            sendErrorAndComplete(emitter, "AI 服务暂时不可用: " + e.getMessage());
        }
    }

    /** 向前端发送 error 事件并完成 SSE；发送失败说明客户端已断开，直接静默收尾 */
    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "error", "content", message)));
        } catch (Exception ignored) { /* 响应已不可用（客户端断开） */ }
        safeComplete(emitter);
    }

    /** 发送 SSE 事件；客户端已断开时统一抛 AsyncRequestNotUsableException 供上层静默处理 */
    private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder builder) throws AsyncRequestNotUsableException {
        try {
            emitter.send(builder);
        } catch (AsyncRequestNotUsableException e) {
            throw e;
        } catch (Exception e) {
            throw new AsyncRequestNotUsableException(e.getMessage(), e);
        }
    }

    /** 完成 SSE；客户端断开导致的失败静默忽略（响应已不可用，无需也无法通知） */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 响应已不可用（客户端断开），complete 只是收尾，失败无影响
        }
    }

    /** 判断异常是否为客户端断开连接所致（不视为服务端错误） */
    private boolean isClientDisconnect(Throwable e) {
        if (e instanceof LlmCallException) return false; // 出站调用失败不属于客户端断开
        if (e instanceof AsyncRequestNotUsableException) return true;
        String msg = e.getMessage();
        if (msg == null) return false;
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("response not usable") || m.contains("broken pipe")
                || m.contains("connection reset") || m.contains("clientabort")
                || m.contains("channel closed");
    }

    /**
     * 可取消的 LLM 调用：客户端断开时立即中断等待中的请求。
     * 遇到网络抖动（Connection reset / 读超时等）自动重试，最多 {@link #LLM_MAX_ATTEMPTS} 次。
     *
     * @return LLM 响应；若已取消返回 null
     * @throws LlmCallException 重试后仍失败
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postChatCompletions(WebClient wc, Map<String, Object> body,
                                                    AtomicBoolean cancelled) {
        LlmCallException lastErr = null;
        for (int attempt = 1; attempt <= LLM_MAX_ATTEMPTS; attempt++) {
            if (cancelled != null && cancelled.get()) return null;

            CompletableFuture<Map> future = wc.post().uri("/v1/chat/completions")
                    .bodyValue(body).retrieve().bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .toFuture();

            // 轮询等待结果，期间响应取消请求（100ms 粒度）
            while (!future.isDone()) {
                if (cancelled != null && cancelled.get()) {
                    future.cancel(true);
                    return null;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    return null;
                }
            }

            try {
                return future.get();
            } catch (Exception e) {
                if (cancelled != null && cancelled.get()) return null;
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                lastErr = new LlmCallException("调用大模型失败: " + cause.getMessage(), cause);
                if (attempt < LLM_MAX_ATTEMPTS) {
                    log.warn("LLM 调用失败，{}ms 后重试（{}/{}）: {}",
                            500L * attempt, attempt, LLM_MAX_ATTEMPTS, cause.getMessage());
                    try {
                        Thread.sleep(500L * attempt); // 线性退避：0.5s、1s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastErr;
                    }
                } else {
                    log.warn("LLM 调用已重试 {} 次仍失败: {}", LLM_MAX_ATTEMPTS, cause.getMessage());
                }
            }
        }
        throw lastErr != null ? lastErr : new LlmCallException("调用大模型失败");
    }

    /**
     * 跑 tool calling 循环，最后流式调用大模型输出答案。
     * tool 调用过程通过 emitter 推送 step 事件；最终答案通过 emitter 推送 delta 事件。
     * 客户端断开（cancelled=true）时随时终止。
     */
    private String runToolLoopAndStreamAnswer(WebClient wc, List<Map<String, Object>> msgs,
                                              String modelName, double temperature,
                                              SseEmitter emitter, AtomicBoolean cancelled) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (cancelled.get()) return "已停止生成";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("messages", msgs);
            body.put("max_tokens", config.getMaxTokens());
            body.put("temperature", temperature);
            body.put("stream", false);
            body.put("tools", toolDefinitions.getToolDefinitions());

            Map<String, Object> result = postChatCompletions(wc, body, cancelled);
            if (result == null) return "已停止生成"; // 客户端已取消

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            if (choices == null || choices.isEmpty()) return "AI 响应无 choices";

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) return "AI 响应无 message";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                msgs.add(message);
                for (Map<String, Object> tc : toolCalls) {
                    String tcId = (String) tc.get("id");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                    String name = (String) fn.get("name");
                    String args = (String) fn.get("arguments");

                    // 推送"调用工具"事件（不显示具体参数，SQL 语句不展示）
                    try {
                        emitter.send(SseEmitter.event().data(Map.of(
                            "type", "step",
                            "stepType", "tool_call",
                            "label", "调用工具: " + name,
                            "content", ""
                        )));
                    } catch (Exception e) {
                        cancelled.set(true);
                        return "已停止生成";
                    }

                    String toolResult = toolExecutor.executeTool(name, args);

                    // 推送"工具返回结果"事件（展示具体数据，但 SQL 语句本身不展示）
                    try {
                        emitter.send(SseEmitter.event().data(Map.of(
                            "type", "step",
                            "stepType", "tool_result",
                            "label", name + " 返回结果",
                            "content", toolResult
                        )));
                    } catch (Exception e) {
                        cancelled.set(true);
                        return "已停止生成";
                    }

                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("role", "tool"); tm.put("tool_call_id", tcId); tm.put("content", enforceCompleteListing(toolResult));
                    msgs.add(tm);
                }
                continue;
            }

            // 无 tool_calls → 开始流式输出答案
            String content = (String) message.get("content");
            if (content != null && !content.isBlank()) {
                // 以“打字机”方式分片推送，避免用户长时间无感知等待
                streamTextInChunks(content, emitter, cancelled);
                return content;
            }

            // 再调一次流式接口拿逐 token 输出
            return streamFinalAnswer(wc, msgs, modelName, temperature, emitter, cancelled);
        }
        return "AI 工具调用轮次超出上限，已终止。";
    }

    /**
     * 将一次性返回的答案按小片段推送，模拟流式输出（提升感知速度）。
     * 客户端断开（如用户点击停止）时立即终止。
     */
    private void streamTextInChunks(String text, SseEmitter emitter, AtomicBoolean cancelled) {
        if (text == null || text.isEmpty()) return;
        int chunkSize = 12;
        for (int i = 0; i < text.length(); i += chunkSize) {
            if (cancelled.get()) break;
            int end = Math.min(i + chunkSize, text.length());
            String part = text.substring(i, end);
            try {
                emitter.send(SseEmitter.event().data(Map.of("type", "delta", "content", part)));
                Thread.sleep(15);
            } catch (Exception e) {
                // 客户端已断开（停止生成 / 关闭页面），终止推送
                cancelled.set(true);
                break;
            }
        }
    }

    /**
     * 流式调用大模型，逐 token 推送 delta 事件。
     */
    private String streamFinalAnswer(WebClient wc, List<Map<String, Object>> msgs,
                                     String modelName, double temperature,
                                     SseEmitter emitter, AtomicBoolean cancelled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", msgs);
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", temperature);
        body.put("stream", true);

        StringBuilder full = new StringBuilder();
        reactor.core.publisher.Flux<String> flux = wc.post().uri("/v1/chat/completions")
                .bodyValue(body).retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                // 客户端断开后停止消费后续流
                .takeWhile(chunk -> !cancelled.get())
                .doOnNext(chunk -> {
                    if (chunk.startsWith("data: ")) {
                        String json = chunk.substring(6).trim();
                        if ("[DONE]".equals(json)) return;
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = com.alibaba.fastjson2.JSON.parseObject(json, Map.class);
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                                if (delta != null && delta.get("content") != null) {
                                    String text = (String) delta.get("content");
                                    full.append(text);
                                    try {
                                        emitter.send(SseEmitter.event().data(Map.of("type", "delta", "content", text)));
                                    } catch (Exception e) {
                                        cancelled.set(true);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
        try {
            flux.blockLast();
        } catch (Exception e) {
            if (cancelled.get()) return full.toString();
            throw new LlmCallException("流式调用大模型失败: " + e.getMessage(), e);
        }
        return full.toString();
    }

    // === 消息构建 ===

    private List<Map<String, Object>> buildMessages(ChatRequest req, boolean includeSchema) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        String sp = resolveSystemPrompt(req);
        // 仅在需要数据查询时注入 schema 到系统提示词，避免纯聊天场景的 token 浪费
        if (includeSchema) {
            String schema = toolExecutor.getSchemaForPrompt();
            sp += "\n\n## 数据库表结构（starlink_attendant）\n" + schema;
        }
        if (contextLoader.hasContext()) {
            sp += "\n\n## 项目业务上下文\n" + contextLoader.getContextContent();
        }
        msgs.add(Map.of("role", "system", "content", sp));
        if (req.getHistory() != null) {
            for (ChatMessage m : req.getHistory()) {
                msgs.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
        }
        msgs.add(Map.of("role", "user", "content", req.getMessage()));
        return msgs;
    }

    /**
     * 根据请求角色选择系统提示词：
     * role=user  → 用户端（顾客/会员）
     * role=admin → 管理端（管理员）
     * 其他/空   → 服务端默认
     */
    private String resolveSystemPrompt(ChatRequest req) {
        String role = req.getRole();
        String sp;
        if (role == null || role.isBlank()) sp = config.getSystemPrompt();
        else sp = switch (role.trim().toLowerCase()) {
            case "user" -> config.getSystemPromptUser();
            case "admin" -> config.getSystemPromptAdmin();
            default -> config.getSystemPrompt();
        };
        // 登录用户信息注入：前端登录后把用户身份摘要随请求传入，作为"我的信息"
        if (req.getUserInfo() != null && !req.getUserInfo().isBlank()) {
            sp += "\n\n## 当前用户信息（我的信息）\n"
                    + req.getUserInfo().trim()
                    + "\n请结合以上用户身份、称呼、权限和场景友好、准确地回答；"
                    + "涉及个人数据（余额、积分、消费记录等）时以此用户为准。";
        }
        return sp;
    }

    // === 文本清洗 ===

    /**
     * 给工具返回结果附加“必须完整列出”的硬指令前缀，防止大模型在总结时省略部分记录。
     */
    private String enforceCompleteListing(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) return toolResult;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\((\\d+)\\s+rows?\\)").matcher(toolResult);
        String count = "若干";
        if (m.find()) count = m.group(1);
        return "[系统指令] 以下为数据库查询结果，共 " + count + " 条记录。你的最终回复必须逐条完整列出全部 " + count +
                " 条记录，禁止省略、合并或只挑选部分内容。\n\n" + toolResult;
    }

    private String cleanArtifacts(String text) {
        if (text == null) return "";
        String[] badPatterns = {"DSML", "tool_calls", "<invoke", "<parameter", "<tool_call", "<|tool"};
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            boolean isBad = false;
            for (String bp : badPatterns) {
                if (line.contains(bp)) { isBad = true; break; }
            }
            if (!isBad) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
        }
        String result = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        return result.isEmpty() ? text.trim() : result;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
