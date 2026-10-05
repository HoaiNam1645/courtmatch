/**
 * Lớp gọi API dùng chung.
 *
 * Component không được gọi `fetch` trực tiếp — mọi lời gọi đi qua đây để token,
 * lỗi và timeout được xử lý ở một chỗ.
 */

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '')
const TOKEN_STORAGE_KEY = 'courtly_access_token'
const REQUEST_TIMEOUT_MS = 15000

/** Sự kiện phát ra khi backend trả 401, để App xoá phiên và đưa về /login. */
export const UNAUTHORIZED_EVENT = 'courtly-unauthorized'

/**
 * Lỗi đã được chuẩn hoá từ response của backend.
 *
 * Luôn xử lý theo `code`, không so sánh chuỗi `message` — message có thể đổi bất cứ lúc nào.
 */
export class ApiError extends Error {
  constructor({ status, code, message, fieldErrors }) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors ?? {}
  }
}

export function readToken() {
  try {
    return window.localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

export function saveToken(token) {
  try {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
  } catch {
    // Chế độ riêng tư có thể chặn localStorage; bỏ qua để trang vẫn chạy được.
  }
}

export function clearToken() {
  try {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    // Bỏ qua, xem chú thích ở saveToken.
  }
}

async function parseError(response) {
  let payload = null
  try {
    payload = await response.json()
  } catch {
    payload = null
  }

  return new ApiError({
    status: response.status,
    code: payload?.code ?? 'UNKNOWN_ERROR',
    message: payload?.message ?? 'Đã có lỗi xảy ra, vui lòng thử lại.',
    fieldErrors: payload?.fieldErrors,
  })
}

/**
 * @param {string} path đường dẫn bắt đầu bằng /api/v1
 * @param {{ method?: string, body?: unknown, auth?: boolean }} options
 */
export async function request(path, { method = 'GET', body, auth = true } = {}) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const token = auth ? readToken() : null
  if (token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
  } catch (error) {
    throw new ApiError({
      status: 0,
      code: error?.name === 'AbortError' ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR',
      message: 'Không kết nối được tới máy chủ. Kiểm tra mạng rồi thử lại.',
    })
  } finally {
    window.clearTimeout(timeoutId)
  }

  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    throw await parseError(response)
  }

  if (!response.ok) {
    throw await parseError(response)
  }

  if (response.status === 204) return null
  return response.json()
}
