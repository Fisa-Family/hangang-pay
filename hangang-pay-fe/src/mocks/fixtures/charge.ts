export const mockChargeInit = {
  partyId: 1,
  transactionUuid: 'mock-charge-uuid-001',
  balance: 124000,
  monthlyLimit: 500000,
  remainingLimit: 300000,
  discountRate: 0.1,
  accounts: [
    {
      accountId: 1,
      institutionId: 4,
      institutionCode: '004',
      institutionName: '국민은행',
      accountNumber: '****-****-9201',
      isPrimary: true,
    },
    {
      accountId: 2,
      institutionId: 88,
      institutionCode: '088',
      institutionName: '신한은행',
      accountNumber: '****-****-4473',
      isPrimary: false,
    },
  ],
}
