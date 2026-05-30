export const mockUserProfile = {
  userId: 1,
  partyId: 1,
  username: '김한강',
  phoneNumber: '010-1234-5678',
  birthDate: '1995-03-15',
  region: '성동구',
}

export const mockAccounts = [
  {
    accountId: 1,
    institutionCode: '004',
    bankName: '국민은행',
    maskedAccountNumber: '****-****-1234',
    accountType: 'PRIMARY' as const,
  },
  {
    accountId: 2,
    institutionCode: '088',
    bankName: '신한은행',
    maskedAccountNumber: '****-****-5678',
    accountType: 'SECONDARY' as const,
  },
]

export const mockWalletBalance = { balance: 152000 }
