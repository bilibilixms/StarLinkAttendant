package com.intcaf.rag.eval;

import com.intcaf.rag.vector.RagHit;

import java.util.List;

/**
 * RAG 检索质量评估（离线标注集打分）。
 *
 * 用法：准备一批人工标注 {query, 期望命中的来源/片段}，跑检索后把
 *  (query, 期望命中关键词, 实际命中 hits) 传进来，统计：
 *   - Recall@K     ：相关结果有没有被召回（在 topK 里出现）
 *   - Precision@K   ：topK 里有多少条相关
 *   - MRR          ：第一个相关结果排第几（越靠前越好，理想=1）
 *
 * 纯计算工具类，不连 Milvus，可单测。
 */
public final class RagEvaluator {

    private RagEvaluator() {}

    /** 单条标注：query + 该 query 期望命中的来源关键词（命中其一即算相关） */
    public record EvalCase(String query, List<String> expectedKeywords) {}

    /** 单条评估结果 */
    public record EvalResult(double recallAtK, double precisionAtK, double mrr) {}

    /**
     * 判定一条 hit 是否相关：content 或 meta.source 命中任一 expectedKeyword。
     */
    private static boolean isRelevant(RagHit hit, List<String> expectedKeywords) {
        if (hit == null) return false;
        String text = (hit.getContent() == null ? "" : hit.getContent())
                + (hit.getMeta() != null && hit.getMeta().getSource() != null ? hit.getMeta().getSource() : "");
        for (String kw : expectedKeywords) {
            if (kw != null && !kw.isBlank() && text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 计算单条 query 的评估指标。
     * @param hits 实际检索结果（已按相关度排序）
     * @param expected 人工标注的期望关键词
     */
    public static EvalResult evaluateSingle(List<RagHit> hits, List<String> expected) {
        if (hits == null || hits.isEmpty()) return new EvalResult(0, 0, 0);

        int relevantCount = 0;
        double mrr = 0;
        for (int i = 0; i < hits.size(); i++) {
            boolean rel = isRelevant(hits.get(i), expected);
            if (rel) {
                relevantCount++;
                if (mrr == 0) mrr = 1.0 / (i + 1); // 第一个相关结果的倒数排名
            }
        }
        double recall = expected.isEmpty() ? 0 : Math.min(1.0, (double) relevantCount / expected.size());
        // 简化口径：有命中相关即算召回成功
        recall = relevantCount > 0 ? 1.0 : 0.0;
        double precision = (double) relevantCount / hits.size();
        return new EvalResult(recall, precision, mrr);
    }

    /**
     * 批量评估：对一组标注集跑检索（由调用方负责调用检索），输出平均分。
     *
     * @param cases    标注集
     * @param actual   与 cases 一一对应的实际检索结果
     */
    public static Summary evaluateBatch(List<EvalCase> cases, List<List<RagHit>> actual) {
        double recallSum = 0, precisionSum = 0, mrrSum = 0;
        int n = Math.min(cases.size(), actual.size());
        int hitCount = 0;
        for (int i = 0; i < n; i++) {
            EvalResult r = evaluateSingle(actual.get(i), cases.get(i).expectedKeywords());
            recallSum += r.recallAtK();
            precisionSum += r.precisionAtK();
            mrrSum += r.mrr();
            if (r.recallAtK() > 0) hitCount++;
        }
        if (n == 0) return new Summary(0, 0, 0, 0, 0);
        return new Summary(n, hitCount, recallSum / n, precisionSum / n, mrrSum / n);
    }

    /** 批量汇总结果 */
    public record Summary(int totalQueries, int hitQueries,
                          double avgRecall, double avgPrecision, double avgMRR) {
        @Override
        public String toString() {
            return String.format("评估 %d 条 query，命中 %d 条 | Recall@K=%.2f Precision@K=%.2f MRR=%.3f",
                    totalQueries, hitQueries, avgRecall, avgPrecision, avgMRR);
        }
    }
}
