package com.intcaf.ai.controller;

import com.intcaf.ai.service.AiSessionService;
import com.starlink.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 会话持久化接口
 * ----------------------------------------------------------
 * 用户端（小程序 / H5）与管理端共用：
 *  - POST   /api/ai/session                创建会话
 *  - GET    /api/ai/session/list           会话列表
 *  - GET    /api/ai/session/{sessionNo}    会话详情（含消息）
 *  - POST   /api/ai/session/{sessionNo}/message  追加消息
 *  - DELETE /api/ai/session/{sessionNo}    逻辑删除会话
 * 身份由请求体 / 查询参数传入（userType + userId），未登录 userId 可为空。
 */
@RestController
@RequestMapping("/api/ai/session")
@RequiredArgsConstructor
public class AiSessionController {

    private final AiSessionService sessionService;

    /** 创建会话：body { userType?, userId?, userName?, title? } */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Long userId = toLong(b.get("userId"));
        String userType = str(b.get("userType"), "user");
        String userName = str(b.get("userName"), null);
        String title = str(b.get("title"), null);
        return Result.ok(sessionService.createSession(userType, userId, userName, title));
    }

    /** 会话列表：?userType=user&userId=1（userId 可空，空则查未登录的会话） */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "user") String userType,
                                                  @RequestParam(required = false) Long userId) {
        return Result.ok(sessionService.listSessions(userType, userId));
    }

    /** 会话详情（含消息列表） */
    @GetMapping("/{sessionNo}")
    public Result<Map<String, Object>> detail(@PathVariable String sessionNo) {
        Map<String, Object> detail = sessionService.getSessionDetail(sessionNo);
        if (detail == null) {
            return Result.fail(404, "会话不存在");
        }
        return Result.ok(detail);
    }

    /** 追加消息：body { role: user|assistant, content, thinkingJson?, costMs?, model? } */
    @PostMapping("/{sessionNo}/message")
    public Result<Void> addMessage(@PathVariable String sessionNo,
                                   @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        sessionService.addMessage(
                sessionNo,
                str(b.get("role"), "user"),
                str(b.get("content"), ""),
                b.get("thinkingJson") == null ? null : String.valueOf(b.get("thinkingJson")),
                toInt(b.get("costMs")),
                str(b.get("model"), null)
        );
        return Result.ok(null);
    }

    /** 逻辑删除会话 */
    @DeleteMapping("/{sessionNo}")
    public Result<Void> delete(@PathVariable String sessionNo) {
        sessionService.deleteSession(sessionNo);
        return Result.ok(null);
    }

    /* ==================== 辅助 ==================== */

    private static String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v).trim();
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        try {
            return Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
