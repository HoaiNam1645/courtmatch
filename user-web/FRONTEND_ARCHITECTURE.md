# Frontend architecture

The frontend is organized by business feature. A feature owns its pages, reusable components, and route definitions so it can be understood or moved without reading `App.jsx`.

```text
src/
  app/
    AppProvider.jsx       # session and application-wide state
    router/AppRouter.jsx  # combines feature routes
  features/
    auth/
      pages/
      routes.jsx
    bookings/
      components/
      pages/
      routes.jsx
    home/
      HomePage.jsx
      routes.jsx
    matches/
      pages/
      routes.jsx
    profile/
      components/
      pages/
      routes.jsx
    venues/
      components/
      pages/
      routes.jsx
  api/                    # HTTP calls grouped by backend resource
  hooks/                  # reusable server-state hooks
  shared/                 # feature-neutral helpers
```

## Adding a feature

1. Create `src/features/<feature>/pages/<PageName>.jsx`.
2. Keep feature-only UI in `src/features/<feature>/components`.
3. Put backend calls in `src/api` and reusable request state in `src/hooks`.
4. Declare URLs in `src/features/<feature>/routes.jsx`.
5. Add that route array to `src/app/router/AppRouter.jsx`.

## Copying a feature

Copy the complete folder under `src/features`. Then copy only the imports it uses from `api`, `hooks`, or `shared`, and register its exported route array. Avoid importing another feature's page; move genuinely shared UI into `shared` or a small public component module instead.

The old `*Pages.jsx` files are compatibility barrels only. New implementation code belongs in `pages` or `components`, not in those barrels.
