import { StatusBadge } from '../feedback/StatusBadge'

interface CheckboxItem {
  id: string
  label: string
  required: boolean
}

interface CheckboxGroupProps {
  items: CheckboxItem[]
  checkedIds: string[]
  onChange: (checkedIds: string[]) => void
}

export function CheckboxGroup({ items, checkedIds, onChange }: CheckboxGroupProps) {
  const allChecked = items.every((item) => checkedIds.includes(item.id))

  const updateItem = (id: string, checked: boolean) => {
    if (checked) {
      onChange([...new Set([...checkedIds, id])])
      return
    }
    onChange(checkedIds.filter((checkedId) => checkedId !== id))
  }

  return (
    <section className="rounded-lg border border-border bg-card">
      <label className="flex min-h-14 items-center gap-3 border-b border-border px-4">
        <input
          type="checkbox"
          checked={allChecked}
          onChange={(event) => onChange(event.target.checked ? items.map((item) => item.id) : [])}
          className="size-5 accent-primary"
        />
        <span className="font-semibold">전체 동의</span>
      </label>
      <div className="divide-y divide-border">
        {items.map((item) => (
          <label key={item.id} className="flex min-h-13 items-center gap-3 px-4">
            <input
              type="checkbox"
              checked={checkedIds.includes(item.id)}
              onChange={(event) => updateItem(item.id, event.target.checked)}
              className="size-5 accent-primary"
            />
            <span className="min-w-0 flex-1 truncate text-sm">{item.label}</span>
            <StatusBadge variant={item.required ? 'danger' : 'neutral'}>
              {item.required ? '필수' : '선택'}
            </StatusBadge>
          </label>
        ))}
      </div>
    </section>
  )
}
