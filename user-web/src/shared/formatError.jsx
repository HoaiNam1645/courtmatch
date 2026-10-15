import { FormAlert } from './FormFeedback'

export function formatError(message) {
  if (!message) return null
  return <div className="mt-5"><FormAlert message={message} /></div>
}
