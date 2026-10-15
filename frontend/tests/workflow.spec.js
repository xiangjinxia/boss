import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const projectRoot = fileURLToPath(new URL('../../', import.meta.url))
const screenshotDir = path.join(projectRoot, 'docs/screenshots')
const webUrl = process.env.WEB_URL || 'http://127.0.0.1:5173'

const errors = []
test.beforeEach(async ({ page }) => {
  errors.length = 0
  page.on('pageerror', (e) => errors.push(e.message))
})
async function login(page, username) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入账号').fill(username)
  await page.getByPlaceholder('请输入密码').fill('Campus123!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)
}
async function screenshot(page, name) {
  await page.evaluate(async () => {
    const images = [...document.images]
    images.forEach((img) => {
      img.loading = 'eager'
    })
    await Promise.all(images.map((img) => img.decode().catch(() => {})))
    await document.fonts.ready
  })
  await page.screenshot({ path: path.join(screenshotDir, name), fullPage: true })
}
test('访客首页、搜索筛选与 375px 手机浏览', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '校园里的宝藏闲置.' })).toBeVisible()
  await expect(page.locator('#market .goods-card')).toHaveCount(12)
  await expect(page.locator('.discovery-section .goods-card')).toHaveCount(8)
  await page.getByText('为什么推荐给我？', { exact: true }).click()
  await expect(page.locator('.recommendation-explainer')).toContainText('规则推荐')
  fs.mkdirSync(screenshotDir, { recursive: true })
  await screenshot(page, '01-market-desktop.png')
  await page.getByRole('textbox', { name: '搜索商品' }).fill('数据结构与算法分析')
  await page.getByRole('button', { name: '搜索好物' }).click()
  await expect(page.locator('#market .goods-card')).toHaveCount(1)
  await page.locator('#market .goods-card').click()
  await expect(page.locator('.detail-info h1')).toContainText('数据结构')
  await page.setViewportSize({ width: 375, height: 812 })
  await screenshot(page, '03-detail-mobile.png')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.goto('/')
  await expect(page.locator('#market .goods-card').first()).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await screenshot(page, '02-market-mobile.png')
  expect(errors).toEqual([])
})
test('学生发布、管理员审核、收藏留言、卖家确认、买家完成', async ({ page, browser }) => {
  const title = `UI 验收教材 ${Date.now()}`
  const adminContext = await browser.newContext({
    baseURL: webUrl,
    recordVideo: { dir: path.join(projectRoot, 'test-results/browser/admin-video') },
    viewport: { width: 1440, height: 1000 }
  })
  const buyerContext = await browser.newContext({
    baseURL: webUrl,
    recordVideo: { dir: path.join(projectRoot, 'test-results/browser/buyer-video') },
    viewport: { width: 1440, height: 1000 }
  })
  const admin = await adminContext.newPage(),
    buyer = await buyerContext.newPage()
  admin.on('pageerror', (e) => errors.push(e.message))
  buyer.on('pageerror', (e) => errors.push(e.message))
  try {
    await login(page, 'seller')
    await page.goto('/publish')
    await page.getByPlaceholder('品牌、名称、特点，让同学一眼找到它').fill(title)
    await page.locator('.el-select').first().click()
    await page.getByRole('option', { name: '教材书籍' }).click()
    await page.getByPlaceholder('给它一个合理的价格').fill('25')
    await page
      .locator('input[type=file]')
      .setInputFiles(path.join(projectRoot, 'scripts/fixtures/book.png'))
    await expect(page.locator('.upload-item')).toHaveCount(1)
    await page.locator('textarea').fill('课程验收实物图书，九成新，校内当面交易。')
    await page.getByRole('button', { name: '提交审核', exact: true }).click()
    await expect(page).toHaveURL(/\/goods\/\d+/)
    const goodsUrl = new URL(page.url()).pathname
    await expect(page.locator('.detail-info .badge').first()).toContainText('待审核')
    await login(admin, 'admin')
    await admin.goto('/admin?tab=goods')
    const row = admin.getByRole('row').filter({ hasText: title })
    await row.getByRole('button', { name: '通过审核' }).click()
    await admin
      .locator('.el-message-box')
      .getByRole('button', { name: '确认', exact: true })
      .click()
    await expect(row.locator('.badge')).toContainText('在售中')
    await admin.goto('/admin')
    await expect(admin.locator('canvas')).toHaveCount(2)
    await screenshot(admin, '04-admin-dashboard.png')
    await login(buyer, 'buyer')
    await buyer.goto(goodsUrl)
    await buyer.getByRole('button', { name: '收藏', exact: true }).click()
    await expect(buyer.getByRole('button', { name: '已收藏', exact: true })).toBeVisible()
    await buyer.locator('textarea').fill('同学你好，这本书还在吗？')
    await buyer.getByRole('button', { name: '发送留言' }).click()
    await expect(buyer.locator('.comment-body p')).toContainText(['同学你好，这本书还在吗？'])
    await buyer.reload()
    await expect(buyer.locator('.comment-body p')).toContainText(['同学你好，这本书还在吗？'])
    await buyer.getByRole('button', { name: '我想要 · 发起交易' }).click()
    await buyer.locator('.el-message-box').getByRole('button', { name: '确认下单' }).click()
    await expect(buyer).toHaveURL(/tab=buying/)
    await page.goto('/me?tab=selling')
    const sellerOrder = page.locator('.list-row').filter({ hasText: title })
    await sellerOrder.getByRole('button', { name: '接受交易' }).click()
    await page
      .locator('.el-message-box')
      .getByRole('button', { name: '接受交易', exact: true })
      .click()
    await expect(sellerOrder.locator('.row-status')).toContainText('待线下交易')
    await buyer.reload()
    const buyerOrder = buyer.locator('.list-row').filter({ hasText: title })
    await buyerOrder.getByRole('button', { name: '确认完成' }).click()
    await buyer
      .locator('.el-message-box')
      .getByRole('button', { name: '确认完成', exact: true })
      .click()
    await expect(buyerOrder.locator('.row-status')).toContainText('已完成')
    await screenshot(buyer, '05-completed-order.png')
    await buyerOrder.getByRole('link', { name: /交易进度/ }).click()
    await expect(buyer.locator('.event-timeline li')).toHaveCount(3)
    await buyer
      .getByPlaceholder('商品是否符合描述？交易体验如何？（5–500 字）')
      .fill('教材很整洁，卖家准时，校园交接顺利。')
    await buyer.getByRole('button', { name: '发布交易评价' }).click()
    await expect(buyer.getByRole('heading', { name: '这次交易的评价' })).toBeVisible()
    await expect(buyer.locator('.event-timeline li')).toHaveCount(4)
    await buyer.reload()
    await expect(
      buyer.getByText('教材很整洁，卖家准时，校园交接顺利。', { exact: true })
    ).toBeVisible()
    await screenshot(buyer, '06-order-timeline-review.png')
    await buyer.getByRole('link', { name: '去卖家小铺' }).click()
    await expect(
      buyer
        .locator('.review-item')
        .filter({ hasText: title })
        .filter({ hasText: '教材很整洁，卖家准时，校园交接顺利。' })
    ).toBeVisible()
    await screenshot(buyer, '07-seller-profile.png')
    await buyer.setViewportSize({ width: 375, height: 812 })
    expect(
      await buyer.evaluate(() => document.documentElement.scrollWidth <= innerWidth)
    ).toBeTruthy()
    await buyer.goto('/notifications')
    await expect(buyer.locator('.notification-item').first()).toBeVisible()
    await buyer.getByRole('button', { name: '全部标为已读' }).click()
    await expect(buyer.locator('.page-subtitle')).toContainText('0 条未读')
    expect(
      await buyer.evaluate(() => document.documentElement.scrollWidth <= innerWidth)
    ).toBeTruthy()
    await buyer.setViewportSize({ width: 1440, height: 1000 })

    await buyer.goto(goodsUrl)
    await expect(buyer.locator('.detail-info .badge').first()).toContainText('已售出')
    expect(errors).toEqual([])
  } finally {
    await adminContext.close()
    await buyerContext.close()
  }
})

test('管理分类公告发布到前台、分页和区间统计', async ({ page }) => {
  const suffix = String(Date.now()),
    title = '校园公告 UI ' + suffix
  await login(page, 'admin')
  await page.goto('/admin?tab=categories')
  await page.getByRole('button', { name: '新增分类', exact: true }).click()
  await page.getByPlaceholder('例如：毕业好物').fill('毕业好物' + suffix)
  await page.getByRole('button', { name: '保存配置' }).click()
  const cat = page.getByRole('row').filter({ hasText: '毕业好物' + suffix })
  await expect(cat).toBeVisible()
  await page.getByRole('button', { name: '公告管理', exact: true }).click()
  await page.getByRole('button', { name: '新增公告', exact: true }).click()
  await page.getByPlaceholder('输入公告标题').fill(title)
  await page
    .getByPlaceholder('输入公告内容，使用纯文本展示')
    .fill('毕业季闲置循环，欢迎同学们在校内公共场所交易。')
  await page.locator('.el-dialog .el-select').click()
  await page.getByRole('option', { name: '发布到首页' }).click()
  await page.getByRole('button', { name: '保存配置' }).click()
  await expect(page.getByRole('row').filter({ hasText: title })).toContainText('已发布')
  await page.goto('/')
  await page
    .locator('.announcement-strip')
    .getByRole('button', { name: new RegExp(title) })
    .click()
  await expect(page.locator('.el-dialog')).toContainText('毕业季闲置循环')
  await page.locator('.el-dialog__headerbtn').click()
  await page.goto('/admin')
  await page.getByPlaceholder('开始日期').fill('2000-01-01')
  await page.getByPlaceholder('结束日期').fill('2000-01-03')
  await page.getByRole('button', { name: '更新统计' }).click()
  await expect(page.locator('.period-summary')).toContainText('发起预约 0 笔')
  await page.setViewportSize({ width: 375, height: 812 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/admin?tab=goods')
  const first = await page.locator('.data-table tbody tr').first().innerText()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect.poll(() => page.locator('.data-table tbody tr').first().innerText()).not.toBe(first)
  expect(errors).toEqual([])
})
