import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AppShell, BackTitleHeader, Button, CheckboxGroup } from '@/components/common'

const TERMS_ITEMS = [
  { id: 'serviceTerms', label: '[필수] 서비스 이용약관', required: true },
  { id: 'privacyTerms', label: '[필수] 개인정보 수집 및 이용 동의', required: true },
  { id: 'electronicFinanceTerms', label: '[필수] 전자금융거래 이용약관', required: true },
  { id: 'localCurrencyTerms', label: '[필수] 위치정보 이용약관', required: true },
  { id: 'marketingTerms', label: '[선택] 마케팅 정보 수신 동의', required: false },
]

const REQUIRED_IDS = TERMS_ITEMS.filter((t) => t.required).map((t) => t.id)

export function RegisterTermsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const isMerchant = location.pathname.startsWith('/merchant/')
  const [checkedIds, setCheckedIds] = useState<string[]>([])

  const allRequiredChecked = REQUIRED_IDS.every((id) => checkedIds.includes(id))

  function handleNext() {
    if (!allRequiredChecked) return
    const nextPath = isMerchant ? '/merchant/register/verify' : '/register/verify'
    navigate(nextPath, {
      state: {
        termsAgreed: {
          serviceTerms: checkedIds.includes('serviceTerms'),
          privacyTerms: checkedIds.includes('privacyTerms'),
          electronicFinanceTerms: checkedIds.includes('electronicFinanceTerms'),
          localCurrencyTerms: checkedIds.includes('localCurrencyTerms'),
        },
      },
    })
  }

  return (
    <AppShell>
      <div className="flex h-full flex-col">
        <BackTitleHeader title="약관 동의" onBack={() => navigate('/')} />

        <div className="flex-1 overflow-y-auto py-4">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-foreground">서비스 이용에 동의해주세요</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {isMerchant
                ? '가맹점 회원가입을 위해 아래 약관에 동의해주세요.'
                : '사용자 회원가입을 위해 아래 약관에 동의해주세요.'}
            </p>
          </div>

          <CheckboxGroup items={TERMS_ITEMS} checkedIds={checkedIds} onChange={setCheckedIds} />
        </div>

        <div className="pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button size="lg" disabled={!allRequiredChecked} onClick={handleNext}>
            다음
          </Button>
        </div>
      </div>
    </AppShell>
  )
}
