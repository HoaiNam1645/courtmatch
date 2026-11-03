/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage } from '../../app/router/RoutePage'

const PartnerSearchPage = lazy(() => import('./pages/PartnerSearchPage'))

const partnerRoutes = [
  { path: '/partners', element: <AppPage component={PartnerSearchPage} /> },
]

export { partnerRoutes }
