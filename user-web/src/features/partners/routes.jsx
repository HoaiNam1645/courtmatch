/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage } from '../../app/router/RoutePage'

const PartnerSearchPage = lazy(() => import('./pages/PartnerSearchPage'))
const PartnerRequestsPage = lazy(() => import('./pages/PartnerRequestsPage'))
const PartnerConnectionsPage = lazy(() => import('./pages/PartnerConnectionsPage'))

const partnerRoutes = [
  { path: '/partners', element: <AppPage component={PartnerSearchPage} /> },
  { path: '/partners/requests', element: <AppPage component={PartnerRequestsPage} /> },
  { path: '/partners/connections', element: <AppPage component={PartnerConnectionsPage} /> },
]

export { partnerRoutes }
