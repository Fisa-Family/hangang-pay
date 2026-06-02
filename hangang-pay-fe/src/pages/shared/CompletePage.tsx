import { useLocation, useNavigate } from 'react-router-dom'
import { formatWon, formatDateTimeDot } from '@/lib/format'
import { Button, CheckCircleIcon } from '@/components/common'

type CompleteState = Record<string, unknown>

interface ResultRow {
  label: string
  value: string
  accent?: boolean
}

interface FlowConfig {
  title: (state: CompleteState) => string
  amountLabel?: string
  rows: (state: CompleteState) => ResultRow[]
  actionLabel?: string
  actionPath?: string
}

const FLOWS: Record<string, FlowConfig> = {
  '/pay/complete': {
    title: (s) => (s.merchantName as string) || '결제 완료',
    amountLabel: '결제 금액',
    rows: (s) => [
      { label: '승인번호', value: (s.approvalNumber as string) ?? '—' },
      { label: '일시', value: s.confirmedAt ? formatDateTimeDot(s.confirmedAt as string) : '—' },
    ],
  },
  '/charge/complete': {
    title: () => '충전이 완료되었습니다',
    amountLabel: '충전 금액',
    rows: (s) => [
      {
        label: '실 결제 금액',
        value: s.finalAmount != null ? formatWon(s.finalAmount as number) : '—',
        accent: true,
      },
      { label: '일시', value: s.chargedAt ? formatDateTimeDot(s.chargedAt as string) : '—' },
    ],
  },
  '/refund/complete': {
    title: () => '환불 신청이 완료되었습니다',
    amountLabel: '환불 금액',
    rows: (s) => [
      {
        label: '입금 계좌',
        value: s.bankName && s.accountNumber ? `${s.bankName} ${s.accountNumber}` : '—',
      },
      {
        label: '신청일시',
        value: s.exchangedAt ? formatDateTimeDot(s.exchangedAt as string) : '—',
      },
    ],
  },
  '/register/complete': {
    title: () => '회원가입이 완료되었습니다',
    rows: () => [],
    actionLabel: '시작하기',
    actionPath: '/home',
  },
  '/merchant/register/complete': {
    title: () => '가맹점 회원가입이 완료되었습니다',
    rows: () => [],
    actionLabel: '시작하기',
    actionPath: '/merchant/home',
  },
}

export function CompletePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as CompleteState | null
  const flow = FLOWS[location.pathname]

  const rows = state && flow ? flow.rows(state) : []
  const amount = state?.amount as number | undefined
  const actionLabel = flow?.actionLabel ?? '홈으로'
  const actionPath = flow?.actionPath ?? '/home'

  return (
    <div className="flex h-dvh flex-col items-center justify-between bg-background px-5 py-14">
      <div className="flex w-full flex-1 flex-col items-center justify-center gap-5">
        <CheckCircleIcon />

        <p className="text-xl font-bold text-foreground">
          {state && flow ? flow.title(state) : '완료'}
        </p>

        {flow?.amountLabel && (
          <div className="w-full rounded-2xl bg-card p-5 shadow-sm ring-1 ring-border/60">
            <p className="mb-1 text-center text-sm text-muted-foreground">{flow.amountLabel}</p>
            <p className="mb-4 text-center text-[32px] font-bold tabular-nums text-foreground">
              {amount != null ? formatWon(amount) : '—'}
            </p>

            {rows.length > 0 && (
              <div className="border-t border-border/60 pt-4">
                {rows.map((row) => (
                  <div key={row.label} className="flex items-center justify-between py-2.5">
                    <span className="text-sm text-muted-foreground">{row.label}</span>
                    <span
                      className={`text-sm font-semibold tabular-nums ${row.accent ? 'text-primary' : 'text-foreground'}`}
                    >
                      {row.value}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <Button
        size="lg"
        className="rounded-2xl"
        onClick={() => navigate(actionPath, { replace: true })}
      >
        {actionLabel}
      </Button>
    </div>
  )
}
