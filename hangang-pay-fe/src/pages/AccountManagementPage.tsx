import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AccountRow, AppShell, Button, PageHeader } from '@/components/common'
import { deleteAccount, fetchAccounts, setPrimaryAccount } from '@/api/accounts'
import { useCurrentUser } from '@/auth/useCurrentUser'

export function AccountManagementPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { isLoading: isAuthLoading } = useCurrentUser()

  const accountsQuery = useQuery({
    queryKey: ['accounts'],
    queryFn: fetchAccounts,
    enabled: !isAuthLoading,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
    },
  })

  const primaryMutation = useMutation({
    mutationFn: setPrimaryAccount,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
    },
  })

  const isLoadingAccounts = isAuthLoading || accountsQuery.isLoading

  const handleBack = () => {
    navigate(-1)
  }

  const handleAddAccount = () => {
    navigate('/mypage/accounts/add')
  }

  return (
    <AppShell className="bg-surface">
      <div className="flex min-h-0 flex-1 flex-col">
        <PageHeader title="계좌 관리" onBack={handleBack} />

        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto pt-3">
          {isLoadingAccounts ? (
            <p className="py-6 text-center text-sm text-muted-foreground">계좌를 불러오는 중입니다.</p>
          ) : accountsQuery.isError ? (
            <p className="py-6 text-center text-sm text-destructive">계좌 정보를 불러오지 못했습니다.</p>
          ) : (
            <section aria-label="등록 계좌 목록" className="flex flex-col gap-2.5">
              {(accountsQuery.data ?? []).map((account) => (
                <AccountRow
                  key={account.accountId}
                  mode="manage"
                  bankName={account.bankName}
                  maskedAccountNumber={account.maskedAccountNumber}
                  primary={account.accountType === 'PRIMARY'}
                  onSetPrimary={() => primaryMutation.mutate(account.accountId)}
                  onDelete={() => deleteMutation.mutate(account.accountId)}
                />
              ))}
            </section>
          )}

          <div className="mt-4">
            <Button
              variant="ghost"
              onClick={handleAddAccount}
              className="gap-2 border border-input bg-card text-foreground hover:bg-muted hover:text-foreground"
            >
              계좌 추가
            </Button>
          </div>

          <aside className="mt-3 flex justify-center rounded-lg px-4 py-3 text-center text-xs font-medium leading-5 text-muted-foreground">
            <p>계좌는 최대 3개까지 등록할 수 있어요.</p>
          </aside>
        </div>
      </div>
    </AppShell>
  )
}
