/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const AdminHomePage = lazy(() => import('./pages/AdminHomePage'))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'))
const AdminPlayersPage = lazy(() => import('./pages/AdminPlayersPage'))
const AdminOwnerPage = lazy(() => import('./pages/AdminOwnerPage'))
const AdminVenuesPage = lazy(() => import('./pages/AdminVenuesPage'))
const AdminUserReportsPage = lazy(() => import('./pages/AdminUserReportsPage'))
const AdminReviewReportsPage = lazy(() => import('./pages/AdminReviewReportsPage'))
const AdminStatsPage = lazy(() => import('./pages/AdminStatsPage'))
const AdminFeePoliciesPage = lazy(() => import('./pages/AdminFeePoliciesPage'))
const AdminFeeRevenuePage = lazy(() => import('./pages/AdminFeeRevenuePage'))
const AdminCommissionTransactionsPage = lazy(() => import('./pages/AdminCommissionTransactionsPage'))
const AdminSettlementsPage = lazy(() => import('./pages/AdminSettlementsPage'))
const AdminVouchersPage = lazy(() => import('./pages/AdminVouchersPage'))
const AdminPermissionsPage = lazy(() => import('./pages/AdminPermissionsPage'))

/** Khu quản trị (Sprint 3, 2.3.28 - 2.3.52). */
const adminRoutes = [
  { path: '/admin', element: <AdminHomePage /> },
  { path: '/admin/users', element: <AdminUsersPage /> },
  { path: '/admin/players', element: <AdminPlayersPage /> },
  { path: '/admin/owner', element: <AdminOwnerPage /> },
  { path: '/admin/venues', element: <AdminVenuesPage /> },
  { path: '/admin/user-reports', element: <AdminUserReportsPage /> },
  { path: '/admin/review-reports', element: <AdminReviewReportsPage /> },
  { path: '/admin/stats', element: <AdminStatsPage /> },
  { path: '/admin/fees', element: <AdminFeePoliciesPage /> },
  { path: '/admin/fee-revenue', element: <AdminFeeRevenuePage /> },
  { path: '/admin/commission-transactions', element: <AdminCommissionTransactionsPage /> },
  { path: '/admin/settlements', element: <AdminSettlementsPage /> },
  { path: '/admin/vouchers', element: <AdminVouchersPage /> },
  { path: '/admin/permissions', element: <AdminPermissionsPage /> },
]

export { adminRoutes }
