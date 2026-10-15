/* Serve the bundled coursework frontend with Node's standard library only. */
const http = require('node:http')
const https = require('node:https')
const fs = require('node:fs')
const path = require('node:path')
const { spawn } = require('node:child_process')

const root = path.resolve(__dirname, '../ready-web')
const port = Number(process.env.WEB_PORT || 5173)
const backend = new URL(process.env.API_ORIGIN || 'http://127.0.0.1:8080')
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  console.error('WEB_PORT must be between 1 and 65535.')
  process.exit(1)
}
if (!['http:', 'https:'].includes(backend.protocol)) {
  console.error('API_ORIGIN must use HTTP or HTTPS.')
  process.exit(1)
}
if (!fs.existsSync(path.join(root, 'index.html'))) {
  console.error('Missing ready-web/index.html. Extract the whole ready-to-run ZIP first.')
  process.exit(1)
}
const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.woff2': 'font/woff2',
  '.ico': 'image/x-icon'
}
function sendError(res, status, message) {
  if (res.headersSent) { res.destroy(); return }
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(JSON.stringify({ code: status, message, data: null }))
}
function proxy(req, res) {
  const transport = backend.protocol === 'https:' ? https : http
  const headers = { ...req.headers, host: backend.host }
  const upstream = transport.request({
    protocol: backend.protocol, hostname: backend.hostname,
    port: backend.port || (backend.protocol === 'https:' ? 443 : 80),
    method: req.method, path: req.url, headers
  }, incoming => {
    res.writeHead(incoming.statusCode, incoming.headers)
    incoming.on('error', () => res.destroy())
    incoming.pipe(res)
  })
  upstream.setTimeout(12000, () => upstream.destroy(new Error('Backend timeout')))
  upstream.on('error', () => sendError(res, 502,
    '后端未连接：请确认 IDEA 中的 CampusApplication 正在运行，端口为 8080。'))
  req.on('aborted', () => upstream.destroy())
  res.on('close', () => { if (!res.writableEnded) upstream.destroy() })
  req.pipe(upstream)
}
const server = http.createServer((req, res) => {
  let pathname
  try { pathname = decodeURIComponent(req.url.split('?')[0]) }
  catch { sendError(res, 400, 'Invalid URL'); return }
  if (pathname.includes('\0') || pathname.includes('\\') || pathname.split('/').includes('..')) {
    sendError(res, 400, 'Invalid path'); return
  }
  if (/^\/(api|uploads|samples)(\/|$)/.test(pathname)) { proxy(req, res); return }
  if (!['GET', 'HEAD'].includes(req.method)) {
    res.setHeader('Allow', 'GET, HEAD'); sendError(res, 405, 'Method not allowed'); return
  }
  const candidate = path.resolve(root, '.' + pathname)
  if (candidate !== root && !candidate.startsWith(root + path.sep)) {
    sendError(res, 403, 'Forbidden'); return
  }
  fs.stat(candidate, (error, stat) => {
    let file = candidate
    if (error || !stat.isFile()) {
      if (path.extname(pathname)) { sendError(res, 404, 'File not found'); return }
      file = path.join(root, 'index.html')
    }
    fs.stat(file, (err, info) => {
      if (err) { sendError(res, 404, 'File not found'); return }
      res.writeHead(200, {
        'Content-Type': types[path.extname(file)] || 'application/octet-stream',
        'Content-Length': info.size,
        'X-Content-Type-Options': 'nosniff',
        'Cache-Control': path.extname(file) === '.html' ? 'no-store' : 'public, max-age=3600'
      })
      if (req.method === 'HEAD') { res.end(); return }
      const stream = fs.createReadStream(file)
      stream.on('error', () => res.destroy())
      stream.pipe(res)
    })
  })
})
server.on('error', error => {
  console.error(error.code === 'EADDRINUSE'
    ? `Port ${port} is already in use. Close the previous frontend terminal and try again.`
    : error.message)
  process.exitCode = 1
})
server.listen(port, '127.0.0.1', () => {
  const url = `http://127.0.0.1:${port}`
  console.log(`Campus Exchange frontend: ${url}`)
  console.log('No npm installation required. Keep this window and the IDEA backend running.')
  console.log('Press Ctrl+C to stop.')
  if (process.platform === 'win32' && process.env.NO_BROWSER !== '1') {
    const child = spawn('cmd.exe', ['/c', 'start', '', url], { stdio: 'ignore', windowsHide: true })
    child.on('error', () => console.log(`Open your browser manually: ${url}`))
    child.unref()
  }
})
