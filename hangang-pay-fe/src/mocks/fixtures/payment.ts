export const mockMerchantInfo = {
  merchantId: 42,
  partyId: 42,
  merchantName: '성수 카페',
  address: '서울 성동구 성수일로 123',
  walletAddress: '0xabcdef1234567890',
}

export const mockPaymentIntent = {
  transactionUuid: 'mock-txn-uuid-001',
}

export const mockPaymentResult = {
  transactionUuid: 'mock-txn-uuid-001',
  status: 'SUCCESS',
  approvalNumber: 'AP-2024-001',
  amount: 8500,
  merchantName: '성수 카페',
  confirmedAt: new Date().toISOString(),
}
