import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { fetchMerchantPayments } from '@/api/merchant'
import {
  BackTitleHeader,
  EmptyState,
  HistoryDateGroupHeader,
  SegmentedTabs,
} from '@/components/common'
import { formatTimeHHmm, formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'
import { groupByDate } from './UserHistoryPage.helpers'
import {
  getNextPageParam,
  toDisplayItem,
  type MerchantPaymentsCursor,
} from './MerchantPaymentsPage.helpers'

type MerchantPayTab = 'PAYMENT' | 'CANCEL'

const TABS: ReadonlyArray<{ key: MerchantPayTab; label: string }> = [
  { key: 'PAYMENT', label: '결제 내역' },
  { key: 'CANCEL', label: '결제 취소 내역' },
]

const PAGE_SIZE = 20
const SENTINEL_ROOT_MARGIN = '120px'

export function MerchantPaymentsPage() {
  const navigate = useNavigate()
  const [tab, setTab] = useState<MerchantPayTab>('PAYMENT')

  const query = useInfiniteQuery({
    queryKey: ['merchant', 'payments'],
    queryFn: ({ pageParam }) =>
      fetchMerchantPayments({
        size: PAGE_SIZE,
        cursorCreatedAt: pageParam?.cursorCreatedAt,
        cursorId: pageParam?.cursorId,
      }),
    initialPageParam: null as MerchantPaymentsCursor | null,
    getNextPageParam,
    retry: false,
  })

  const items = useMemo(
    () => (query.data?.pages.flatMap((p) => p.content) ?? []).map(toDisplayItem),
    [query.data]
  )
  // BE에 타입 필터 파라미터가 없어 혼합 스트림을 탭별로 클라이언트 필터
  const filtered = useMemo(() => items.filter((i) => i.displayType === tab), [items, tab])
  const grouped = useMemo(() => groupByDate(filtered), [filtered])

  const sentinelRef = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    const node = sentinelRef.current
    if (!node || !query.hasNextPage) return
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && !query.isFetchingNextPage) void query.fetchNextPage()
        }
      },
      { rootMargin: SENTINEL_ROOT_MARGIN }
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [query])

  const errorMessage =
    query.error instanceof ApiError
      ? query.error.message
      : query.error
        ? '결제 내역을 불러오지 못했습니다.'
        : null
  // 필터 결과가 비어도 다음 페이지가 남아 있으면 sentinel이 계속 로드 → EmptyState는 마지막에만
  const showEmpty = !query.isLoading && !query.error && filtered.length === 0 && !query.hasNextPage

  return (
    <div className="flex h-full flex-col">
      <BackTitleHeader title="결제 내역" onBack={() => navigate('/merchant/home')} />
      <SegmentedTabs tabs={TABS} value={tab} onChange={setTab} />

      <div className="flex-1 overflow-y-auto">
        {query.isLoading && (
          <p className="px-4 py-6 text-center text-sm text-muted-foreground">불러오는 중...</p>
        )}
        {errorMessage && (
          <div className="m-4 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm font-medium text-destructive shadow-sm">
            {errorMessage}
          </div>
        )}
        {showEmpty && (
          <div className="p-4">
            <EmptyState message="내역이 없습니다." />
          </div>
        )}

        {!query.error &&
          grouped.map((group) => (
            <section key={group.date}>
              <HistoryDateGroupHeader isoDate={group.items[0].createdAt} />
              {group.items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => navigate(`/merchant/payments/${item.id}`)}
                  className="flex w-full items-center justify-between gap-3 border-b border-border/50 px-4 py-3 text-left transition-colors last:border-b-0 active:bg-muted/40"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">
                      {item.counterpartName}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {formatTimeHHmm(item.createdAt)}
                    </p>
                  </div>
                  <span
                    className={cn(
                      'shrink-0 text-sm font-bold tabular-nums',
                      item.displayType === 'CANCEL' ? 'text-primary' : 'text-foreground'
                    )}
                  >
                    {item.sign}
                    {formatWon(item.amount)}
                  </span>
                </button>
              ))}
            </section>
          ))}

        {query.hasNextPage && (
          <div ref={sentinelRef} className="px-4 py-4 text-center text-sm text-muted-foreground">
            {query.isFetchingNextPage ? '불러오는 중...' : ''}
          </div>
        )}
      </div>
    </div>
  )
}
