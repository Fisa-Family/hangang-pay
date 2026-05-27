import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { formatDateGroup, formatPhoneNumber, formatTimeHHmm } from './format'

describe('formatPhoneNumber', () => {
  describe('Property-Based Tests', () => {
    it('Property 1: Phone Number Formatting Correctness - **Validates: Requirements 3.1**', () => {
      // For any 11-digit phone number string, formatting SHALL produce a string
      // matching the pattern "XXX-XXXX-XXXX" where X represents a digit from the original input
      fc.assert(
        fc.property(fc.stringMatching(/^\d{11}$/), (phoneNumber) => {
          const formatted = formatPhoneNumber(phoneNumber)

          // Check pattern matches XXX-XXXX-XXXX
          const pattern = /^\d{3}-\d{4}-\d{4}$/
          expect(formatted).toMatch(pattern)

          // Verify digits are preserved from original input
          const originalDigits = phoneNumber
          const formattedDigits = formatted.replace(/-/g, '')
          expect(formattedDigits).toBe(originalDigits)
        })
      )
    })

    it('Property 2: Phone Number Formatting Immutability - **Validates: Requirements 3.3**', () => {
      // For any phone number string, calling the format function SHALL not modify
      // the original input value
      fc.assert(
        fc.property(fc.string(), (phoneNumber) => {
          const originalValue = phoneNumber
          const originalCopy = phoneNumber.slice() // Create a copy to compare

          formatPhoneNumber(phoneNumber)

          // Verify original input is unchanged
          expect(phoneNumber).toBe(originalValue)
          expect(phoneNumber).toBe(originalCopy)
        })
      )
    })

    it('Property 3: Phone Number Invalid Length Handling - **Validates: Requirements 3.2**', () => {
      // For any phone number string with length not equal to 11, the format function
      // SHALL return the original unmodified string
      fc.assert(
        fc.property(
          fc.string().filter((s) => s.length !== 11),
          (phoneNumber) => {
            const result = formatPhoneNumber(phoneNumber)

            // Should return original string unchanged
            expect(result).toBe(phoneNumber)
          }
        )
      )
    })
  })

  describe('Example-Based Tests', () => {
    it('should format valid 11-digit phone number', () => {
      expect(formatPhoneNumber('01012345678')).toBe('010-1234-5678')
      expect(formatPhoneNumber('01098765432')).toBe('010-9876-5432')
    })

    it('should return original string for invalid lengths', () => {
      expect(formatPhoneNumber('0101234567')).toBe('0101234567') // 10 digits
      expect(formatPhoneNumber('010123456789')).toBe('010123456789') // 12 digits
      expect(formatPhoneNumber('')).toBe('') // empty
      expect(formatPhoneNumber('123')).toBe('123') // too short
    })

    it('should handle non-numeric 11-character strings', () => {
      expect(formatPhoneNumber('abcdefghijk')).toBe('abc-defg-hijk')
    })
  })
})

describe('formatTimeHHmm', () => {
  it('should zero-pad single-digit hour and minute', () => {
    expect(formatTimeHHmm('2026-05-14T00:05:00')).toBe('00:05')
    expect(formatTimeHHmm('2026-05-14T09:30:00')).toBe('09:30')
    expect(formatTimeHHmm('2026-05-14T23:59:00')).toBe('23:59')
  })
})

describe('formatDateGroup', () => {
  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

  it('should format YYYY.MM.DD with Korean weekday for all 7 weekdays', () => {
    // Pick 7 consecutive days; the weekday is computed from the local Date so
    // we don't hardcode a calendar mapping.
    for (let day = 3; day <= 9; day += 1) {
      const iso = `2026-05-0${day}T12:00:00`
      const expectedWeekday = WEEKDAYS[new Date(iso).getDay()]
      expect(formatDateGroup(iso)).toBe(`2026.05.0${day} (${expectedWeekday})`)
    }
  })

  it('should zero-pad month and day', () => {
    const iso = '2026-01-01T12:00:00'
    const expectedWeekday = WEEKDAYS[new Date(iso).getDay()]
    expect(formatDateGroup(iso)).toBe(`2026.01.01 (${expectedWeekday})`)
  })
})
