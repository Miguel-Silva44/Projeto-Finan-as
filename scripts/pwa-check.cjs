const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const base = process.env.BASE_URL || 'http://localhost:8082';
(async () => {
  const browser = await chromium.launch();
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    await page.goto(`${base}/auth/login`);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    await page.locator('[name=email]').fill('dev@financas.local');
    await page.locator('[name=password]').fill('DevFinanceiro123!');
    await Promise.all([page.waitForURL('**/dashboard'), page.locator('form button[type=submit], form button').first().click()]);
    await page.goto(`${base}/settings`);
    const keys = await page.evaluate(async () => {
      const result = [];
      for (const name of await caches.keys()) {
        for (const request of await (await caches.open(name)).keys()) result.push(new URL(request.url).pathname);
      }
      return result.sort();
    });
    assert.deepEqual(keys, ['/icons/icon-192.png', '/icons/icon-512.png', '/icons/maskable-512.png', '/offline.html']);
    await context.setOffline(true);
    await page.goto(`${base}/dashboard`);
    await page.getByRole('heading', { name: 'Não foi possível conectar' }).waitFor();
    assert.equal(await page.locator('form').count(), 0);
    await context.setOffline(false);
    await page.getByRole('link', { name: 'Tentar novamente' }).click();
    await page.waitForURL('**/dashboard');
    const logout = page.locator('form[action="/auth/logout"]');
    await Promise.all([page.waitForURL('**/auth/login?logout'), logout.locator('button').click()]);
    await page.goBack();
    await page.waitForURL('**/auth/login');
    console.log('PWA aprovada: login, cache público, fallback offline, retorno online e proteção após logout.');
    await context.close();
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
