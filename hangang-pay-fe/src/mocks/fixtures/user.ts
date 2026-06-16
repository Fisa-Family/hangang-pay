export const mockUserProfile = {
  userId: 1,
  partyId: 1,
  username: '김지수',
  phoneNumber: '010-9876-5432',
  birthDate: '1998-07-22',
  region: '성동구',
}

export const mockAccounts = [
  {
    accountId: 1,
    institutionCode: '004',
    bankName: '국민은행',
    maskedAccountNumber: '****-****-9201',
    accountType: 'PRIMARY' as const,
  },
  {
    accountId: 2,
    institutionCode: '088',
    bankName: '신한은행',
    maskedAccountNumber: '****-****-4473',
    accountType: 'SECONDARY' as const,
  },
]

export const mockWalletBalance = { balance: 124000 }
