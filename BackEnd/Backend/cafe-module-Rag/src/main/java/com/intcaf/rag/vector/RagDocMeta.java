package com.intcaf.rag.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文档片段元数据。
 * 每个入库 chunk 都带这些字段，支持：
 *   - 按来源(source)做增量更新/删除
 *   - 按分类(category)、更新时间(updatedAt)、权限角色(permission)做检索过滤
 *   - 生成层引用溯源（答案标注来自哪个文件哪一节）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocMeta {

    /** 来源文件名，如 business-rules.md —— 增量更新/删除的主键 */
    private String source;

    /** 文档标题（从 md 一级标题提取，便于溯源展示） */
    private String title;

    /** 业务分类，如 member / billing / session / inventory —— 检索时按域过滤 */
    private String category;

    /** 文档更新时间戳(秒)，检索时可按时间过滤最新规则 */
    private long updatedAt;

    /** 可见权限角色，如 admin / staff / all —— 检索时按当前用户角色过滤 */
    private String permission;
}
