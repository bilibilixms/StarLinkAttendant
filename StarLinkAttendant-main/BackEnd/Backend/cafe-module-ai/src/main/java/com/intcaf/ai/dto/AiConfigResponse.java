package com.intcaf.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 配置信息响应（返回给前端，不含 apiKey）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConfigResponse {

    /** 当前 API 基础地址 */
    private String baseUrl;

    /** 当前模型名称 */
    private String model;

    /** 当前温度参数 */
    private Double temperature;

    /** API Key 是否已配置（不返回实际 key） */
    private Boolean apiKeyConfigured;
}
