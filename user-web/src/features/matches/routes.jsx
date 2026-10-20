/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage, ParamPage } from '../../app/router/RoutePage'

const MatchesPage = lazy(() => import('./pages/MatchesPage'))
const NewMatchPage = lazy(() => import('./pages/NewMatchPage'))
const MatchDetailPage = lazy(() => import('./pages/MatchDetailPage'))
const PerformancePage = lazy(() => import('./pages/PerformancePage'))

const matchRoutes = [
  { path: '/matches', element: <AppPage component={MatchesPage} /> },
  { path: '/matches/new', element: <AppPage component={NewMatchPage} /> },
  { path: '/matches/:matchId', element: <ParamPage component={MatchDetailPage} paramName="matchId" propName="matchId" /> },
  { path: '/performance', element: <AppPage component={PerformancePage} /> },
]

export { matchRoutes }
