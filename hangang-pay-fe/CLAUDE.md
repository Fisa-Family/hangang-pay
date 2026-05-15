# hangang-pay-fe

React 19 mobile web SPA for 한강페이 — 성동구 지역화폐 (HRC, 한강코인) PG system.
Target: mobile browser only. Desktop layout is out of scope.

## Tech Stack

| Library                   | Role                 | Why                                                                   |
| ------------------------- | -------------------- | --------------------------------------------------------------------- |
| React 19 + React Compiler | UI                   | Auto-memoization — no manual `useMemo`/`useCallback` needed           |
| Vite                      | Build                | Native ESM, fast HMR                                                  |
| TypeScript                | Type safety          | —                                                                     |
| Tailwind CSS              | Styling              | Mobile-first custom UI; full control over layout and brand tokens     |
| CVA + `cn()`              | Variant system       | Declarative component variants; safe Tailwind class merging           |
| shadcn/ui + Radix         | Shared UI primitives | a11y built-in; used for Dialog, Toast, Form, Badge, Skeleton only     |
| React Router v7           | Routing              | Team familiarity; sufficient for this SPA scale                       |
| TanStack Query            | Server state         | Cache, refetch, mutation for 잔액·거래 내역 that must always be fresh |
| Zustand                   | Client state         | 결제 flow state only (session-based auth — no token storage needed)   |
| react-hook-form           | Forms                | Built-in validation rules; no extra schema library needed             |
| Vitest                    | Unit tests           | Vite-native; used only for pure utility functions in `src/utils/`     |

## Component Patterns

### `cn()` helper

```ts
// src/lib/utils.ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

### CVA variant system

Define variants with `cva`, expose `VariantProps`, accept `className` for overrides.

```ts
const buttonVariants = cva('base-classes', {
  variants: {
    variant: { primary: '...', ghost: '...', danger: '...' },
    size: { sm: '...', md: '...', lg: '...' },
  },
  defaultVariants: { variant: 'primary', size: 'md' },
})

interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

export function Button({ variant, size, className, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
```

### shadcn/ui usage scope

shadcn components are desktop-oriented. Use them only for UI that is equally natural on mobile:

- **Use shadcn for**: Form, Toast (Sonner), Dialog, Badge, Skeleton, Select
- **Build from scratch with Tailwind + Radix Primitive**: 바텀시트, 핀패드, 하단 내비게이션바, 결제 카드, 금액 입력 키패드

Never force-fit a shadcn component into a mobile-native interaction pattern.

## Auth

Session-based (not JWT). The server issues an `HttpOnly` cookie on login — the browser attaches it automatically on every request. No token is stored or read in JS.

**Current user info** (role, name) is server state — owned by TanStack Query, not Zustand:

```ts
// src/hooks/useCurrentUser.ts
export function useCurrentUser() {
  return useQuery({
    queryKey: ['currentUser'],
    queryFn: () => api.get('/api/me'),
    retry: false,
  })
}
```

- 401 response → redirect to `/login`
- `currentUser.role` (`'USER'` | `'MERCHANT'`) drives route guards and nav branching

The API client must include `credentials: 'include'` on every request so the session cookie is sent cross-origin (local dev → BE).

CORS for local dev is handled on the **BE side** (`application-local.yaml`: `allowed-origins: http://localhost:5173`, `allow-credentials: true`). Do not add a Vite proxy.

## State Management

Decide where state lives based on its origin:

| State type          | Owner          | Example                                     |
| ------------------- | -------------- | ------------------------------------------- |
| Server data         | TanStack Query | 잔액, 거래 내역, 가맹점 정보, 현재 유저     |
| Global client state | Zustand        | 결제 flow progress (가맹점 정보, 입력 금액) |
| Local UI state      | `useState`     | Modal open, input value, step index         |

**Rules:**

- After a mutation (결제, 충전, 환불), invalidate the relevant query — do not manually update Zustand with server data.
- 핀패드 and 금액 입력 are local `useState`, not form fields.
- `useForm` (react-hook-form) is for multi-field submit forms (회원가입, 로그인). Do not wrap single-input flows in a form.

## Form Validation

Use react-hook-form's built-in `register` rules. No external schema library.

```ts
register('amount', {
  required: '금액을 입력해주세요',
  min: { value: 1000, message: '최소 1,000원 이상' },
  max: { value: 500000, message: '최대 500,000원' },
})
```

Backend is the authoritative validator. Frontend validation exists only for immediate UX feedback, not as a security boundary.

## Screen ID Convention

All screens follow a structured ID system. Refer to the full map and per-screen specs:

→ [`docs/user-flow-figma-map_v2.md`](docs/user-flow-figma-map_v2.md)

Prefix summary:

| Prefix    | Area                   |
| --------- | ---------------------- |
| `A-*`     | Auth / 공통 진입       |
| `A-PW-*`  | 비밀번호 찾기          |
| `U-*`     | 사용자 앱              |
| `U-REG-*` | 사용자 회원가입        |
| `U-PAY-*` | 사용자 결제            |
| `U-CHG-*` | 사용자 충전            |
| `U-REF-*` | 사용자 환불            |
| `U-MY-*`  | 사용자 마이페이지·내역 |
| `U-ACC-*` | 사용자 계좌 관리       |
| `M-*`     | 가맹점 앱              |
| `M-REG-*` | 가맹점 회원가입        |
| `M-QR-*`  | 가맹점 QR              |
| `M-PAY-*` | 가맹점 결제 내역·취소  |
| `M-SET-*` | 가맹점 정산            |
| `M-MY-*`  | 가맹점 마이페이지      |
| `C-*`     | 공통 모달·오류·상태    |

When naming route paths, components, and test IDs, use these screen IDs as the reference.

## Testing

Vitest is used only for pure utility functions in `src/utils/` — charge discount calculation (10% 할인), refund eligibility check (최근 충전액 60% 이상 사용), etc.

Do not write component rendering tests or E2E tests for this project.

## What NOT to Do

- Do not use shadcn components for mobile-native UX (바텀시트, 핀패드, 탭바). Build those with Tailwind + Radix Primitive directly.
- Do not put server data (잔액, 거래 내역, 현재 유저) into Zustand. Use TanStack Query.
- Do not store auth token in JS (localStorage, sessionStorage, Zustand). Auth is session-based — cookie is managed by the browser.
- Do not add a Vite proxy for CORS. Local dev CORS is handled by BE (`application-local.yaml`).
- Do not use `git commit -m` — use the `.gitmessage` template (see root `CLAUDE.md`).
- Do not mix BE and FE changes in a single commit.
- Do not add zod or any runtime schema validation library — react-hook-form built-in rules are sufficient.
- Do not write desktop-oriented layouts. This is a mobile web app.
