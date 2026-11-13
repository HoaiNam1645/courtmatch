package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.User;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueImageRepository;
import com.courtly.service.storage.VenueImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/** 2.3.6 - Tai len hinh anh san. */
@TestPropertySource(properties = {
        "courtly.storage.upload-dir=target/test-uploads",
        "courtly.storage.public-base-url=http://media.test"})
class OwnerVenueImageTest extends OwnerApiTestBase {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Autowired private VenueImageRepository imageRepository;
    @Autowired private VenueImageStorage storage;

    private String ownerToken;
    private Venue venue;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        User owner = persistOwner("owner-image");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Hinh Anh");
    }

    @Test
    @DisplayName("Tai anh: anh dau la anh bia, anh sau xep cuoi; file luu tren dia va xem cong khai duoc")
    void uploadAndServe() throws Exception {
        JsonNode first = upload(JPEG, "san.jpg", "image/jpeg", "Mat tien san");
        assertThat(first.get("cover").asBoolean()).isTrue();
        assertThat(first.get("displayOrder").asInt()).isEqualTo(1);
        assertThat(first.get("caption").asText()).isEqualTo("Mat tien san");
        String url = first.get("imageUrl").asText();
        assertThat(url).startsWith("http://media.test/api/v1/media/venues/" + venue.getId() + "/").endsWith(".jpg");

        JsonNode second = upload(PNG, "ben-trong.png", "image/png", null);
        assertThat(second.get("cover").asBoolean()).isFalse();
        assertThat(second.get("displayOrder").asInt()).isEqualTo(2);
        upload(WEBP, "anh.webp", "image/webp", null);

        String mediaPath = url.substring("http://media.test".length());
        byte[] served = mockMvc.perform(get(mediaPath))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(JPEG);
        String fileName = mediaPath.substring(mediaPath.lastIndexOf('/') + 1);
        assertThat(Files.exists(Path.of("target/test-uploads/venues", venue.getId().toString(), fileName))).isTrue();

        // Trang chi tiet cong khai dung anh vua tai, anh bia dung dau.
        mockMvc.perform(get("/api/v1/venues/" + venue.getSlug()))
                .andExpect(jsonPath("$.images", hasSize(3)))
                .andExpect(jsonPath("$.images[0].imageUrl").value(url))
                .andExpect(jsonPath("$.images[0].cover").value(true));
    }

    @Test
    @DisplayName("Tu choi file khong phai anh (doi ten/Content-Type), file rong, anh qua 5MB, thieu file")
    void rejectInvalidFiles() throws Exception {
        mockMvc.perform(multipart(imagesPath())
                        .file(new MockMultipartFile("file", "virus.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes()))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"))
                .andExpect(jsonPath("$.fieldErrors.file").exists());

        mockMvc.perform(multipart(imagesPath())
                        .file(new MockMultipartFile("file", "rong.jpg", "image/jpeg", new byte[0]))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"));

        byte[] big = Arrays.copyOf(JPEG, 5 * 1024 * 1024 + 1);
        mockMvc.perform(multipart(imagesPath())
                        .file(new MockMultipartFile("file", "lon.jpg", "image/jpeg", big))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"));

        mockMvc.perform(multipart(imagesPath()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.file").exists());

        assertThat(imageRepository.countByVenueId(venue.getId())).isZero();
    }

    @Test
    @DisplayName("Toi da 20 anh moi san")
    void imageLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            upload(PNG, "anh-" + i + ".png", "image/png", null);
        }
        mockMvc.perform(multipart(imagesPath())
                        .file(new MockMultipartFile("file", "thua.png", "image/png", PNG))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("Doi anh bia, sap xep lai, sua chu thich, xoa anh bia thi anh dau con lai thanh anh bia")
    void manageImages() throws Exception {
        String a = upload(JPEG, "a.jpg", "image/jpeg", null).get("id").asText();
        String b = upload(PNG, "b.png", "image/png", null).get("id").asText();
        String c = upload(WEBP, "c.webp", "image/webp", null).get("id").asText();

        mockMvc.perform(post(imagesPath() + "/" + b + "/cover").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cover==true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.id=='" + b + "')].cover").value(true));

        mockMvc.perform(put(imagesPath() + "/order")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(c, b, a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(c))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[2].id").value(a));

        // Thieu anh hoac trung lap thi tu choi.
        mockMvc.perform(put(imagesPath() + "/order")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(c, c, a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.imageIds").exists());

        mockMvc.perform(patch(imagesPath() + "/" + a)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"  Khu ghe cho  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("Khu ghe cho"));
        mockMvc.perform(patch(imagesPath() + "/" + a)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete(imagesPath() + "/" + b).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(imagesPath()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(c))
                .andExpect(jsonPath("$[0].cover").value(true));
    }

    @Test
    @DisplayName("Anh cua san khac tra 404; duong dan media sai hoac co ../ tra 404; anh lien ket ngoai khong bi xoa file")
    void ownershipAndPaths() throws Exception {
        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        mockMvc.perform(multipart("/api/v1/owner/venues/" + other.getId() + "/images")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());

        String mine = upload(JPEG, "a.jpg", "image/jpeg", null).get("id").asText();
        mockMvc.perform(delete("/api/v1/owner/venues/" + other.getId() + "/images/" + mine)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/media/venues/" + venue.getId() + "/khong-ton-tai.jpg"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/media/venues/" + venue.getId() + "/" + UUID.randomUUID() + ".jpg"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/media/venues/" + venue.getId() + "/..%2F..%2Fapplication.yml"))
                .andExpect(status().is4xxClientError());

        // Anh seed la URL ngoai: xoa ban ghi khong dung toi file nao.
        storage.deleteIfStored("https://images.unsplash.com/photo-1.jpg");
    }

    private JsonNode upload(byte[] bytes, String name, String type, String caption) throws Exception {
        var request = multipart(imagesPath())
                .file(new MockMultipartFile("file", name, type, bytes))
                .header("Authorization", bearer(ownerToken));
        if (caption != null) {
            request.param("caption", caption);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl", startsWith("http://media.test/")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String imagesPath() {
        return "/api/v1/owner/venues/" + venue.getId() + "/images";
    }
}
