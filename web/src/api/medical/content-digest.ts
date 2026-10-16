import { canonicalizeEx } from 'json-canonicalize'

export async function byteDigest(value: BufferSource): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', value)
  return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('')
}
export async function sourceDigest(value: unknown): Promise<string> {
  return byteDigest(new TextEncoder().encode(canonicalizeEx(value, { strictUndefined: true })))
}
