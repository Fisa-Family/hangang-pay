import { apiFetch } from './client'

export interface WalletBalanceResult {
  balance: number
}

export function fetchWalletBalance(): Promise<WalletBalanceResult> {
  return apiFetch<WalletBalanceResult>('/wallet/balance')
}
