import { useEffect, useMemo, useRef } from 'react'
import { Calendar } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import {
  fetchUserHistoriesNormalized,
  type HistoryListItem as HistoryListItemModel,
  type HistoryPage,
  type HistoryTab,
} from '@/api/user'
import {
  EmptyState,
  HistoryDateGroupHeader,
  HistoryListItem,
  HistoryTypeTabs,
  PageHeader,
} from '@/components/common'
import { groupByDate } from './UserHistoryPage.helpers'

const API_SPEC = {
  MY_002: { id: 'MY-002' },
} as const

function getHistoryDetailPath(item: HistoryListItemModel) {
  if (item.historyId == null) return null

  switch (item.displayType) {
    case 'CHARGE':
      return `/mypage/history/charges/${item.historyId}`

    case 'PAYMENT':
      return `/mypage/history/payments/${item.historyId}`

    case 'CANCEL':
      return null
    case 'EXCHANGE':
      return `/mypage/history/exchanges/${item.historyId}`

    default:
      return null
  }
}

function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }

  return `${spec.id} 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.`
}

const HISTORY_PAGE_SIZE = 20
const SENTINEL_ROOT_MARGIN = '120px'

export function UserHistoryPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const tabParam = searchParams.get('tab')
  const tab: HistoryTab =
    tabParam === 'PAYMENT' || tabParam === 'CHARGE' || tabParam === 'EXCHANGE' || tabParam === 'ALL'
      ? tabParam
      : 'ALL'

  const query = useInfiniteQuery({
    queryKey: ['users', 'histories', tab],
    queryFn: ({ pageParam }) =>
      fetchUserHistoriesNormalized({
        tab,
        size: HISTORY_PAGE_SIZE,
        cursorCreatedAt: pageParam?.cursorCreatedAt,
        cursorId: pageParam?.cursorId,
      }),
    initialPageParam: null as HistoryPage['nextCursor'],
    getNextPageParam: (lastPage) => lastPage?.nextCursor ?? null,
    retry: false,
  })

  const items = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data])
  const grouped = useMemo(() => groupByDate(items), [items])

  const sentinelRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const node = sentinelRef.current
    if (!node) return
    if (!query.hasNextPage) return

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

  const errorMessage = query.error ? buildErrorMessage(API_SPEC.MY_002, query.error) : null
  const showInitialLoading = query.isLoading && !query.error
  const showEmpty = !query.isLoading && !query.error && items.length === 0

  return (
    <div className="flex h-full flex-col bg-card -mx-5 -my-5 px-5 py-5">
      <PageHeader
        title="내역 조회"
        onBack={() => navigate('/home')}
        rightAction={
          <button
            type="button"
            aria-label="날짜 필터"
            onClick={() => {}}
            className="flex size-11 items-center justify-center rounded-lg text-foreground hover:bg-muted"
          >
            <Calendar size={22} aria-hidden />
          </button>
        }
      />

      <HistoryTypeTabs
        value={tab}
        onChange={(nextTab) => {
          setSearchParams(nextTab === 'ALL' ? {} : { tab: nextTab })
        }}
      />
      <div className="flex-1 overflow-y-auto pb-5">
        {showInitialLoading && (
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

              {group.items.map((item) => {
                const detailPath = getHistoryDetailPath(item)

                return (
                  <HistoryListItem
                    key={item.id}
                    item={item}
                    onClick={detailPath ? () => navigate(detailPath) : undefined}
                  />
                )
              })}
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
