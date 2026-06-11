type NavigationState = Record<string, unknown> | null | undefined

function getRouteFromState(state: NavigationState, key: 'cancelRoute' | 'backRoute') {
  const value = state?.[key]
  return typeof value === 'string' && value.length > 0 ? value : null
}

export function resolveBackDestination(pathname: string, state?: NavigationState) {
  const backRoute = getRouteFromState(state, 'backRoute')
  if (backRoute) return backRoute

  if (pathname.startsWith('/pay/pin')) {
    return getRouteFromState(state, 'cancelRoute') ?? '/home'
  }
  if (
    pathname.startsWith('/pay/scan') ||
    pathname.startsWith('/pay/amount/') ||
    pathname.startsWith('/pay/confirm') ||
    pathname.startsWith('/pay/processing') ||
    pathname.startsWith('/pay/complete')
  ) {
    return '/home'
  }

  if (pathname.startsWith('/charge/pin')) {
    return getRouteFromState(state, 'cancelRoute') ?? '/charge/amount'
  }
  if (pathname.startsWith('/charge/amount')) return '/home'
  if (pathname.startsWith('/charge/processing')) return '/charge/amount'
  if (pathname.startsWith('/charge/complete')) return '/home'

  if (pathname.startsWith('/refund/pin')) {
    return getRouteFromState(state, 'cancelRoute') ?? '/refund/check'
  }
  if (pathname.startsWith('/refund/check')) return '/home'
  if (pathname.startsWith('/refund/processing')) return '/refund/check'
  if (pathname.startsWith('/refund/complete')) return '/home'

  if (pathname.startsWith('/mypage/history/')) return '/mypage/payments'
  if (pathname.startsWith('/mypage/accounts/add')) return '/mypage/accounts'
  if (pathname.startsWith('/mypage/accounts')) return '/mypage'

  if (pathname.startsWith('/merchant/payments/')) return '/merchant/payments'
  if (pathname.startsWith('/merchant/qr')) return '/merchant/home'

  return pathname.startsWith('/merchant/') ? '/merchant/home' : '/home'
}
