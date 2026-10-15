export function FieldError({ message }) {
  if (!message) return null
  return <span className="mt-2 block text-sm font-medium text-red-600">{message}</span>
}

export function FormAlert({ message }) {
  if (!message) return null
  return (
    <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
      {message}
    </div>
  )
}
