package com.courtly.service.storage;

import com.courtly.common.config.StorageProperties;
import com.courtly.common.exception.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Luu anh bang chung cua bao cao don dat san (2.3.88).
 *
 * <p>Anh nam tai {@code {uploadDir}/disputes/{disputeId}/{uuid}.{ext}} va KHONG duoc phuc vu cong khai nhu anh san: chi doc qua
 * API da kiem tra quyen (nguoi bao cao, chu san cua san, quan tri vien). Ten file do server sinh.
 */
@Slf4j
@Component
public class DisputeEvidenceStorage {

    private static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp)$");

    private final Path root;

    public DisputeEvidenceStorage(StorageProperties properties) {
        this.root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    /** @return ten file da luu (khong gom thu muc) */
    public String store(UUID disputeId, byte[] bytes, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = disputeDir(disputeId).resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("Khong luu duoc anh bang chung", exception);
        }
        return fileName;
    }

    public void delete(UUID disputeId, String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(disputeDir(disputeId).resolve(fileName));
        } catch (IOException exception) {
            // File mo coi chi ton dia, khong anh huong du lieu.
            log.warn("Khong xoa duoc anh bang chung {}/{}", disputeId, fileName, exception);
        }
    }

    /** 404 cho moi ten file khong hop le hoac khong ton tai. */
    public Path resolveExisting(UUID disputeId, String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw ApiException.notFound("Khong tim thay anh bang chung.");
        }
        Path file = disputeDir(disputeId).resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw ApiException.notFound("Khong tim thay anh bang chung.");
        }
        return file;
    }

    private Path disputeDir(UUID disputeId) {
        return root.resolve("disputes").resolve(disputeId.toString());
    }
}
