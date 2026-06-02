import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AppShell, BackTitleHeader, Button, TextField } from '@/components/common'

const PASSWORD_RULES = [
  { label: '영문 포함', test: (pw: string) => /[a-zA-Z]/.test(pw) },
  { label: '숫자 포함', test: (pw: string) => /\d/.test(pw) },
  { label: '특수문자 포함 (!@#$%^&*)', test: (pw: string) => /[!@#$%^&*]/.test(pw) },
  { label: '8자 이상', test: (pw: string) => pw.length >= 8 },
]

export function RegisterPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const isMerchant = location.pathname.startsWith('/merchant/')
  const steps = isMerchant ? 8 : 7
  const state = location.state as Record<string, unknown> | null

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')

  const allRulesMet = PASSWORD_RULES.every((r) => r.test(password))
  const confirmError =
    confirm.length > 0 && password !== confirm ? '비밀번호가 일치하지 않습니다.' : ''
  const canNext = allRulesMet && confirm.length > 0 && confirmError === ''

  function handleNext() {
    const nextPath = isMerchant ? '/merchant/register/business' : '/register/account'
    navigate(nextPath, { state: { ...state, password } })
  }

  return (
    <AppShell>
      <div className="flex h-full flex-col">
        <BackTitleHeader
          title={isMerchant ? '가맹점 회원가입' : '사용자 회원가입'}
          onBack={() =>
            navigate(isMerchant ? '/merchant/register/verify' : '/register/verify', { state })
          }
        />

        <div className="mb-4 flex gap-1">
          {Array.from({ length: steps }).map((_, i) => (
            <div
              key={i}
              className={`h-1 flex-1 rounded-full ${i < 3 ? 'bg-primary' : 'bg-muted'}`}
            />
          ))}
        </div>

        <div className="flex-1 overflow-y-auto">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-foreground">비밀번호 설정</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              서비스 이용에 사용할 비밀번호를 설정해주세요.
            </p>
          </div>

          <div className="space-y-4">
            <TextField
              label="비밀번호"
              value={password}
              onChange={setPassword}
              type="password"
              placeholder="영문, 숫자, 특수문자 포함 8자 이상"
            />

            <TextField
              label="비밀번호 확인"
              value={confirm}
              onChange={setConfirm}
              type="password"
              placeholder="비밀번호를 다시 입력하세요"
              error={confirmError || undefined}
            />

            <div className="rounded-xl bg-muted px-4 py-3">
              <p className="mb-2 text-sm font-semibold text-foreground">비밀번호 규칙</p>
              <ul className="space-y-1">
                {PASSWORD_RULES.map((rule) => (
                  <li
                    key={rule.label}
                    className={`flex items-center gap-2 text-sm ${password.length > 0 && rule.test(password) ? 'text-primary' : 'text-muted-foreground'}`}
                  >
                    <span className="inline-block h-1.5 w-1.5 rounded-full bg-current" />
                    {rule.label}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        <div className="pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button size="lg" disabled={!canNext} onClick={handleNext}>
            다음
          </Button>
        </div>
      </div>
    </AppShell>
  )
}
