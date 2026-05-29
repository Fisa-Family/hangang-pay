import { useLocation, useNavigate } from 'react-router-dom'
import type { ChargeExecuteResult } from '@/api/charge'
import { formatWon } from '@/lib/format'
import { Button } from '@/components/common'

function CheckCircleIcon() {
  return (
    <div className="relative flex items-center justify-center">
      <div className="absolute h-[76px] w-[76px] rounded-full bg-[#00A441]/10" />
      <svg width="56" height="56" viewBox="0 0 56 56" fill="none" aria-hidden>
        <circle cx="28" cy="28" r="28" fill="#00A441" />
        <path
          d="M17 28l8 8 14-16"
          stroke="white"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  )
}

function formatChargedAt(iso: string): string {
  const d = new Date(iso)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}.${mm}.${dd} ${hh}:${min}`
}

function SummaryRow({
  label,
  value,
  accent = false,
}: {
  label: string
  value: string
  accent?: boolean
}) {
  return (
    <div className="flex items-center justify-between py-3">
      <span className="text-sm text-[#8B95A1]">{label}</span>
      <span
        className={`text-sm font-semibold tabular-nums ${accent ? 'text-[#0057FF]' : 'text-[#191F28]'}`}
      >
        {value}
      </span>
    </div>
  )
}

export function ChargeCompletePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const result = location.state as ChargeExecuteResult | null

  return (
    <div className="flex h-dvh flex-col items-center justify-between bg-[#F7F8FA] px-5 py-14">
      {/* 상단: 아이콘 + 타이틀 + 요약 카드 */}
      <div className="flex w-full flex-1 flex-col items-center justify-center gap-6">
        <CheckCircleIcon />

        <p className="text-xl font-bold text-[#191F28]">충전이 완료되었습니다</p>

        {/* 트랜잭션 요약 카드 */}
        <div className="w-full rounded-2xl border border-[#E5E8EB] bg-white px-5 py-1 shadow-sm">
          <SummaryRow
            label="충전 금액"
            value={result ? formatWon(result.amount) : '—'}
          />
          <SummaryRow
            label="실 결제 금액"
            value={result ? formatWon(result.finalAmount) : '—'}
            accent
          />
          <div className="h-px bg-[#E5E8EB]" />
          <SummaryRow
            label="일시"
            value={result?.chargedAt ? formatChargedAt(result.chargedAt) : '—'}
          />
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
