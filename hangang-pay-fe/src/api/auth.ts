import { apiFetch } from './client'
import type { UserRole } from '@/auth/types'

export interface LoginResult {
  principalId: number
  partyId: number
  role: UserRole
}

export interface SmsVerificationCodeResult {
  code: string
}

export interface UserRegisterRequest {
  name: string
  birthDate: string
  phoneNumber: string
  password: string
  paymentPin: string
  institutionId: number
  accountNumber: string
  termsAgreed: {
    serviceTerms: boolean
    privacyTerms: boolean
    electronicFinanceTerms: boolean
    localCurrencyTerms: boolean
  }
}

export interface UserRegisterResult {
  partyId: number
  userId: number
}

export function loginUser(phoneNumber: string, password: string): Promise<LoginResult> {
  return apiFetch<LoginResult>('/auth/users/login', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber, password }),
  })
}

export function loginMerchant(businessNumber: string, password: string): Promise<LoginResult> {
  return apiFetch<LoginResult>('/auth/merchants/login', {
    method: 'POST',
    body: JSON.stringify({ businessNumber, password }),
  })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/auth/logout', { method: 'POST' })
}

export function sendSms(phoneNumber: string): Promise<SmsVerificationCodeResult> {
  return apiFetch<SmsVerificationCodeResult>('/auth/sms/send', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber }),
  })
}

export function verifySms(phoneNumber: string, code: string): Promise<void> {
  return apiFetch<void>('/auth/sms/verify', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber, code }),
  })
}

export function registerUser(request: UserRegisterRequest): Promise<UserRegisterResult> {
  return apiFetch<UserRegisterResult>('/auth/users/register', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export interface BusinessInfoResult {
  businessNumber: string
  merchantName: string
  ownerName: string
  address: string
  businessType: string
}

export interface MerchantRegisterRequest {
  businessNumber: string
  username: string
  password: string
  paymentPin: string
  institutionId: number
  accountNumber: string
  termsAgreed: {
    serviceTerms: boolean
    privacyTerms: boolean
    electronicFinanceTerms: boolean
    localCurrencyTerms: boolean
  }
}

export interface MerchantRegisterResult {
  partyId: number
  merchantId: number
  merchantName: string
}

export function getBusinessInfo(businessNumber: string): Promise<BusinessInfoResult> {
  return apiFetch<BusinessInfoResult>(
    `/auth/merchants/business-info?businessNumber=${encodeURIComponent(businessNumber)}`
  )
}

export function registerMerchant(
  request: MerchantRegisterRequest
): Promise<MerchantRegisterResult> {
  return apiFetch<MerchantRegisterResult>('/auth/merchants/register', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
