package com.courtly.api.owner.dto;

import java.util.List;

/** Danh muc ly do tu choi yeu cau dat san (2.3.14). */
public record RejectReasonsResponse(List<Reason> reasons) {

    /** @param noteRequired true khi bat buoc nhap ghi chu (ly do "other") */
    public record Reason(String code, String label, boolean noteRequired) {
    }
}
