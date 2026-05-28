import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

interface LocationState {
  transactionUuid: string
  amount: number
  merchantName: string
  balance: number
}

const PIN_LENGTH = 6

// 패드 레이아웃: null = 빈 칸
const PIN_PAD_ROWS = [
  [
    { d: '1', s: '' },
    { d: '2', s: 'ABC' },
    { d: '3', s: 'DEF' },
  ],
  [
    { d: '4', s: 'GHI' },
    { d: '5', s: 'JKL' },
    { d: '6', s: 'MNO' },
  ],
  [
    { d: '7', s: 'PQRS' },
    { d: '8', s: 'TUV' },
    { d: '9', s: 'WXYZ' },
  ],
] as const

function BackspaceIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z" />
      <line x1="18" y1="9" x2="12" y2="15" />
      <line x1="12" y1="9" x2="18" y2="15" />
    </svg>
  )
}

export function PayPinPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null

  const [pin, setPin] = useState('')

  // 6자리 입력 완료 시 처리 화면으로 이동
  useEffect(() => {
    if (pin.length === PIN_LENGTH && state) {
      navigate('/pay/processing', {
        state: {
          transactionUuid: state.transactionUuid,
          pin,
          amount: state.amount,
          merchantName: state.merchantName,
        },
      })
    }
  }, [pin, navigate, state])

  function handleDigit(d: string) {
    setPin((p) => (p.length < PIN_LENGTH ? p + d : p))
  }

  function handleBackspace() {
    setPin((p) => p.slice(0, -1))
  }

  return (
    <div className="flex h-dvh flex-col bg-white">
      {/* 헤더 */}
      <header className="flex items-center justify-between px-5 pt-14 pb-2">
        <h1 className="text-base font-bold text-foreground">PIN번호 입력</h1>
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="text-sm font-medium text-muted-foreground"
        >
          취소
        </button>
      </header>

      {/* 안내 문구 + 도트 */}
      <div className="flex flex-1 flex-col items-center justify-center gap-8 px-5">
        <div className="flex flex-col items-center gap-3 text-center">
          <p className="text-[22px] font-bold text-foreground">PIN번호를 입력해주세요</p>
          <p className="text-[13px] text-muted-foreground">
            안전한 서비스 이용을 위해 PIN번호를 입력해주세요.
          </p>
        </div>

        {/* PIN 도트 */}
        <div className="flex gap-4">
          {Array.from({ length: PIN_LENGTH }, (_, i) => (
            <div
              key={i}
              className={`h-4 w-4 rounded-full border-2 transition-colors ${
                i < pin.length
                  ? 'border-foreground bg-foreground'
                  : 'border-muted-foreground/40 bg-transparent'
              }`}
            />
          ))}
        </div>

        <button
          type="button"
          className="text-sm font-medium text-primary"
          onClick={() => {/* TODO: PIN 재설정 플로우 */}}
        >
          PIN번호를 잊으셨나요?
        </button>

        {import.meta.env.DEV && (
          <button
            type="button"
            onClick={() =>
              navigate('/pay/processing', {
                state: {
                  transactionUuid: state?.transactionUuid ?? 'dev-uuid-1234',
                  pin: '000000',
                  amount: state?.amount ?? 6500,
                  merchantName: state?.merchantName ?? '[DEV] 카페 드롭탑 강남점',
                },
              })
            }
            className="rounded-full bg-blue-600 px-8 py-2 text-sm font-bold text-white"
          >
            [DEV] 결제 처리로 스킵
          </button>
        )}
      </div>

      {/* 전화기식 키패드 */}
      <div className="border-t border-border/60">
        {/* 1~9 */}
        {PIN_PAD_ROWS.map((row) => (
          <div key={row[0].d} className="grid grid-cols-3">
            {row.map(({ d, s }) => (
              <button
                key={d}
                type="button"
                onClick={() => handleDigit(d)}
                className="flex h-16 flex-col items-center justify-center gap-0.5 border-b border-r border-border/60 active:bg-muted"
              >
                <span className="text-xl font-semibold text-foreground">{d}</span>
                {s && <span className="text-[10px] tracking-widest text-muted-foreground">{s}</span>}
              </button>
            ))}
          </div>
        ))}
        {/* 빈칸 / 0 / ⌫ */}
        <div className="grid grid-cols-3">
          <div className="h-16 border-b border-r border-border/60" />
          <button
            type="button"
            onClick={() => handleDigit('0')}
            className="flex h-16 items-center justify-center border-b border-r border-border/60 text-xl font-semibold text-foreground active:bg-muted"
          >
            0
          </button>
          <button
            type="button"
            onClick={handleBackspace}
            className="flex h-16 items-center justify-center border-b border-r border-border/60 text-foreground active:bg-muted"
            aria-label="지우기"
          >
            <BackspaceIcon />
          </button>
        </div>
      </div>
    </div>
  )
}
