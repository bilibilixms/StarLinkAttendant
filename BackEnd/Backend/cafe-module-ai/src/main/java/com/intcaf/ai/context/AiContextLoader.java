package com.intcaf.ai.context;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * AI 上下文加载器
 * 启动时扫描以下两处目录下的所有 .txt / .md 文件，读取内容并缓存：
 *   1. classpath:ai-context/（src/main/resources/ai-context/）
 *   2. 模块根/uploaded/（cafe-module-ai/uploaded/，MinerU 文档转换产物输出目录）
 * 每次 AI 对话时，将上下文内容注入系统提示词中，让 LLM 拥有项目业务知识。
 *
 * 使用方式：
 *   - 在 src/main/resources/ai-context/ 下放置 .txt 或 .md 文件；
 *   - 或经管理端「文档转换」上传，产物自动落在 uploaded/。
 * 文件按名称排序加载，每个文件内容以 "## [文件名]" 作为分隔标题。
 */
@Slf4j
@Component
public class AiContextLoader {

    /** 上下文文件路径模式（classpath 内） */
    private static final String CONTEXT_PATTERN = "classpath:ai-context/**/*.{txt,md}";

    /** 模块根目录（cafe-module-ai），由 class 文件位置自动推导，不依赖启动工作目录 */
    private static final Path MODULE_ROOT = resolveModuleRoot();

    /** 外部上传目录：模块根/uploaded（MinerU 转换产物） */
    private static final Path UPLOAD_DIR = MODULE_ROOT.resolve("uploaded");

    /** 缓存：文件名 → 文件内容 */
    private final Map<String, String> contextFiles = new LinkedHashMap<>();

    /** 合并后的完整上下文文本 */
    private String cachedContext = "";

    private static Path resolveModuleRoot() {
        try {
            Path classesDir = Path.of(AiContextLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return classesDir.resolveSibling("..").normalize();
        } catch (URISyntaxException e) {
            return Path.of("").toAbsolutePath();
        }
    }

    @PostConstruct
    public void loadContextFiles() {
        contextFiles.clear();
        cachedContext = "";
        StringBuilder combined = new StringBuilder();

        // 1) classpath:ai-context/**
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CONTEXT_PATTERN);
            log.info("AI context: scanning classpath:ai-context/ ... found {} file(s)", resources.length);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                try {
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (!content.isBlank()) {
                        contextFiles.put(filename, content);
                        combined.append("\n\n## [").append(filename).append("]\n").append(content.trim());
                        log.info("AI context: loaded '{}' ({} chars)", filename, content.length());
                    }
                } catch (IOException e) {
                    log.warn("AI context: failed to read '{}': {}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("AI context: classpath 'ai-context/' not found or empty: {}", e.getMessage());
        }

        // 2) 模块根/uploaded/**（MinerU 上传产物，覆盖同名 classpath 文件）
        if (Files.isDirectory(UPLOAD_DIR)) {
            try (Stream<Path> walk = Files.walk(UPLOAD_DIR)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".md") || n.endsWith(".txt");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String filename = UPLOAD_DIR.relativize(p).toString().replace('\\', '/');
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            if (!content.isBlank()) {
                                contextFiles.put(filename, content);
                                combined.append("\n\n## [").append(filename).append("]\n").append(content.trim());
                                log.info("AI context: loaded '{}' ({} chars)", filename, content.length());
                            }
                        } catch (IOException e) {
                            log.warn("AI context: failed to read '{}': {}", filename, e.getMessage());
                        }
                    });
            } catch (IOException e) {
                log.warn("AI context: failed to scan upload dir {}: {}", UPLOAD_DIR, e.getMessage());
            }
        } else {
            log.info("AI context: upload dir '{}' not exists yet, skipped", UPLOAD_DIR);
        }

        cachedContext = combined.toString().trim();
        log.info("AI context: total {} file(s) loaded, {} chars combined",
                contextFiles.size(), cachedContext.length());
    }

    /**
     * 运行时重新加载上下文（MinerU 文档转换等新增/更新上下文文件后调用，
     * 无需重启应用，新文档立即进入 AI 知识库）
     */
    public synchronized void reload() {
        loadContextFiles();
        log.info("AI context: reloaded, now {} file(s)", contextFiles.size());
    }

    /**
     * 获取合并后的完整上下文文本
     * @return 所有上下文文件内容（已按文件名排序拼接），无文件时返回空字符串
     */
    public String getContextContent() {
        return cachedContext;
    }

    /**
     * 是否加载了任何上下文文件
     */
    public boolean hasContext() {
        return !cachedContext.isEmpty();
    }

    /**
     * 获取已加载的文件名列表
     */
    public java.util.Set<String> getLoadedFiles() {
        return contextFiles.keySet();
    }
}
