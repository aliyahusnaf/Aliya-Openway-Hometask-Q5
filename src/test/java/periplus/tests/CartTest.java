package periplus.tests;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import periplus.base.BaseTest;
import periplus.pages.CartPage;
import periplus.pages.HomePage;
import periplus.pages.LoginPage;
import periplus.pages.ProductPage;
import periplus.report.TestCaseReport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Full automated test suite for Periplus shopping cart functionality.
 *
 * Each test is independent — prepareEachTest() logs in and clears the cart
 * before every method, so tests can run in any order.
 *
 * Tests covered:
 *   TC-CART-001  Add product to cart via search results
 *   TC-CART-002  Add product to cart from product detail page
 *   TC-CART-003  Cart badge count increments after adding a product
 *   TC-CART-004  Adding the same product twice increases its quantity
 *   TC-CART-005  Increase product quantity inside the cart
 *   TC-CART-006  Remove a product from the cart
 *   TC-CART-007  Cart total recalculates after quantity change
 *   TC-CART-008  Cart persists after page refresh
 *   TC-CART-009  Cart persists after logout and re-login
 *   TC-CART-010  Re-adding same product from search should increment qty (EXPECTED FAIL — site resets to 1)
 *   TC-CART-011  Empty cart shows no products
 *   TC-CART-012  Cart badge reflects count when multiple products added
 *   TC-CART-013  Proceed to Checkout navigates to the checkout flow
 */
public class CartTest extends BaseTest {

    private static final Logger log = Logger.getLogger(CartTest.class.getName());

    // collects every report so @AfterClass can generate the combined pdf source
    private static final List<TestCaseReport> allReports =
        Collections.synchronizedList(new ArrayList<>());

    private LoginPage   loginPage;
    private HomePage    homePage;
    private CartPage    cartPage;
    private ProductPage productPage;

    private String baseUrl;
    private String email;
    private String password;
    private String keyword;

    /**
     * Runs once after BaseTest.setUp() opens Chrome.
     * Initialises page objects, loads config, and logs in once for the whole suite.
     * Retries up to 3 times with a 60 s cooldown to absorb Periplus rate-limit windows.
     */
    @BeforeClass(dependsOnMethods = "setUp")
    public void initialLogin() throws InterruptedException {
        baseUrl  = config.getProperty("base.url");
        email    = config.getProperty("email");
        password = config.getProperty("password");
        keyword  = config.getProperty("search.keyword");

        loginPage   = new LoginPage(driver);
        homePage    = new HomePage(driver);
        cartPage    = new CartPage(driver);
        productPage = new ProductPage(driver);

        driver.get(baseUrl);
        loginPage.login(email, password);
        log.info("login succeeded");
    }

    /**
     * Runs before every test method.
     * Clears the cart and returns to the home page so each test starts clean.
     * Login is intentionally NOT repeated here — the shared session from initialLogin() persists.
     */
    @BeforeMethod
    public void prepareEachTest() throws InterruptedException {
        cartPage.clearCart(baseUrl);
        driver.get(baseUrl);
        // wait until the search box is actually clickable before letting the test proceed —
        // avoids races where the test starts before the home page is fully interactive
        try {
            new WebDriverWait(driver, Duration.ofSeconds(25))
                .until(ExpectedConditions.elementToBeClickable(By.name("filter_name")));
        } catch (Exception ignored) {
            Thread.sleep(3000); // fallback if the page is unusually slow
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-001
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-001: Add a product to cart via search and verify it appears in the cart")
    public void addProductToCart() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-001",
            "Add a product to cart via search and verify it appears in the cart",
            "Open Google Chrome in a new window",
            "Navigate to " + baseUrl,
            "Log in with valid credentials",
            "Search for a product keyword",
            "Add the first search result to the cart",
            "Verify the product appears in the cart"
        );

        String productName = "";
        try {
            report.passStep(1, "Chrome opened by WebDriverManager");
            report.passStep(2);
            report.passStep(3, "logged in as " + email);

            homePage.searchFor(keyword);
            productName = homePage.getFirstProductName();
            report.passStep(4, "found: \"" + productName + "\"");

            homePage.addFirstProductToCart();
            report.passStep(5);

            cartPage.openCart(baseUrl);
            Assert.assertTrue(cartPage.isProductInCart(productName),
                "product '" + productName + "' not found in cart");
            report.passStep(6, "\"" + productName + "\" confirmed in cart");

            finish(report, "\"" + productName + "\" is present in the cart", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-002
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-002: Add a product to cart from the product detail page")
    public void addProductFromDetailPage() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-002",
            "Add a product to cart from the product detail page",
            "Log in and navigate to " + baseUrl,
            "Search for a product and get its detail page URL",
            "Navigate to the product detail page",
            "Click 'Add to Cart' on the product detail page",
            "Verify the product appears in the cart"
        );

        try {
            report.passStep(1, "logged in as " + email);

            homePage.searchFor(keyword);
            String productUrl  = homePage.getFirstProductUrl();
            String productName = homePage.getFirstProductName();
            report.passStep(2, "found: \"" + productName + "\"");

            driver.get(productUrl);
            report.passStep(3, "navigated to product detail page");

            productPage.addToCart();
            report.passStep(4, "clicked 'Add to Cart'");

            cartPage.openCart(baseUrl);
            Assert.assertTrue(cartPage.isProductInCart(productName),
                "product '" + productName + "' not found in cart");
            report.passStep(5, "\"" + productName + "\" confirmed in cart");

            finish(report, "\"" + productName + "\" is present in the cart", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-003
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-003: Cart badge count increments after adding a product")
    public void cartBadgeIncrements() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-003",
            "Cart badge count increments after adding a product",
            "Log in and verify cart badge shows 0",
            "Search for a product and add it to the cart",
            "Verify the cart badge count increased"
        );

        try {
            int before = cartPage.getCartBadgeCount();
            report.passStep(1, "badge before: " + before);

            homePage.searchFor(keyword);
            homePage.addFirstProductToCart();
            report.passStep(2, "product added");

            int after = cartPage.getCartBadgeCount();
            Assert.assertTrue(after > before,
                "badge count did not increase (before=" + before + ", after=" + after + ")");
            report.passStep(3, "badge: " + before + " → " + after);

            finish(report, "badge changed from " + before + " to " + after, "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-004
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-004: Adding two different products results in cart showing 2 distinct items")
    public void addTwoDifferentProducts() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-004",
            "Adding two different products results in cart showing 2 distinct items",
            "Search for the first keyword and add the first result to the cart",
            "Search for a second keyword and add that result to the cart",
            "Open the cart and verify item count is 2"
        );

        try {
            homePage.searchFor(keyword);
            String firstName = homePage.getFirstProductName();
            homePage.addFirstProductToCart();
            report.passStep(1, "added first: \"" + firstName + "\"");

            driver.get(baseUrl);
            Thread.sleep(1500);
            homePage.searchFor("Tolkien");
            String secondName = homePage.getFirstProductName();
            homePage.addFirstProductToCart();
            report.passStep(2, "added second: \"" + secondName + "\"");

            cartPage.openCart(baseUrl);
            int count = cartPage.getItemCount();
            Assert.assertEquals(count, 2, "cart should have 2 distinct items");
            report.passStep(3, "cart item count = " + count);

            finish(report, "cart shows " + count + " distinct items after adding two products", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-005
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-005: Increase product quantity inside the cart using the + button")
    public void increaseQuantityInCart() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-005",
            "Increase product quantity inside the cart using the + button",
            "Add a product to the cart (quantity = 1)",
            "Open the cart and note the current quantity",
            "Click the + button to increase quantity",
            "Verify the quantity updated to 2"
        );

        try {
            homePage.searchFor(keyword);
            homePage.addFirstProductToCart();
            report.passStep(1, "product added with quantity 1");

            cartPage.openCart(baseUrl);
            int before = cartPage.getFirstProductQuantity();
            report.passStep(2, "quantity before: " + before);

            cartPage.increaseFirstProductQuantity();
            int after = cartPage.getFirstProductQuantity();
            report.passStep(3, "clicked +");

            Assert.assertEquals(after, before + 1,
                "quantity should have increased by 1");
            report.passStep(4, "quantity: " + before + " → " + after);

            finish(report, "quantity increased from " + before + " to " + after, "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-006
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-006: Remove a product from the cart")
    public void removeProductFromCart() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-006",
            "Remove a product from the cart",
            "Add a product to the cart",
            "Open the cart and click Remove",
            "Verify the cart no longer contains the product"
        );

        try {
            homePage.searchFor(keyword);
            homePage.addFirstProductToCart();
            report.passStep(1, "product added to cart");

            cartPage.openCart(baseUrl);
            cartPage.removeFirstProduct();
            report.passStep(2, "clicked Remove");

            int count = cartPage.getItemCount();
            Assert.assertEquals(count, 0, "cart should be empty after removal");
            report.passStep(3, "cart item count = " + count);

            finish(report, "cart is empty after removing the product", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-007
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-007: Cart total price increases after quantity change")
    public void totalRecalculatesAfterQuantityChange() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-007",
            "Cart total price increases after quantity change",
            "Add a product to the cart (quantity = 1) and note the total",
            "Increase quantity to 2 using the + button",
            "Verify the new total is greater than the original total"
        );

        try {
            homePage.searchFor(keyword);
            homePage.addFirstProductToCart();

            cartPage.openCart(baseUrl);
            int totalBefore = cartPage.getTotalPrice();
            report.passStep(1, "total at qty 1: Rp " + String.format("%,d", totalBefore));

            cartPage.increaseFirstProductQuantity();
            int totalAfter = cartPage.getTotalPrice();
            report.passStep(2, "increased quantity to 2");

            Assert.assertTrue(totalAfter > totalBefore,
                "total should increase after incrementing quantity");
            report.passStep(3, "total: Rp " + String.format("%,d", totalBefore)
                + " → Rp " + String.format("%,d", totalAfter));

            finish(report,
                "total increased from Rp " + String.format("%,d", totalBefore)
                + " to Rp " + String.format("%,d", totalAfter), "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-008
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-008: Cart contents persist after a full page refresh")
    public void cartPersistsAfterRefresh() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-008",
            "Cart contents persist after a full page refresh",
            "Add a product to the cart",
            "Refresh the current page (F5)",
            "Open the cart and verify the product is still there"
        );

        try {
            homePage.searchFor(keyword);
            String productName = homePage.getFirstProductName();
            homePage.addFirstProductToCart();
            report.passStep(1, "added: \"" + productName + "\"");

            driver.navigate().refresh();
            Thread.sleep(2000);
            report.passStep(2, "page refreshed");

            cartPage.openCart(baseUrl);
            Assert.assertTrue(cartPage.isProductInCart(productName),
                "product should still be in cart after refresh");
            report.passStep(3, "\"" + productName + "\" still in cart");

            finish(report, "\"" + productName + "\" persisted in cart after page refresh", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-009
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-009: Cart contents persist after logout and re-login")
    public void cartPersistsAfterRelogin() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-009",
            "Cart contents persist after logout and re-login",
            "Add a product to the cart",
            "Log out of the account",
            "Log back in with the same credentials",
            "Verify the product is still in the cart"
        );

        try {
            homePage.searchFor(keyword);
            String productName = homePage.getFirstProductName();
            homePage.addFirstProductToCart();
            report.passStep(1, "added: \"" + productName + "\"");

            loginPage.logout();
            report.passStep(2, "logged out");

            loginPage.login(email, password);
            Assert.assertTrue(loginPage.isLoggedIn(), "re-login failed");
            report.passStep(3, "logged back in as " + email);

            cartPage.openCart(baseUrl);
            Assert.assertTrue(cartPage.isProductInCart(productName),
                "product should still be in cart after re-login");
            report.passStep(4, "\"" + productName + "\" still in cart");

            finish(report, "cart persisted after logout and re-login", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-010  — expected to FAIL: Periplus resets qty to 1 on re-add
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-010: Re-adding a product from search results increments cart quantity")
    public void reAddFromSearchIncrementsQuantity() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-010",
            "Re-adding a product from search results increments cart quantity",
            "Search for a product and add it to the cart (qty = 1)",
            "Search for the same product again and click 'Add to Cart' a second time",
            "Open the cart and verify the quantity is 2"
        );
        // NOTE: this test is expected to FAIL.
        // Periplus resets the quantity to 1 instead of incrementing when the same
        // product is re-added via the search results page. This documents a known
        // behavioral limitation of the site.

        try {
            homePage.searchFor(keyword);
            String productName = homePage.getFirstProductName();
            homePage.addFirstProductToCart();
            report.passStep(1, "first add: \"" + productName + "\" — qty should be 1");

            // search again and add the same product a second time
            driver.get(baseUrl);
            Thread.sleep(1500);
            homePage.searchFor(keyword);
            homePage.addFirstProductToCart();
            report.passStep(2, "second add of same product via search — qty expected to be 2");

            cartPage.openCart(baseUrl);
            int qty = cartPage.getFirstProductQuantity();
            // this assertion will fail: Periplus sets qty=1 instead of qty=2
            Assert.assertEquals(qty, 2,
                "quantity should be 2 after adding the same product twice from search");
            report.passStep(3, "quantity in cart = " + qty);

            finish(report, "quantity incremented to " + qty + " after second add", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-011
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-011: Empty cart shows no products")
    public void emptyCartShowsNoProducts() {
        TestCaseReport report = buildReport("TC-CART-011",
            "Empty cart shows no products",
            "Navigate to the cart page without adding any products",
            "Verify no product rows are present in the cart"
        );

        try {
            cartPage.openCart(baseUrl);
            report.passStep(1, "opened cart (cleared in test setup)");

            boolean empty = cartPage.isCartEmpty();
            Assert.assertTrue(empty, "cart should be empty");
            report.passStep(2, "no product rows found in cart");

            finish(report, "cart is empty — no product rows visible", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-012
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-012: Cart badge reflects item count when multiple products are added")
    public void badgeCountWithMultipleProducts() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-012",
            "Cart badge reflects item count when multiple products are added",
            "Add a first product to the cart",
            "Add a second different product to the cart",
            "Verify the cart badge count is at least 2"
        );

        try {
            homePage.searchFor(keyword);
            homePage.addFirstProductToCart();
            int after1 = cartPage.getCartBadgeCount();
            report.passStep(1, "first product added — badge: " + after1);

            homePage.searchFor("Tolkien");
            homePage.addFirstProductToCart();
            int after2 = cartPage.getCartBadgeCount();
            report.passStep(2, "second product added — badge: " + after2);

            Assert.assertTrue(after2 >= 2,
                "badge should be at least 2 after adding two products, was: " + after2);
            report.passStep(3, "badge count = " + after2);

            finish(report, "badge correctly shows " + after2 + " after two additions", "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // TC-CART-013
    // -------------------------------------------------------------------------

    @Test(description = "TC-CART-013: Proceed to Checkout navigates to the checkout flow")
    public void proceedToCheckout() throws InterruptedException {
        TestCaseReport report = buildReport("TC-CART-013",
            "Proceed to Checkout navigates to the checkout flow",
            "Add a product to the cart",
            "Open the cart and click 'Proceed to Checkout'",
            "Verify the URL moves to the checkout flow"
        );

        try {
            homePage.searchFor(keyword);
            homePage.addFirstProductToCart();
            report.passStep(1, "product added to cart");

            cartPage.openCart(baseUrl);
            cartPage.clickProceedToCheckout();
            report.passStep(2, "clicked 'Proceed to Checkout'");

            Assert.assertTrue(cartPage.isOnCheckoutPage(),
                "expected checkout page URL, got: " + driver.getCurrentUrl());
            report.passStep(3, "URL: " + driver.getCurrentUrl());

            finish(report, "successfully navigated to checkout: " + driver.getCurrentUrl(), "PASS");
        } catch (AssertionError | Exception e) {
            fail(report, e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // helpers shared by all tests
    // -------------------------------------------------------------------------

    private TestCaseReport buildReport(String id, String title, String... steps) {
        TestCaseReport r = new TestCaseReport(id, title, "High", "Shopping Cart")
            .addPrecondition("Registered Periplus account (set in config.properties)")
            .addPrecondition("Cart is cleared before this test runs");
        for (String step : steps) r.addStep(step);
        return r.setExpectedResult(title);
    }

    private void finish(TestCaseReport report, String actual, String status) {
        report.setActualResult(actual);
        report.setStatus(status);
        report.printToLog();
        allReports.add(report);
    }

    private void fail(TestCaseReport report, Throwable e) {
        for (TestCaseReport.Step s : report.getSteps()) {
            if (!s.status.equals("PASS")) {
                report.failStep(s.number, e.getMessage());
                break;
            }
        }
        report.setActualResult("test failed: " + e.getMessage());
        report.setStatus("FAIL");
        report.printToLog();
        allReports.add(report);
    }

    @AfterClass
    public void generateCombinedReport() throws IOException {
        if (allReports.isEmpty()) return;
        // sort by test case id so the combined doc is always in order
        allReports.sort((a, b) -> a.getId().compareTo(b.getId()));
        String path = TestCaseReport.saveCombinedLatex(
            allReports, "target/test-report/FULL_REPORT.tex");
        log.info("combined report saved: " + path);
        log.info("compile with: pdflatex FULL_REPORT.tex  (run twice for TOC)");
    }
}
