package com.starlink.common.oss;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 阿里云 OSS 文件存储实现。
 * <p>
 * 配置 {@code file.storage=oss} 时激活，替代本地存储。
 * 密钥通过环境变量注入，见 {@link OssProperties}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.storage", havingValue = "oss")
public class OssFileService implements FileService {

    private final OssProperties properties;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
        log.info("阿里云 OSS 客户端初始化完成: endpoint={}, bucket={}",
                properties.getEndpoint(), properties.getBucketName());
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Override
    public String upload(InputStream inputStream, String fileName, String bizType) {
        try {
            String extension = FileUtil.extName(fileName);
            String objectKey = buildObjectKey(bizType, extension);
            ossClient.putObject(properties.getBucketName(), objectKey, inputStream);

            String fileUrl = "https://" + properties.getBucketName() + "." + properties.getEndpoint() + "/" + objectKey;
            log.info("文件上传 OSS 成功: {} -> {}", fileName, fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("文件上传 OSS 失败: {}", fileName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        try {
            String objectKey = extractObjectKey(fileUrl);
            if (objectKey == null) {
                log.warn("OSS 文件删除失败，无法从 URL 解析对象键: {}", fileUrl);
                return false;
            }
            ossClient.deleteObject(properties.getBucketName(), objectKey);
            log.info("OSS 文件删除: {} -> {}", fileUrl, objectKey);
            return true;
        } catch (Exception e) {
            log.error("OSS 文件删除失败: {}", fileUrl, e);
            return false;
        }
    }

    @Override
    public String generatePresignedUrl(String fileUrl, int expirationMinutes) {
        try {
            String objectKey = extractObjectKey(fileUrl);
            if (objectKey == null) {
                return fileUrl;
            }
            Date expiration = new Date(System.currentTimeMillis() + expirationMinutes * 60_000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(properties.getBucketName(), objectKey);
            request.setExpiration(expiration);
            URL signedUrl = ossClient.generatePresignedUrl(request);
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("OSS 签名 URL 生成失败: {}", fileUrl, e);
            return fileUrl;
        }
    }

    /** 对象键：dirPrefix/bizType/yyyyMM/uuid.ext */
    private String buildObjectKey(String bizType, String extension) {
        String dateDir = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String name = IdUtil.fastSimpleUUID() + (extension != null && !extension.isEmpty() ? "." + extension : "");
        String prefix = properties.getDirPrefix() == null || properties.getDirPrefix().isEmpty()
                ? "" : properties.getDirPrefix() + "/";
        return prefix + bizType + "/" + dateDir + "/" + name;
    }

    /** 从完整 URL 中提取对象键（bucket 域名之后的部分） */
    private String extractObjectKey(String fileUrl) {
        String host = properties.getBucketName() + "." + properties.getEndpoint() + "/";
        int idx = fileUrl.indexOf(host);
        if (idx < 0) {
            return null;
        }
        return fileUrl.substring(idx + host.length());
    }
}
