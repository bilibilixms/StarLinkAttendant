package com.intcaf.rag.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户反馈收集：用户对某次 AI 回答/某条检索结果点赞(1)/点踩(0)。
 * 反馈数据用于后续 badcase 分析、补充知识库、调优阈值。
 * 内存存储，重启即失（生产应落库）。
 */
@Component
public class RagFeedback {

    private static final Logger log = LoggerFactory.getLogger(RagFeedback.class);
    private static final int MAX_FEEDBACK = 500;

    public record Feedback(long id, long ts, String query, String docSource,
                           int thumbs, String comment) {}

    private final AtomicLong seq = new AtomicLong(0);
    private final Deque<Feedback> store = new ArrayDeque<>();

    /**
     * 记录一条反馈。
     * @param thumbs 1=有用 0=没用
     */
    public synchronized void submit(String query, String docSource, int thumbs, String comment) {
        Feedback f = new Feedback(seq.incrementAndGet(), System.currentTimeMillis(),
                query, docSource, thumbs, comment == null ? "" : comment);
        store.addLast(f);
        while (store.size() > MAX_FEEDBACK) store.removeFirst();
        log.info("[RAG反馈] query='{}' source={} 赞={} 评论='{}'", query, docSource, thumbs, comment);
    }

    /** 全部反馈 */
    public synchronized List<Feedback> all() {
        return new ArrayList<>(store);
    }

    /** 统计点赞率 */
    public synchronized String stats() {
        long good = store.stream().filter(f -> f.thumbs() == 1).count();
        long bad = store.size() - good;
        double rate = store.isEmpty() ? 0 : 100.0 * good / store.size();
        return String.format("反馈总数=%d 点赞=%d 点踩=%d 点赞率=%.1f%%", store.size(), good, bad, rate);
    }
}
