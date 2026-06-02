export const mockExchangeInit = {
  eligible: true,
  walletBalance: 152000,
}

export const mockExchangeResult = {
  transactionId: 200,
  transactionUuid: 'mock-exchange-uuid-001',
  amount: 30000,
  accountNumber: '****-****-1234',
  bankName: '국민은행',
  txHash: '0x789abc123def456',
  status: 'SUCCESS',
  exchangedAt: new Date().toISOString(),
}
