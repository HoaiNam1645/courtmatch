import { ChevronRight, Clock3, Tag, Umbrella, Zap } from 'lucide-react'
import { classNames } from '../../../shared/utils'
import { SectionTitle } from './SectionTitle'

function PromoStrip({ promotions = [] }) {
  if (!promotions.length) return null

  const promoMeta = {
    green: {
      wrap: 'bg-emerald-900 text-white',
      badge: 'bg-white/16 text-emerald-50',
      button: 'bg-white text-emerald-950',
      icon: Tag,
      image: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=85',
    },
    gold: {
      wrap: 'bg-gradient-to-br from-amber-200 via-amber-300 to-yellow-400 text-emerald-950',
      badge: 'bg-white/42 text-emerald-950',
      button: 'bg-amber-400 text-emerald-950',
      icon: Zap,
      image: 'https://images.unsplash.com/photo-1613918431703-aa50889e3be2?auto=format&fit=crop&w=900&q=85',
    },
    dark: {
      wrap: 'bg-emerald-950 text-white',
      badge: 'bg-white/16 text-emerald-50',
      button: 'bg-white text-emerald-950',
      icon: Umbrella,
      image: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=85',
    },
  }

  return (
    <section className="mx-auto mt-10 max-w-[1680px] px-4 md:px-6 lg:px-8">
      <SectionTitle eyebrow="Ưu đãi cho người chơi" title="Đặt sân lợi hơn" action="Xem ưu đãi" icon={Zap} />
      <div className="mt-5 grid gap-4 md:grid-cols-3">
        {promotions.map((promo) => {
          const meta = promoMeta[promo.tone] ?? promoMeta.green
          const PromoIcon = meta.icon
          return (
          <article
            key={promo.id}
            className={classNames(
              'relative min-h-[210px] overflow-hidden rounded-lg p-6 shadow-[0_14px_34px_rgba(34,52,40,0.12)]',
              meta.wrap,
            )}
          >
            <img
              className="absolute inset-y-0 right-0 h-full w-[48%] object-cover opacity-78"
              src={meta.image}
              alt={promo.title}
            />
            <div className="absolute inset-0 bg-[linear-gradient(110deg,rgba(0,95,61,0.92)_0_48%,rgba(0,95,61,0.20)_48%_100%)] mix-blend-multiply" />
            {promo.tone === 'gold' && (
              <div className="absolute inset-0 bg-[linear-gradient(110deg,rgba(255,229,112,0.95)_0_45%,rgba(255,229,112,0.52)_45%_63%,transparent_63%)] mix-blend-normal" />
            )}
            <div className="absolute -left-14 -bottom-16 size-56 rounded-full bg-white/10" />
            <div className="absolute right-5 top-5 grid size-10 place-items-center rounded-full bg-white/82 text-emerald-900 shadow-sm">
              <PromoIcon className="size-5" />
            </div>
            <div className="relative z-10">
              <span className={classNames('inline-flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold uppercase tracking-[0.08em]', meta.badge)}>
                <Clock3 className="size-4" />
                {promo.tag}
              </span>
              <h3 className="mt-5 max-w-[310px] text-2xl font-semibold leading-tight tracking-normal">
                {promo.title}
              </h3>
              <p className="mt-3 max-w-[300px] text-base font-medium leading-6 opacity-88">
                {promo.tone === 'green' && 'Chơi khỏe, bắt đầu ngày mới năng lượng với ưu đãi đặc biệt.'}
                {promo.tone === 'gold' && 'Ưu đãi đặc biệt cho các khung giờ buổi tối hôm nay.'}
                {promo.tone === 'dark' && 'Không lo nắng mưa, thoải mái chơi mọi lúc.'}
              </p>
              <button className={classNames('mt-5 inline-flex items-center gap-2 rounded-lg px-5 py-3 text-sm font-semibold shadow-sm', meta.button)}>
                {promo.cta}
                <ChevronRight className="size-4" />
              </button>
            </div>
          </article>
          )
        })}
      </div>
    </section>
  )
}

export { PromoStrip }
