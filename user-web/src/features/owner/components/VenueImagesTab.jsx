import { useRef, useState } from 'react'
import { ArrowDown, ArrowUp, ImagePlus, Star, Trash2 } from 'lucide-react'
import {
  ACCEPTED_IMAGE_TYPES,
  deleteVenueImage,
  fetchVenueImages,
  MAX_IMAGE_BYTES,
  MAX_IMAGES_PER_VENUE,
  reorderImages,
  setCoverImage,
  updateImageCaption,
  uploadVenueImage,
} from '../../../api/ownerImages'
import { useApiResource } from '../../../hooks/useApiResource'
import { FormAlert } from '../../../shared/FormFeedback'
import { inputClass, primaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

const iconButton = 'grid size-9 place-items-center rounded-lg border border-emerald-200 text-emerald-700 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-40'

function ImageCard({ image, index, total, busy, onAction }) {
  const [caption, setCaption] = useState(image.caption)
  const changed = caption.trim() !== image.caption

  return (
    <article className="overflow-hidden rounded-xl border border-emerald-100 bg-white" data-testid={`venue-image-${index}`}>
      <div className="relative aspect-[4/3] bg-emerald-50">
        <img className="size-full object-cover" src={image.imageUrl} alt={image.caption || `Ảnh sân ${index + 1}`} loading="lazy" />
        {image.cover && <span className="absolute left-2 top-2 rounded-full bg-lime-300 px-2.5 py-1 text-xs font-bold text-emerald-950" data-testid="cover-badge">Ảnh bìa</span>}
        <span className="absolute right-2 top-2 rounded-full bg-black/55 px-2 py-0.5 text-xs font-semibold text-white">{index + 1}/{total}</span>
      </div>
      <div className="grid gap-3 p-3">
        <div className="flex gap-2">
          <input className={`${inputClass} h-9`} aria-label={`Chú thích ảnh ${index + 1}`} maxLength={255} placeholder="Chú thích (không bắt buộc)" value={caption} onChange={(event) => setCaption(event.target.value)} />
          {changed && (
            <button type="button" className="h-9 shrink-0 rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white disabled:opacity-60" disabled={busy} onClick={() => onAction('caption', image, caption)}>Lưu</button>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" className={iconButton} aria-label={`Đưa ảnh ${index + 1} lên trước`} disabled={busy || index === 0} onClick={() => onAction('move', image, -1)}><ArrowUp className="size-4" /></button>
          <button type="button" className={iconButton} aria-label={`Đưa ảnh ${index + 1} ra sau`} disabled={busy || index === total - 1} onClick={() => onAction('move', image, 1)}><ArrowDown className="size-4" /></button>
          {!image.cover && (
            <button type="button" className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-emerald-200 px-3 text-xs font-semibold text-emerald-800 hover:bg-emerald-50 disabled:opacity-60" disabled={busy} onClick={() => onAction('cover', image)}>
              <Star className="size-4" />Đặt làm ảnh bìa
            </button>
          )}
          <button type="button" className="ml-auto grid size-9 place-items-center rounded-lg border border-red-200 text-red-700 hover:bg-red-50 disabled:opacity-60" aria-label={`Xóa ảnh ${index + 1}`} disabled={busy} onClick={() => onAction('delete', image)}>
            <Trash2 className="size-4" />
          </button>
        </div>
      </div>
    </article>
  )
}

/** 2.3.6 — tải lên, sắp xếp, chọn ảnh bìa và xóa ảnh sân. */
function VenueImagesTab({ venue, onSaved }) {
  const { status, data, error, reload } = useApiResource(() => fetchVenueImages(venue.id), [venue.id])
  const fileInput = useRef(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState('')
  const [actionError, setActionError] = useState('')

  const images = data ?? []
  const remaining = MAX_IMAGES_PER_VENUE - images.length

  async function handleFiles(fileList) {
    const files = Array.from(fileList ?? [])
    if (fileInput.current) fileInput.current.value = ''
    if (files.length === 0 || busy) return
    setActionError('')

    // Kiểm tra sớm cho người dùng; backend vẫn kiểm tra lại nội dung file.
    const invalid = files.find((file) => !ACCEPTED_IMAGE_TYPES.includes(file.type) || file.size > MAX_IMAGE_BYTES || file.size === 0)
    if (invalid) {
      setActionError(`Ảnh “${invalid.name}” không hợp lệ: chỉ nhận JPEG, PNG, WebP và tối đa 5MB.`)
      return
    }
    if (files.length > remaining) {
      setActionError(`Sân chỉ còn được thêm ${remaining} ảnh (tối đa ${MAX_IMAGES_PER_VENUE}).`)
      return
    }

    setBusy(true)
    let uploaded = 0
    try {
      for (const file of files) {
        setProgress(`Đang tải ${uploaded + 1}/${files.length}: ${file.name}`)
        await uploadVenueImage(venue.id, file)
        uploaded += 1
      }
      onSaved(`Đã tải lên ${uploaded} ảnh.`)
    } catch (uploadError) {
      setActionError(uploadError?.message ?? 'Không tải được ảnh.')
      setBusy(false)
      setProgress('')
      if (uploaded > 0) reload()
    }
  }

  async function handleAction(action, image, value) {
    if (busy) return
    if (action === 'delete' && !window.confirm('Xóa ảnh này khỏi sân?')) return
    setBusy(true)
    setActionError('')
    try {
      if (action === 'caption') {
        await updateImageCaption(venue.id, image.id, value)
        onSaved('Đã lưu chú thích ảnh.')
      } else if (action === 'cover') {
        await setCoverImage(venue.id, image.id)
        onSaved('Đã đổi ảnh bìa.')
      } else if (action === 'delete') {
        await deleteVenueImage(venue.id, image.id)
        onSaved('Đã xóa ảnh.')
      } else if (action === 'move') {
        const ids = images.map((item) => item.id)
        const from = ids.indexOf(image.id)
        const to = from + value
        ;[ids[from], ids[to]] = [ids[to], ids[from]]
        await reorderImages(venue.id, ids)
        onSaved('Đã cập nhật thứ tự ảnh.')
      }
    } catch (failure) {
      setActionError(failure?.message ?? 'Không thực hiện được thao tác.')
      setBusy(false)
      // Ảnh có thể đã đổi ở tab khác: tải lại danh sách mới nhất.
      if (failure?.status === 400 || failure?.status === 404) reload()
    }
  }

  const uploadButton = (
    <label className={`${primaryButtonClass} cursor-pointer ${busy || remaining <= 0 ? 'pointer-events-none opacity-60' : ''}`}>
      <ImagePlus className="size-4" />
      Tải ảnh lên
      <input
        ref={fileInput}
        type="file"
        className="sr-only"
        accept={ACCEPTED_IMAGE_TYPES.join(',')}
        multiple
        disabled={busy || remaining <= 0}
        aria-label="Chọn ảnh sân"
        onChange={(event) => handleFiles(event.target.files)}
      />
    </label>
  )

  return (
    <OwnerCard
      title="Hình ảnh sân"
      description={`JPEG, PNG hoặc WebP, tối đa 5MB mỗi ảnh và ${MAX_IMAGES_PER_VENUE} ảnh mỗi sân. Ảnh bìa và thứ tự ở đây là thứ tự người chơi thấy.`}
      actions={status === 'success' || status === 'empty' ? uploadButton : null}
    >
      {progress && busy && <p className="mb-3 text-sm font-semibold text-emerald-700" role="status" aria-live="polite">{progress}</p>}
      {actionError && <div className="mb-3"><FormAlert message={actionError} /></div>}
      {status === 'loading' && <OwnerLoading rows={1} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && images.length === 0 && (
        <OwnerEmpty title="Sân chưa có ảnh" description="Thêm ảnh sân để người chơi dễ chọn hơn. Ảnh đầu tiên sẽ là ảnh bìa." />
      )}
      {status === 'success' && images.length > 0 && (
        <>
          <p className="mb-3 text-xs font-semibold text-stone-500" data-testid="image-count">{images.length}/{MAX_IMAGES_PER_VENUE} ảnh</p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {images.map((image, index) => (
              <ImageCard key={`${image.id}-${image.caption}`} image={image} index={index} total={images.length} busy={busy} onAction={handleAction} />
            ))}
          </div>
        </>
      )}
    </OwnerCard>
  )
}

export { VenueImagesTab }
