package com.intcaf.rag.rewrite;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query 改写 / 扩展（规则版，不依赖大模型，可离线自测）。
 * 把用户口语化提问扩展成多个规范检索式，多路召回后合并，减少漏检。
 * 生产环境可换成 LLM 改写，接口保持不变。
 */
@Component
public class QueryRewriter {

    private static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();

    static {
        SYNONYMS.put("开机|上网|上机|开台|坐机器|开机器", List.of("上机管理", "开机登录"));
        SYNONYMS.put("充钱|充值|续费|加钱|充会员", List.of("会员充值", "续费规则"));
        SYNONYMS.put("会员|客户|用户|客人", List.of("会员管理"));
        SYNONYMS.put("余额|剩多少钱|还有多久", List.of("账户余额", "会员余额"));
        SYNONYMS.put("下机|关机|结账|走人|锁机", List.of("下机结账", "会话结束"));
        SYNONYMS.put("商品|卖东西|零食|水|饮料", List.of("商品销售", "库存"));
        SYNONYMS.put("计费|多少钱一小时|价格|费用|扣钱", List.of("计费规则", "费率"));
        SYNONYMS.put("退款|退钱|退余额", List.of("退款", "会员退余额"));
        SYNONYMS.put("网管|服务员|员工|收银", List.of("员工管理"));
    }

    private static final List<String> STOP_WORDS =
            List.of("怎么", "如何", "为什么", "告诉我", "请问", "一下", "可以", "能不能");

    /**
     * 把原始 query 扩展成 1 个原始 + N 个改写式（去重、保留顺序）。
     * @return 第一个一定是原始 query
     */
    public List<String> rewrite(String query) {
        List<String> result = new ArrayList<>();
        if (query == null || query.isBlank()) return result;

        String trimmed = query.trim();
        result.add(trimmed);

        String cleaned = trimmed;
        for (String sw : STOP_WORDS) {
            cleaned = cleaned.replace(sw, "");
        }
        cleaned = cleaned.trim();
        if (!cleaned.isEmpty() && !cleaned.equals(trimmed)) {
            addDistinct(result, cleaned);
        }

        for (Map.Entry<String, List<String>> e : SYNONYMS.entrySet()) {
            String[] triggers = e.getKey().split("\\|");
            for (String t : triggers) {
                if (trimmed.contains(t)) {
                    for (String syn : e.getValue()) {
                        addDistinct(result, syn);
                    }
                    break;
                }
            }
        }
        return result;
    }

    private void addDistinct(List<String> list, String s) {
        if (s == null || s.isBlank()) return;
        for (String existing : list) {
            if (existing.equals(s)) return;
        }
        list.add(s);
    }
}
