/* oxlint-disable react/only-export-components */
import { lazy } from 'react'
import { AppPage, ParamPage } from '../../app/router/RoutePage'

const BookingCheckoutPage = lazy(() => import('./pages/BookingCheckoutPage'))
const BookingSuccessPage = lazy(() => import('./pages/BookingSuccessPage'))
const PaymentPage = lazy(() => import('./pages/PaymentPage'))
const BookingsPage = lazy(() => import('./pages/BookingsPage'))
const BookingDetailPage = lazy(() => import('./pages/BookingDetailPage'))

const bookingRoutes = [
  { path: '/booking/checkout', element: <AppPage component={BookingCheckoutPage} /> },
  { path: '/booking/success/:bookingId', element: <ParamPage component={BookingSuccessPage} paramName="bookingId" propName="bookingId" /> },
  { path: '/payments/:paymentId', element: <ParamPage component={PaymentPage} paramName="paymentId" propName="paymentId" /> },
  { path: '/bookings', element: <AppPage component={BookingsPage} /> },
  { path: '/bookings/:bookingId', element: <ParamPage component={BookingDetailPage} paramName="bookingId" propName="bookingId" /> },
]

export { bookingRoutes }
