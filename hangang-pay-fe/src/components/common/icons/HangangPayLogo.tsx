interface HangangPayLogoProps {
  size?: number
  className?: string
}

export function HangangPayLogo({ size = 48, className }: HangangPayLogoProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 60 60"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
    >
      <rect x="6" y="2" width="13" height="36" rx="6.5" fill="#1d4ed8" />
      <rect x="41" y="2" width="13" height="36" rx="6.5" fill="#1d4ed8" />
      <path
        d="M2 30 C12 20 22 36 30 30 C38 24 48 38 58 30"
        stroke="#1d4ed8"
        strokeWidth="7"
        strokeLinecap="round"
      />
      <path
        d="M2 42 C12 32 22 48 30 42 C38 36 48 50 58 42"
        stroke="#60a5fa"
        strokeWidth="5"
        strokeLinecap="round"
        opacity="0.75"
      />
    </svg>
  )
}
