import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'
export default defineConfig({
  testDir: './tests',
  workers: 1,
  timeout: 90000,
  expect: { timeout: 12000 },
  outputDir: '../test-results/browser',
  reporter: [
    ['list'],
    [
      'json',
      {
        outputFile: fileURLToPath(new URL('../test-results/browser-results.json', import.meta.url))
      }
    ]
  ],
  use: {
    baseURL: process.env.WEB_URL || 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 1000 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    screenshot: 'only-on-failure',
    video: 'on',
    trace: 'retain-on-failure',
    launchOptions: {
      executablePath: process.env.CHROMIUM_PATH || undefined,
      args: ['--no-sandbox', '--disable-dev-shm-usage']
    }
  }
})
