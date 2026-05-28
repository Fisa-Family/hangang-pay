import type { HistoryListItem } from '@/api/user'

export interface DateGroup {
  date: string
  items: HistoryListItem[]
}

export function groupByDate(items: HistoryListItem[]): DateGroup[] {
  const groups: DateGroup[] = []
  for (const item of items) {
    const date = item.createdAt.slice(0, 10)
    const last = groups[groups.length - 1]
    if (last && last.date === date) {
      last.items.push(item)
    } else {
      groups.push({ date, items: [item] })
    }
  }
  return groups
}
