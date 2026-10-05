import { useEffect, useRef, useState } from 'react'

/**
 * Bản đồ vị trí sân (2.1.25).
 *
 * Dùng Leaflet với tile của `tile.openstreetmap.fr` giống trang /map, thay vì nhúng
 * iframe từ `openstreetmap.org` — iframe phụ thuộc vào một host khác và không báo lỗi
 * được khi host đó không truy cập được (bị chặn DNS, chặn khung nhúng, mất mạng).
 */
/** Quá thời gian này mà chưa tile nào tải được thì coi như không hiển thị được bản đồ. */
const TILE_TIMEOUT_MS = 8000

export function VenueMap({ latitude, longitude, name, className = '' }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const failTimerRef = useRef(null)
  const [failed, setFailed] = useState(false)

  const hasCoords = Number.isFinite(latitude) && Number.isFinite(longitude)

  useEffect(() => {
    if (!hasCoords) return undefined
    let cancelled = false
    let loadedAnyTile = false

    import('leaflet')
      .then((L) => {
        if (cancelled || !containerRef.current || mapRef.current) return

        const map = L.map(containerRef.current, {
          center: [latitude, longitude],
          zoom: 16,
          zoomControl: true,
          attributionControl: true,
          scrollWheelZoom: false,
        })

        const tiles = L.tileLayer('https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', {
          subdomains: 'abc',
          maxZoom: 19,
          attribution: '© OpenStreetMap contributors',
        })

        // Vài tile lỗi lẻ tẻ là chuyện bình thường (một subdomain chậm, vùng chưa có tile),
        // nên không lấy đó làm căn cứ. Chỉ khi quá thời gian mà KHÔNG tile nào tải được
        // thì mới coi là hỏng và hiện khối thay thế.
        tiles.on('tileload', () => {
          loadedAnyTile = true
        })
        tiles.addTo(map)

        failTimerRef.current = window.setTimeout(() => {
          if (!loadedAnyTile) setFailed(true)
        }, TILE_TIMEOUT_MS)

        L.marker([latitude, longitude], {
          icon: L.divIcon({
            className: '',
            html: '<span class="leaflet-pick-pin"><span></span></span>',
            iconSize: [34, 34],
            iconAnchor: [17, 17],
          }),
        })
          .addTo(map)
          .bindTooltip(name, { direction: 'top', offset: [0, -14], className: 'leaflet-venue-tooltip' })

        mapRef.current = map
        setTimeout(() => map.invalidateSize(), 120)
      })
      .catch(() => setFailed(true))

    return () => {
      cancelled = true
      if (failTimerRef.current) {
        window.clearTimeout(failTimerRef.current)
        failTimerRef.current = null
      }
      if (mapRef.current) {
        mapRef.current.remove()
        mapRef.current = null
      }
    }
  }, [hasCoords, latitude, longitude, name])

  if (!hasCoords || failed) {
    return (
      <div className={`grid place-items-center bg-emerald-50/60 p-6 text-center ${className}`}>
        <div>
          <p className="text-sm font-semibold text-emerald-950">Không hiển thị được bản đồ</p>
          <p className="mt-2 text-xs font-medium leading-5 text-stone-500">
            {hasCoords
              ? 'Kiểm tra kết nối mạng rồi tải lại trang. Bạn vẫn có thể bấm "Mở chỉ đường".'
              : 'Sân này chưa có toạ độ.'}
          </p>
        </div>
      </div>
    )
  }

  return <div ref={containerRef} className={className} />
}
