/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const OwnerHomePage = lazy(() => import('./pages/OwnerHomePage'))
const OwnerChangePasswordPage = lazy(() => import('./pages/OwnerChangePasswordPage'))
const OwnerProfilePage = lazy(() => import('./pages/OwnerProfilePage'))
const OwnerVenuesPage = lazy(() => import('./pages/OwnerVenuesPage'))
const OwnerVenueCreatePage = lazy(() => import('./pages/OwnerVenueCreatePage'))

/** Khu quản lý của chủ sân (Sprint 3, 2.3.x). */
const ownerRoutes = [
  { path: '/owner', element: <OwnerHomePage /> },
  { path: '/owner/change-password', element: <OwnerChangePasswordPage /> },
  { path: '/owner/profile', element: <OwnerProfilePage /> },
  { path: '/owner/venues', element: <OwnerVenuesPage /> },
  { path: '/owner/venues/new', element: <OwnerVenueCreatePage /> },
]

export { ownerRoutes }
