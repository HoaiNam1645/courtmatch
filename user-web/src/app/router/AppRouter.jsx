import { Suspense } from 'react'
import { Navigate, useRoutes } from 'react-router-dom'
import { SplashScreen } from '../AppLayout'
import { useApp } from '../useApp'
import { adminRoutes } from '../../features/admin/routes'
import { authRoutes } from '../../features/auth/routes'
import { bookingRoutes } from '../../features/bookings/routes'
import { homeRoutes } from '../../features/home/routes'
import { matchRoutes } from '../../features/matches/routes'
import { notificationRoutes } from '../../features/notifications/routes'
import { ownerRoutes } from '../../features/owner/routes'
import { partnerRoutes } from '../../features/partners/routes'
import { profileRoutes } from '../../features/profile/routes'
import { venueRoutes } from '../../features/venues/routes'

const routes = [
  ...homeRoutes,
  ...authRoutes,
  ...bookingRoutes,
  ...profileRoutes,
  ...matchRoutes,
  ...partnerRoutes,
  ...venueRoutes,
  ...ownerRoutes,
  ...adminRoutes,
  ...notificationRoutes,
  { path: '*', element: <Navigate to="/" replace /> },
]

function AppRouter() {
  const { ready } = useApp()
  const element = useRoutes(routes)
  if (!ready) return <SplashScreen />
  return <Suspense fallback={<SplashScreen />}>{element}</Suspense>
}

export { AppRouter }
