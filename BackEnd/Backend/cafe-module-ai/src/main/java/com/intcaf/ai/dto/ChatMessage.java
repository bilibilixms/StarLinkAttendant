package com.intcaf.ai.dto;

import lombok.Data;

/**
 * 对话消息条目
 */
@Data
public class ChatMessage {

    /**
     * 角色：system / user / assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
