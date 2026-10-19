package com.intcaf.ai.service;

import com.starlink.system.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiSessionServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sessionLookupIsScopedToAuthenticatedOwner() {
        SecurityUser member = new SecurityUser(7L, "13800000000", (byte) 1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member, null, member.getAuthorities()));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("session_no = ?"), eq("S-other"), eq("user"), eq(7L)))
                .thenReturn(List.of());

        assertNull(new AiSessionService(jdbc).getSessionDetail("S-other"));
        verify(jdbc).queryForList(contains("user_id = ?"), eq("S-other"), eq("user"), eq(7L));
        verifyNoMoreInteractions(jdbc);
    }
}
