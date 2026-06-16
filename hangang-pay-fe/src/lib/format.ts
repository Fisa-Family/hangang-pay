export function formatWon(value: number) {
  return `${new Intl.NumberFormat('ko-KR').format(value)}원`
}

export function appendAmountDigit(value: number, digit: string, maxDigits = 9) {
  const next = `${value || ''}${digit}`.slice(0, maxDigits)
  return Number(next)
}

export function removeAmountDigit(value: number) {
  return Math.floor(value / 10)
}

export function formatTimeHHmm(iso: string): string {
  const d = new Date(iso)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'] as const

export function formatDateGroup(iso: string): string {
  const d = new Date(iso)
  const yyyy = String(d.getFullYear())
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const weekday = WEEKDAY_LABELS[d.getDay()]
  return `${yyyy}.${mm}.${dd} (${weekday})`
}

export function shortHash(hash?: string): string {
  return hash ? `${hash.slice(0, 14)}...${hash.slice(-4)}` : '-'
}

export function formatDateTimeDot(iso: string): string {
  const d = new Date(iso)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}.${mm}.${dd} ${hh}:${mi}`
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

export function formatMaskedAccount(institutionName: string, accountNumber: string): string {
  if (!institutionName || !accountNumber) {
    return ''
  }
  return `${institutionName} ****${accountNumber.slice(-4)}`
}

export function formatPhoneNumber(raw: string | undefined | null): string {
  if (!raw) return ''
  // 휴대전화 (010-XXXX-XXXX)
  if (raw.length === 11) {
    return `${raw.substring(0, 3)}-${raw.substring(3, 7)}-${raw.substring(7, 11)}`
  }

  // 서울 지역번호 유선전화 (02-XXX-XXXX / 02-XXXX-XXXX)
  if (raw.startsWith('02') && (raw.length === 9 || raw.length === 10)) {
    const middleEnd = raw.length - 4
    return `${raw.substring(0, 2)}-${raw.substring(2, middleEnd)}-${raw.substring(middleEnd)}`
  }

  // 그 외 지역번호 유선전화 (0XX-XXX-XXXX)
  if (raw.length === 10) {
    return `${raw.substring(0, 3)}-${raw.substring(3, 6)}-${raw.substring(6, 10)}`
  }

  return raw
}

export function formatBusinessNumber(raw: string | undefined | null): string {
  if (!raw) return ''
  if (raw.length !== 10) {
    return raw
  }

  const part1 = raw.substring(0, 3)
  const part2 = raw.substring(3, 5)
  const part3 = raw.substring(5, 10)

  return `${part1}-${part2}-${part3}`
}
