package com.starlink.common.util;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** 身份证号仅用于核验，数据库只保存带密钥的摘要。 */
@Component
public class IdCardProtector {
    private final byte[] key;

    public IdCardProtector(@Value("${privacy.id-card-hmac-key:}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ID_CARD_HMAC_KEY 必须设置且不少于 32 字节");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String idCard) {
        String normalized = normalize(idCard);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("身份证摘要计算失败", e);
        }
    }

    public String mask(String idCard) {
        String normalized = normalize(idCard);
        return normalized.substring(0, 3) + "***********" + normalized.substring(normalized.length() - 4);
    }

    private String normalize(String idCard) {
        String normalized = idCard == null ? "" : idCard.strip().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("(?:[1-9]\\d{14}|[1-9]\\d{16}[0-9X])")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "身份证号格式不正确");
        }
        return normalized;
    }
}
