import { useLocation, useNavigate } from 'react-router-dom'
import type { PaymentResult } from '@/api/payment'
import { formatWon } from '@/lib/format'
import { Button } from '@/components/common'

function formatPaidAt(isoString: string): string {
  const d = new Date(isoString)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}.${mm}.${dd} ${hh}:${min}`
}

function CheckCircleIcon() {
  return (
    <svg width="56" height="56" viewBox="0 0 56 56" fill="none" aria-hidden>
      <circle cx="28" cy="28" r="28" fill="#22C55E" />
      <path
        d="M17 28l8 8 14-16"
        stroke="white"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ResultRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-2.5">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-semibold tabular-nums text-foreground">{value}</span>
    </div>
  )
}

export function PayCompletePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const result = location.state as PaymentResult | null

  return (
    <div className="flex h-dvh flex-col items-center justify-between bg-white px-5 py-14">
      {/* 상단 결과 */}
      <div className="flex flex-1 flex-col items-center justify-center gap-5 w-full">
        <CheckCircleIcon />

        <p className="text-xl font-bold text-foreground">{result?.merchantName ?? '결제 완료'}</p>

        {/* 영수증 카드 */}
        <div className="w-full rounded-2xl bg-white p-5 shadow-sm">
          <p className="mb-1 text-center text-sm text-muted-foreground">결제 금액</p>
          <p className="mb-4 text-center text-[32px] font-bold tabular-nums text-foreground">
            {result ? formatWon(result.amount) : '—'}
          </p>

          <div className="border-t border-border/60 pt-4">
            <ResultRow
              label="남은 잔액"
              value={result ? formatWon(result.remainingBalance) : '—'}
            />
            <ResultRow label="승인번호" value={result?.approvalNumber ?? '—'} />
            <ResultRow label="일시" value={result ? formatPaidAt(result.paidAt) : '—'} />
          </div>
        </div>
      </div>

      {/* 홈으로 버튼 */}
      <Button
        size="lg"
        className="rounded-2xl"
        onClick={() => navigate('/home', { replace: true })}
      >
        홈으로
      </Button>
    </div>
  )
}
