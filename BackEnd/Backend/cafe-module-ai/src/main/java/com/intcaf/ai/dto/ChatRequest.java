package com.intcaf.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 前端聊天请求体
 */
@Data
public class ChatRequest {

    /**
     * 当前用户输入的消息
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "消息内容不能超过 8000 字符")
    private String message;

    /**
     * 历史对话记录（可选，用于保持上下文）
     */
    private List<ChatMessage> history;

}
