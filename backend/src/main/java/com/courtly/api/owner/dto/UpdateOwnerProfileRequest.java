package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 2.3.2 - cap nhat ho so chu san.
 *
 * <p>Email la danh tinh dang nhap nen khong sua o day. Trang thai xac minh do quan tri
 * vien quyet dinh, request co gui len cung bi bo qua vi khong co truong tuong ung.
 */
public record UpdateOwnerProfileRequest(

        @NotBlank(message = "Vui long nhap ten nguoi lien he")
        @Size(min = 2, max = 150, message = "Ten nguoi lien he tu 2 den 150 ky tu")
        String fullName,

        // Kiem tra long o day; chuan hoa va kiem tra chat o service.
        @NotBlank(message = "Vui long nhap so dien thoai lien he")
        @Pattern(regexp = "^[0-9+ .\\-()]{9,20}$", message = "So dien thoai khong dung dinh dang")
        String phone,

        @NotBlank(message = "Vui long nhap ten doanh nghiep hoac ho kinh doanh")
        @Size(max = 200, message = "Ten doanh nghiep toi da 200 ky tu")
        String businessName,

        @Pattern(regexp = "^$|^\\d{10}(-\\d{3})?$",
                message = "Ma so thue gom 10 so, hoac 10 so kem -3 so chi nhanh")
        String taxCode,

        @NotBlank(message = "Vui long nhap ten ngan hang")
        @Size(max = 100, message = "Ten ngan hang toi da 100 ky tu")
        String bankName,

        @NotBlank(message = "Vui long nhap so tai khoan")
        @Pattern(regexp = "^\\d{6,20}$", message = "So tai khoan gom 6 den 20 chu so")
        String bankAccountNo,

        @NotBlank(message = "Vui long nhap ten chu tai khoan")
        @Size(max = 150, message = "Ten chu tai khoan toi da 150 ky tu")
        String bankAccountName) {

    public UpdateOwnerProfileRequest {
        fullName = trim(fullName);
        phone = trim(phone);
        businessName = trim(businessName);
        taxCode = trim(taxCode);
        bankName = trim(bankName);
        bankAccountNo = bankAccountNo == null ? null : bankAccountNo.replaceAll("\\s", "");
        bankAccountName = trim(bankAccountName);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
