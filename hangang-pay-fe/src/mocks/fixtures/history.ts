const now = new Date()
const daysAgo = (n: number) => new Date(now.getTime() - n * 86400000).toISOString()

export const mockAllHistoryPage = {
  historyType: 'ALL',
  response: {
    content: [
      {
        historyId: 10,
        historyType: 'PAYMENT',
        merchantName: '성수 카페',
        amount: 8500,
        status: 'SUCCESS',
        createdAt: daysAgo(0),
      },
      {
        historyId: 9,
        historyType: 'CHARGE',
        amount: 50000,
        discountAmount: 5000,
        discountRate: 10,
        status: 'SUCCESS',
        createdAt: daysAgo(1),
      },
      {
        historyId: 8,
        historyType: 'CANCEL',
        merchantName: '뚝섬 식당',
        amount: 12000,
        status: 'SUCCESS',
        createdAt: daysAgo(2),
      },
      {
        historyId: 7,
        historyType: 'EXCHANGE',
        amount: 30000,
        status: 'SUCCESS',
        createdAt: daysAgo(3),
      },
      {
        historyId: 6,
        historyType: 'PAYMENT',
        merchantName: '왕십리 마트',
        amount: 25000,
        status: 'SUCCESS',
        createdAt: daysAgo(5),
      },
    ],
    nextCursorCreatedAt: null,
    nextCursorId: null,
    hasNext: false,
  },
}

export const mockPaymentHistoryPage = {
  historyType: 'PAYMENT',
  response: {
    content: [
      {
        paymentId: 'PAY-001',
        merchantName: '성수 카페',
        amount: 8500,
        paidAt: daysAgo(0),
        status: 'SUCCESS',
        historyType: 'PAYMENT' as const,
        cursorCreatedAt: daysAgo(0),
        cursorId: 10,
      },
      {
        paymentId: 'PAY-002',
        merchantName: '뚝섬 식당',
        amount: 12000,
        paidAt: daysAgo(2),
        status: 'CANCEL',
        historyType: 'CANCEL' as const,
        cursorCreatedAt: daysAgo(2),
        cursorId: 8,
      },
      {
        paymentId: 'PAY-003',
        merchantName: '왕십리 마트',
        amount: 25000,
        paidAt: daysAgo(5),
        status: 'SUCCESS',
        historyType: 'PAYMENT' as const,
        cursorCreatedAt: daysAgo(5),
        cursorId: 6,
      },
    ],
    nextCursorCreatedAt: null,
    nextCursorId: null,
    hasNext: false,
  },
}

export const mockChargeHistoryPage = {
  historyType: 'CHARGE',
  response: {
    content: [
      {
        id: 9,
        amount: 50000,
        discountAmount: 5000,
        discountRate: 10,
        status: 'SUCCESS',
        historyType: 'CHARGE' as const,
        chargedAt: daysAgo(1),
      },
    ],
    nextCursorCreatedAt: null,
    nextCursorId: null,
    hasNext: false,
  },
}

export const mockExchangeHistoryPage = {
  historyType: 'EXCHANGE',
  response: {
    content: [
      {
        id: 7,
        amount: 30000,
        status: 'SUCCESS',
        historyType: 'EXCHANGE' as const,
        exchangedAt: daysAgo(3),
      },
    ],
    nextCursorCreatedAt: null,
    nextCursorId: null,
    hasNext: false,
  },
}

export const mockPaymentDetail = {
  historyType: 'PAYMENT',
  detail: {
    historyId: 10,
    itemName: '성수 카페',
    amount: 8500,
    approvalNumber: 'AP-2024-001',
    paymentStatus: 'SUCCESS',
    createdAt: daysAgo(0),
    txHash: '0xabc123def456',
  },
}

export const mockChargeDetail = {
  historyType: 'CHARGE',
  detail: {
    historyId: 9,
    amount: 50000,
    discountAmount: 5000,
    actualPaidAmount: 45000,
    accountNumber: '****-****-1234',
    bankName: '국민은행',
    createdAt: daysAgo(1),
    txHash: '0xdef456abc789',
  },
}

export const mockExchangeDetail = {
  historyType: 'EXCHANGE',
  detail: {
    historyId: 7,
    amount: 30000,
    transferStatus: 'SUCCESS',
    transferType: 'EXCHANGE',
    accountNumber: '****-****-1234',
    bankName: '국민은행',
    walletAddress: '0x1234567890abcdef',
    createdAt: daysAgo(3),
    updatedAt: daysAgo(3),
    txHash: '0x789abc123def',
    blockchainStatus: 'CONFIRMED',
  },
}
