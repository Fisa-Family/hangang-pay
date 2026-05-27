import type { FormEvent } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppShell, Button, PageHeader, PinCodeInput, SelectField, TextField } from '@/components/common'

const BANK_OPTIONS = [
  { label: '우리은행', value: '우리은행' },
  { label: '국민은행', value: '국민은행' },
  { label: '신한은행', value: '신한은행' },
  { label: '하나은행', value: '하나은행' },
  { label: '농협은행', value: '농협은행' },
  { label: '카카오뱅크', value: '카카오뱅크' },
  { label: '토스뱅크', value: '토스뱅크' },
]

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

function ChevronDownIcon() {
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
      <path d="m6 9 6 6 6-6" />
    </svg>
  )
}

function onlyDigits(value: string) {
  return value.replace(/\D/g, '')
}

export function AddAccountPage({ onBack, onRequestVerification, onSubmit }: AddAccountPageProps) {
  const navigate = useNavigate()
  const [selectedBank, setSelectedBank] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [verificationCode, setVerificationCode] = useState('')

  const canRequestVerification = selectedBank.length > 0 && accountNumber.length > 0
  const canSubmit = canRequestVerification && verificationCode.length === 3

  const handleBack = () => {
    if (onBack) {
      onBack()
      return
    }

    navigate(-1)
  }

  const handleAccountNumberChange = (value: string) => {
    setAccountNumber(onlyDigits(value))
  }

  const handleVerificationCodeChange = (value: string) => {
    setVerificationCode(onlyDigits(value).slice(0, 3))
  }

  const handleRequestVerification = () => {
    if (!canRequestVerification) return

    onRequestVerification?.({
      bank: selectedBank,
      accountNumber,
    })
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSubmit) return

    onSubmit?.({
      bank: selectedBank,
      accountNumber,
      verificationCode,
    })
  }

  return (
    <AppShell className="bg-card">
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
              onChange={setSelectedBank}
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
            <div className="mt-2">
              <p className={SUPPORTING_TEXT_CLASS}>
                입력한 계좌번호가 본인 명의인지 1원 인증을 통해 확인합니다.
              </p>
            </div>
          </section>

          <section className="py-6" aria-labelledby="one-won-verification-title">
            <h2 id="one-won-verification-title" className={SECTION_TITLE_CLASS}>
              1원 인증
            </h2>
            <p className={SUPPORTING_TEXT_CLASS}>
              입력하신 계좌로 1원을 보내드려요.
              <br />
              입금자명에 표시된 숫자 3자리를 입력해주세요.
            </p>

            <Button
              variant="ghost"
              size="lg"
              onClick={handleRequestVerification}
              disabled={!canRequestVerification}
              className="mt-4 gap-2 border border-primary/60 bg-card text-primary shadow-sm shadow-primary/20 hover:bg-primary/5 hover:text-primary"
            >
              1원 인증 요청
            </Button>

            <div className="flex justify-center py-3 text-muted-foreground">
              <ChevronDownIcon />
            </div>

            <div>
              <p className={SECTION_TITLE_CLASS}>인증번호 입력</p>
              <PinCodeInput
                value={verificationCode}
                length={3}
                onChange={handleVerificationCodeChange}
              />
            </div>
          </section>
        </div>

        <footer className="shrink-0 bg-card pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button type="submit" size="lg" disabled={!canSubmit}>
            확인
          </Button>
        </footer>
      </form>
    </AppShell>
  )
}
