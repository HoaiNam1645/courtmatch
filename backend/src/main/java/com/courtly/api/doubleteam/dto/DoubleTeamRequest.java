package com.courtly.api.doubleteam.dto;

import com.courtly.common.enums.DoubleTeamType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Chon ba nguoi choi con lai de lap doi doi cung nguoi dang nhap (2.3.63). */
public record DoubleTeamRequest(
        @NotNull(message = "Hay chon loai doi") DoubleTeamType matchType,
        @NotNull(message = "Hay chon du ba nguoi choi")
        @Size(min = 3, max = 3, message = "Hay chon dung ba nguoi choi") List<@NotNull UUID> playerIds) {
}
