/** 2.3.6 — ảnh của sân. */

import { request } from './client'

/** Cùng giới hạn với backend (OwnerVenueImageService). */
export const MAX_IMAGES_PER_VENUE = 20
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

function imagesPath(venueId) {
  return `/api/v1/owner/venues/${encodeURIComponent(venueId)}/images`
}

function toUiImage(image) {
  return {
    id: image.id,
    imageUrl: image.imageUrl,
    caption: image.caption ?? '',
    displayOrder: image.displayOrder,
    cover: Boolean(image.cover),
  }
}

export async function fetchVenueImages(venueId) {
  return (await request(imagesPath(venueId))).map(toUiImage)
}

export async function uploadVenueImage(venueId, file, caption = '') {
  const form = new FormData()
  form.append('file', file)
  if (caption.trim()) form.append('caption', caption.trim())
  // Tải ảnh lớn qua mạng chậm cần lâu hơn mức mặc định.
  return toUiImage(await request(imagesPath(venueId), { method: 'POST', body: form, timeoutMs: 60000 }))
}

export async function updateImageCaption(venueId, imageId, caption) {
  return toUiImage(await request(`${imagesPath(venueId)}/${encodeURIComponent(imageId)}`, {
    method: 'PATCH',
    body: { caption: caption.trim() || null },
  }))
}

export function setCoverImage(venueId, imageId) {
  return request(`${imagesPath(venueId)}/${encodeURIComponent(imageId)}/cover`, { method: 'POST' })
}

export function reorderImages(venueId, imageIds) {
  return request(`${imagesPath(venueId)}/order`, { method: 'PUT', body: { imageIds } })
}

export function deleteVenueImage(venueId, imageId) {
  return request(`${imagesPath(venueId)}/${encodeURIComponent(imageId)}`, { method: 'DELETE' })
}
