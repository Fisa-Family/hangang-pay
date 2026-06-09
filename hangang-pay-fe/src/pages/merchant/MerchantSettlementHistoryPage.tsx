import { useEffect, useMemo, useRef } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '@/api/client'
import { fetchMerchantSettlements } from '@/api/merchant'
import {
  BackTitleHeader,
  EmptyState,
  HistoryDateGroupHeader,
  HistoryListItem,
} from '@/components/common'
import { groupByDate } from '../history/UserHistoryPage.helpers'
import {
  getNextSettlementPageParam,
  toDisplaySettlementItem,
  type MerchantSettlementsCursor,
} from './MerchantSettlementHistoryPage.helpers'

const PAGE_SIZE = 20
const SENTINEL_ROOT_MARGIN = '120px'

export function MerchantSettlementHistoryPage() {
  const navigate = useNavigate()

  const query = useInfiniteQuery({
    queryKey: ['merchant', 'settlements'],
    queryFn: ({ pageParam }) =>
      fetchMerchantSettlements({
        size: PAGE_SIZE,
        cursorCreatedAt: pageParam?.cursorCreatedAt,
        cursorId: pageParam?.cursorId,
      }),
    initialPageParam: null as MerchantSettlementsCursor | null,
    getNextPageParam: getNextSettlementPageParam,
    retry: false,
  })

  const items = useMemo(
    () =>
      (query.data?.pages.flatMap((page) => page?.content ?? []) ?? []).map(toDisplaySettlementItem),
    [query.data]
  )

  const grouped = useMemo(() => groupByDate(items), [items])
  const sentinelRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const node = sentinelRef.current
    if (!node || !query.hasNextPage) return

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && !query.isFetchingNextPage) {
            void query.fetchNextPage()
          }
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
        ? '정산 내역을 불러오지 못했습니다.'
        : null

  const showEmpty = !query.isLoading && !query.error && items.length === 0 && !query.hasNextPage

  return (
    <div className="flex h-full flex-col bg-card -mx-5 -my-5 px-5 py-5">
      <BackTitleHeader title="출금 내역" onBack={() => navigate('/merchant/home')} />

      <div className="mt-4 flex-1 overflow-y-auto pb-5">
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
            <EmptyState message="출금 내역이 없습니다." />
          </div>
        )}

        {!query.error &&
          grouped.map((group) => (
            <section key={group.date}>
              <HistoryDateGroupHeader isoDate={group.items[0].createdAt} />
              {group.items.map((item) => (
                <HistoryListItem key={item.id} item={item} />
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
