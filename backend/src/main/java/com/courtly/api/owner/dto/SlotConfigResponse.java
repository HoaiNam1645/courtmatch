package com.courtly.api.owner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 2.3.9 - cau hinh va xem truoc khung gio cua san.
 *
 * @param stepMinutes   buoc hien tai (30 hoac 60)
 * @param allowedSteps  cac buoc duoc phep
 * @param days          so khung gio moi ngay theo gio mo cua hien tai, kem so o chua co gia theo ma san con
 */
public record SlotConfigResponse(int stepMinutes, List<Integer> allowedSteps, List<DayPreview> days) {

    /**
     * @param slotCount     so khung gio / san con trong ngay (0 neu nghi)
     * @param unpricedSlots ma san con -> so khung gio chua co gia, chi liet ke san con con thieu
     */
    public record DayPreview(int dayOfWeek, boolean closed,
                             @JsonFormat(pattern = "HH:mm") LocalTime openTime,
                             @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
                             int slotCount, Map<String, Integer> unpricedSlots) {
    }
}
