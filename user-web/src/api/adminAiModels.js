/** 2.2.16, 2.2.19: quản lý phiên bản model AI (nhập, kích hoạt, quay lại). */

import { request } from './client'

export const KIND_LABEL = { partner: 'Xếp hạng đối tác', team: 'Chia đội' }
export const METRIC_LABEL = { 'ndcg@10': 'NDCG@10', log_loss: 'Log-loss' }

function toModel(item) {
  return {
    id: item.id,
    kind: item.kind,
    modelType: item.modelType,
    modelVersion: item.modelVersion,
    status: item.status,
    trainedAt: item.trainedAt ?? null,
    activatedAt: item.activatedAt ?? null,
    metrics: item.metrics ?? {},
    gatePasses: item.gatePasses ?? null,
    gateReasons: item.gateReasons ?? [],
    canActivate: Boolean(item.canActivate),
    canRollback: Boolean(item.canRollback),
  }
}

export async function fetchAiModels() {
  const data = await request('/api/v1/admin/ai-models')
  return {
    models: data.models.map(toModel),
    importable: data.importable ?? [],
    current: data.current ?? [],
    service: data.service ?? { configured: false, reachable: false, loaded: {} },
  }
}

export async function importAiModel(modelType, modelVersion) {
  return toModel(await request('/api/v1/admin/ai-models/import', { method: 'POST', body: { modelType, modelVersion } }))
}

export async function activateAiModel(modelId) {
  return toModel(await request(`/api/v1/admin/ai-models/${modelId}/activate`, { method: 'POST' }))
}

export async function rollbackAiModel(modelId) {
  return toModel(await request(`/api/v1/admin/ai-models/${modelId}/rollback`, { method: 'POST' }))
}
