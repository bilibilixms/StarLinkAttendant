package com.intcaf.rag.vector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条检索结果：片段文本 + 召回分数 + 元数据。
 * 精排/阈值过滤/去重都基于它；生成层可拿 source/title 做引用溯源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagHit {

    /** Milvus 主键 id */
    private long id;

    /** 知识片段原文 */
    private String content;

    /** 召回分数（混合检索为 RRF 融合分；纯语义为余弦相似度），越大越相关 */
    private double score;

    /** 元数据（source/title/category/updatedAt/permission） */
    private RagDocMeta meta;
}
