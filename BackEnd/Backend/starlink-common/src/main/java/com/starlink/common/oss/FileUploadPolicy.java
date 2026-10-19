package com.starlink.common.oss;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;

import java.util.Locale;
import java.util.Set;

/** 公网文件目录只接收受支持的图片，并限制目录名。 */
public final class FileUploadPolicy {
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private FileUploadPolicy() {}

    public static String safeBizType(String bizType) {
        if (bizType == null || !bizType.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "文件业务类型不合法");
        }
        return bizType;
    }

    public static String safeExtension(String fileName) {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "文件名不合法");
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "仅支持 JPG、PNG、GIF 和 WebP 图片");
        }
        return extension;
    }

    public static void verifyImageSignature(String extension, byte[] header) {
        boolean jpeg = header.length >= 3 && (header[0] & 0xff) == 0xff
                && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
        boolean png = header.length >= 8 && (header[0] & 0xff) == 0x89
                && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 13 && header[5] == 10 && header[6] == 26 && header[7] == 10;
        boolean gif = header.length >= 6 && header[0] == 'G' && header[1] == 'I'
                && header[2] == 'F' && header[3] == '8'
                && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
        boolean webp = header.length >= 12 && header[0] == 'R' && header[1] == 'I'
                && header[2] == 'F' && header[3] == 'F' && header[8] == 'W'
                && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        boolean matches = switch (extension) {
            case "jpg", "jpeg" -> jpeg;
            case "png" -> png;
            case "gif" -> gif;
            case "webp" -> webp;
            default -> false;
        };
        if (!matches) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "图片内容与格式不符");
        }
    }
}
