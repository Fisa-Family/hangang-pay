import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AccountRow, AppShell, Button, PageHeader } from '@/components/common'

interface RegisteredAccount {
  id: string
  bankName: string
  maskedAccountNumber: string
  primary: boolean
}

const SAMPLE_ACCOUNTS: RegisteredAccount[] = [
  {
    id: 'woori-1002',
    bankName: '우리은행',
    maskedAccountNumber: '1002-9764-1234',
    primary: true,
  },
  {
    id: 'kb-1234',
    bankName: '국민은행',
    maskedAccountNumber: '1234-0865-5678',
    primary: false,
  },
]

const SUPPORTING_TEXT_CLASS = 'text-xs font-medium leading-5 text-muted-foreground'

function PlusIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      aria-hidden
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

export function AccountManagementPage() {
  const navigate = useNavigate()
  const [accounts, setAccounts] = useState(SAMPLE_ACCOUNTS)

  const handleBack = () => {
    navigate(-1)
  }

  const handleDelete = (accountId: string) => {
    setAccounts((currentAccounts) => currentAccounts.filter((account) => account.id !== accountId))
  }

  const handleSetPrimary = (accountId: string) => {
    setAccounts((currentAccounts) =>
      currentAccounts.map((account) => ({
        ...account,
        primary: account.id === accountId,
      }))
    )
  }

  const handleAddAccount = () => {
    navigate('/mypage/accounts/add')
  }

  return (
    <AppShell className="bg-surface">
      <div className="flex min-h-0 flex-1 flex-col">
        <PageHeader title="계좌 관리" onBack={handleBack} />

        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto pt-3">
          <section aria-label="등록 계좌 목록" className="flex flex-col gap-2.5">
            {accounts.map((account) => (
              <AccountRow
                key={account.id}
                mode="manage"
                bankName={account.bankName}
                maskedAccountNumber={account.maskedAccountNumber}
                primary={account.primary}
                onSetPrimary={() => handleSetPrimary(account.id)}
                onDelete={() => handleDelete(account.id)}
              />
            ))}
          </section>

          <div className="mt-4">
            <Button
              variant="ghost"
              onClick={handleAddAccount}
              className="gap-2 border border-input bg-card text-foreground hover:bg-muted hover:text-foreground"
            >
              <PlusIcon />
              계좌 추가
            </Button>
          </div>

          <aside
            className={`mt-3 flex justify-center rounded-lg px-4 py-3 text-center ${SUPPORTING_TEXT_CLASS}`}
          >
            <p>계좌는 최대 3개까지 등록할 수 있어요.</p>
          </aside>
        </div>
      </div>
    </AppShell>
  )
}
