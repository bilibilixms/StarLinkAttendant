package com.starlink.common.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置项。
 * <p>
 * 密钥等敏感信息一律通过环境变量注入（见 application.yml 占位符），禁止明文写入仓库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** 节点，如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    /** AccessKey ID（环境变量 ALIYUN_OSS_ACCESS_KEY_ID） */
    private String accessKeyId;

    /** AccessKey Secret（环境变量 ALIYUN_OSS_ACCESS_KEY_SECRET） */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 对象键目录前缀 */
    private String dirPrefix = "starlink";
}
