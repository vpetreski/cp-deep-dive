import { test, expect } from "@playwright/test";

/**
 * Smoke test — asserts the shell boots, the major navigation targets resolve
 * without crashes, and the upload widget is present on the home page.
 *
 * This test deliberately does NOT call either backend: it exercises only the
 * frontend routes that work offline.
 */

test.describe("NSP frontend smoke", () => {
  test("home page loads with hero, upload card, and navigation", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/NSP/);
    await expect(
      page.getByRole("heading", { level: 1 }),
    ).toBeVisible();
    // Upload widget shows either tabs or a drop target
    await expect(page.getByRole("tab", { name: /Upload file/i })).toBeVisible();
    await expect(page.getByRole("tab", { name: /Paste JSON/i })).toBeVisible();
  });

  test("navigates to Instances and About", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: /^Instances$/ }).first().click();
    await expect(page).toHaveURL(/\/instances$/);

    await page.getByRole("link", { name: /^About$/ }).first().click();
    await expect(page).toHaveURL(/\/about$/);
    await expect(
      page.getByRole("heading", { name: /NSP Scheduler/i }),
    ).toBeVisible();
  });

  test("pressing ? opens the keyboard shortcuts help", async ({ page }) => {
    await page.goto("/");
    await page.keyboard.press("Shift+/");
    // The help dialog surfaces visible shortcut keys.
    await expect(
      page.getByRole("dialog"),
    ).toBeVisible();
  });

  test("switches backend via BackendSwitch", async ({ page }) => {
    await page.goto("/about");
    const kotlinRadio = page.getByRole("radio", { name: /Kotlin/i }).first();
    await kotlinRadio.click();
    await expect(kotlinRadio).toBeChecked();
    // Persisted across reload
    await page.reload();
    await expect(page.getByRole("radio", { name: /Kotlin/i }).first()).toBeChecked();
  });

  test("theme toggle flips between light and dark", async ({ page }) => {
    await page.goto("/");
    const html = page.locator("html");
    const before = (await html.getAttribute("class")) ?? "";
    await page.getByRole("button", { name: /theme/i }).first().click();
    await page.waitForTimeout(100);
    const after = (await html.getAttribute("class")) ?? "";
    // We can't guarantee direction, only that the class list changes on toggle.
    expect(after).not.toBe(before);
  });
});
