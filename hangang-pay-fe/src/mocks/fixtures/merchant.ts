const now = new Date()
const daysAgo = (n: number) => new Date(now.getTime() - n * 86400000).toISOString()

export const mockMerchantMyPage = {
  merchantId: 42,
  partyId: 42,
  merchantName: '루나 악세사리',
  businessNumber: '312-86-01542',
  ownerName: '이하린',
  phoneNumber: '01046823317',
  address: '서울 성동구 성수이로14길 8',
  settlementAccount: {
    accountId: 10,
    institutionName: '기업은행',
    accountNumber: '3820648100193',
    accountType: 'PRIMARY',
  },
}

export const mockMerchantDashboard = {
  todaySales: 187000,
  todayCount: 11,
  pendingSettlement: 342000,
  monthlyTotalSales: 2850000,
}

export const mockMerchantQr = {
  qrImageBase64:
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
}

export const mockMerchantPayments = {
  content: [
    {
      transactionId: 301,
      approvalNumber: 'APV-2026-00000301',
      payerName: '김*수',
      amount: 35000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(0),
    },
    {
      transactionId: 302,
      approvalNumber: 'APV-2026-00000302',
      payerName: '이*진',
      amount: 28000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(0),
    },
    {
      transactionId: 303,
      approvalNumber: 'APV-2026-00000303',
      payerName: '박*현',
      amount: 52000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(0),
    },
    {
      transactionId: 304,
      approvalNumber: 'APV-2026-00000304',
      payerName: '최*아',
      amount: 18000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(0),
    },
    {
      transactionId: 305,
      approvalNumber: 'APV-2026-00000305',
      payerName: '한*솔',
      amount: 23000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(1),
    },
    {
      transactionId: 306,
      approvalNumber: 'APV-2026-00000306',
      payerName: '정*린',
      amount: 38000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(1),
    },
    {
      transactionId: 307,
      approvalNumber: 'APV-2026-00000307',
      payerName: '오*빈',
      amount: 15000,
      transactionType: 'CANCEL' as const,
      createdAt: daysAgo(1),
    },
    {
      transactionId: 308,
      approvalNumber: 'APV-2026-00000308',
      payerName: '김*수',
      amount: 52000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(2),
    },
    {
      transactionId: 309,
      approvalNumber: 'APV-2026-00000309',
      payerName: '이*현',
      amount: 25000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(2),
    },
    {
      transactionId: 310,
      approvalNumber: 'APV-2026-00000310',
      payerName: '박*연',
      amount: 42000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(3),
    },
    {
      transactionId: 311,
      approvalNumber: 'APV-2026-00000311',
      payerName: '최*민',
      amount: 19000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(3),
    },
    {
      transactionId: 312,
      approvalNumber: 'APV-2026-00000312',
      payerName: '정*호',
      amount: 33000,
      transactionType: 'PAYMENT' as const,
      createdAt: daysAgo(4),
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
    amount: 35000,
    payerName: '김*수',
    approvalNumber: 'APV-2026-00000301',
    paymentStatus: 'SUCCESS',
    createdAt: daysAgo(0),
    cancelAvailable: true,
  },
}

export const mockMerchantSettlements = {
  content: [
    {
      settlementId: 404,
      amount: 342000,
      settlementStatus: 'PENDING',
      settlementStatusText: '정산 대기',
      requestedAt: daysAgo(0),
      completedAt: null,
    },
    {
      settlementId: 403,
      amount: 198000,
      settlementStatus: 'COMPLETED',
      settlementStatusText: '정산 완료',
      requestedAt: daysAgo(3),
      completedAt: daysAgo(2),
    },
    {
      settlementId: 402,
      amount: 156000,
      settlementStatus: 'COMPLETED',
      settlementStatusText: '정산 완료',
      requestedAt: daysAgo(5),
      completedAt: daysAgo(4),
    },
    {
      settlementId: 401,
      amount: 285000,
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
  availableAmount: 342000,
  settlementAccount: {
    institutionName: '기업은행',
    accountNumber: '3820648100193',
    accountType: 'PRIMARY',
  },
}

export const mockMerchantRedeemIntent = {
  transactionUuid: 'mock-redeem-uuid-001',
  status: 'PENDING',
  amount: 342000,
  accountNumber: '3820648100193',
  bankName: '기업은행',
  expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
}

export const mockMerchantRedeemResult = {
  transactionId: 501,
  transactionUuid: 'mock-redeem-uuid-001',
  amount: 342000,
  accountNumber: '3820648100193',
  bankName: '기업은행',
  txHash: '0xf2a9c4e1b8d3f7a0c5e2b9d4f1a8c3e6',
  status: 'SUCCESS',
  exchangedAt: new Date().toISOString(),
}

export const mockPaymentCancelResult = {
  transactionUuid: 'mock-cancel-uuid-001',
  status: 'SUCCESS',
  approvalNumber: 'APV-2026-00000399',
  amount: 35000,
  confirmedAt: new Date().toISOString(),
}
