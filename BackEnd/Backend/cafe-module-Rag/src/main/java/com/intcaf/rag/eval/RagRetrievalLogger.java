package com.intcaf.rag.eval;

import com.intcaf.rag.vector.RagHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 检索日志观测：记录每次检索的 query、命中、耗时、命中来源，
 * 便于排查"为什么没召回/召回错了"。内存环形缓冲，最多保留最近 N 条。
 */
@Component
public class RagRetrievalLogger {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalLogger.class);
    private static final int MAX_LOG = 200;

    public record RetrievalLog(long ts, String query, int hitCount, long costMs,
                               List<String> hitSources) {}

    private final Deque<RetrievalLog> recent = new ArrayDeque<>();

    /** 记录一次检索（检索完调用） */
    public synchronized void record(String query, List<RagHit> hits, long costMs) {
        List<String> sources = new ArrayList<>();
        if (hits != null) {
            for (RagHit h : hits) {
                if (h.getMeta() != null && h.getMeta().getSource() != null) {
                    sources.add(h.getMeta().getSource());
                }
            }
        }
        RetrievalLog entry = new RetrievalLog(System.currentTimeMillis(), query,
                hits == null ? 0 : hits.size(), costMs, sources);
        recent.addLast(entry);
        while (recent.size() > MAX_LOG) recent.removeFirst();

        log.info("[RAG检索] query='{}' 命中{}条 耗时{}ms 来源={}",
                query, entry.hitCount(), costMs, sources);
    }

    /** 取最近 N 条检索日志 */
    public synchronized List<RetrievalLog> recent(int n) {
        List<RetrievalLog> all = new ArrayList<>(recent);
        int from = Math.max(0, all.size() - n);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    public synchronized void clear() {
        recent.clear();
    }
}
