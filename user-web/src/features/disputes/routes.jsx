/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage, ParamPage } from '../../app/router/RoutePage'

const DisputesPage = lazy(() => import('./DisputesPage'))
const DisputeDetailPage = lazy(() => import('./DisputeDetailPage'))

/** Báo cáo về đơn đặt sân của người chơi (2.3.89 - 2.3.92). */
const disputeRoutes = [
  { path: '/bao-cao', element: <AppPage component={DisputesPage} /> },
  { path: '/bao-cao/:reportId', element: <ParamPage component={DisputeDetailPage} paramName="reportId" propName="reportId" /> },
]

export { disputeRoutes }
