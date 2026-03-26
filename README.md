# Periplus Shopping Cart — Automated Test Suite

Selenium + TestNG automation for the Periplus.com shopping cart, generating a single LaTeX/PDF report after every run.

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 11 or higher |
| Maven | 3.6 or higher |
| Google Chrome | Any recent version (WebDriverManager downloads the matching driver automatically) |
| pdflatex | Optional, only needed to compile the report to PDF |

---

## Configuration

Edit `src/test/resources/config.properties` before running:

```properties
base.url=https://www.periplus.com/
email=your@email.com
password=yourpassword
search.keyword=keywoard
```

Use a registered Periplus account. The suite logs in once and reuses the session across all tests.
Note: the keyword should at least give 1 output when searched in the web

---

## How to Run

```bash
mvn clean test
```

The browser opens automatically, runs all 13 test cases, then closes.

**Report output:** `target/test-report/FULL_REPORT.tex`

To compile to PDF:

```bash
cd target/test-report
pdflatex FULL_REPORT.tex
pdflatex FULL_REPORT.tex   # run twice to generate the table of contents
```

> **Note:** If Periplus returns HTTP 429 (Too Many Requests), the suite will fail immediately with a clear error message. Wait at least 1 hour, or switch to a different network, before re-running.

---

## Test Cases

| ID | Title | Expected Result |
|---|---|---|
| TC-CART-001 | Add product to cart via search | Product appears in cart |
| TC-CART-002 | Add product from product detail page | Product appears in cart |
| TC-CART-003 | Cart badge increments after adding a product | Badge count increases by 1 |
| TC-CART-004 | Add two different products | Cart shows 2 distinct items |
| TC-CART-005 | Increase quantity using the + button in cart | Quantity updates to 2 |
| TC-CART-006 | Remove a product from the cart | Cart is empty after removal |
| TC-CART-007 | Cart total recalculates after quantity change | Total increases proportionally |
| TC-CART-008 | Cart persists after page refresh | Product still in cart after F5 |
| TC-CART-009 | Cart persists after logout and re-login | Product still in cart after re-login |
| TC-CART-010 | Re-adding same product from search increments quantity | Periplus resets qty to 1 instead of incrementing (this is currently a known site bug) |
| TC-CART-011 | Empty cart shows no products | Cart page shows no items |
| TC-CART-012 | Cart badge reflects count with multiple products | Badge shows ≥ 2 |
| TC-CART-013 | Proceed to Checkout navigates to checkout flow | URL moves away from cart page |

---

## Project Structure

```
src/test/java/periplus/
  base/         BaseTest.java          — Chrome setup/teardown (@BeforeClass/@AfterClass)
  pages/        CartPage.java          — Cart page interactions
                HomePage.java          — Search and add-to-cart from search results
                LoginPage.java         — Login/logout
                ProductPage.java       — Add to cart from product detail page
  report/       TestCaseReport.java    — Builds the FULL_REPORT.tex after each run
  tests/        CartTest.java          — All 13 test cases

src/test/resources/
  config.properties                    — Credentials and base URL (edit before running)

pom.xml                                — Maven dependencies (Selenium, TestNG, WebDriverManager)
testng.xml                             — Test suite definition
```
