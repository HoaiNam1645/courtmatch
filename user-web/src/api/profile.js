/** Hồ sơ người chơi: 2.1.6 xem, 2.1.7 sửa, 2.1.8–2.1.12 thiết lập chơi. */

import { toUiUser } from './auth'
import { request } from './client'

const EMPTY_STATISTICS = {
  totalMatches: 0,
  totalWins: 0,
  totalLosses: 0,
  winRate: 0,
  ratingScore: 0,
  lastPlayedAt: null,
}

/**
 * Backend trả `availability[].dayOfWeek` và `preferredLocations`;
 * giao diện đang dùng `availability[].day` và `locations`.
 * Quy đổi ở đây để component không phải biết hình dạng response.
 */
function toUiProfile(data) {
  const profile = data.profile ?? {}
  const statistics = data.statistics ?? EMPTY_STATISTICS

  return {
    gender: profile.gender ?? '',
    dateOfBirth: profile.dateOfBirth ?? '',
    bio: profile.bio ?? '',
    skillLevel: profile.skillLevel ?? '',
    // Điểm kỹ năng do hệ thống tính, có thể chưa có với người chơi mới.
    skillScore: profile.skillScore == null ? null : Number(profile.skillScore),
    dominantHand: profile.dominantHand ?? '',
    playingStyle: profile.playingStyle ?? '',
    preferredPlayType: profile.preferredPlayType ?? '',
    availability: (data.availability ?? []).map((slot) => ({
      day: slot.dayOfWeek,
      startTime: slot.startTime,
      endTime: slot.endTime,
    })),
    locations: (data.preferredLocations ?? []).map((location) => ({
      id: location.id,
      label: location.label ?? '',
      address: location.address ?? '',
      // null nghĩa là chưa ghim toạ độ; khi đó cột location (PostGIS) ở backend để trống
      // và không ghép cặp theo khoảng cách được.
      latitude: location.latitude == null ? null : Number(location.latitude),
      longitude: location.longitude == null ? null : Number(location.longitude),
      radiusKm: Number(location.radiusKm ?? 5),
      isDefault: Boolean(location.isDefault),
    })),
    statistics: {
      totalMatches: statistics.totalMatches ?? 0,
      totalWins: statistics.totalWins ?? 0,
      totalLosses: statistics.totalLosses ?? 0,
      winRate: Number(statistics.winRate ?? 0),
      ratingScore: Number(statistics.ratingScore ?? 0),
      lastPlayedAt: statistics.lastPlayedAt ?? null,
    },
  }
}

function unwrap(data) {
  return { user: toUiUser(data.user), profile: toUiProfile(data) }
}

/** 2.1.6 — một lần gọi phục vụ cả /profile và /profile/preferences. */
export async function fetchProfile() {
  return unwrap(await request('/api/v1/users/me/profile'))
}

/** 2.1.7 — thông tin liên hệ và thông tin cá nhân. */
export async function updateProfile({ fullName, email, phone, gender, dateOfBirth, bio }) {
  const data = await request('/api/v1/users/me/profile', {
    method: 'PUT',
    body: {
      fullName,
      email: email || null,
      phone: phone || null,
      gender: gender || null,
      dateOfBirth: dateOfBirth || null,
      bio: bio || null,
    },
  })
  return unwrap(data)
}

/** 2.1.8–2.1.12 — màn hình chỉ có một nút lưu nên gửi trọn một lần. */
export async function updatePreferences(profile) {
  const data = await request('/api/v1/users/me/preferences', {
    method: 'PUT',
    body: {
      skillLevel: profile.skillLevel || null,
      dominantHand: profile.dominantHand || null,
      playingStyle: profile.playingStyle || null,
      preferredPlayType: profile.preferredPlayType || null,
      availability: profile.availability.map((slot) => ({
        dayOfWeek: slot.day,
        startTime: slot.startTime,
        endTime: slot.endTime,
      })),
      preferredLocations: profile.locations.map((location) => ({
        label: location.label,
        address: location.address || null,
        latitude: location.latitude ?? null,
        longitude: location.longitude ?? null,
        radiusKm: location.radiusKm,
        isDefault: location.isDefault,
      })),
    },
  })
  return unwrap(data)
}
