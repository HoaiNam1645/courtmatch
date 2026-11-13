package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 2.3.4 - sua mot san con: doi ten, ma, loai, mat san va trang thai.
 *
 * @param status active, maintenance hoac inactive
 */
public record UpdateCourtRequest(

        @NotBlank(message = "Vui long nhap ten san con")
        @Size(max = 100, message = "Ten san con toi da 100 ky tu")
        String name,

        @NotBlank(message = "Vui long nhap ma san con")
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$",
                message = "Ma san con gom 1-20 ky tu chu, so, gach ngang hoac gach duoi")
        String courtCode,

        @NotBlank(message = "Vui long chon loai san")
        String courtType,

        String surfaceType,

        boolean indoor,

        @NotBlank(message = "Vui long chon trang thai san con")
        @Pattern(regexp = "^(active|maintenance|inactive)$",
                message = "Trang thai chi nhan active, maintenance hoac inactive")
        String status) {

    /** Phan thong tin chung, dung lai quy tac kiem tra cua CourtInput. */
    public CourtInput toCourtInput() {
        return new CourtInput(name, courtCode, courtType, surfaceType, indoor);
    }
}
