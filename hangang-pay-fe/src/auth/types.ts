export type UserRole = 'USER' | 'MERCHANT'

export interface CurrentUser {
  id: number
  name: string
  role: UserRole
}
