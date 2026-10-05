package com.courtly.api.profile.dto;

import com.courtly.common.enums.DominantHand;
import com.courtly.common.enums.Gender;
import com.courtly.common.enums.PlayType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.SkillLevel;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ho so nguoi choi.
 *
 * @param skillScore diem ky nang do he thong tinh tu ket qua tran dau, nguoi dung
 *                   khong tu sua duoc nen khong co trong request cap nhat
 */
public record PlayerProfileDto(Gender gender,
                               LocalDate dateOfBirth,
                               String bio,
                               SkillLevel skillLevel,
                               BigDecimal skillScore,
                               DominantHand dominantHand,
                               PlayingStyle playingStyle,
                               PlayType preferredPlayType) {
}
