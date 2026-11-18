package com.courtly.api.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 2.3.62 - cap nhat tuy chon nhan thong bao theo tung loai. */
public record UpdatePreferencesRequest(
        @NotEmpty(message = "Can it nhat mot tuy chon") @Size(max = 40, message = "Toi da 40 tuy chon") List<@Valid Item> preferences) {

    public record Item(
            @NotBlank(message = "Loai thong bao la bat buoc") String type,
            @NotNull(message = "Kenh trong ung dung la bat buoc") Boolean inAppEnabled,
            @NotNull(message = "Kenh email la bat buoc") Boolean emailEnabled) {
    }
}
