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
