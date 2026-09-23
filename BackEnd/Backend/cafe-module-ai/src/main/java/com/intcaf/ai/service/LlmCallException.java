package com.intcaf.ai.service;

/**
 * 大模型（LLM）出站调用异常
 * ----------------------------------------------------------
 * 用于明确区分两类连接问题，避免错误地静默收尾：
 *  - 本异常：后端 → DeepSeek 出站连接失败（如 Connection reset、超时），
 *    必须向前端发送 error 事件并完成 SSE；
 *  - {@link org.springframework.web.context.request.async.AsyncRequestNotUsableException}：
 *    前端 → 后端 SSE 客户端断开（用户点停止 / 关页面），静默处理。
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
