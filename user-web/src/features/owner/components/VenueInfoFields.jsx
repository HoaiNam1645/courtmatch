import { LocationPicker } from '../../../components/LocationPicker'
import { FieldError } from '../../../shared/FormFeedback'
import { inputClass } from '../ownerStyles'
import { OwnerFormField } from './OwnerFormField'

/**
 * Thông tin chung, vị trí và tiện ích của sân (2.3.3, 2.3.4).
 *
 * Danh sách quận/huyện và tiện ích lấy từ API `/api/v1/owner/venue-options`.
 */
function VenueInfoFields({ values, onChange, errors, options }) {
  function setField(name, value) {
    onChange({ ...values, [name]: value })
  }

  function toggleService(code, checked) {
    const services = checked
      ? [...values.services, { code, price: '', note: '' }]
      : values.services.filter((service) => service.code !== code)
    onChange({ ...values, services })
  }

  function updateService(code, field, value) {
    onChange({
      ...values,
      services: values.services.map((service) => (service.code === code ? { ...service, [field]: value } : service)),
    })
  }

  return (
    <div className="grid gap-5">
      <div className="grid gap-5 md:grid-cols-2">
        <OwnerFormField label="Tên sân" name="name" value={values.name} onChange={setField} error={errors.name} required maxLength={200} className="block md:col-span-2" />
        <OwnerFormField label="Mô tả" name="description" value={values.description} onChange={setField} error={errors.description} as="textarea" maxLength={2000} className="block md:col-span-2" />
        <OwnerFormField label="Địa chỉ" name="address" value={values.address} onChange={setField} error={errors.address} required maxLength={300} className="block md:col-span-2" />
        <OwnerFormField label="Phường/xã" name="ward" value={values.ward} onChange={setField} error={errors.ward} maxLength={100} />
        <label className="block">
          <span className="text-sm font-semibold text-emerald-950">Quận/huyện<span className="text-red-600"> *</span></span>
          <select className={`${inputClass} mt-2`} name="district" value={values.district} onChange={(event) => setField('district', event.target.value)} aria-invalid={Boolean(errors.district)}>
            <option value="">Chọn quận/huyện</option>
            {options.districts.map((district) => <option key={district} value={district}>{district}</option>)}
          </select>
          <FieldError message={errors.district} />
        </label>
        <OwnerFormField label="Số điện thoại của sân" name="phone" value={values.phone} onChange={setField} error={errors.phone} required inputMode="tel" hint="Di động hoặc số cố định, vd 0236 3888 999." />
        <OwnerFormField label="Email của sân" name="email" value={values.email} onChange={setField} error={errors.email} type="email" maxLength={255} />
      </div>

      <div>
        <span className="text-sm font-semibold text-emerald-950">Vị trí trên bản đồ<span className="text-red-600"> *</span></span>
        <LocationPicker
          latitude={values.latitude}
          longitude={values.longitude}
          showRadius={false}
          heightClass="h-[280px]"
          onChange={({ latitude, longitude }) => onChange({ ...values, latitude, longitude })}
        />
        <p className="mt-2 text-xs font-semibold text-emerald-800" data-testid="venue-coordinates">
          {values.latitude != null && values.longitude != null
            ? `Đã ghim: ${values.latitude.toFixed(6)}, ${values.longitude.toFixed(6)}`
            : 'Chưa ghim vị trí sân'}
        </p>
        <FieldError message={errors.latitude ?? errors.longitude} />
      </div>

      <fieldset>
        <legend className="text-sm font-semibold text-emerald-950">Tiện ích</legend>
        <div className="mt-3 grid gap-3">
          {options.services.map((option) => {
            const index = values.services.findIndex((service) => service.code === option.value)
            const selected = index >= 0 ? values.services[index] : null
            return (
              <div key={option.value} className="rounded-lg border border-emerald-100 p-3">
                <label className="flex items-center gap-3 text-sm font-semibold text-emerald-950">
                  <input type="checkbox" className="size-4 accent-emerald-700" checked={Boolean(selected)} onChange={(event) => toggleService(option.value, event.target.checked)} />
                  {option.label}
                </label>
                {selected && (
                  <div className="mt-3 grid gap-3 sm:grid-cols-[180px_minmax(0,1fr)]">
                    <label className="block">
                      <span className="text-xs font-semibold text-stone-600">Giá (đ, để trống nếu không niêm yết)</span>
                      <input className={`${inputClass} mt-1`} inputMode="numeric" aria-label={`Giá ${option.label}`} value={selected.price} onChange={(event) => updateService(option.value, 'price', event.target.value.replace(/[^\d]/g, ''))} />
                      <FieldError message={errors[`services[${index}].price`]} />
                    </label>
                    <label className="block">
                      <span className="text-xs font-semibold text-stone-600">Ghi chú</span>
                      <input className={`${inputClass} mt-1`} aria-label={`Ghi chú ${option.label}`} maxLength={255} value={selected.note} onChange={(event) => updateService(option.value, 'note', event.target.value)} />
                      <FieldError message={errors[`services[${index}].note`] ?? errors[`services[${index}].code`]} />
                    </label>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </fieldset>
    </div>
  )
}

export { VenueInfoFields }
