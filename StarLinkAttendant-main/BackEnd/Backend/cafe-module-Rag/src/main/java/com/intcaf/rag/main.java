package com.intcaf.rag;

import com.intcaf.rag.eval.RagEvaluator;
import com.intcaf.rag.eval.RagFeedback;
import com.intcaf.rag.eval.RagRetrievalLogger;
import com.intcaf.rag.rewrite.QueryRewriter;
import com.intcaf.rag.vector.RagDocMeta;
import com.intcaf.rag.vector.RagHit;
import com.intcaf.rag.vector.RagVectorService;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 完整功能自检入口（脱离 Spring，手动 new + 反射注入）。
 *
 * 依次验证：
 *   1) 元数据入库
 *   2) 增量更新 upsertBySource / deleteBySource / countDocs
 *   3) 元数据过滤检索 expr（category == 'xxx'）
 *   4) 分数阈值过滤 + 近重复去重（rerankSearchHits 内部已串）
 *   5) QueryRewriter 改写扩展
 *   6) 结果缓存（同 query 第二次应秒回）
 *   7) RagEvaluator 离线评估 Recall@K / MRR
 *   8) RagRetrievalLogger 日志观测 + RagFeedback 用户反馈
 *
 * 前提：embedding(8001) / rerank(8002) / Milvus(19530) 已启动。
 */
public class main {

    static RagVectorService service = new RagVectorService();
    static QueryRewriter rewriter = new QueryRewriter();
    static RagRetrievalLogger retrievalLogger = new RagRetrievalLogger();
    static RagFeedback feedback = new RagFeedback();

    public static void main(String[] args) throws Exception {
        // ===== 手动注入 RagVectorService 配置 =====
        set(service, "embeddingUrl", "http://127.0.0.1:8001/embeddings");
        set(service, "milvusUri", "http://127.0.0.1:19530");
        set(service, "vectorDim", 512);
        set(service, "defaultCollection", "intcaf_business_kb");
        set(service, "chunkSize", 300);
        set(service, "chunkOverlap", 50);
        set(service, "rerankUrl", "http://127.0.0.1:8002/rerank");
        set(service, "rerankEnabled", true);
        set(service, "rerankCandidateMultiplier", 5);
        set(service, "scoreThreshold", 0.1);
        set(service, "cacheEnabled", false);
        set(service, "cacheTtlSeconds", 300);

        String collection = "intcaf_business_kb";
        Path moduleRoot = Path.of(main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolveSibling("..").normalize();
        String mdDir = moduleRoot.resolve("../cafe-module-ai/src/main/resources/ai-context").toString();

        // 不删集合，直接在已有集合上跑（ingestMdFile 内部会自动建集合）
//        service.dropCollection(collection);  // 只跑一次，清完删掉这行

        // ===== 1. 元数据入库 =====
        File dir = new File(mdDir);
        File[] mdFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".md"));
        if (mdFiles == null || mdFiles.length == 0) {
            System.out.println("ai-context 下没有 md 文件：" + mdDir);
            return;
        }
        for (File f : mdFiles) {
            RagDocMeta meta = RagDocMeta.builder()
                    .source(f.getName())
                    .title(f.getName())
                    .category("general")
                    .updatedAt(System.currentTimeMillis() / 1000)
                    .permission("all")
                    .build();
            System.out.println("--- 入库（upsert）：" + f.getName());
            service.upsertBySource(f.getAbsolutePath(), collection, meta);
        }
        System.out.println("入库后文档片段数 countDocs = " + service.countDocs(collection));

        // ===== 2. 增量更新：演示 upsertBySource 不重建整库 =====
        System.out.println("\n===== 2. 增量更新 =====");
        System.out.println("更新前片段数: " + service.countDocs(collection));
        // 对第一个文件重新 upsert（内部先删 source 再插）
        service.upsertBySource(mdFiles[0].getAbsolutePath(), collection,
                RagDocMeta.builder().source(mdFiles[0].getName()).category("billing").permission("all").build());
        System.out.println("upsert[" + mdFiles[0].getName() + "] 后片段数: " + service.countDocs(collection));

        // ===== 3. 元数据过滤检索 =====
        System.out.println("\n===== 3. 元数据过滤检索（expr: source 限定单文件）=====");
        List<RagHit> filtered = service.hybridSearchHits(collection, "上机", 3,
                "source == '" + mdFiles[0].getName() + "'");
        System.out.println("只在[" + mdFiles[0].getName() + "]里搜到 " + filtered.size() + " 条：");
        printHits(filtered);

        // ===== 4. 精排 + 阈值过滤 + 去重 =====
        System.out.println("\n===== 4. 精排检索（含阈值过滤+去重）=====");
        String question = "怎么计费的？";

        // 4a. 先看原始召回（不经过 rerank/阈值过滤），定位是召回漏了还是过滤丢了
        System.out.println("  [诊断] 原始混合召回 top15（未精排）：");
        List<RagHit> raw = service.hybridSearchHits(collection, question, 15);
        printHits(raw);

        System.out.println("  [诊断] 精排+阈值过滤后：");
        long t0 = System.currentTimeMillis();
        List<RagHit> hits = service.rerankSearchHits(collection, question, 3);
        long cost = System.currentTimeMillis() - t0;
        System.out.println("query=[" + question + "] 命中 " + hits.size() + " 条，耗时 " + cost + "ms：");
        printHits(hits);
        retrievalLogger.record(question, hits, cost);

        // ===== 5. QueryRewriter 改写扩展（演示：AI 实际调用方式）=====
        // AI 调用链：rewriter.rewrite(query) → 多个检索式分别检索 → 合并去重
        System.out.println("\n===== 5. QueryRewriter 改写扩展 =====");
        String userQuery = "充钱上网怎么弄";
        List<String> rewritten = rewriter.rewrite(userQuery);
        System.out.println("用户原话=[" + userQuery + "] 扩展出 " + rewritten.size() + " 个检索式: " + rewritten);

        // 用每个扩展 query 分别检索，合并结果（模拟 AI 实际调用）
        Map<String, RagHit> merged = new LinkedHashMap<>();
        for (String q : rewritten) {
            List<RagHit> sub = service.hybridSearchHits(collection, q, 3);
            for (RagHit h : sub) {
                String key = h.getContent().substring(0, Math.min(50, h.getContent().length()));
                merged.putIfAbsent(key, h);
            }
        }
        System.out.println("多路合并后去重，共 " + merged.size() + " 条相关片段：");
        printHits(new ArrayList<>(merged.values()));

        // ===== 6. RagEvaluator 离线评估 =====
        System.out.println("\n===== 6. 检索质量评估 =====");
        List<RagEvaluator.EvalCase> cases = List.of(
                new RagEvaluator.EvalCase("上机管理", List.of("上机", "开机")),
                new RagEvaluator.EvalCase("计费", List.of("计费", "费用"))
        );
        List<List<RagHit>> actual = List.of(
                service.hybridSearchHits(collection, cases.get(0).query(), 5),
                service.hybridSearchHits(collection, cases.get(1).query(), 5)
        );
        RagEvaluator.Summary summary = RagEvaluator.evaluateBatch(cases, actual);
        System.out.println(summary);
        System.out.println("最近检索日志: " + retrievalLogger.recent(3).size() + " 条");

        service.destroy();
        System.out.println("\n自检结束");
    }

    /** 打印检索结果（完整内容，方便肉眼检查） */
    private static void printHits(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            System.out.println("  （无结果）");
            return;
        }
        for (int i = 0; i < hits.size(); i++) {
            RagHit h = hits.get(i);
            String src = h.getMeta() != null && h.getMeta().getSource() != null ? h.getMeta().getSource() : "?";
            System.out.println("  ──[" + (i + 1) + "] score=" + String.format("%.3f", h.getScore()) + " source=" + src);
            System.out.println("     " + h.getContent().replace("\n", "\n     "));
        }
    }

    /** 反射给私有字段赋值，仅测试入口使用 */
    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
