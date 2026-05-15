import { AppShell, Button } from '@/components/common'

export function LoginPage() {
  return (
    <AppShell>
      <section className="flex h-full flex-col justify-center gap-8">
        <div className="space-y-3">
          <p className="text-sm font-semibold text-primary">Hangang Pay</p>
          <h1 className="text-3xl font-bold text-foreground">로그인</h1>
          <p className="text-sm leading-6 text-muted-foreground">
            세션 기반 인증 연결 전까지 사용하는 임시 진입 화면입니다.
          </p>
        </div>
        <Button type="button">로그인 계속하기</Button>
      </section>
    </AppShell>
  )
}
