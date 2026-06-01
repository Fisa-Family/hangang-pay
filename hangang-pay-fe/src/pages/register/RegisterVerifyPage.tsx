import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { sendSms, verifySms } from '@/api/auth'
import { ApiError } from '@/api/client'
import {
  AppShell,
  BackTitleHeader,
  Button,
  TextField,
  Toast,
  type ToastState,
} from '@/components/common'

const CODE_LENGTH = 6

interface LocationState {
  termsAgreed: {
    serviceTerms: boolean
    privacyTerms: boolean
    electronicFinanceTerms: boolean
    localCurrencyTerms: boolean
  }
}

function onlyDigits(value: string) {
  return value.replace(/\D/g, '')
}

function getBirthDateError(value: string): string | undefined {
  if (value.length < 8) return '생년월일 8자리를 입력해주세요'
  const month = parseInt(value.slice(4, 6), 10)
  const day = parseInt(value.slice(6, 8), 10)
  if (month < 1 || month > 12) return '올바른 월을 입력해주세요 (01-12)'
  if (day < 1 || day > 31) return '올바른 일을 입력해주세요 (01-31)'
  return undefined
}

export function RegisterVerifyPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const isMerchant = location.pathname.startsWith('/merchant/')
  const steps = isMerchant ? 8 : 7
  const state = location.state as LocationState | null

  const [name, setName] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [toast, setToast] = useState<ToastState | null>(null)

  const sendMutation = useMutation({
    mutationFn: () => sendSms(phoneNumber),
    onSuccess: (data) => {
      setCodeSent(true)
      setCode(data.code)
      setErrorMessage('')
      setToast({
        message: `인증번호가 발송되었습니다. 테스트 코드: ${data.code}`,
        variant: 'success',
      })
    },
    onError: (err) => {
      setErrorMessage(err instanceof ApiError ? err.message : 'SMS 발송에 실패했습니다.')
    },
  })

  const verifyMutation = useMutation({
    mutationFn: () => verifySms(phoneNumber, code),
    onSuccess: () => {
      const nextPath = isMerchant ? '/merchant/register/password' : '/register/password'
      navigate(nextPath, {
        state: {
          ...state,
          name,
          birthDate,
          phoneNumber,
        },
      })
    },
    onError: (err) => {
      setErrorMessage(err instanceof ApiError ? err.message : '인증에 실패했습니다.')
      setCode('')
    },
  })

  const canSend =
    name.trim().length > 0 && getBirthDateError(birthDate) === undefined && phoneNumber.length >= 10
  const canVerify = codeSent && code.length === CODE_LENGTH
  const isSubmitting = sendMutation.isPending || verifyMutation.isPending

  function handleSend() {
    setErrorMessage('')
    sendMutation.mutate()
  }

  function handleVerify() {
    if (!canVerify) return
    setErrorMessage('')
    verifyMutation.mutate()
  }

  function handleBirthDateChange(value: string) {
    setBirthDate(onlyDigits(value).slice(0, 8))
  }

  function handlePhoneChange(value: string) {
    setPhoneNumber(onlyDigits(value))
    setCodeSent(false)
    setCode('')
    setErrorMessage('')
  }

  return (
    <AppShell>
      <div className="flex h-full flex-col">
        <BackTitleHeader
          title={isMerchant ? '가맹점 회원가입' : '사용자 회원가입'}
          onBack={() =>
            navigate(isMerchant ? '/merchant/register/terms' : '/register/terms', { state })
          }
        />

        <div className="mb-4 flex gap-1">
          {Array.from({ length: steps }).map((_, i) => (
            <div
              key={i}
              className={`h-1 flex-1 rounded-full ${i < 2 ? 'bg-primary' : 'bg-muted'}`}
            />
          ))}
        </div>

        <div className="flex-1 overflow-y-auto">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-foreground">휴대폰 본인 인증</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              실명과 휴대폰 번호를 입력하고 인증을 완료해주세요.
            </p>
          </div>

          <div className="space-y-4">
            <TextField
              label="이름"
              value={name}
              onChange={setName}
              placeholder="실명을 입력하세요"
            />

            <TextField
              label="생년월일"
              value={birthDate}
              onChange={handleBirthDateChange}
              type="tel"
              placeholder="예: 19900101"
              error={birthDate.length > 0 ? getBirthDateError(birthDate) : undefined}
            />

            <div>
              <span className="mb-1.5 block text-sm font-semibold text-foreground">
                휴대폰 번호
              </span>
              <div className="flex gap-2">
                <TextField
                  label="휴대폰 번호"
                  value={phoneNumber}
                  onChange={handlePhoneChange}
                  type="tel"
                  placeholder="01012345678"
                  className="flex-1 space-y-0 [&>span:first-child]:sr-only"
                />
                <Button
                  type="button"
                  variant="secondary"
                  onClick={handleSend}
                  disabled={!canSend || isSubmitting}
                  className="h-12 w-auto shrink-0 whitespace-nowrap self-start mt-0 px-4"
                >
                  {sendMutation.isPending ? '발송 중' : codeSent ? '재발송' : '인증번호 발송'}
                </Button>
              </div>
              {phoneNumber.length > 0 && phoneNumber.length < 10 && (
                <p className="mt-1 text-sm font-medium text-destructive">
                  올바른 휴대폰 번호를 입력해주세요
                </p>
              )}
            </div>

            {codeSent && (
              <div>
                <span className="mb-1.5 block text-sm font-semibold text-foreground">인증번호</span>
                <TextField
                  label="인증번호"
                  value={code}
                  onChange={(v) => setCode(onlyDigits(v).slice(0, CODE_LENGTH))}
                  type="tel"
                  placeholder="6자리 숫자"
                  className="space-y-0 [&>span:first-child]:sr-only"
                />
                <p className="mt-1 text-xs text-muted-foreground">인증번호 발송 후 입력해주세요</p>
              </div>
            )}

            {errorMessage && <p className="text-sm font-medium text-destructive">{errorMessage}</p>}
          </div>
        </div>

        <div className="pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button size="lg" disabled={!canVerify || isSubmitting} onClick={handleVerify}>
            {verifyMutation.isPending ? '인증 중...' : '확인'}
          </Button>
        </div>
      </div>
      <Toast open={toast !== null} message={toast?.message ?? ''} variant={toast?.variant} />
    </AppShell>
  )
}
