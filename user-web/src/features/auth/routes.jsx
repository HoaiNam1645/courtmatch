/* oxlint-disable react/only-export-components */
import { lazy } from 'react'

const LoginPage = lazy(() => import('./pages/LoginPage'))
const RegisterPage = lazy(() => import('./pages/RegisterPage'))
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage'))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage'))
const VerifyRecoveryPage = lazy(() => import('./pages/VerifyRecoveryPage'))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'))
const ResetPasswordSuccessPage = lazy(() => import('./pages/ResetPasswordSuccessPage'))

const authRoutes = [
  { path: '/login', element: <LoginPage /> },
  { path: '/ulogin', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/uregister', element: <RegisterPage /> },
  { path: '/register/verify', element: <VerifyEmailPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/forgot', element: <ForgotPasswordPage /> },
  { path: '/uforgot', element: <ForgotPasswordPage /> },
  { path: '/forgot-password/verify', element: <VerifyRecoveryPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/reset-password/success', element: <ResetPasswordSuccessPage /> },
]

export { authRoutes }
