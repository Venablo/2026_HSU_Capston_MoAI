# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev        # Start Vite dev server at http://localhost:5173
npm run build      # TypeScript check + Vite production build
npm run lint       # ESLint with TypeScript + React Hooks rules
npm run preview    # Preview production build locally
```

No test runner is configured in this project.

## Architecture Overview

**MoAI** is a React 19 + TypeScript SPA for an AI-powered personalized learning platform (HSU Capstone 2026). The frontend connects to a Spring Boot backend.

- **Backend base URL**: `http://localhost:8080` (dev) / `http://54.180.124.4:8080` (prod), set via `VITE_API_BASE_URL` in `.env.*` files.
- **Build tool**: Vite 7, **UI**: Chakra UI 3 + Tailwind CSS, **Routing**: React Router v7, **Data fetching**: TanStack Query v5, **Animations**: Framer Motion

## Key Structural Patterns

### Routing & Layout
`App.tsx` defines all routes. Authenticated routes are wrapped in `<AppLayout>` which renders `<Sidebar>` + `<Outlet>`. The main routes are `/main`, `/my-studies`, `/study/:studyId/dashboard`, and `/study/:studyId/classroom`.

### State Management
Two React Contexts handle global state (no Redux/Zustand):
- `context/AuthContext.tsx` — JWT token + user info. Tokens are saved to `localStorage` first (so Axios interceptors can read synchronously), then React state. Exposes `useAuth()`.
- `context/ClassroomModalContext.tsx` — Controls which of 10+ learning-event modals is open, with typed discriminated-union payloads. Exposes `useClassroomModal()`.

### API Layer
- `api/axios.ts` — Axios instance that attaches `Authorization: Bearer {token}` on every request. On 401, clears localStorage and redirects to `/`.
- `services/apiService.ts` — 41 documented endpoint functions organized by feature (Auth, Onboarding, Learning Room, Event Logs, Flipped Learning, Final Quiz, Study Matching, Notifications, My Page). All responses follow `{ success, data, message, timestamp }`; use the `unwrap()` helper to extract `.data`.
- `services/aiSummaryService.ts` — Mock AI service with 1-second delays for offline development.
- Real-time: SSE streams for flipped learning and notifications; WebSocket for group chat.

### Modal System
`components/modals/` contains 10+ modals triggered by AI-detected learning events (video rewind detection → `monitoring` modal, quiz outcomes, `fast-track`, `meta-evaluation`, `study-matching`, etc.). Payloads are strongly typed via the discriminated union in `ClassroomModalContext`.

### Type Definitions
- `types/api.ts` — Complete request/response shapes for all 41 endpoints.
- `types/aiEvents.ts` — AI pattern types and modal payload types.

### Styling
Mixed approach: Tailwind utility classes + scoped CSS files per component/page + CSS variables in `styles/`. Chakra UI handles component-level theming. Framer Motion handles animations.

## File Organization

```
src/
├── api/           # Axios instance
├── components/    # Reusable UI (modals/, layout/, Study/)
├── constants/     # Static data and mock data
├── context/       # AuthContext, ClassroomModalContext
├── hooks/         # Custom hooks (useClassroomModals)
├── pages/         # Route-level components (main, my-studies, study/*)
├── services/      # API calls and AI mock service
├── styles/        # CSS files
└── types/         # TypeScript interfaces
```
