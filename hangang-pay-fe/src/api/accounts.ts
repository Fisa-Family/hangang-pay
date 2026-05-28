import { apiFetch } from './client'

export interface RegisteredAccount {
  accountId: number
  institutionCode: string
  bankName: string
  maskedAccountNumber: string
  accountType: 'PRIMARY' | 'SECONDARY'
}

interface AccountAddRequest {
  institutionCode: string
  accountNumber: string
}

interface AccountListResponse {
  accounts: RegisteredAccount[]
}

interface PrimaryAccountResponse {
  accountId: number
}

interface VerificationCodeResponse {
  code: string
}

interface AccountVerificationPayload {
  institutionId: number
  accountNumber: string
}

export async function fetchAccounts(): Promise<RegisteredAccount[]> {
  const response = await apiFetch<AccountListResponse>('/accounts')

  return response.accounts
}

export function addAccount(payload: AccountAddRequest): Promise<RegisteredAccount> {
  return apiFetch<RegisteredAccount>('/accounts', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function deleteAccount(accountId: number): Promise<void> {
  await apiFetch(`/accounts/${accountId}`, {
    method: 'DELETE',
  })
}

export async function setPrimaryAccount(accountId: number): Promise<PrimaryAccountResponse> {
  return apiFetch<PrimaryAccountResponse>(`/accounts/${accountId}/primary`, {
    method: 'PATCH',
  })
}

export function requestAccountVerification(
  payload: AccountVerificationPayload
): Promise<VerificationCodeResponse> {
  return apiFetch<VerificationCodeResponse>('/auth/account/send', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function verifyAccount(
  payload: AccountVerificationPayload & { code: string }
): Promise<void> {
  return apiFetch<void>('/auth/account/verify', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
