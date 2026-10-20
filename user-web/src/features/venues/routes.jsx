/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage, ParamPage } from '../../app/router/RoutePage'

const MapPage = lazy(() => import('./pages/MapPage'))
const VenueDetailPage = lazy(() => import('./pages/VenueDetailPage'))

const venueRoutes = [
  { path: '/map', element: <AppPage component={MapPage} /> },
  { path: '/ban-do', element: <AppPage component={MapPage} /> },
  { path: '/san/:venueKey', element: <ParamPage component={VenueDetailPage} paramName="venueKey" propName="venueKey" /> },
  { path: '/venue/:venueKey', element: <ParamPage component={VenueDetailPage} paramName="venueKey" propName="venueKey" /> },
]

export { venueRoutes }
