import type { AxiosRequestConfig } from 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    medical?: 'envelope' | 'auth' | 'framework'
    medicalFrameworkIdentity?: { actor: string; generation: number }
  }
}

declare module '*.yaml?raw' {
  const value: string
  export default value
}
