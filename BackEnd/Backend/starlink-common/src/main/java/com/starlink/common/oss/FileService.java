package com.starlink.common.oss;

import java.io.InputStream;

/**
 * 文件存储服务接口。
 * <p>
 * 抽象文件上传/下载操作，支持阿里云 OSS 和本地存储两种实现。
 * 各业务模块通过注入此接口使用文件存储能力，无需关心底层实现。
 *
 * @see LocalFileService 本地文件存储实现
 */
public interface FileService {

    /**
     * 上传文件。
     *
     * @param inputStream 文件输入流
     * @param fileName    原始文件名
     * @param bizType     业务类型（avatar / product / export）
     * @return 文件访问 URL
     */
    String upload(InputStream inputStream, String fileName, String bizType);

    /**
     * 删除文件。
     *
     * @param fileUrl 文件 URL
     * @return 是否删除成功
     */
    boolean delete(String fileUrl);

    /**
     * 生成签名访问 URL（适用于私有文件）。
     *
     * @param fileUrl          文件 URL
     * @param expirationMinutes 过期时间（分钟）
     * @return 签名 URL
     */
    String generatePresignedUrl(String fileUrl, int expirationMinutes);
}
