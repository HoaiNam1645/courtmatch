import { request } from './client'

function toPartnerRequest(item) {
  return {
    id: item.id,
    requester: item.requester,
    receiver: item.receiver,
    message: item.message,
    status: item.status,
    respondedAt: item.respondedAt ?? null,
    createdAt: item.createdAt,
  }
}

/** 2.2.31: gửi lời mời ghép cặp tới một người chơi khác. */
export async function createPartnerRequest(receiverId, message) {
  const data = await request('/api/v1/partner-requests', {
    method: 'POST',
    body: { receiverId, message },
  })
  return toPartnerRequest(data)
}

/** 2.2.32: lấy danh sách phân trang các lời mời người chơi đã nhận. */
export async function getReceivedPartnerRequests({ status = 'all', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  const data = await request(`/api/v1/partner-requests/received?${search.toString()}`)
  return { ...data, content: data.content.map(toPartnerRequest) }
}

/** 2.2.33: người nhận chấp nhận một yêu cầu đang chờ. */
export async function acceptPartnerRequest(requestId) {
  return toPartnerRequest(await request(`/api/v1/partner-requests/${requestId}/accept`, { method: 'POST' }))
}

/** 2.2.34: người nhận từ chối một yêu cầu đang chờ. */
export async function rejectPartnerRequest(requestId) {
  return toPartnerRequest(await request(`/api/v1/partner-requests/${requestId}/reject`, { method: 'POST' }))
}

/** Danh sách phân trang lời mời do người chơi hiện tại đã gửi. */
export async function getSentPartnerRequests({ status = 'all', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  const data = await request(`/api/v1/partner-requests/sent?${search.toString()}`)
  return { ...data, content: data.content.map(toPartnerRequest) }
}

/** 2.2.35: người gửi hủy một yêu cầu vẫn đang chờ. */
export async function cancelPartnerRequest(requestId) {
  return toPartnerRequest(await request(`/api/v1/partner-requests/${requestId}/cancel`, { method: 'POST' }))
}

export { toPartnerRequest }
