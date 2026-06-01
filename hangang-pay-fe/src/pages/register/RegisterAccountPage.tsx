import type { FormEvent } from 'react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import {
  AppShell,
  BackTitleHeader,
  Button,
  PinCodeInput,
  SelectField,
  TextField,
  Toast,
  type ToastState,
} from '@/components/common'
import { requestAccountVerification, verifyAccount } from '@/api/accounts'
import { ApiError } from '@/api/client'

const BANK_OPTIONS = [
  { label: '우리은행', value: 'WR', institutionId: 2 },
  { label: '신한은행', value: 'SH', institutionId: 3 },
  { label: '하나은행', value: 'HN', institutionId: 4 },
]

const VERIFICATION_CODE_LENGTH = 6

function onlyDigits(value: string) {
  return value.replace(/\D/g, '')
}

function buildErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '요청에 실패했습니다. 네트워크 연결을 확인해 주세요.'
}

export function RegisterAccountPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const isMerchant = location.pathname.startsWith('/merchant/')
  const steps = isMerchant ? 8 : 7
  const stepFilled = isMerchant ? 5 : 4
  const state = location.state as Record<string, unknown> | null

  const [selectedBank, setSelectedBank] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [verificationRequested, setVerificationRequested] = useState(false)
  const [toast, setToast] = useState<ToastState | null>(
    state?.error ? { message: state.error as string, variant: 'error' } : null
  )

  const selectedBankOption = BANK_OPTIONS.find((o) => o.value === selectedBank)

  const canRequestVerification = selectedBank.length > 0 && accountNumber.length > 0
  const canSubmit =
    canRequestVerification &&
    verificationRequested &&
    verificationCode.length === VERIFICATION_CODE_LENGTH

  const requestMutation = useMutation({
    mutationFn: () =>
      requestAccountVerification({
        institutionId: selectedBankOption!.institutionId,
        accountNumber,
      }),
    onSuccess: (res) => {
      setVerificationRequested(true)
      setVerificationCode(res.code)
      setToast({
        message: `인증번호가 발송되었습니다. 테스트 코드: ${res.code}`,
        variant: 'success',
      })
    },
    onError: (err) => {
      setVerificationRequested(false)
      setToast({ message: buildErrorMessage(err), variant: 'error' })
    },
  })

  const verifyMutation = useMutation({
    mutationFn: () =>
      verifyAccount({
        institutionId: selectedBankOption!.institutionId,
        accountNumber,
        code: verificationCode,
      }),
    onSuccess: () => {
      const nextPath = isMerchant ? '/merchant/register/pin' : '/register/pin'
      navigate(nextPath, {
        state: {
          ...state,
          institutionId: selectedBankOption!.institutionId,
          accountNumber,
        },
      })
    },
    onError: (err) => {
      setToast({ message: buildErrorMessage(err), variant: 'error' })
    },
  })

  const isSubmitting = requestMutation.isPending || verifyMutation.isPending

  function resetVerification() {
    setVerificationCode('')
    setVerificationRequested(false)
  }

  function handleBankChange(value: string) {
    setSelectedBank(value)
    resetVerification()
  }

  function handleAccountNumberChange(value: string) {
    setAccountNumber(onlyDigits(value))
    resetVerification()
  }

  function handleRequestVerification() {
    if (!canRequestVerification || !selectedBankOption) return
    setVerificationCode('')
    setVerificationRequested(false)
    requestMutation.mutate()
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!canSubmit) return
    verifyMutation.mutate()
  }

  return (
    <AppShell className="bg-card">
      <form className="flex h-full flex-col" onSubmit={handleSubmit}>
        <BackTitleHeader
          title={isMerchant ? '가맹점 회원가입' : '사용자 회원가입'}
          onBack={() =>
            navigate(isMerchant ? '/merchant/register/business' : '/register/password', { state })
          }
        />

        <div className="mb-4 flex gap-1">
          {Array.from({ length: steps }).map((_, i) => (
            <div
              key={i}
              className={`h-1 flex-1 rounded-full ${i < stepFilled ? 'bg-primary' : 'bg-muted'}`}
            />
          ))}
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-foreground">계좌 입력 및 1원 인증</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {isMerchant
                ? '정산 계좌를 입력하고 1원 인증을 완료해주세요.'
                : '충전/환불에 사용할 계좌를 입력하고 1원 인증을 완료해주세요.'}
            </p>
          </div>

          <div className="space-y-6">
            <section>
              <h3 className="mb-3 text-base font-bold text-foreground">은행 선택</h3>
              <SelectField
                label="은행 선택"
                value={selectedBank}
                options={BANK_OPTIONS}
                onChange={handleBankChange}
                placeholder="은행을 선택해주세요"
                className="space-y-0 [&>span:first-child]:sr-only"
              />
            </section>

            <section>
              <h3 className="mb-3 text-base font-bold text-foreground">계좌 정보 입력</h3>
              <TextField
                label="계좌번호"
                value={accountNumber}
                onChange={handleAccountNumberChange}
                type="tel"
                placeholder="'-' 없이 숫자만 입력해주세요"
                className="space-y-0 [&>span:first-child]:sr-only"
              />
            </section>

            <section>
              <h3 className="mb-3 text-base font-bold text-foreground">1원 인증</h3>
              <p className="mb-3 text-xs text-muted-foreground">
                입력하신 계좌로 1원을 보내드려요.
                <br />
                입금자명에 표시된 숫자 6자리를 입력해주세요.
              </p>
              <Button
                type="button"
                variant="ghost"
                size="lg"
                onClick={handleRequestVerification}
                disabled={!canRequestVerification || isSubmitting}
                className="gap-2 border border-primary/60 bg-card text-primary shadow-sm shadow-primary/20 hover:bg-primary/5 hover:text-primary"
              >
                {requestMutation.isPending
                  ? '요청 중'
                  : verificationRequested
                    ? '재발송'
                    : '1원 인증 요청'}
              </Button>

              <div className="mt-4">
                <p className="mb-2 text-base font-bold text-foreground">인증번호 입력</p>
                <PinCodeInput
                  value={verificationCode}
                  length={VERIFICATION_CODE_LENGTH}
                  onChange={(v) =>
                    setVerificationCode(onlyDigits(v).slice(0, VERIFICATION_CODE_LENGTH))
                  }
                  disabled={!verificationRequested || verifyMutation.isPending}
                />
              </div>
            </section>
          </div>
        </div>

        <div className="shrink-0 pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button type="submit" size="lg" disabled={!canSubmit || isSubmitting}>
            {verifyMutation.isPending ? '확인 중' : '다음'}
          </Button>
        </div>
      </form>

      <Toast open={toast !== null} message={toast?.message ?? ''} variant={toast?.variant} />
    </AppShell>
  )
}
