package com.starlink.system.controller;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.oss.FileService;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 通用文件上传接口。
 * <p>
 * 实际存储由 {@code file.storage} 决定：local 本地磁盘 / oss 阿里云 OSS。
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件，返回访问 URL。
     *
     * @param file    文件
     * @param bizType 业务类型（avatar / product / combo 等，用作存储目录）
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "bizType", defaultValue = "common") String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        try {
            String url = fileService.upload(file.getInputStream(), file.getOriginalFilename(), bizType);
            log.info("文件上传成功: {} -> {}", file.getOriginalFilename(), url);
            return Result.ok(Map.of("url", url, "name", file.getOriginalFilename()));
        } catch (IOException e) {
            log.error("文件读取失败: {}", file.getOriginalFilename(), e);
            throw new BusinessException(500, "文件上传失败");
        }
    }
}
