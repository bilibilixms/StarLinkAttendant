package com.intcaf.ai.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String message;

    /**
     * 历史对话记录（可选，用于保持上下文）
     */
    private List<ChatMessage> history;

    /**
     * 使用端角色（可选）：user = 用户端（顾客/会员），admin = 管理端（管理员）
     * 决定注入哪套系统提示词；不传则使用服务端默认提示词
     */
    private String role;

    /**
     * 当前登录用户信息（可选）：登录后由前端传入的用户身份摘要（JSON 字符串），
     * 例如 {"id":1,"memberNo":"M202609010001","nickName":"张三","levelName":"黄金会员","balance":88.5}
     * AI 会将其作为"我的信息"结合身份回答；未登录/不传则为空
     */
    private String userInfo;

    // ============ 可选：覆盖服务端默认配置 ============

    /**
     * 模型名称（可选，不传则使用服务端配置）
     */
    private String model;

    /**
     * API 基础地址（可选，不传则使用服务端配置）
     */
    private String baseUrl;

    /**
     * API Key（可选，不传则使用服务端配置）
     */
    private String apiKey;

    /**
     * 温度参数 0.0~2.0（可选，不传则使用服务端配置）
     */
    private Double temperature;
}
