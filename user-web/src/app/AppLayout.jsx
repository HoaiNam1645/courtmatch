import { useEffect, useState } from 'react'
import { Bell, CalendarDays, LogOut, Zap } from 'lucide-react'
import { classNames } from '../shared/utils'
import { readCachedUser } from '../shared/session'
import { fallbackSlides, navigationTabs } from './config'

function SplashScreen() {
  return (
    <div className="splash-screen">
      <div className="splash-orbit" aria-hidden="true">
        <span className="splash-float splash-shuttle splash-float-1">
          <span />
        </span>
        <span className="splash-float splash-racket splash-float-2" />
        <span className="splash-float splash-shuttle splash-float-3">
          <span />
        </span>
        <span className="splash-float splash-court splash-float-4" />
        <span className="splash-float splash-racket splash-float-5" />
        <span className="splash-float splash-shuttle splash-float-6">
          <span />
        </span>
      </div>
      <div className="splash-logo" aria-hidden="true">
        <img src="/logo.png" alt="" />
      </div>
      <p className="text-[32px] font-bold tracking-[0.2em] text-white max-sm:text-[24px]">
        COURTLY
      </p>
      <p className="text-sm font-semibold uppercase tracking-[0.18em] text-emerald-100/75">
        Đặt lịch sân cầu lông
      </p>
      <div className="flex gap-2 pt-3" aria-hidden="true">
        <span className="splash-dot" />
        <span className="splash-dot animation-delay-150" />
        <span className="splash-dot animation-delay-300" />
      </div>
    </div>
  )
}

function BrandMark({ compact = false, mini = false }) {
  return (
    <div
      className={classNames(
        'grid shrink-0 place-items-center overflow-hidden rounded-lg shadow-[0_10px_24px_rgba(8,112,64,0.18)]',
        mini ? 'size-9' : compact ? 'size-11' : 'size-16',
      )}
    >
      <img className="size-full scale-[1.34] object-contain" src="/logo.png" alt="Courtly" />
    </div>
  )
}

function HeroSlides({ slides = fallbackSlides }) {
  const [activeIndex, setActiveIndex] = useState(0)
  const safeIndex = slides.length ? activeIndex % slides.length : 0
  const activeSlide = slides[safeIndex] ?? slides[0] ?? fallbackSlides[0]

  useEffect(() => {
    if (slides.length <= 1) return undefined
    const timer = window.setInterval(() => {
      setActiveIndex((current) => (current + 1) % slides.length)
    }, 4200)
    return () => window.clearInterval(timer)
  }, [slides.length])

  return (
    <section className="relative min-h-[470px] overflow-hidden px-4 pb-24 pt-14 md:px-6 lg:px-8 lg:pt-16">
      <img
        className="absolute inset-0 h-full w-full object-cover"
        src={activeSlide.image}
        alt={activeSlide.title}
      />
      <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(0,75,48,0.92)_0%,rgba(0,97,63,0.62)_38%,rgba(0,50,41,0.48)_100%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_75%_28%,rgba(158,255,111,0.10),transparent_26%),linear-gradient(130deg,rgba(16,185,129,0.20)_0_18%,transparent_18%_50%,rgba(16,185,129,0.13)_50%_66%,transparent_66%)]" />
      <div className="absolute bottom-0 right-0 h-32 w-[42vw] bg-emerald-400/28 [clip-path:polygon(100%_0,0_100%,100%_100%)]" />

      <div className="relative z-10 mx-auto max-w-[1680px]">
        <div className="max-w-[620px] pt-6">
          <span className="inline-flex items-center gap-2 rounded-lg bg-white/16 px-4 py-2 text-xs font-semibold uppercase tracking-[0.08em] text-lime-100 ring-1 ring-white/14 backdrop-blur">
            <Zap className="size-4 fill-lime-300 text-lime-300" />
            Đặt sân nhanh
          </span>
          <h1 className="mt-5 text-balance text-[36px] font-semibold leading-[1.07] tracking-normal text-white md:text-[50px] lg:text-[58px]">
            So giá và chọn sân
            <span className="block text-lime-300">trong một màn</span>
          </h1>
          <p className="mt-5 text-lg font-medium leading-8 text-emerald-50/82 md:text-2xl">
            Lọc theo khoảng cách, giá và sân còn trống.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-5">
            <button className="inline-flex h-16 items-center gap-3 rounded-lg bg-lime-300 px-8 text-base font-semibold text-emerald-950 shadow-[0_18px_32px_rgba(163,230,53,0.26)]">
              <CalendarDays className="size-5" />
              Tìm sân phù hợp
            </button>
            <div className="flex items-center gap-2">
              {slides.map((slide, index) => (
                <button
                  key={slide.id}
                  className={classNames(
                    'size-3 rounded-full transition-all',
                    index === safeIndex ? 'w-7 bg-lime-300' : 'bg-white/35',
                  )}
                  onClick={() => setActiveIndex(index)}
                  aria-label={`Chuyển sang slide ${index + 1}`}
                />
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function Header({ slides, user, onLogout }) {
  return (
    <div className="relative overflow-hidden bg-emerald-950 text-white">
      <AppHeader user={user} onLogout={onLogout} />
      <HeroSlides slides={slides} />
    </div>
  )
}

function AppHeader({ user, onLogout }) {
  return (
    <header className="sticky top-0 z-40 border-b border-white/10 bg-emerald-950 px-4 py-2.5 text-white shadow-[0_8px_22px_rgba(4,47,32,0.14)] md:px-6 lg:px-8">
      <div className="mx-auto flex max-w-[1680px] items-center justify-between gap-5">
        <a className="flex items-center gap-3 text-white" href="/">
          <BrandMark mini />
          <span className="text-[23px] font-semibold tracking-normal">Courtly</span>
        </a>

        {user ? (
          <div className="flex items-center gap-2">
            <button className="hidden size-9 place-items-center rounded-lg text-white/90 hover:bg-white/8 sm:grid">
              <Bell className="size-[18px]" />
            </button>
            <a
              href="/profile"
              className="flex h-10 items-center gap-2.5 rounded-lg border border-white/22 bg-white/8 px-3 text-left text-white shadow-sm backdrop-blur"
            >
              <span className="grid size-7 place-items-center rounded-md bg-lime-300 text-xs font-semibold text-emerald-950">
                {user.name.slice(0, 1)}
              </span>
              <span className="hidden leading-tight sm:grid">
                <span className="text-[13px] font-semibold">{user.name}</span>
                <span className="text-[11px] font-medium text-lime-200">Người chơi</span>
              </span>
            </a>
            <button
              className="grid size-10 place-items-center rounded-lg border border-white/22 text-white hover:bg-white/8"
              onClick={onLogout}
              aria-label="Đăng xuất"
              title="Đăng xuất"
            >
              <LogOut className="size-[18px]" />
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-3">
            <button className="hidden size-9 place-items-center rounded-lg text-white/90 hover:bg-white/8 sm:grid">
              <Bell className="size-[18px]" />
            </button>
            <a className="grid h-10 place-items-center rounded-lg border border-white/22 bg-white/7 px-5 text-sm font-semibold text-white backdrop-blur" href="/login">
              Đăng nhập
            </a>
            <a className="hidden h-10 place-items-center rounded-lg bg-lime-300 px-5 text-sm font-semibold text-emerald-950 shadow-[0_10px_22px_rgba(163,230,53,0.2)] sm:grid" href="/register">
              Đăng kí
            </a>
          </div>
        )}
      </div>
    </header>
  )
}

function BottomNav({ activeLabel = 'Trang chủ' }) {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-emerald-100 bg-white/96 px-2 py-1 shadow-[0_-10px_26px_rgba(24,48,35,0.1)] backdrop-blur">
      <div className="mx-auto grid max-w-5xl grid-cols-5 items-end">
        {navigationTabs.map((tab) => {
          const Icon = tab.icon
          const active = tab.label === activeLabel
          const href = tab.label === 'Tài khoản' && readCachedUser() ? '/profile' : tab.href
          return (
            <a
              key={tab.label}
              href={href}
              className={classNames(
                'relative grid place-items-center gap-0.5 rounded-lg py-1 text-[11px] font-bold',
                active ? 'text-emerald-600' : 'text-stone-400',
                tab.raised && '-mt-6',
              )}
            >
              <span
                className={classNames(
                  'grid place-items-center',
                  tab.raised
                    ? 'size-11 rounded-md border-[3px] border-white bg-white text-emerald-600 shadow-[0_8px_20px_rgba(9,128,72,0.22)] ring-2 ring-emerald-500'
                    : 'size-6',
                )}
              >
                <Icon className={tab.raised ? 'size-5' : 'size-5'} />
              </span>
              <span>{tab.label}</span>
            </a>
          )
        })}
      </div>
    </nav>
  )
}

export { AppHeader, BottomNav, BrandMark, Header, SplashScreen }
