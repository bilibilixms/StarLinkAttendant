package com.starlink.marketing.service;

import com.starlink.common.util.IdCardProtector;
import com.starlink.marketing.mapper.IntegrationMapper;
import com.starlink.marketing.mapper.SmsNotificationMapper;
import com.starlink.marketing.service.impl.IntegrationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityPrivacyTest {
    @Test
    void verificationQueriesHashAndOnlyAuditsMaskedCard() {
        IntegrationMapper mapper = mock(IntegrationMapper.class);
        IdCardProtector protector = new IdCardProtector("test-only-id-card-hmac-key-0123456789");
        String idCard = "11010119900101123X";
        when(mapper.selectMemberByNameAndIdCard("张三", protector.hash(idCard)))
                .thenReturn(Map.of("id", 7L));
        IntegrationServiceImpl service = new IntegrationServiceImpl(mapper,
                mock(SmsNotificationMapper.class), protector);

        Map<String, Object> result = service.verifyIdentity("张三", idCard, 7L);

        assertEquals(true, result.get("success"));
        assertNotEquals(idCard, result.get("idCard"));
        ArgumentCaptor<String> audit = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertAuditLog(eq("identity_verify"), eq(7L), eq("verify"), audit.capture());
        assertFalse(audit.getValue().contains(idCard));
        assertEquals(protector.hash(idCard.toLowerCase()), protector.hash(idCard));
    }
}
