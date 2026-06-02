import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { loginMerchant, loginUser } from '@/api/auth'
import { ApiError } from '@/api/client'
import { AppShell, BackTitleHeader, Button, TextField } from '@/components/common'
import { cn } from '@/lib/utils'

type Tab = 'user' | 'merchant'

export function LoginPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [tab, setTab] = useState<Tab>('user')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [businessNumber, setBusinessNumber] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')

  const mutation = useMutation({
    mutationFn: () =>
      tab === 'user' ? loginUser(phoneNumber, password) : loginMerchant(businessNumber, password),
    onSuccess: (data) => {
      localStorage.setItem('role', data.role)
      queryClient.setQueryData(['currentUser'], {
        id: data.principalId,
        name: '',
        role: data.role,
      })
      navigate(data.role === 'MERCHANT' ? '/merchant/home' : '/home', { replace: true })
    },
    onError: (err) => {
      setErrorMessage(err instanceof ApiError ? err.message : '로그인에 실패했습니다.')
    },
  })

  function handleSubmit() {
    setErrorMessage('')
    mutation.mutate()
  }

  function handleTabChange(next: Tab) {
    setTab(next)
    setErrorMessage('')
  }

  return (
    <AppShell>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          handleSubmit()
        }}
        className="flex h-full flex-col"
      >
        <BackTitleHeader title="로그인" onBack={() => navigate('/')} />

        <div className="flex-1 space-y-6 overflow-y-auto py-2">
          <div className="flex rounded-xl bg-muted p-1">
            {(['user', 'merchant'] as const).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => handleTabChange(t)}
                className={cn(
                  'flex-1 rounded-lg py-2 text-sm font-semibold transition-colors',
                  tab === t ? 'bg-card text-primary shadow-sm' : 'text-muted-foreground'
                )}
              >
                {t === 'user' ? '사용자' : '가맹점'}
              </button>
            ))}
          </div>

          <div className="space-y-4">
            {tab === 'user' ? (
              <div className="space-y-1">
                <TextField
                  label="휴대폰 번호"
                  value={phoneNumber}
                  onChange={setPhoneNumber}
                  type="tel"
                  placeholder="01012345678"
                />
                <p className="text-xs text-muted-foreground">'-' 없이 숫자만 입력해주세요</p>
              </div>
            ) : (
              <div className="space-y-1">
                <TextField
                  label="사업자번호"
                  value={businessNumber}
                  onChange={setBusinessNumber}
                  type="tel"
                  placeholder="1234567890"
                />
                <p className="text-xs text-muted-foreground">'-' 없이 숫자만 입력해주세요</p>
              </div>
            )}

            <TextField
              label="비밀번호"
              value={password}
              onChange={setPassword}
              type="password"
              placeholder="비밀번호를 입력하세요"
            />
          </div>

          <p className="text-center text-sm text-muted-foreground">
            <span>비밀번호 찾기</span>
            <span className="mx-2 text-border">|</span>
            <button
              type="button"
              onClick={() =>
                navigate(tab == 'user' ? '/register/terms' : '/merchant/register/terms')
              }
              className="font-medium text-foreground"
            >
              회원가입
            </button>
          </p>
        </div>

        <div className="pt-4">
          {errorMessage ? (
            <p className="mb-3 text-center text-sm font-medium text-destructive">{errorMessage}</p>
          ) : null}
          <Button type="submit" size="lg" disabled={mutation.isPending}>
            {mutation.isPending ? '로그인 중...' : '로그인'}
          </Button>
        </div>
      </form>
    </AppShell>
  )
}
