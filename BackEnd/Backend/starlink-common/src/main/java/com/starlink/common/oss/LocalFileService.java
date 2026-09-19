package com.starlink.common.oss;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储实现。
 * <p>
 * 将文件保存到本地磁盘，适用于开发环境或 Demo 部署。
 * 生产环境可通过配置 {@code file.storage=oss} 切换为阿里云 OSS 实现。
 * <p>
 * 配置项：
 * <ul>
 *   <li>{@code file.storage} — 存储方式，默认 local</li>
 *   <li>{@code file.local.base-path} — 本地存储根目录</li>
 *   <li>{@code file.local.base-url} — 本地文件访问 URL 前缀</li>
 * </ul>
 *
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileService implements FileService {

    @Value("${file.local.base-path:./uploads}")
    private String basePath;

    @Value("${file.local.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    @Override
    public String upload(InputStream inputStream, String fileName, String bizType) {
        try {
            // 生成唯一文件名
            String extension = FileUtil.extName(fileName);
            String storedName = IdUtil.fastSimpleUUID() + (extension != null ? "." + extension : "");

            // 构建存储路径：basePath/bizType/yyyyMM/storedName
            Path dirPath = Paths.get(basePath, bizType);
            Files.createDirectories(dirPath);

            Path filePath = dirPath.resolve(storedName);
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = baseUrl + "/" + bizType + "/" + storedName;
            log.info("文件上传成功: {} -> {}", fileName, fileUrl);
            return fileUrl;

        } catch (IOException e) {
            log.error("文件上传失败: {}", fileName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        try {
            String relativePath = fileUrl.replace(baseUrl + "/", "");
            Path filePath = Paths.get(basePath, relativePath);
            File file = filePath.toFile();
            if (file.exists()) {
                boolean deleted = file.delete();
                log.info("文件删除: {} -> {}", fileUrl, deleted);
                return deleted;
            }
            return false;
        } catch (Exception e) {
            log.error("文件删除失败: {}", fileUrl, e);
            return false;
        }
    }

    @Override
    public String generatePresignedUrl(String fileUrl, int expirationMinutes) {
        // 本地存储无需签名 URL，直接返回原始 URL
        return fileUrl;
    }
}
