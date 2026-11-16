/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const NotificationsPage = lazy(() => import('./NotificationsPage'))

const notificationRoutes = [{ path: '/notifications', element: <NotificationsPage /> }]

export { notificationRoutes }
