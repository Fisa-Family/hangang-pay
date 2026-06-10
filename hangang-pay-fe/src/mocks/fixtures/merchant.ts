const now = new Date()
const daysAgo = (n: number) => new Date(now.getTime() - n * 86400000).toISOString()

export const mockMerchantMyPage = {
  merchantId: 42,
  partyId: 42,
  merchantName: '성수 카페',
  businessNumber: '123-45-67890',
  ownerName: '박성수',
  phoneNumber: '010-9876-5432',
  address: '서울 성동구 성수일로 123',
  settlementAccount: {
    accountId: 10,
    institutionName: '기업은행',
    accountNumber: '****-****-9012',
    accountType: 'PRIMARY',
  },
}

export const mockMerchantDashboard = {
  todaySales: 125000,
  todayCount: 8,
  pendingSettlement: 205000,
  monthlyTotalSales: 1340000,
}

export const mockMerchantQr = {
  qrImageBase64:
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
}

export const mockMerchantPayments = {
  content: [
    {
      transactionId: 301,
      approvalNumber: 'AP-2024-301',
      payerName: '김*강',
      amount: 8500,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(0),
    },
    {
      transactionId: 302,
      approvalNumber: 'AP-2024-302',
      payerName: '이*수',
      amount: 15000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(1),
    },
    {
      transactionId: 303,
      approvalNumber: 'AP-2024-303',
      payerName: '박*영',
      amount: 12000,
      transactionType: 'CANCEL' as const,
      createdAt: daysAgo(2),
    },
  ],
  nextCursorCreatedAt: null,
  nextCursorId: null,
  hasNext: false,
}

export const mockMerchantPaymentDetail = {
  transactionType: 'PAYMENT' as const,
  detail: {
    transactionId: 301,
    transactionType: 'PAYMENT' as const,
    amount: 8500,
    payerName: '김*강',
    approvalNumber: 'AP-2024-301',
    paymentStatus: 'SUCCESS',
    createdAt: daysAgo(0),
    cancelAvailable: true,
  },
}

export const mockMerchantSettlements = {
  content: [
    {
      settlementId: 404,
      amount: 205000,
      settlementStatus: 'PENDING',
      settlementStatusText: '정산 대기',
      requestedAt: daysAgo(0),
      completedAt: null,
    },
    {
      settlementId: 403,
      amount: 97000,
      settlementStatus: 'COMPLETED',
      settlementStatusText: '정산 완료',
      requestedAt: daysAgo(3),
      completedAt: daysAgo(2),
    },
    {
      settlementId: 402,
      amount: 85000,
      settlementStatus: 'COMPLETED',
      settlementStatusText: '정산 완료',
      requestedAt: daysAgo(5),
      completedAt: daysAgo(4),
    },
    {
      settlementId: 401,
      amount: 120000,
      settlementStatus: 'COMPLETED',
      settlementStatusText: '정산 완료',
      requestedAt: daysAgo(7),
      completedAt: daysAgo(6),
    },
  ],
  nextCursorCreatedAt: null,
  nextCursorId: null,
  hasNext: false,
}

export const mockMerchantRedeemInit = {
  availableAmount: 205000,
  settlementAccount: {
    institutionName: '기업은행',
    accountNumber: '****-****-9012',
    accountType: 'PRIMARY',
  },
}

export const mockMerchantRedeemIntent = {
  transactionUuid: 'mock-redeem-uuid-001',
  status: 'PENDING',
  amount: 205000,
  accountNumber: '****-****-9012',
  bankName: '기업은행',
  expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
}

export const mockMerchantRedeemResult = {
  transactionId: 501,
  transactionUuid: 'mock-redeem-uuid-001',
  amount: 205000,
  accountNumber: '****-****-9012',
  bankName: '기업은행',
  txHash: '0xredeem123abc456',
  status: 'SUCCESS',
  exchangedAt: new Date().toISOString(),
}

export const mockPaymentCancelResult = {
  transactionUuid: 'mock-cancel-uuid-001',
  status: 'SUCCESS',
  approvalNumber: 'AP-2024-CANCEL-301',
  amount: 8500,
  confirmedAt: new Date().toISOString(),
}
