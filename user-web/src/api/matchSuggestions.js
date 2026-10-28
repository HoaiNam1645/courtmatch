import { request } from './client'

/** 2.2.1 - booking da hoan tat nhung chua duoc ghi thanh tran dau. */
export async function fetchMatchSuggestions({ page = 0, size = 20 } = {}) {
  const data = await request(`/api/v1/matches/suggestions?page=${page}&size=${size}`)
  return { suggestions: data.content, totalElements: data.totalElements }
}
