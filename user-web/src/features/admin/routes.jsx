/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const AdminHomePage = lazy(() => import('./pages/AdminHomePage'))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'))

/** Khu quản trị (Sprint 3, 2.3.28 - 2.3.35). */
const adminRoutes = [
  { path: '/admin', element: <AdminHomePage /> },
  { path: '/admin/users', element: <AdminUsersPage /> },
]

export { adminRoutes }
