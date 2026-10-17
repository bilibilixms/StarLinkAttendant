package com.starlink.system.controller;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.oss.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileControllerSecurityTest {
    @Test
    void rejectsHtmlDisguisedAsImageBeforeStorage() {
        FileService storage = mock(FileService.class);
        FileController controller = new FileController(storage);
        MockMultipartFile file = new MockMultipartFile("file", "payload.jpg", "image/jpeg",
                "<html><script>bad()</script></html>".getBytes());

        assertThrows(BusinessException.class, () -> controller.upload(file, "product"));
        verify(storage, never()).upload(any(), any(), any());
    }
}
