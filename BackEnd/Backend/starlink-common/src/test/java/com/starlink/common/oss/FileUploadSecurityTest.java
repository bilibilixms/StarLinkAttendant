package com.starlink.common.oss;

import com.starlink.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadSecurityTest {
    @TempDir Path temp;

    @Test
    void rejectsDirectoryTraversalAndActiveContent() {
        assertThrows(BusinessException.class, () -> FileUploadPolicy.safeBizType("../outside"));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.safeExtension("payload.html"));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.verifyImageSignature("jpg",
                "<html>attack</html>".getBytes()));
    }

    @Test
    void localStorageNeverDeletesOutsideItsRoot() throws Exception {
        Path root = temp.resolve("uploads");
        Files.createDirectories(root);
        Path outside = temp.resolve("outside.jpg");
        Files.writeString(outside, "keep");
        LocalFileService service = new LocalFileService();
        ReflectionTestUtils.setField(service, "basePath", root.toString());
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test/uploads");

        assertFalse(service.delete("https://example.test/uploads/../outside.jpg"));
        assertFalse(service.delete("https://other.test/uploads/outside.jpg"));
        assertTrue(Files.exists(outside));
        assertThrows(BusinessException.class, () -> service.upload(new ByteArrayInputStream(new byte[0]),
                "photo.jpg", "../../outside"));
    }
}
