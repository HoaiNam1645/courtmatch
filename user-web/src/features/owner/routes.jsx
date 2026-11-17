/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const OwnerHomePage = lazy(() => import('./pages/OwnerHomePage'))
const OwnerChangePasswordPage = lazy(() => import('./pages/OwnerChangePasswordPage'))
const OwnerProfilePage = lazy(() => import('./pages/OwnerProfilePage'))
const OwnerVenuesPage = lazy(() => import('./pages/OwnerVenuesPage'))
const OwnerVenueCreatePage = lazy(() => import('./pages/OwnerVenueCreatePage'))
const OwnerVenueDetailPage = lazy(() => import('./pages/OwnerVenueDetailPage'))
const OwnerBookingRequestsPage = lazy(() => import('./pages/OwnerBookingRequestsPage'))
const OwnerBookingHistoryPage = lazy(() => import('./pages/OwnerBookingHistoryPage'))
const OwnerReviewsPage = lazy(() => import('./pages/OwnerReviewsPage'))
const OwnerPaymentsPage = lazy(() => import('./pages/OwnerPaymentsPage'))
const OwnerFinancePage = lazy(() => import('./pages/OwnerFinancePage'))

/** Khu quản lý của chủ sân (Sprint 3, 2.3.x). */
const ownerRoutes = [
  { path: '/owner', element: <OwnerHomePage /> },
  { path: '/owner/change-password', element: <OwnerChangePasswordPage /> },
  { path: '/owner/profile', element: <OwnerProfilePage /> },
  { path: '/owner/venues', element: <OwnerVenuesPage /> },
  { path: '/owner/venues/new', element: <OwnerVenueCreatePage /> },
  { path: '/owner/venues/:venueId', element: <OwnerVenueDetailPage /> },
  { path: '/owner/booking-requests', element: <OwnerBookingRequestsPage /> },
  { path: '/owner/bookings', element: <OwnerBookingHistoryPage /> },
  { path: '/owner/reviews', element: <OwnerReviewsPage /> },
  { path: '/owner/payments', element: <OwnerPaymentsPage /> },
  { path: '/owner/finance', element: <OwnerFinancePage /> },
]

export { ownerRoutes }
