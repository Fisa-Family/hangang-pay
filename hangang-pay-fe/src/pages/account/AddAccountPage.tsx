import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  AppShell,
  Button,
  PageHeader,
  PinCodeInput,
  SelectField,
  TextField,
  Toast,
  type ToastState,
} from '@/components/common'
import { addAccount, requestAccountVerification, verifyAccount } from '@/api/accounts'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import { useCurrentUser } from '@/auth/useCurrentUser'

const BANK_OPTIONS = [
  { label: '우리은행', value: 'WR', institutionId: 2 },
  { label: '신한은행', value: 'SH', institutionId: 3 },
  { label: '하나은행', value: 'HN', institutionId: 4 },
]

const VERIFICATION_CODE_LENGTH = 6
const SECTION_TITLE_CLASS = 'mb-3 text-base font-bold text-foreground'
const HIDDEN_FIELD_LABEL_CLASS = 'space-y-0 [&>span:first-child]:sr-only'
const SUPPORTING_TEXT_CLASS = 'text-xs font-medium leading-5 text-muted-foreground'

interface VerificationRequestPayload {
  bank: string
  accountNumber: string
}

interface AddAccountSubmitPayload extends VerificationRequestPayload {
  verificationCode: string
}

interface AddAccountPageProps {
  onBack?: () => void
  onRequestVerification?: (payload: VerificationRequestPayload) => void
  onSubmit?: (payload: AddAccountSubmitPayload) => void
}

function buildErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }

  if (error instanceof Error) return error.message

  return '요청에 실패했습니다. 네트워크 연결을 확인해 주세요.'
}

import { ChevronDown as ChevronDownIcon } from 'lucide-react'

function onlyDigits(value: string) {
  return value.replace(/\D/g, '')
}

export function AddAccountPage({ onBack, onRequestVerification, onSubmit }: AddAccountPageProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { isAuthenticated, isLoading: isAuthLoading } = useCurrentUser()
  const [selectedBank, setSelectedBank] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [verificationRequested, setVerificationRequested] = useState(false)
  const [toast, setToast] = useState<ToastState | null>(null)

  const selectedBankOption = BANK_OPTIONS.find((option) => option.value === selectedBank)

  const canRequestVerification =
    isAuthenticated && !isAuthLoading && selectedBank.length > 0 && accountNumber.length > 0
  const canSubmit =
    canRequestVerification &&
    verificationRequested &&
    verificationCode.length === VERIFICATION_CODE_LENGTH

  const requestVerificationMutation = useMutation({
    mutationFn: requestAccountVerification,
    onSuccess: (response) => {
      setVerificationRequested(true)
      setToast({
        message: `인증번호가 발송되었습니다. 테스트 코드: ${response.code}`,
        variant: 'success',
      })
    },
    onError: (error) => {
      setVerificationRequested(false)
      setToast({ message: buildErrorMessage(error), variant: 'error' })
    },
  })

  const addAccountMutation = useMutation({
    mutationFn: async () => {
      if (!selectedBankOption) throw new Error('은행을 선택해주세요.')

      try {
        await verifyAccount({
          institutionId: selectedBankOption.institutionId,
          accountNumber,
          code: verificationCode,
        })
      } catch (error) {
        throw new Error(`인증 확인 실패: ${buildErrorMessage(error)}`, {
          cause: error,
        })
      }

      try {
        return await addAccount({
          institutionCode: selectedBankOption.value,
          accountNumber,
        })
      } catch (error) {
        throw new Error(`계좌 등록 실패: ${buildErrorMessage(error)}`, {
          cause: error,
        })
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      navigate('/mypage/accounts', { replace: true })
    },
    onError: (error) => {
      setToast({ message: buildErrorMessage(error), variant: 'error' })
    },
  })

  const isSubmitting =
    isAuthLoading || requestVerificationMutation.isPending || addAccountMutation.isPending

  useEffect(() => {
    if (!toast) return

    const timeoutId = window.setTimeout(() => {
      setToast(null)
    }, 3000)

    return () => window.clearTimeout(timeoutId)
  }, [toast])

  const resetVerification = () => {
    setVerificationCode('')
    setVerificationRequested(false)
    setToast(null)
  }

  const handleBack = () => {
    if (onBack) {
      onBack()
      return
    }

    navigate(-1)
  }

  const handleAccountNumberChange = (value: string) => {
    setAccountNumber(onlyDigits(value))
    resetVerification()
  }

  const handleVerificationCodeChange = (value: string) => {
    setVerificationCode(onlyDigits(value).slice(0, VERIFICATION_CODE_LENGTH))
  }

  const handleBankChange = (value: string) => {
    setSelectedBank(value)
    resetVerification()
  }

  const handleRequestVerification = () => {
    if (!isAuthLoading && !isAuthenticated) {
      setToast({ message: '로그인 세션을 만들지 못했습니다.', variant: 'error' })
      return
    }

    if (!canRequestVerification || !selectedBankOption) return

    setVerificationCode('')
    setVerificationRequested(false)

    onRequestVerification?.({
      bank: selectedBankOption.label,
      accountNumber,
    })

    requestVerificationMutation.mutate({
      institutionId: selectedBankOption.institutionId,
      accountNumber,
    })
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSubmit) return

    onSubmit?.({
      bank: selectedBankOption?.label ?? selectedBank,
      accountNumber,
      verificationCode,
    })

    addAccountMutation.mutate()
  }

  return (
    <AppShell>
      <form className="flex min-h-0 flex-1 flex-col" onSubmit={handleSubmit}>
        <PageHeader title="계좌 추가" onBack={handleBack} />

        <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden">
          <section className="py-5" aria-labelledby="bank-selection-title">
            <h2 id="bank-selection-title" className={SECTION_TITLE_CLASS}>
              은행 선택
            </h2>
            <SelectField
              label="은행 선택"
              value={selectedBank}
              options={BANK_OPTIONS}
              onChange={handleBankChange}
              placeholder="은행을 선택해주세요"
              className={HIDDEN_FIELD_LABEL_CLASS}
            />
          </section>

          <section className="py-6" aria-labelledby="account-info-title">
            <h2 id="account-info-title" className={SECTION_TITLE_CLASS}>
              계좌 정보 입력
            </h2>
            <TextField
              label="계좌번호"
              value={accountNumber}
              onChange={handleAccountNumberChange}
              type="tel"
              placeholder="'-' 없이 숫자만 입력해주세요"
              className={HIDDEN_FIELD_LABEL_CLASS}
            />
          </section>

          <section className="py-6" aria-labelledby="one-won-verification-title">
            <h2 id="one-won-verification-title" className={SECTION_TITLE_CLASS}>
              1원 인증
            </h2>
            <p className={SUPPORTING_TEXT_CLASS}>
              입력하신 계좌로 1원을 보내드려요.
              <br />
              입금자명에 표시된 숫자 6자리를 입력해주세요.
            </p>

            <Button
              variant="ghost"
              size="lg"
              onClick={handleRequestVerification}
              disabled={!canRequestVerification || isSubmitting}
              className="mt-4 gap-2 border border-primary/60 bg-card text-primary shadow-sm shadow-primary/20 hover:bg-primary/5 hover:text-primary"
            >
              {isAuthLoading
                ? '준비 중'
                : !isAuthenticated
                  ? '로그인 필요'
                  : requestVerificationMutation.isPending
                    ? '요청 중'
                    : '1원 인증 요청'}
            </Button>

            <div className="flex justify-center py-3 text-muted-foreground">
              <ChevronDownIcon />
            </div>

            <div>
              <p className={SECTION_TITLE_CLASS}>인증번호 입력</p>
              <PinCodeInput
                value={verificationCode}
                length={VERIFICATION_CODE_LENGTH}
                onChange={handleVerificationCodeChange}
                disabled={!verificationRequested || addAccountMutation.isPending}
              />
            </div>
          </section>
        </div>

        <footer className="shrink-0 pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button type="submit" size="lg" disabled={!canSubmit || isSubmitting}>
            {addAccountMutation.isPending ? '등록 중' : '확인'}
          </Button>
        </footer>
      </form>

      <Toast
        open={toast !== null}
        message={toast?.message ?? ''}
        variant={toast?.variant}
        actionLabel={toast?.variant === 'error' ? '다시 시도' : undefined}
        onAction={toast?.variant === 'error' ? handleRequestVerification : undefined}
      />
    </AppShell>
  )
}
