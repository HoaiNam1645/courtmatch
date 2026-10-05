import { useEffect, useRef, useState } from 'react'

/** Toạ độ mặc định khi người chơi chưa ghim vị trí nào: trung tâm Đà Nẵng. */
const FALLBACK_CENTER = { lat: 16.0544, lng: 108.2022 }

/**
 * Ghim toạ độ cho một địa điểm ưu tiên (2.1.11).
 *
 * Địa chỉ dạng chữ không đủ để ghép cặp theo khoảng cách — backend cần
 * `latitude`/`longitude` để dựng cột `location` (PostGIS) và dùng `ST_DWithin`.
 *
 * @param {{latitude: number|null, longitude: number|null, radiusKm: number,
 *          onChange: (coords: {latitude: number, longitude: number}) => void}} props
 */
export function LocationPicker({ latitude, longitude, radiusKm, onChange }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)
  const circleRef = useRef(null)
  const onChangeRef = useRef(onChange)
  const [locating, setLocating] = useState(false)
  const [locateError, setLocateError] = useState('')

  // Giữ callback mới nhất mà không phải dựng lại bản đồ mỗi lần render.
  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])

  useEffect(() => {
    let cancelled = false

    import('leaflet').then((L) => {
      if (cancelled || !containerRef.current || mapRef.current) return

      const start = latitude != null && longitude != null
        ? { lat: latitude, lng: longitude }
        : FALLBACK_CENTER

      const map = L.map(containerRef.current, {
        center: [start.lat, start.lng],
        zoom: latitude != null ? 15 : 12,
        zoomControl: true,
        attributionControl: false,
      })

      L.tileLayer('https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', {
        subdomains: 'abc',
        maxZoom: 19,
      }).addTo(map)

      // divIcon thay cho marker mặc định: ảnh marker của Leaflet không được bundle.
      const icon = L.divIcon({
        className: '',
        html: '<span class="leaflet-pick-pin"><span></span></span>',
        iconSize: [34, 34],
        iconAnchor: [17, 17],
      })
      const marker = L.marker([start.lat, start.lng], { draggable: true, icon }).addTo(map)
      const circle = L.circle([start.lat, start.lng], {
        radius: (radiusKm ?? 5) * 1000,
        color: '#047857',
        weight: 1,
        fillColor: '#10b981',
        fillOpacity: 0.12,
      }).addTo(map)

      function commit(lat, lng) {
        const next = { latitude: Number(lat.toFixed(7)), longitude: Number(lng.toFixed(7)) }
        marker.setLatLng([next.latitude, next.longitude])
        circle.setLatLng([next.latitude, next.longitude])
        onChangeRef.current?.(next)
      }

      marker.on('dragend', () => {
        const position = marker.getLatLng()
        commit(position.lat, position.lng)
      })
      map.on('click', (event) => commit(event.latlng.lat, event.latlng.lng))

      mapRef.current = map
      markerRef.current = marker
      circleRef.current = circle
      // Bản đồ nằm trong khối vừa mở nên phải tính lại kích thước.
      setTimeout(() => map.invalidateSize(), 120)
    })

    return () => {
      cancelled = true
      if (mapRef.current) {
        mapRef.current.remove()
        mapRef.current = null
        markerRef.current = null
        circleRef.current = null
      }
    }
    // Bản đồ chỉ dựng một lần cho mỗi lần mở khối; toạ độ ban đầu chỉ dùng để đặt tâm.
    // Thay đổi sau đó được hai effect bên dưới xử lý, nên không đưa vào mảng phụ thuộc.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Kéo thanh bán kính thì vòng tròn trên bản đồ đổi theo.
  useEffect(() => {
    if (circleRef.current) circleRef.current.setRadius((radiusKm ?? 5) * 1000)
  }, [radiusKm])

  // Toạ độ đổi từ bên ngoài (ví dụ bấm "Dùng vị trí hiện tại").
  useEffect(() => {
    if (latitude == null || longitude == null || !mapRef.current) return
    markerRef.current?.setLatLng([latitude, longitude])
    circleRef.current?.setLatLng([latitude, longitude])
    mapRef.current.setView([latitude, longitude], Math.max(mapRef.current.getZoom(), 15))
  }, [latitude, longitude])

  function useCurrentPosition() {
    if (!navigator.geolocation) {
      setLocateError('Trình duyệt không hỗ trợ định vị.')
      return
    }
    setLocating(true)
    setLocateError('')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false)
        onChangeRef.current?.({
          latitude: Number(position.coords.latitude.toFixed(7)),
          longitude: Number(position.coords.longitude.toFixed(7)),
        })
      },
      () => {
        setLocating(false)
        setLocateError('Không lấy được vị trí. Bạn có thể bấm trực tiếp lên bản đồ.')
      },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  return (
    <div className="mt-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-medium text-stone-500">
          Bấm lên bản đồ hoặc kéo ghim để chọn vị trí.
        </p>
        <button
          type="button"
          onClick={useCurrentPosition}
          disabled={locating}
          className="h-9 rounded-lg border border-emerald-200 bg-emerald-50 px-3 text-xs font-semibold text-emerald-700 disabled:opacity-60"
        >
          {locating ? 'Đang định vị...' : 'Dùng vị trí hiện tại'}
        </button>
      </div>

      <div
        ref={containerRef}
        className="mt-3 h-[220px] w-full overflow-hidden rounded-lg ring-1 ring-emerald-100"
      />

      {locateError && <p className="mt-2 text-xs font-medium text-red-600">{locateError}</p>}
    </div>
  )
}
