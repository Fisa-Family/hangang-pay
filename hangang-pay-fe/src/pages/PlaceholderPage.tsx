interface PlaceholderPageProps {
  title: string
  screenId: string
}

export function PlaceholderPage({ title, screenId }: PlaceholderPageProps) {
  return (
    <section className="flex h-full flex-col gap-4">
      <div className="space-y-2">
        <p className="text-sm font-semibold text-primary">{screenId}</p>
        <h1 className="text-2xl font-bold text-foreground">{title}</h1>
      </div>
      <div className="rounded-lg border border-dashed border-border bg-card p-5">
        <p className="text-sm leading-6 text-muted-foreground">화면 구현 전 임시 페이지입니다.</p>
      </div>
    </section>
  )
}
