/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage, ParamPage } from '../../app/router/RoutePage'

const PartnerSearchPage = lazy(() => import('./pages/PartnerSearchPage'))
const PartnerRequestsPage = lazy(() => import('./pages/PartnerRequestsPage'))
const PartnerConnectionsPage = lazy(() => import('./pages/PartnerConnectionsPage'))
const DoubleTeamPage = lazy(() => import('./pages/DoubleTeamPage'))
const DoubleTeamHistoryPage = lazy(() => import('./pages/DoubleTeamHistoryPage'))
const DoubleTeamResultPage = lazy(() => import('./pages/DoubleTeamResultPage'))
const PartnerReviewsPage = lazy(() => import('./pages/PartnerReviewsPage'))

const partnerRoutes = [
  { path: '/partners', element: <AppPage component={PartnerSearchPage} /> },
  { path: '/partners/requests', element: <AppPage component={PartnerRequestsPage} /> },
  { path: '/partners/connections', element: <AppPage component={PartnerConnectionsPage} /> },
  { path: '/partners/double-teams', element: <AppPage component={DoubleTeamPage} /> },
  { path: '/partners/double-teams/history', element: <AppPage component={DoubleTeamHistoryPage} /> },
  { path: '/partners/double-teams/:batchId', element: <ParamPage component={DoubleTeamResultPage} paramName="batchId" propName="batchId" /> },
  { path: '/partners/:playerId/reviews', element: <ParamPage component={PartnerReviewsPage} paramName="playerId" propName="playerId" /> },
]

export { partnerRoutes }
