package com.intcaf.ai.controller;

import com.intcaf.ai.context.AiContextLoader;
import com.intcaf.rag.minerU.MinerUUtil;
import com.intcaf.rag.vector.RagVectorService;
import com.starlink.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * MinerU 文档转化接口（Web 端 AI 页面「文档转换」功能）
 *
 * 支持 pdf / doc / docx（走本地 MinerU 服务 127.0.0.1:8000 解析为 Markdown）、
 * txt（纯文本直接拷贝为 Markdown）。
 * 转换产物输出到 cafe-module-ai/uploaded/ 目录（模块根目录下，方便直接查看管理），
 * 转换完成后：
 *   1. 触发 AiContextLoader.reload() —— 新文档立即进入 AI 上下文知识库（无需重启）；
 *   2. 调用 RagVectorService.ingestMdFile() —— 切片向量化写入 Milvus，供 RAG 语义检索。
 *      Milvus / embedding 服务不可用时自动降级（文档仍进上下文库，仅跳过向量入库）。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/minerU")
@RequiredArgsConstructor
public class MinerUController {

    private final AiContextLoader aiContextLoader;
    private final RagVectorService ragVectorService;

    /** RAG 默认集合名（与 application.yml 的 rag.default-collection 一致） */
    @Value("${rag.default-collection:intcaf_business_kb}")
    private String ragCollection;

    /** 本模块根目录（cafe-module-ai），由 class 文件位置自动推导，不依赖启动工作目录 */
    private static final Path MODULE_ROOT = resolveModuleRoot();

    /** 转换输出目录：模块根/uploaded（cafe-module-ai/uploaded，AiContextLoader 会额外扫描该目录并加载进上下文） */
    private static final File OUTPUT_DIR = MODULE_ROOT.resolve("uploaded").toFile();

    /** 支持的文档扩展名 */
    private static final Set<String> SUPPORTED_EXTS = Set.of("pdf", "doc", "docx", "txt");

    private static Path resolveModuleRoot() {
        try {
            Path classesDir = Path.of(MinerUController.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return classesDir.resolveSibling("..").normalize();
        } catch (Exception e) {
            return Path.of("").toAbsolutePath();
        }
    }

    private static String extension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** 文件名安全化：仅取最后一段路径，防止路径穿越 */
    private static String safeBaseName(String originalName) {
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    /**
     * 上传并转换文档为 Markdown
     * POST /api/ai/minerU/convert
     * @param file 待转换文档（pdf/doc/docx/txt）
     * @param ocr  是否开启 OCR（默认 false，仅对扫描件/图片型 PDF 有意义）
     */
    @PostMapping("/convert")
    public Result<Map<String, Object>> convert(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "ocr", defaultValue = "false") boolean ocr) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "请选择要转换的文档");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return Result.fail(400, "文件名不能为空");
        }
        String ext = extension(originalName);
        if (!SUPPORTED_EXTS.contains(ext)) {
            return Result.fail(400, "不支持的文件类型 ." + ext + "，仅支持 pdf / doc / docx / txt");
        }

        if (!OUTPUT_DIR.exists() && !OUTPUT_DIR.mkdirs()) {
            log.error("MinerU: 无法创建输出目录 {}", OUTPUT_DIR);
            return Result.fail(500, "无法创建输出目录，请检查后端 resources 目录权限");
        }

        String base = safeBaseName(originalName);
        File outMd = new File(OUTPUT_DIR, base + ".md");
        File tmp = null;

        try {
            // 临时落盘（txt 直拷也走统一路径）
            tmp = File.createTempFile("minerU_", "." + ext);
            file.transferTo(tmp);

            if ("txt".equals(ext)) {
                Files.copy(tmp.toPath(), outMd.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                MinerUUtil.pdfToMarkdown(tmp.getAbsolutePath(), outMd.getAbsolutePath(), ocr);
            }

            String mdText = Files.readString(outMd.toPath(), StandardCharsets.UTF_8);

            // 1) 新文档立即进入 AI 上下文知识库（无需重启）
            aiContextLoader.reload();

            // 2) 切片向量化写入 Milvus（RAG 语义检索）；Milvus/embedding 不可用时降级，不影响文档入库
            String ragStatus;
            try {
                ragVectorService.ingestMdFile(outMd.getAbsolutePath(), ragCollection);
                ragVectorService.clearCache(); // 清检索缓存，新文档立即可被检索到
                ragStatus = "ingested";
                log.info("MinerU: 文档[{}]已写入 Milvus 集合[{}]", outMd.getName(), ragCollection);
            } catch (Exception ragEx) {
                ragStatus = "skipped: " + (ragEx.getMessage() == null ? "向量服务不可用" : ragEx.getMessage());
                log.warn("MinerU: 文档[{}]向量入库失败（已降级，文档仍在上下文知识库中）: {}",
                        outMd.getName(), ragEx.getMessage());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileName", outMd.getName());
            data.put("size", outMd.length());
            data.put("chars", mdText.length());
            data.put("loaded", true);
            data.put("ragStatus", ragStatus);
            data.put("message", "转换成功，文档已加入 AI 知识库"
                    + ("ingested".equals(ragStatus) ? "并写入向量检索库，可直接向 AI 提问「" + base + "」中的内容"
                                                    : "（向量检索库暂不可用，上下文问答可用）"));
            log.info("MinerU: {} -> {} ({} chars, rag={})", originalName, outMd.getName(), mdText.length(), ragStatus);
            return Result.ok(data);
        } catch (Exception e) {
            log.error("MinerU 转换失败: {}", e.getMessage(), e);
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused"))) {
                return Result.fail(502, "MinerU 解析服务未启动（127.0.0.1:8000），请先在本地启动 MinerU 服务");
            }
            return Result.fail(500, "转换失败：" + (msg == null ? "未知错误" : msg));
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    /** 旧转换输出目录：src/main/resources/ai-context/minerU（兼容手动放置的 md） */
    private static final File LEGACY_OUTPUT_DIR = MODULE_ROOT.resolve("src/main/resources/ai-context/minerU").toFile();

    /**
     * md 文件直接写入向量库（RAG 检索），无需经过 MinerU 转换
     * POST /api/ai/minerU/ingest
     * 查找顺序：uploaded/ → 旧目录 resources/ai-context/minerU/
     * @param file  md 文件名（可选；不传则入库全部可入库的 md 文件）
     * @return 每个文件的入库结果（ingested / skipped + 原因）及向量库当前文档数
     */
    @PostMapping("/ingest")
    public Result<Map<String, Object>> ingest(@RequestParam(value = "file", required = false) String file) {
        // 收集待入库 md 文件：指定文件 或 两目录全部 md
        List<File> targets = new ArrayList<>();
        if (file != null && !file.isBlank()) {
            String name = safeBaseName(file) + ".md";
            File f1 = new File(OUTPUT_DIR, name);
            File f2 = new File(LEGACY_OUTPUT_DIR, name);
            File found = f1.isFile() ? f1 : (f2.isFile() ? f2 : null);
            if (found == null) {
                return Result.fail(404, "未找到文档 " + name + "（已检查 uploaded/ 与 resources/ai-context/minerU/）");
            }
            targets.add(found);
        } else {
            for (File dir : new File[]{OUTPUT_DIR, LEGACY_OUTPUT_DIR}) {
                File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".md"));
                if (files != null) {
                    targets.addAll(Arrays.asList(files));
                }
            }
            if (targets.isEmpty()) {
                return Result.fail(404, "未找到任何可入库的 md 文件（uploaded/ 与 resources/ai-context/minerU/ 均为空）");
            }
        }

        // 逐个入库，单个失败不影响其余
        List<Map<String, Object>> results = new ArrayList<>();
        boolean anyIngested = false;
        for (File f : targets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileName", f.getName());
            item.put("size", f.length());
            try {
                ragVectorService.ingestMdFile(f.getAbsolutePath(), ragCollection);
                anyIngested = true;
                item.put("status", "ingested");
                log.info("MinerU ingest: {} -> Milvus 集合[{}]", f.getName(), ragCollection);
            } catch (Exception ragEx) {
                item.put("status", "skipped");
                item.put("reason", ragEx.getMessage() == null ? "向量服务不可用" : ragEx.getMessage());
                log.warn("MinerU ingest: 文档[{}]向量入库失败: {}", f.getName(), ragEx.getMessage());
            }
            results.add(item);
        }

        // 有成功入库则清检索缓存，新文档立即可被检索到
        if (anyIngested) {
            try {
                ragVectorService.clearCache();
            } catch (Exception ignored) {
                // 缓存清理失败不影响结果
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", results);
        try {
            data.put("totalDocs", ragVectorService.countDocs(ragCollection));
        } catch (Exception e) {
            data.put("totalDocs", "查询失败：" + (e.getMessage() == null ? "向量服务不可用" : e.getMessage()));
        }
        data.put("message", anyIngested
                ? "入库完成，可直接向 AI 提问文档中的内容"
                : "全部入库失败，请检查 embedding 服务(8001)与 Milvus(19530)是否已启动");
        return Result.ok(data);
    }

    /**
     * 已转换文档列表
     * GET /api/ai/minerU/files
     */
    @GetMapping("/files")
    public Result<List<Map<String, Object>>> listFiles() {
        List<Map<String, Object>> list = new ArrayList<>();
        File[] files = OUTPUT_DIR.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".md"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (File f : files) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", f.getName());
                item.put("size", f.length());
                item.put("lastModified", f.lastModified());
                list.add(item);
            }
        }
        return Result.ok(list);
    }

    /**
     * 查看已转换文档的 Markdown 内容
     * GET /api/ai/minerU/files/{name}
     */
    @GetMapping("/files/{name}")
    public Result<Map<String, Object>> getFileContent(@PathVariable("name") String name) {
        String decoded = URLDecoder.decode(name, StandardCharsets.UTF_8);
        File f = new File(OUTPUT_DIR, decoded);
        // 防路径穿越：确认解析后仍位于输出目录内
        try {
            Path outPath = OUTPUT_DIR.getCanonicalFile().toPath();
            Path targetPath = f.getCanonicalFile().toPath();
            if (!targetPath.startsWith(outPath) || !decoded.toLowerCase(Locale.ROOT).endsWith(".md") || !f.isFile()) {
                return Result.fail(404, "文档不存在");
            }
        } catch (IOException e) {
            return Result.fail(404, "文档不存在");
        }
        try {
            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", decoded);
            data.put("content", content);
            data.put("chars", content.length());
            return Result.ok(data);
        } catch (IOException e) {
            return Result.fail(500, "读取文档失败：" + e.getMessage());
        }
    }
}
