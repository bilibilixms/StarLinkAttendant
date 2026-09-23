package com.intcaf.rag.vector;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PreDestroy;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * RAG 向量检索服务（完整版）：
 *   md 切片 → bge embedding → Milvus 存储 / 混合检索(dense+BM25+RRF) → rerank 精排
 *
 * 本次补全的工程能力：
 *   - 元数据：source/title/category/updatedAt/permission，支持按来源增量更新、按域/时间/权限过滤
 *   - 增量更新：upsertBySource / deleteBySource / countDocs（不再整库 drop）
 *   - 检索精排后处理：分数阈值过滤、近重复片段去重
 *   - 结果缓存：同一 query 短时间内复用召回结果
 *   - 降级容错：embedding / Milvus / rerank 任一不可用时优雅降级，不炸业务
 */
@Component
public class RagVectorService {

    private static final Logger log = LoggerFactory.getLogger(RagVectorService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    @Value("${rag.embedding-url:http://127.0.0.1:8001/embeddings}")
    private String embeddingUrl;

    @Value("${rag.milvus-uri:http://127.0.0.1:19530}")
    private String milvusUri;

    @Value("${rag.vector-dim:512}")
    private int vectorDim;

    @Value("${rag.default-collection:intcaf_business_kb}")
    private String defaultCollection;

    /** rerank 精排服务地址 */
    @Value("${rag.rerank-url:http://127.0.0.1:8002/rerank}")
    private String rerankUrl;

    @Value("${rag.rerank-enabled:true}")
    private boolean rerankEnabled;

    @Value("${rag.rerank-candidate-multiplier:5}")
    private int rerankCandidateMultiplier;

    /** 精排后最小分数阈值，低于此值的片段直接丢弃（0~1，rerank normalize 分数） */
    @Value("${rag.score-threshold:0.3}")
    private double scoreThreshold;

    /** 结果缓存开关 */
    @Value("${rag.cache-enabled:true}")
    private boolean cacheEnabled;

    /** 结果缓存过期秒数 */
    @Value("${rag.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Value("${rag.chunk-size:300}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    private static final String DENSE_FIELD = "vector";
    private static final String SPARSE_FIELD = "sparse";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /** Milvus 客户端懒加载：Milvus 没启动时不影响应用启动 */
    private volatile MilvusClientV2 client;

    /** 检索结果缓存：key = collection|expr|query|topK */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(List<RagHit> hits, long expireAt) {}

    private MilvusClientV2 client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    log.info("连接 Milvus: {}", milvusUri);
                    client = new MilvusClientV2(ConnectConfig.builder().uri(milvusUri).build());
                }
            }
        }
        return client;
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    // ============================================================
    // embedding
    // ============================================================

    /** 调 bge embedding 服务，把单段文本转向量；失败抛出由上层降级 */
    public List<Float> embed(String text) throws IOException {
        String body = new JSONObject().fluentPut("input", text).toJSONString();
        Request request = new Request.Builder()
                .url(embeddingUrl)
                .post(RequestBody.create(body, JSON_TYPE))
                .build();
        try (Response resp = HTTP.newCall(request).execute()) {
            String respStr = resp.body() != null ? resp.body().string() : "{}";
            JSONObject jsonResp = JSON.parseObject(respStr);
            return jsonResp.getJSONArray("data").getJSONObject(0)
                    .getJSONArray("embedding").toJavaList(Float.class);
        }
    }

    // ============================================================
    // 切片
    // ============================================================

    /**
     * Markdown 感知切片：以标题（#~######）为小节边界，整节保留不被腰斩；
     * 多个短小节贪心合并到 chunkSize 以内；单节超过 chunkSize 才按 overlap 硬切。
     */
    public List<String> splitTextChunk(String text) {
        List<String> sections = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (cur.length() > 0 && line.trim().matches("^#{1,6}\\s+.*")) {
                sections.add(cur.toString().trim());
                cur = new StringBuilder();
            }
            cur.append(line).append("\n");
        }
        if (cur.length() > 0) {
            sections.add(cur.toString().trim());
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String sec : sections) {
            while (sec.length() > chunkSize) {
                if (buf.length() > 0) {
                    chunks.add(buf.toString().trim());
                    buf = new StringBuilder();
                }
                chunks.add(sec.substring(0, chunkSize).trim());
                sec = sec.substring(Math.max(chunkSize - chunkOverlap, 1));
            }
            if (buf.length() > 0 && buf.length() + sec.length() > chunkSize) {
                chunks.add(buf.toString().trim());
                buf = new StringBuilder();
            }
            buf.append(sec).append("\n");
        }
        if (buf.length() > 0) {
            chunks.add(buf.toString().trim());
        }
        return chunks;
    }

    // ============================================================
    // 集合 schema（含元数据字段）
    // ============================================================

    /**
     * 集合 schema：
     *   id + content + vector(512 dense) + sparse(BM25 function)
     *   + source / title / category / updated_at / permission（元数据，可过滤）
     */
    public void createCollectionIfNotExist(String collectionName) {
        MilvusClientV2 c = client();
        Boolean has = c.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build());
        if (Boolean.TRUE.equals(has)) {
            return;
        }
        CreateCollectionReq.CollectionSchema schema = c.createSchema();
        schema.addField(AddFieldReq.builder().fieldName("id")
                .dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        schema.addField(AddFieldReq.builder().fieldName("content")
                .dataType(DataType.VarChar).maxLength(4096)
                .enableAnalyzer(true).analyzerParams(Map.of("type", "chinese"))
                .build());
        schema.addField(AddFieldReq.builder().fieldName(DENSE_FIELD)
                .dataType(DataType.FloatVector).dimension(vectorDim).build());
        schema.addField(AddFieldReq.builder().fieldName(SPARSE_FIELD)
                .dataType(DataType.SparseFloatVector).build());

        // 元数据字段
        schema.addField(AddFieldReq.builder().fieldName("source")
                .dataType(DataType.VarChar).maxLength(256).build());
        schema.addField(AddFieldReq.builder().fieldName("title")
                .dataType(DataType.VarChar).maxLength(256).build());
        schema.addField(AddFieldReq.builder().fieldName("category")
                .dataType(DataType.VarChar).maxLength(64).build());
        schema.addField(AddFieldReq.builder().fieldName("updated_at")
                .dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("permission")
                .dataType(DataType.VarChar).maxLength(64).build());

        schema.addFunction(CreateCollectionReq.Function.builder()
                .functionType(FunctionType.BM25)
                .name("bm25_function")
                .inputFieldNames(List.of("content"))
                .outputFieldNames(List.of(SPARSE_FIELD))
                .build());

        Map<String, Object> denseParams = new HashMap<>();
        denseParams.put("nlist", 128);
        IndexParam denseIdx = IndexParam.builder()
                .fieldName(DENSE_FIELD)
                .indexName("dense_idx")
                .indexType(IndexParam.IndexType.IVF_FLAT)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(denseParams)
                .build();
        IndexParam sparseIdx = IndexParam.builder()
                .fieldName(SPARSE_FIELD)
                .indexName("sparse_idx")
                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25)
                .build();

        c.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(List.of(denseIdx, sparseIdx))
                .build());
        log.info("Milvus集合[{}]创建完成（dense+BM25+元数据字段）", collectionName);
    }

    // ============================================================
    // 入库 + 增量更新
    // ============================================================

    /** md 文件入库（自动带元数据）：读文件 → 切片 → 向量化 → 插入 */
    public void ingestMdFile(String mdPath, String collectionName) {
        Path p = Path.of(mdPath);
        String filename = p.getFileName().toString();
        RagDocMeta meta = RagDocMeta.builder()
                .source(filename)
                .title(extractTitle(filename, p))
                .category(inferCategory(filename))
                .updatedAt(System.currentTimeMillis() / 1000)
                .permission("all")
                .build();
        ingestMdFile(mdPath, collectionName, meta);
    }

    /** 带自定义元数据的入库 */
    public void ingestMdFile(String mdPath, String collectionName, RagDocMeta meta) {
        try {
            String fullText = Files.readString(Path.of(mdPath), StandardCharsets.UTF_8);
            List<String> chunks = splitTextChunk(fullText);
            log.info("文档[{}]切片完成，共 {} 块", mdPath, chunks.size());

            createCollectionIfNotExist(collectionName);
            client().loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());

            List<JsonObject> entities = new ArrayList<>();
            for (String chunk : chunks) {
                List<Float> vec = embed(chunk);
                JsonObject row = new JsonObject();
                row.addProperty("content", chunk);
                JsonArray vecArr = new JsonArray();
                for (Float v : vec) vecArr.add(v);
                row.add(DENSE_FIELD, vecArr);
                row.addProperty("source", meta.getSource());
                row.addProperty("title", meta.getTitle() == null ? "" : meta.getTitle());
                row.addProperty("category", meta.getCategory() == null ? "" : meta.getCategory());
                row.addProperty("updated_at", meta.getUpdatedAt());
                row.addProperty("permission", meta.getPermission() == null ? "all" : meta.getPermission());
                entities.add(row);
            }
            client().insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(entities)
                    .build());
            // flush 让新数据立即可被 filter/统计查到
            try {
                client().flush(io.milvus.v2.service.utility.request.FlushReq.builder()
                        .collectionNames(java.util.List.of(collectionName)).build());
            } catch (Exception ignored) {}
            log.info("文档[{}]入库完成，{} 块（source={}）", mdPath, chunks.size(), meta.getSource());
        } catch (Exception e) {
            throw new RuntimeException("md 入库失败: " + mdPath, e);
        }
    }

    /**
     * 增量更新某来源：先删除该 source 的所有旧片段，再重新插入新片段。
     * 单文件更新无需重建整个集合。
     */
    public void upsertBySource(String mdPath, String collectionName, RagDocMeta meta) {
        try {
            deleteBySource(collectionName, meta.getSource());
            ingestMdFile(mdPath, collectionName, meta);
            log.info("增量更新完成: source={}", meta.getSource());
        } catch (Exception e) {
            log.warn("增量更新失败 source={}: {}", meta.getSource(), e.getMessage());
        }
    }

    /** 按来源删除所有片段 */
    public void deleteBySource(String collectionName, String source) {
        try {
            client().delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter("source == '" + source.replace("'", "") + "'")
                    .build());
            log.info("已删除 source={} 的旧片段", source);
        } catch (Exception e) {
            log.warn("删除 source={} 失败: {}", source, e.getMessage());
        }
    }

    /** 集合当前文档片段数 */
    public long countDocs(String collectionName) {
        try {
            var stats = client().getCollectionStats(
                    GetCollectionStatsReq.builder().collectionName(collectionName).build());
            return stats.getNumOfEntities();
        } catch (Exception e) {
            log.warn("统计行数失败: {}", e.getMessage());
            return -1;
        }
    }

    private String extractTitle(String filename, Path p) {
        try {
            String head = Files.readString(p, StandardCharsets.UTF_8);
            for (String line : head.split("\n", 5)) {
                if (line.trim().matches("^#{1,2}\\s+.*")) {
                    return line.replaceAll("^#{1,6}\\s+", "").trim();
                }
            }
        } catch (Exception ignored) {}
        return filename;
    }

    private String inferCategory(String filename) {
        String f = filename.toLowerCase();
        if (f.contains("member")) return "member";
        if (f.contains("billing") || f.contains("rule") || f.contains("price")) return "billing";
        if (f.contains("session") || f.contains("seat") || f.contains("pc")) return "session";
        if (f.contains("inventory") || f.contains("goods") || f.contains("product")) return "inventory";
        if (f.contains("schema") || f.contains("database")) return "database";
        return "general";
    }

    // ============================================================
    // 检索（带 score / 元数据 / 过滤 / 缓存）
    // ============================================================

    /** 纯语义检索，返回 RagHit 列表（按相似度降序） */
    public List<RagHit> searchHits(String collectionName, String query, int topK) {
        return searchHits(collectionName, query, topK, null);
    }

    /** 纯语义检索 + 元数据过滤表达式（expr 示例: "category == 'billing'"） */
    public List<RagHit> searchHits(String collectionName, String query, int topK, String expr) {
        try {
            String cacheKey = collectionName + "|sem|" + (expr == null ? "" : expr) + "|" + query + "|" + topK;
            List<RagHit> cached = cacheGet(cacheKey);
            if (cached != null) return cached;

            MilvusClientV2 c = client();
            c.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());
            float[] vec = toFloatArray(embed(query));

            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("nprobe", 10);
            var b = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(new FloatVec(vec)))
                    .annsField(DENSE_FIELD)
                    .topK(topK)
                    .searchParams(searchParams)
                    .outputFields(List.of("content", "source", "title", "category", "updated_at", "permission"));
            if (expr != null && !expr.isBlank()) b.filter(expr);

            SearchResp resp = c.search(b.build());
            List<RagHit> hits = extractHits(resp);
            cachePut(cacheKey, hits);
            return hits;
        } catch (Exception e) {
            log.warn("纯语义检索失败: {}，返回空", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 混合检索（dense+BM25+RRF），返回 RagHit 列表 */
    public List<RagHit> hybridSearchHits(String collectionName, String query, int topK) {
        return hybridSearchHits(collectionName, query, topK, null);
    }

    /** 混合检索 + 元数据过滤表达式 */
    public List<RagHit> hybridSearchHits(String collectionName, String query, int topK, String expr) {
        try {
            String cacheKey = collectionName + "|hyb|" + (expr == null ? "" : expr) + "|" + query + "|" + topK;
            List<RagHit> cached = cacheGet(cacheKey);
            if (cached != null) return cached;

            MilvusClientV2 c = client();
            c.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());
            float[] dense = toFloatArray(embed(query));

            var denseB = AnnSearchReq.builder()
                    .vectorFieldName(DENSE_FIELD)
                    .vectors(List.of(new FloatVec(dense)))
                    .topK(topK * 2);
            if (expr != null && !expr.isBlank()) denseB.filter(expr);
            AnnSearchReq denseReq = denseB.build();

            var sparseB = AnnSearchReq.builder()
                    .vectorFieldName(SPARSE_FIELD)
                    .vectors(List.of(new EmbeddedText(query)))
                    .topK(topK * 2);
            if (expr != null && !expr.isBlank()) sparseB.filter(expr);
            AnnSearchReq sparseReq = sparseB.build();

            var hb = HybridSearchReq.builder()
                    .collectionName(collectionName)
                    .searchRequests(List.of(denseReq, sparseReq))
                    .ranker(new RRFRanker(60))
                    .topK(topK)
                    .outFields(List.of("content", "source", "title", "category", "updated_at", "permission"));

            SearchResp resp = c.hybridSearch(hb.build());
            List<RagHit> hits = extractHits(resp);
            cachePut(cacheKey, hits);
            return hits;
        } catch (Exception e) {
            log.warn("混合检索失败: {}，返回空", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 精排检索：混合召回放大候选 → rerank 打分 → 阈值过滤 → 去重 → 截断 topK。
     */
    public List<RagHit> rerankSearchHits(String collectionName, String query, int topK) {
        int candidateN = Math.max(topK * rerankCandidateMultiplier, topK);
        List<RagHit> candidates = hybridSearchHits(collectionName, query, candidateN);
        if (candidates.isEmpty()) return candidates;

        List<RagHit> ranked = rerankHits(query, candidates);
        List<RagHit> filtered = filterByScore(ranked, scoreThreshold);
        List<RagHit> deduped = dedupNearDuplicates(filtered);
        return deduped.size() > topK ? new ArrayList<>(deduped.subList(0, topK)) : deduped;
    }

    /** 调 rerank 服务，按精排分数重排 candidates（原 RRF 分被精排分覆盖） */
    public List<RagHit> rerankHits(String query, List<RagHit> candidates) {
        try {
            List<String> docs = new ArrayList<>(candidates.size());
            for (RagHit h : candidates) docs.add(h.getContent());

            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("documents", docs);
            Request request = new Request.Builder()
                    .url(rerankUrl)
                    .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                    .build();
            try (Response resp = HTTP.newCall(request).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "{}";
                JSONObject jsonResp = JSON.parseObject(respStr);
                JSONArray results = jsonResp.getJSONArray("results");
                if (results == null || results.isEmpty()) {
                    log.warn("rerank 返回空，保留原召回顺序");
                    return candidates;
                }
                // 服务端返回的 text 与 documents 一一对应且已按分数降序
                Map<String, Double> scoreByText = new LinkedHashMap<>();
                for (int i = 0; i < results.size(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    scoreByText.put(item.getString("text"), item.getDouble("score"));
                }
                List<RagHit> ranked = new ArrayList<>();
                for (Map.Entry<String, Double> e : scoreByText.entrySet()) {
                    RagHit src = candidates.stream()
                            .filter(c -> c.getContent().equals(e.getKey())).findFirst().orElse(null);
                    if (src != null) {
                        src.setScore(e.getValue());
                        ranked.add(src);
                    }
                }
                return ranked.isEmpty() ? candidates : ranked;
            }
        } catch (Exception e) {
            log.warn("rerank 服务调用失败，降级为原召回顺序: {}", e.getMessage());
            return candidates;
        }
    }

    // ============================================================
    // 后处理：阈值过滤 + 去重
    // ============================================================

    /** 分数阈值过滤：丢弃 score 低于 minScore 的片段 */
    public List<RagHit> filterByScore(List<RagHit> hits, double minScore) {
        List<RagHit> kept = new ArrayList<>();
        for (RagHit h : hits) {
            if (h.getScore() >= minScore) kept.add(h);
        }
        if (kept.isEmpty() && !hits.isEmpty()) {
            log.debug("阈值{}过滤后全部丢弃，保留 top1 兜底", minScore);
            kept.add(hits.get(0));
        }
        return kept;
    }

    /** 近重复去重：标题/前60字相同或高度包含的片段只保留分数最高的一条 */
    public List<RagHit> dedupNearDuplicates(List<RagHit> hits) {
        List<RagHit> kept = new ArrayList<>();
        for (RagHit h : hits) {
            boolean dup = false;
            String head = head60(h.getContent());
            for (RagHit k : kept) {
                String kh = head60(k.getContent());
                if (head.equals(kh) || head.startsWith(kh) || kh.startsWith(head)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) kept.add(h);
        }
        return kept;
    }

    private String head60(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", "");
        return t.length() <= 60 ? t : t.substring(0, 60);
    }

    // ============================================================
    // 缓存
    // ============================================================

    private List<RagHit> cacheGet(String key) {
        if (!cacheEnabled) return null;
        CacheEntry e = cache.get(key);
        if (e == null) return null;
        if (System.currentTimeMillis() / 1000 > e.expireAt()) {
            cache.remove(key);
            return null;
        }
        return e.hits();
    }

    private void cachePut(String key, List<RagHit> hits) {
        if (!cacheEnabled || hits == null || hits.isEmpty()) return;
        cache.put(key, new CacheEntry(hits, System.currentTimeMillis() / 1000 + cacheTtlSeconds));
        // 简单容量上限，防止无限增长
        if (cache.size() > 500) {
            cache.clear();
        }
    }

    /** 清空检索缓存（知识库更新后调用） */
    public void clearCache() {
        cache.clear();
        log.info("RAG 检索缓存已清空");
    }

    // ============================================================
    // 工具：解析结果 / 拼 prompt
    // ============================================================

    private float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private List<RagHit> extractHits(SearchResp resp) {
        List<RagHit> result = new ArrayList<>();
        if (resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) return result;
        for (SearchResp.SearchResult item : resp.getSearchResults().get(0)) {
            RagHit hit = new RagHit();
            Object idObj = item.getId();
            hit.setId(idObj instanceof Number ? ((Number) idObj).longValue() : 0L);
            hit.setScore(item.getScore());
            Object content = item.getEntity().get("content");
            hit.setContent(content != null ? content.toString() : "");
            RagDocMeta meta = new RagDocMeta();
            Object src = item.getEntity().get("source");
            Object title = item.getEntity().get("title");
            Object cat = item.getEntity().get("category");
            Object perm = item.getEntity().get("permission");
            meta.setSource(src != null ? src.toString() : "");
            meta.setTitle(title != null ? title.toString() : "");
            meta.setCategory(cat != null ? cat.toString() : "");
            Object ua = item.getEntity().get("updated_at");
            meta.setUpdatedAt(ua instanceof Number ? ((Number) ua).longValue() : 0L);
            meta.setPermission(perm != null ? perm.toString() : "all");
            hit.setMeta(meta);
            result.add(hit);
        }
        return result;
    }

    /** 检索并拼成一段文本，方便直接拼进 AI 提示词（默认 rerank 精排，可开关） */
    public String searchAsPromptContext(String query, int topK) {
        List<RagHit> hits = rerankEnabled
                ? rerankSearchHits(defaultCollection, query, topK)
                : hybridSearchHits(defaultCollection, query, topK);
        if (hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("以下是从业务知识库中检索到的相关资料：\n");
        for (int i = 0; i < hits.size(); i++) {
            RagHit h = hits.get(i);
            sb.append(i + 1).append(". ").append(h.getContent());
            if (h.getMeta() != null && h.getMeta().getSource() != null) {
                sb.append("  [来源:").append(h.getMeta().getSource()).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 删除集合——仅开发调试用 */
    public void dropCollection(String collectionName) {
        MilvusClientV2 c = client();
        Boolean has = c.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build());
        if (Boolean.TRUE.equals(has)) {
            try { c.releaseCollection(ReleaseCollectionReq.builder().collectionName(collectionName).build()); } catch (Exception ignored) {}
            c.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
            cache.clear();
            log.warn("已删除集合: {}", collectionName);
        }
    }
}
