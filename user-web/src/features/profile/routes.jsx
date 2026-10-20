/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage } from '../../app/router/RoutePage'

const ProfilePage = lazy(() => import('./pages/ProfilePage'))
const ProfileEditPage = lazy(() => import('./pages/ProfileEditPage'))
const ProfilePreferencesPage = lazy(() => import('./pages/ProfilePreferencesPage'))

const profileRoutes = [
  { path: '/profile', element: <AppPage component={ProfilePage} /> },
  { path: '/profile/edit', element: <AppPage component={ProfileEditPage} /> },
  { path: '/profile/preferences', element: <AppPage component={ProfilePreferencesPage} /> },
]

export { profileRoutes }
