package com.starlink.member.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.util.IdCardProtector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdCardProtectorTest {
    @Test
    void storesDeterministicKeyedHashWithoutExposingCard() {
        IdCardProtector protector = new IdCardProtector("test-only-id-card-hmac-key-0123456789");
        String card = "11010119900101123X";
        String hash = protector.hash(card);

        assertEquals(64, hash.length());
        assertEquals(hash, protector.hash(card.toLowerCase()));
        assertFalse(hash.contains(card));
        assertEquals("110***********123X", protector.mask(card));
        assertThrows(BusinessException.class, () -> protector.hash("123"));
    }
}
