package com.intcaf.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 思考步骤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingStep {

    /**
     * 步骤类型：thinking / sql / sql_result / tool
     */
    private String type;

    /**
     * 步骤内容
     */
    private String content;

    /**
     * 步骤标签（简短描述，如"生成SQL"、"执行查询"）
     */
    private String label;
}
