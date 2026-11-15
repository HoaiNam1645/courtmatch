/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const AdminHomePage = lazy(() => import('./pages/AdminHomePage'))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'))
const AdminPlayersPage = lazy(() => import('./pages/AdminPlayersPage'))
const AdminOwnerPage = lazy(() => import('./pages/AdminOwnerPage'))
const AdminVenuesPage = lazy(() => import('./pages/AdminVenuesPage'))
const AdminUserReportsPage = lazy(() => import('./pages/AdminUserReportsPage'))
const AdminPermissionsPage = lazy(() => import('./pages/AdminPermissionsPage'))

/** Khu quản trị (Sprint 3, 2.3.28 - 2.3.43). */
const adminRoutes = [
  { path: '/admin', element: <AdminHomePage /> },
  { path: '/admin/users', element: <AdminUsersPage /> },
  { path: '/admin/players', element: <AdminPlayersPage /> },
  { path: '/admin/owner', element: <AdminOwnerPage /> },
  { path: '/admin/venues', element: <AdminVenuesPage /> },
  { path: '/admin/user-reports', element: <AdminUserReportsPage /> },
  { path: '/admin/permissions', element: <AdminPermissionsPage /> },
]

export { adminRoutes }
