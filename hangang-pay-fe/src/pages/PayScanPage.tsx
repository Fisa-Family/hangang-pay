import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import jsQR from 'jsqr'

function ChevronLeft() {
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="m15 18-6-6 6-6" />
    </svg>
  )
}

export function PayScanPage() {
  const navigate = useNavigate()
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const rafRef = useRef<number>(0)
  const streamRef = useRef<MediaStream | null>(null)
  const [permissionError, setPermissionError] = useState(false)
  const [detected, setDetected] = useState(false)

  const stopCamera = useCallback(() => {
    cancelAnimationFrame(rafRef.current)
    streamRef.current?.getTracks().forEach((t) => t.stop())
  }, [])

  useEffect(() => {
    let active = true

    function scanLoop() {
      if (!active) return
      const video = videoRef.current
      const canvas = canvasRef.current
      if (!video || !canvas || video.readyState < 2) {
        rafRef.current = requestAnimationFrame(scanLoop)
        return
      }
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(video, 0, 0)
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
      const code = jsQR(imageData.data, imageData.width, imageData.height)
      if (code?.data) {
        active = false
        setDetected(true)
        stopCamera()
        navigate('/pay/confirm', { state: { merchantId: code.data } })
        return
      }
      rafRef.current = requestAnimationFrame(scanLoop)
    }

    async function startCamera() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
        })
        streamRef.current = stream
        if (!active) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        const video = videoRef.current!
        video.srcObject = stream
        await video.play()
        scanLoop()
      } catch {
        setPermissionError(true)
      }
    }

    startCamera()
    return () => {
      active = false
      stopCamera()
    }
  }, [navigate, stopCamera])

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[#0D1520]">
      {/* 카메라 비디오 */}
      <video
        ref={videoRef}
        className="absolute inset-0 h-full w-full object-cover"
        playsInline
        muted
      />
      <canvas ref={canvasRef} className="hidden" />

      {/* 뒤로가기 */}
      <div className="relative z-10 flex items-center px-4 pt-14">
        <button
          type="button"
          onClick={() => {
            stopCamera()
            navigate(-1)
          }}
          className="text-white"
          aria-label="뒤로가기"
        >
          <ChevronLeft />
        </button>
      </div>

      {/* 스캔 영역 */}
      <div className="relative z-10 flex flex-1 flex-col items-center justify-center gap-8">
        <div className="relative" style={{ width: 280, height: 280 }}>
          {/* 외부 어둡게 (box-shadow 트릭) */}
          <div
            className="absolute inset-0 rounded-2xl"
            style={{ boxShadow: '0 0 0 9999px rgba(13, 21, 32, 0.82)' }}
          />
          {/* 프레임 테두리 */}
          <div className="absolute inset-0 rounded-2xl border border-white/20" />
          {/* 파란 코너 마커 */}
          <div className="absolute left-0 top-0 h-8 w-8 rounded-tl-2xl border-l-[3px] border-t-[3px] border-blue-400" />
          <div className="absolute right-0 top-0 h-8 w-8 rounded-tr-2xl border-r-[3px] border-t-[3px] border-blue-400" />
          <div className="absolute bottom-0 left-0 h-8 w-8 rounded-bl-2xl border-b-[3px] border-l-[3px] border-blue-400" />
          <div className="absolute bottom-0 right-0 h-8 w-8 rounded-br-2xl border-b-[3px] border-r-[3px] border-blue-400" />
        </div>

        <p className="text-sm font-medium text-white">
          {permissionError
            ? '카메라 권한이 필요합니다.'
            : detected
              ? 'QR 코드를 인식했습니다.'
              : '가맹점 QR을 스캔해주세요'}
        </p>
      </div>

      {/* 닫기 버튼 */}
      <div className="relative z-10 flex flex-col items-center gap-3 pb-14">
        {import.meta.env.DEV && (
          <button
            type="button"
            onClick={() => {
              stopCamera()
              navigate('/pay/confirm', { state: { merchantId: 'test-merchant-1' } })
            }}
            className="rounded-full bg-blue-600 px-10 py-3 text-sm font-medium text-white"
          >
            [DEV] 스캔 스킵
          </button>
        )}
        <button
          type="button"
          onClick={() => {
            stopCamera()
            navigate(-1)
          }}
          className="rounded-full bg-[#374151] px-14 py-4 text-base font-medium text-white"
        >
          닫기
        </button>
      </div>
    </div>
  )
}
