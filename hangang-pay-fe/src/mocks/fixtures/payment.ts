export const mockMerchantInfo = {
  merchantId: 42,
  partyId: 42,
  merchantName: '루나 악세사리',
  address: '서울 성동구 성수이로14길 8',
  walletAddress: '0xabcdef1234567890',
}

export const mockPaymentIntent = {
  transactionUuid: 'mock-txn-uuid-001',
}

export const mockPaymentResult = {
  transactionUuid: 'mock-txn-uuid-001',
  status: 'SUCCESS',
  approvalNumber: 'APV-2026-00000301',
  amount: 35000,
  merchantName: '루나 악세사리',
  confirmedAt: new Date().toISOString(),
}
