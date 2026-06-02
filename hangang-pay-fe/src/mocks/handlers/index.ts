import { authHandlers } from './auth'
import { userHandlers } from './user'
import { paymentHandlers } from './payment'
import { chargeHandlers } from './charge'
import { exchangeHandlers } from './exchange'
import { merchantHandlers } from './merchant'

export const handlers = [
  ...authHandlers,
  ...userHandlers,
  ...paymentHandlers,
  ...chargeHandlers,
  ...exchangeHandlers,
  ...merchantHandlers,
]
