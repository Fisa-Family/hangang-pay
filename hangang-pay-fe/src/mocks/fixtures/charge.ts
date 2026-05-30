export const mockChargeInit = {
  partyId: 1,
  transactionUuid: 'mock-charge-uuid-001',
  balance: 152000,
  monthlyLimit: 500000,
  remainingLimit: 348000,
  discountRate: 10,
  accounts: [
    {
      accountId: 1,
      institutionId: 4,
      institutionCode: '004',
      institutionName: '국민은행',
      accountNumber: '****-****-1234',
      isPrimary: true,
    },
    {
      accountId: 2,
      institutionId: 88,
      institutionCode: '088',
      institutionName: '신한은행',
      accountNumber: '****-****-5678',
      isPrimary: false,
    },
  ],
}

export const mockChargeResult = {
  partyId: 1,
  chargeId: 100,
  amount: 50000,
  finalAmount: 55000,
  chargedAt: new Date().toISOString(),
}
