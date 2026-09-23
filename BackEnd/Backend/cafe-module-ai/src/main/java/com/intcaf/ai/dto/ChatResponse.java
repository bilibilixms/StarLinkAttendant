package com.intcaf.ai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天响应体
 */
@Data
@NoArgsConstructor
public class ChatResponse {

    /** AI 回复内容 */
    private String reply;

    /** 使用的模型名称 */
    private String model;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 思考过程步骤列表 */
    private List<ThinkingStep> thinkingSteps;

    public ChatResponse(String reply, String model, Long costMs) {
        this.reply = reply;
        this.model = model;
        this.costMs = costMs;
    }

    public ChatResponse(String reply, String model, Long costMs, List<ThinkingStep> thinkingSteps) {
        this.reply = reply;
        this.model = model;
        this.costMs = costMs;
        this.thinkingSteps = thinkingSteps;
    }
}
