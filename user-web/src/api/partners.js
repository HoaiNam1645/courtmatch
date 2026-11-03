import { request } from './client'

function toPartner(candidate) {
  return {
    id: candidate.userId,
    fullName: candidate.fullName,
    avatarUrl: candidate.avatarUrl ?? null,
    playingStyle: candidate.playingStyle ?? null,
    preferredPlayType: candidate.preferredPlayType ?? null,
    ratingScore: Number(candidate.ratingScore),
    ratingLabel: candidate.ratingLabel,
    totalMatches: candidate.totalMatches,
    provisional: Boolean(candidate.provisional),
    ratingGap: Number(candidate.ratingGap),
    skillSimilarity: Number(candidate.skillSimilarity),
    distanceKm: Number(candidate.distanceKm),
    locationCompatibility: Number(candidate.locationCompatibility),
    timeCompatibility: Number(candidate.timeCompatibility),
    styleCompatibility: Number(candidate.styleCompatibility),
    availableFrom: candidate.availableFrom,
    availableUntil: candidate.availableUntil,
  }
}

/** 2.2.20-2.2.27: backend loc ung vien va tinh cac diem tuong thich. */
export async function searchPartners({
  preferredLocationId,
  date,
  time,
  durationMinutes,
  maxRatingDifference,
  page = 0,
  size = 12,
}) {
  const search = new URLSearchParams({
    preferredLocationId,
    scheduledAt: `${date}T${time}:00+07:00`,
    durationMinutes: String(durationMinutes),
    maxRatingDifference: String(maxRatingDifference),
    page: String(page),
    size: String(size),
  })
  const data = await request(`/api/v1/partners/search?${search.toString()}`)
  return {
    partners: data.content.map(toPartner),
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
  }
}
