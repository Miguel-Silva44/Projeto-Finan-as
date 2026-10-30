// Browser tooling only. The application runtime and build are Java/Maven.
const { chromium, webkit } = require(
  process.env.PLAYWRIGHT_MODULE || "playwright",
);
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const base = process.env.BASE_URL || "http://localhost:8080";
const today = new Intl.DateTimeFormat("en-CA", {
  timeZone: "America/Sao_Paulo",
}).format(new Date());
const prior = new Date(`${today}T12:00:00Z`);
prior.setUTCMonth(prior.getUTCMonth() - 1);
prior.setUTCDate(10);
const purchaseDate = prior.toISOString().slice(0, 10);
async function submit(page, form, button) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: "networkidle" }),
    form.getByRole("button", { name: button, exact: true }).click(),
  ]);
  const error = page.getByRole("heading", {
    name: "Não foi possível concluir",
  });
  if (await error.count())
    throw new Error(await page.locator("main").innerText());
  assert(!(await page.title()).includes("Error"), await page.title());
}
async function run(engine, name, width, height, mobile) {
  const browser = await engine.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width, height },
    isMobile: mobile,
    hasTouch: mobile,
    locale: "pt-BR",
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  const email = `browser-${name}-${Date.now()}@example.test`,
    password = "Teste navegador seguro 123!";
  try {
    await page.goto(`${base}/auth/register`);
    await page.getByLabel("Seu nome").fill("Marina Teste");
    await page.getByLabel("E-mail", { exact: true }).fill(email);
    await page.locator("input[name=password]").fill(password);
    await submit(page, page.locator("form"), "Criar minha conta →");
    const mails = await fs.readdir("data/mail");
    let token;
    for (const file of mails) {
      const text = await fs.readFile(path.join("data/mail", file), "utf8");
      if (text.includes(email) && text.includes("/auth/verify?token=")) {
        token = text.match(/token=([^\s]+)/)[1];
        break;
      }
    }
    assert(token, "E-mail de confirmação local não encontrado");
    await page.goto(`${base}/auth/verify?token=${token}`);
    await submit(page, page.locator("form"), "Confirmar meu e-mail");
    await page.getByLabel("E-mail", { exact: true }).fill(email);
    await page.locator("input[name=password]").fill(password);
    await submit(page, page.locator("form"), "Entrar na minha conta →");
    await page.goto(`${base}/accounts`);
    let form = page.locator('form[action="/accounts"]');
    await form.getByLabel("Nome da conta").fill("Conta principal");
    await form.getByLabel("Saldo inicial").fill("1000,00");
    await form.getByLabel("Data inicial").fill(`${today.slice(0, 4)}-01-01`);
    await submit(page, form, "Salvar conta");
    form = page.locator('form[action="/accounts"]');
    await form.getByLabel("Nome da conta").fill("Reserva");
    await form.getByLabel("Data inicial").fill(`${today.slice(0, 4)}-01-01`);
    await submit(page, form, "Salvar conta");
    await page.goto(`${base}/transactions`);
    form = page.locator('form[action="/transactions"]');
    await form.getByLabel("Descrição").fill("Mercado da semana");
    await form.getByLabel("Valor").fill("200,00");
    await form
      .locator('[name="account_id"]')
      .selectOption({ label: "Conta principal" });
    const categoryOption = await form
      .locator('[name="category_id"] option')
      .allTextContents();
    const categoryLabel = categoryOption.find((s) =>
      s.startsWith("Alimentação"),
    );
    await form
      .locator('[name="category_id"]')
      .selectOption({ label: categoryLabel });
    await form.locator("select[name=status]").selectOption("REALIZADO");
    await submit(page, form, "Salvar lançamento");
    assert(
      (await page.locator("main").innerText()).includes("Mercado da semana"),
    );
    form = page.locator('form[action="/transfers"]');
    await form
      .locator('[name="source_id"]')
      .selectOption({ label: "Conta principal" });
    await form
      .locator('[name="destination_id"]')
      .selectOption({ label: "Reserva" });
    await form.getByLabel("Valor").fill("100");
    await submit(page, form, "Confirmar transferência");
    await page.goto(`${base}/budgets`);
    const budget = page
      .locator(".budget-card")
      .filter({
        has: page.getByRole("heading", { name: "Alimentação", exact: true }),
      });
    await budget.getByLabel("Limite mensal").fill("500");
    await submit(page, budget.locator("form"), "Salvar limite");
    await page.goto(`${base}/cards`);
    form = page.locator('form[action="/cards"]');
    await form.getByLabel("Nome do cartão").fill("Meu cartão");
    await form.getByLabel("Limite informado").fill("3000");
    await submit(page, form, "Salvar cartão");
    form = page.locator('form[action="/purchases/preview"]');
    await form
      .locator("select[name=card_id]")
      .selectOption({ label: "Meu cartão" });
    await form.getByLabel("Descrição").fill("Compra planejada");
    await form
      .locator('[name="category_id"]')
      .selectOption({ label: categoryLabel });
    await form.getByLabel("Valor").fill("100");
    await form.getByLabel("Parcelas", { exact: true }).fill("1");
    await form.getByLabel("Data da compra").fill(purchaseDate);
    await submit(page, form, "Conferir parcelas →");
    await submit(
      page,
      page.locator('form[action="/purchases"]'),
      "Confirmar compra e parcelas",
    );
    let invoice = page.locator(".invoice").first();
    await invoice.locator("summary").click();
    await submit(
      page,
      invoice.locator('form[action$="/close"]'),
      "Fechar fatura",
    );
    invoice = page.locator(".invoice").first();
    await invoice.locator("summary").click();
    form = invoice.locator('form[action$="/pay"]');
    await form
      .locator('[name="account_id"]')
      .selectOption({ label: "Conta principal" });
    page.once("dialog", (dialog) => dialog.accept());
    await submit(page, form, "Registrar pagamento integral");
    assert(
      (await page.locator(".invoice").first().innerText()).includes("PAGA"),
    );
    for (const route of [
      "dashboard",
      "transactions",
      "accounts",
      "categories",
      "cards",
      "budgets",
      "recurrences",
      "reports",
      "settings",
      "audit",
    ]) {
      const response = await page.goto(`${base}/${route}`);
      assert.equal(response.status(), 200, route);
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth > window.innerWidth + 1,
      );
      assert(!overflow, `${name}: overflow em ${route}`);
    }
    await page.goto(`${base}/dashboard`);
    await fs.mkdir("target/screenshots", { recursive: true });
    await page.screenshot({
      path: `target/screenshots/${name}.png`,
      fullPage: true,
    });
    if (mobile) {
      await page.setViewportSize({ width: height, height: width });
      await page.goto(`${base}/transactions`);
      assert(
        !(await page.evaluate(
          () => document.documentElement.scrollWidth > window.innerWidth + 1,
        )),
      );
      await page.setViewportSize({ width: 320, height: 740 });
      await page.goto(`${base}/dashboard`);
      assert(
        !(await page.evaluate(
          () => document.documentElement.scrollWidth > window.innerWidth + 1,
        )),
      );
    }
    assert.deepEqual(errors, [], `${name}: erros JavaScript`);
    console.log(
      `${name}: cadastro, confirmação, login, contas, lançamento, transferência, orçamento, compra, fatura e 10 telas OK`,
    );
    // Leave no example account or financial records in the user's local application.
    await page.goto(`${base}/settings`);
    await page
      .locator("summary")
      .filter({ hasText: "Excluir minha conta" })
      .click();
    form = page.locator('form[action="/me/delete"]');
    await form.getByLabel("Digite EXCLUIR").fill("EXCLUIR");
    await form.getByLabel("Confirme sua senha").fill(password);
    page.once("dialog", (dialog) => dialog.accept());
    await submit(page, form, "Excluir minha conta");
  } catch (error) {
    await fs.mkdir("target/screenshots", { recursive: true });
    await page.screenshot({path:`target/screenshots/${name}-failure.png`,fullPage:true});
    console.error(name, page.url(), (await page.locator("body").innerText()).slice(0,900));
    throw error;
  } finally {
    await browser.close();
  }
}
(async () => {
  if (!process.env.BROWSER || process.env.BROWSER === "chromium") await run(chromium, "desktop-chromium", 1440, 1000, false);
  if (!process.env.BROWSER || process.env.BROWSER === "chromium") await run(chromium, "mobile-chromium", 390, 844, true);
  if (!process.env.BROWSER || process.env.BROWSER === "webkit") await run(webkit, "mobile-webkit", 390, 844, true);
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
