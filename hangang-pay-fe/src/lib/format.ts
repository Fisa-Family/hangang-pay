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

export function formatPhoneNumber(raw: string): string {
  if (raw.length !== 11) {
    return raw
  }

  const part1 = raw.substring(0, 3)
  const part2 = raw.substring(3, 7)
  const part3 = raw.substring(7, 11)

  return `${part1}-${part2}-${part3}`
}
