package com.courtly.service.storage;

import com.courtly.common.config.StorageProperties;
import com.courtly.common.exception.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Luu anh san tren dia cua server (2.3.6).
 *
 * <p>Anh nam tai {@code {uploadDir}/venues/{venueId}/{uuid}.{ext}} va duoc phuc vu cong khai
 * tai {@code {publicBaseUrl}/api/v1/media/venues/{venueId}/{uuid}.{ext}}. Ten file do server
 * sinh, khong bao gio dung ten file nguoi dung gui len.
 */
@Slf4j
@Component
public class VenueImageStorage {

    public static final String MEDIA_PATH = "/api/v1/media/venues/";

    /** Chi ten file do chinh server sinh moi duoc doc: chan "../" va moi ky tu la. */
    private static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp)$");

    public static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final Path root;
    private final String publicBaseUrl;

    public VenueImageStorage(StorageProperties properties) {
        this.root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
        String base = properties.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Nhan dang loai anh qua cac byte dau file (magic number), khong tin Content-Type va ten
     * file client gui len.
     *
     * @return phan mo rong jpg, png hoac webp; rong neu khong phai anh hop le
     */
    public static Optional<String> detectExtension(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of("jpg");
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return Optional.of("png");
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of("webp");
        }
        return Optional.empty();
    }

    /** @return URL cong khai cua anh vua luu */
    public String store(UUID venueId, byte[] bytes, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = venueDir(venueId).resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("Khong luu duoc anh san", exception);
        }
        return publicBaseUrl + MEDIA_PATH + venueId + "/" + fileName;
    }

    /** Xoa file neu URL la anh do server nay luu; anh lien ket ngoai (seed) thi bo qua. */
    public void deleteIfStored(String imageUrl) {
        String prefix = publicBaseUrl + MEDIA_PATH;
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            return;
        }
        String[] parts = imageUrl.substring(prefix.length()).split("/");
        if (parts.length != 2 || !FILE_NAME.matcher(parts[1]).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(venueDir(UUID.fromString(parts[0])).resolve(parts[1]));
        } catch (IOException | IllegalArgumentException exception) {
            // File mo coi chi ton dia, khong anh huong du lieu: ghi log va bo qua.
            log.warn("Khong xoa duoc file anh {}", imageUrl, exception);
        }
    }

    /** Doc file de phuc vu cong khai; 404 cho moi ten file khong hop le hoac khong ton tai. */
    public Path resolveExisting(UUID venueId, String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw ApiException.notFound("Khong tim thay anh.");
        }
        Path file = venueDir(venueId).resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw ApiException.notFound("Khong tim thay anh.");
        }
        return file;
    }

    public static String contentTypeOf(String fileName) {
        return CONTENT_TYPES.getOrDefault(fileName.substring(fileName.lastIndexOf('.') + 1), "application/octet-stream");
    }

    private Path venueDir(UUID venueId) {
        return root.resolve("venues").resolve(venueId.toString());
    }
}
