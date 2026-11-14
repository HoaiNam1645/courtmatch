import { useMemo, useState } from 'react'
import { CheckCircle2 } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchPermissionMatrix, updateRolePermissions } from '../../../api/adminPermissions'
import { ROLE_LABEL } from '../../../api/adminUsers'
import { useApiResource } from '../../../hooks/useApiResource'
import { FormAlert } from '../../../shared/FormFeedback'
import { OwnerCard, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { primaryButtonClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'

/** Chênh lệch giữa tập quyền đã lưu và bản nháp của một vai trò. */
function diffOf(saved, draft) {
  return {
    added: [...draft].filter((code) => !saved.includes(code)).sort(),
    removed: saved.filter((code) => !draft.has(code)).sort(),
  }
}

function MatrixEditor({ matrix, onSaved }) {
  const [draft, setDraft] = useState(() => Object.fromEntries(matrix.roles.map((role) => [role.code, new Set(role.permissions)])))
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const changes = useMemo(() => matrix.roles
    .map((role) => ({ role, ...diffOf(role.permissions, draft[role.code]) }))
    .filter((item) => item.added.length > 0 || item.removed.length > 0), [matrix, draft])
  const nameOf = (code) => matrix.permissions.find((item) => item.code === code)?.name ?? code

  function toggle(roleCode, permissionCode) {
    setConfirming(false)
    setError('')
    setDraft((current) => {
      const next = new Set(current[roleCode])
      if (next.has(permissionCode)) next.delete(permissionCode)
      else next.add(permissionCode)
      return { ...current, [roleCode]: next }
    })
  }

  async function save() {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      let latest = matrix
      for (const change of changes) latest = await updateRolePermissions(change.role.code, [...draft[change.role.code]])
      setConfirming(false)
      onSaved(latest, `Đã lưu quyền của ${changes.length} vai trò.`)
    } catch (failure) {
      setError(failure instanceof ApiError ? (failure.fieldErrors?.permissions ?? failure.message) : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-5">
      <div className="overflow-x-auto rounded-xl border border-slate-200">
        <table className="w-full min-w-[640px] border-collapse text-sm" data-testid="permission-matrix">
          <thead>
            <tr className="bg-slate-50 text-left">
              <th className="px-4 py-3 font-semibold text-slate-700">Quyền</th>
              {matrix.roles.map((role) => <th key={role.code} className="px-3 py-3 text-center font-semibold text-slate-700">{ROLE_LABEL[role.code] ?? role.name}</th>)}
            </tr>
          </thead>
          <tbody>
            {matrix.permissions.map((permission) => (
              <tr key={permission.code} className="border-t border-slate-100" data-testid={`permission-row-${permission.code}`}>
                <td className="px-4 py-2.5"><span className="block font-semibold text-slate-800">{permission.name}</span><span className="text-xs font-medium text-slate-400">{permission.code}</span></td>
                {matrix.roles.map((role) => {
                  const locked = role.code === 'admin' && matrix.protectedPermissions.includes(permission.code)
                  return (
                    <td key={role.code} className="px-3 py-2.5 text-center">
                      <input type="checkbox" className="size-4 accent-slate-800 disabled:opacity-50" checked={draft[role.code].has(permission.code)} disabled={locked || busy}
                        aria-label={`${ROLE_LABEL[role.code] ?? role.name}: ${permission.name}`} title={locked ? 'Quyền cốt lõi của quản trị viên, không thể gỡ' : undefined}
                        onChange={() => toggle(role.code, permission.code)} />
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs font-medium text-slate-500">Quyền cốt lõi của quản trị viên (quản lý tài khoản, phân quyền vai trò) được khóa để không ai bị mất quyền quản trị.</p>

      <FormAlert message={error} />
      {changes.length > 0 && !confirming && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4" data-testid="permission-changes">
          <p className="text-sm font-semibold text-amber-900">{changes.length} vai trò có thay đổi chưa lưu.</p>
          <div className="flex gap-2">
            <button type="button" className={secondaryButtonClass} onClick={() => setDraft(Object.fromEntries(matrix.roles.map((role) => [role.code, new Set(role.permissions)])))}>Hoàn tác</button>
            <button type="button" className={primaryButtonClass} data-testid="permission-save" onClick={() => setConfirming(true)}>Lưu thay đổi</button>
          </div>
        </div>
      )}
      {confirming && (
        <div className="rounded-xl border border-slate-300 bg-white p-4" role="alertdialog" aria-label="Xác nhận lưu quyền" data-testid="permission-confirm">
          <p className="text-sm font-semibold text-slate-900">Xác nhận thay đổi quyền?</p>
          <ul className="mt-2 grid gap-1 text-sm font-medium text-slate-600">
            {changes.map((item) => (
              <li key={item.role.code}>
                <span className="font-semibold text-slate-800">{ROLE_LABEL[item.role.code] ?? item.role.name}</span>
                {item.added.length > 0 && <span className="text-emerald-700"> · thêm: {item.added.map(nameOf).join(', ')}</span>}
                {item.removed.length > 0 && <span className="text-red-700"> · bỏ: {item.removed.map(nameOf).join(', ')}</span>}
              </li>
            ))}
          </ul>
          <div className="mt-3 flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => setConfirming(false)}>Quay lại</button>
            <button type="button" className={primaryButtonClass} data-testid="permission-confirm-save" disabled={busy} onClick={save}>{busy ? 'Đang lưu...' : 'Xác nhận lưu'}</button>
          </div>
        </div>
      )}
    </div>
  )
}

/** 2.3.30 — quản lý quyền của từng vai trò. */
function AdminPermissionsPage() {
  const { status, data, error, reload } = useApiResource(fetchPermissionMatrix, [])
  const [notice, setNotice] = useState('')
  const [version, setVersion] = useState(0)
  const [override, setOverride] = useState(null)

  return (
    <AdminLayout eyebrow="2.3.30 · Quyền" title="Vai trò và quyền" description="Bật hoặc tắt quyền chi tiết của từng vai trò. Người dùng nhận quyền qua vai trò của mình.">
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        {status === 'loading' && <OwnerLoading rows={3} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'success' && (
          <MatrixEditor
            key={version}
            matrix={override ?? data}
            onSaved={(latest, message) => { setOverride(latest); setVersion((current) => current + 1); setNotice(message) }}
          />
        )}
      </OwnerCard>
    </AdminLayout>
  )
}

export default AdminPermissionsPage
