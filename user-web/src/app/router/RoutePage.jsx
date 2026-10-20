import { useParams } from 'react-router-dom'
import { useApp } from '../useApp'

function AppPage({ component: Component }) {
  const { user, logout } = useApp()
  return <Component user={user} onLogout={logout} />
}

function ParamPage({ component: Component, paramName, propName }) {
  const params = useParams()
  const { user, logout } = useApp()
  return <Component {...{ [propName]: params[paramName] ?? '' }} user={user} onLogout={logout} />
}

export { AppPage, ParamPage }
