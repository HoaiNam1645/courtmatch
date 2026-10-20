/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const HomePage = lazy(() => import('./HomePage'))

const homeRoutes = [{ path: '/', element: <HomePage /> }]

export { homeRoutes }
