/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const OwnerHomePage = lazy(() => import('./pages/OwnerHomePage'))
const OwnerChangePasswordPage = lazy(() => import('./pages/OwnerChangePasswordPage'))
const OwnerProfilePage = lazy(() => import('./pages/OwnerProfilePage'))

/** Khu quản lý của chủ sân (Sprint 3, 2.3.x). */
const ownerRoutes = [
  { path: '/owner', element: <OwnerHomePage /> },
  { path: '/owner/change-password', element: <OwnerChangePasswordPage /> },
  { path: '/owner/profile', element: <OwnerProfilePage /> },
]

export { ownerRoutes }
