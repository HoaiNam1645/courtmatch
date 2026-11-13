import { FieldError } from '../../../shared/FormFeedback'
import { inputClass, textareaClass } from '../ownerStyles'

/** Ô nhập có nhãn và lỗi hiển thị ngay bên dưới (theo fieldErrors của backend). */
function OwnerFormField({ label, name, value, onChange, error, hint, required, as = 'input', className, ...props }) {
  const Component = as
  return (
    <label className={className ?? 'block'}>
      <span className="text-sm font-semibold text-emerald-950">
        {label}
        {required && <span className="text-red-600"> *</span>}
      </span>
      <Component
        className={as === 'textarea' ? `${textareaClass} mt-2` : `${inputClass} mt-2`}
        name={name}
        value={value ?? ''}
        onChange={(event) => onChange?.(name, event.target.value)}
        aria-invalid={Boolean(error)}
        {...props}
      />
      {hint && !error && <span className="mt-1.5 block text-xs font-medium text-stone-500">{hint}</span>}
      <FieldError message={error} />
    </label>
  )
}

export { OwnerFormField }
