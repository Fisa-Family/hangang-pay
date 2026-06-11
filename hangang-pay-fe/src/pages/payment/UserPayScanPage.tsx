import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
import { resolveBackDestination } from '@/lib/navigation'

type ScanError =
  | { kind: 'permission' }
  | { kind: 'parse'; message: string }
  | { kind: 'other'; message: string }

export function UserPayScanPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const videoRef = useRef<HTMLVideoElement>(null)
  const controlsRef = useRef<IScannerControls | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const [error, setError] = useState<ScanError | null>(null)
  const [restartKey, setRestartKey] = useState(0)
  const backPath = resolveBackDestination(location.pathname, location.state)

  const stopScanner = () => {
    controlsRef.current?.stop()
    controlsRef.current = null
    mediaStreamRef.current?.getTracks().forEach((t) => t.stop())
    mediaStreamRef.current = null
    if (videoRef.current) {
      videoRef.current.srcObject = null
    }
  }

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    let cancelled = false
    let mediaStream: MediaStream | null = null

    void (async () => {
      try {
        // stream을 우리가 직접 잡아 cleanup에서 확실히 풀 수 있게 함 (StrictMode 이중 마운트 안전).
        mediaStream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
        })
        mediaStreamRef.current = mediaStream
        if (cancelled) {
          mediaStream.getTracks().forEach((t) => t.stop())
          mediaStream = null
          mediaStreamRef.current = null
          return
        }
        video.srcObject = mediaStream
        await video.play()
        if (cancelled) return

        // 이미 재생 중인 video element에서 ZXing은 디코딩만 담당.
        const reader = new BrowserQRCodeReader()
        const controls = await reader.decodeFromVideoElement(video, (result, _err, ctrls) => {
          if (cancelled || !result) return
          ctrls.stop()
          try {
            const parsed = JSON.parse(result.getText()) as { merchantId?: unknown }
            if (typeof parsed.merchantId !== 'number') {
              throw new Error('invalid merchantId')
            }
            cancelled = true
            stopScanner()
            navigate(`/pay/amount/${parsed.merchantId}`, { replace: true })
          } catch {
            setError({ kind: 'parse', message: '유효하지 않은 QR 코드입니다.' })
          }
        })
        if (cancelled) {
          controls.stop()
          return
        }
        controlsRef.current = controls
      } catch (e) {
        if (cancelled) return
        if (
          e instanceof DOMException &&
          (e.name === 'NotAllowedError' || e.name === 'SecurityError')
        ) {
          setError({ kind: 'permission' })
        } else {
          setError({
            kind: 'other',
            message: e instanceof Error ? e.message : '카메라를 사용할 수 없습니다.',
          })
        }
      }
    })()

    return () => {
      cancelled = true
      stopScanner()
      mediaStream?.getTracks().forEach((t) => t.stop())
      mediaStream = null
    }
  }, [navigate, restartKey])

  const handleRetry = () => {
    setError(null)
    setRestartKey((k) => k + 1)
  }

  return (
    <div className="relative flex h-full w-full flex-col overflow-hidden bg-black text-white">
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className="pointer-events-none absolute inset-0 z-0 h-full w-full object-cover"
      />

      <button
        type="button"
        aria-label="뒤로가기"
        onClick={() => navigate('/home')}
        className="absolute left-4 top-4 z-20 flex size-11 items-center justify-center rounded-lg text-white hover:bg-white/10"
      >
        <ChevronLeft className="h-6 w-6" aria-hidden />
      </button>

      <div className="relative z-10 flex flex-1 flex-col items-center justify-center gap-6 px-6">
        <div className="pointer-events-none relative aspect-square w-full max-w-[260px] rounded-2xl shadow-[0_0_0_9999px_rgba(0,0,0,0.45)]">
          <span className="absolute left-0 top-0 h-6 w-6 rounded-tl-lg border-l-4 border-t-4 border-primary" />
          <span className="absolute right-0 top-0 h-6 w-6 rounded-tr-lg border-r-4 border-t-4 border-primary" />
          <span className="absolute bottom-0 left-0 h-6 w-6 rounded-bl-lg border-b-4 border-l-4 border-primary" />
          <span className="absolute bottom-0 right-0 h-6 w-6 rounded-br-lg border-b-4 border-r-4 border-primary" />
        </div>
        <p className="text-sm font-medium drop-shadow">가맹점 QR을 스캔해주세요</p>

        {error && (
          <div className="max-w-xs rounded-lg bg-black/60 px-4 py-3 text-center text-sm">
            {error.kind === 'permission' &&
              '카메라 권한이 필요합니다. 브라우저 설정에서 허용해 주세요.'}
            {error.kind === 'parse' && (
              <>
                {error.message}
                <button type="button" onClick={handleRetry} className="ml-2 underline">
                  다시 시도
                </button>
              </>
            )}
            {error.kind === 'other' && error.message}
          </div>
        )}
      </div>

      <div className="relative z-10 px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <button
          type="button"
          onClick={() => {
            stopScanner()
            navigate(backPath, { replace: true })
          }}
          className="h-12 w-full rounded-2xl bg-black/60 text-base font-semibold text-white hover:bg-black/70"
        >
          닫기
        </button>
      </div>
    </div>
  )
}
