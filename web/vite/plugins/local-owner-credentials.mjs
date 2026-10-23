import { closeSync, constants, fstatSync, openSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const endpoint = '/__local/owner-credentials'
const origin = 'http://127.0.0.1:18083'
const credentialPath = fileURLToPath(new URL('../../../.implementation-work/runtime/owner-credentials.json', import.meta.url))

function reply(response, status, value) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store, max-age=0',
    'Pragma': 'no-cache',
    'X-Content-Type-Options': 'nosniff',
    'Cross-Origin-Resource-Policy': 'same-origin'
  })
  response.end(JSON.stringify(value))
}

export default function localOwnerCredentials() {
  return {
    name: 'medical-local-owner-credentials',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        if (new URL(request.url, origin).pathname !== endpoint) return next()
        const address = request.socket.remoteAddress
        if (!['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(address)
            || request.headers.host !== '127.0.0.1:18083'
            || request.headers.origin !== undefined && request.headers.origin !== origin
            || request.headers['sec-fetch-site'] !== undefined
              && !['same-origin', 'none'].includes(request.headers['sec-fetch-site'])) {
          return reply(response, 403, { message: '仅允许本机登录页读取账号' })
        }
        if (request.method !== 'GET') return reply(response, 405, { message: '仅支持读取账号' })
        if (request.url !== endpoint) return reply(response, 400, { message: '账号读取不接受查询参数' })

        // 账号只从本机私有文件读取，构建文件不包含凭据。
        const descriptor = openSync(credentialPath, constants.O_RDONLY | constants.O_NOFOLLOW)
        let content
        try {
          const metadata = fstatSync(descriptor)
          if (!metadata.isFile() || metadata.uid !== process.getuid() || (metadata.mode & 0o777) !== 0o600) {
            throw new Error('本机账号文件权限无效')
          }
          content = readFileSync(descriptor, 'utf8')
        } finally {
          closeSync(descriptor)
        }
        const credentials = JSON.parse(content)
        if (Object.keys(credentials).sort().join(',') !== 'display_name,password,username'
            || Object.values(credentials).some(value => typeof value !== 'string' || value.length === 0)) {
          throw new Error('本机账号文件格式无效')
        }
        reply(response, 200, credentials)
      })
    }
  }
}
