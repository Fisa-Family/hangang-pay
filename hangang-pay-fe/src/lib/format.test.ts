import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { formatPhoneNumber } from './format'

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
