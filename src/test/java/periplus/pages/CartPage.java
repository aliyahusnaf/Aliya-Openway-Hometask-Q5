package periplus.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// page object for the Periplus cart page (/checkout/cart).
// selectors are based on live HTML inspection:
//  - product name     : p.product-name a
//  - quantity input   : input.input-number  (value attribute = current qty)
//  - plus button      : button[data-type='plus']
//  - minus button     : button[data-type='minus']
//  - remove link      : a.btn-cart-remove   (navigates to ?remove=productId)
//  - total price      : .total-amount       (last occurrence = grand total)
//  - checkout button  : a[href*='checkout/checkout']
//  - cart badge       : #cart_total_mobile

public class CartPage {

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final JavascriptExecutor js;

    private final By cartItemNames   = By.cssSelector("p.product-name a");
    private final By quantityInput   = By.cssSelector("input.input-number");
    private final By plusBtn         = By.cssSelector("button[data-type='plus']");
    private final By removeLink      = By.cssSelector("a.btn-cart-remove");
    private final By totalAmount     = By.cssSelector(".total-amount");
    // both /checkout/checkout and /checkout/step-* are valid checkout entry points
    private final By checkoutBtn = By.xpath("//a[contains(@href,'checkout') and not(contains(@href,'cart'))]");
    // cart badge is read via JS (document.getElementById), no by field needed

    public CartPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(15));
        this.js     = (JavascriptExecutor) driver;
    }

    // navigation

    // navigates directly to the cart page and waits for the badge JS to settle
    public void openCart(String baseUrl) throws InterruptedException {
        driver.get(baseUrl + "checkout/cart");
        Thread.sleep(1500); // badge count is updated async after page load
    }

    // removes all items from the cart one by one
    // after each removal, waits for the page to redirect back to cart and does a hard refresh to clear cache before checking remaining items
    // used in beforeEach to ensure the cart starts empty before every test.

    public void clearCart(String baseUrl) throws InterruptedException {
        String cartUrl = baseUrl + "checkout/cart";
        driver.get(cartUrl);
        Thread.sleep(1500);
        List<WebElement> links = driver.findElements(removeLink);
        while (!links.isEmpty()) {
            driver.get(links.get(0).getAttribute("href")); // navigate to ?remove=id
            Thread.sleep(2500);
            // force a fresh server load — avoids cached page after the redirect
            driver.navigate().refresh();
            Thread.sleep(2500);
            links = driver.findElements(removeLink);
        }
    }

    // reads

    // returns true if the named product is visible in the cart
    public boolean isProductInCart(String name) {
        List<WebElement> items;
        try {
            items = wait.until(
                ExpectedConditions.visibilityOfAllElementsLocatedBy(cartItemNames)
            );
        } catch (Exception e) {
            return false; // cart is empty or items not yet visible
        }
        String expected = name.toLowerCase();
        return items.stream().anyMatch(i -> i.getText().trim().toLowerCase().contains(expected));
    }

    // returns the number of distinct product rows in the cart
    public int getItemCount() {
        return driver.findElements(cartItemNames).size();
    }

    // returns the current quantity shown in the first product's quantity input
    public int getFirstProductQuantity() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(quantityInput));
        Object val = js.executeScript("return arguments[0].value", driver.findElement(quantityInput));
        return Integer.parseInt(val.toString().trim());
    }

    // returns the grand total price as an integer (removing the Rp.)
    // reads the last .total-amount on the page which corresponds to the main cart total
    // falls back to JS textContent read if the element visibility wait times out

    public int getTotalPrice() {
        try {
            List<WebElement> totals = wait.until(
                ExpectedConditions.visibilityOfAllElementsLocatedBy(totalAmount)
            );
            String raw = totals.get(totals.size() - 1).getText().trim();
            if (!raw.isEmpty()) return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
            // fall through to JS approach
        }
        // js fallback: innerText may contain duplicated price from nested spans so take first number group
        List<WebElement> elements = driver.findElements(totalAmount);
        if (elements.isEmpty()) throw new RuntimeException("no .total-amount elements found in cart");
        WebElement last = elements.get(elements.size() - 1);
        Object val = js.executeScript("return arguments[0].innerText", last);
        String raw2 = val == null ? "" : val.toString().trim();
        Matcher m = Pattern.compile("[0-9][0-9.,]*").matcher(raw2);
        if (!m.find()) throw new RuntimeException("could not parse price from: " + raw2);
        return Integer.parseInt(m.group().replaceAll("[^0-9]", ""));
    }

    // returns the number shown on the cart badge in the header
    public int getCartBadgeCount() {
        try {
            Object result = js.executeScript(
                "var el = document.getElementById('cart_total_mobile'); return el ? el.textContent.trim() : '0';"
            );
            String text = result == null ? "0" : result.toString().trim();
            return text.isEmpty() ? 0 : Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    // returns true if the cart is empty based on the cart badge (#cart_total_mobile)
    // badge is used instead of other selectors like input.input-number or a.btn-cart-remove
    // because those can also appear in the product recommendation section on the empty cart page,
    // which would give a false result

    public boolean isCartEmpty() {
        return getCartBadgeCount() == 0;
    }

    // returns true after clicking Proceed to Checkout and the URL moves away from the cart
    public boolean isOnCheckoutPage() {
        try {
            // wait for the URL to change away from the cart page
            wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("checkout/cart")));
            String url = driver.getCurrentUrl();
            return !url.contains("checkout/cart");
        } catch (Exception e) {
            return false;
        }
    }

    // actions

    // JS-clicks the + button on the first product and waits for the AJAX update
    // uses JS click to bypass the div.preloader overlay that intercepts normal clicks
    // TC-CART-005, TC-CART-007

    public void increaseFirstProductQuantity() throws InterruptedException {
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(plusBtn));
        js.executeScript("arguments[0].click()", btn);
        Thread.sleep(3000);
    }

     // removes the first product by navigating to its remove URL.
     // TC-CART-006
    public void removeFirstProduct() throws InterruptedException {
        WebElement link = wait.until(ExpectedConditions.presenceOfElementLocated(removeLink));
        driver.get(link.getAttribute("href"));
        Thread.sleep(1000);
    }

    // JS-clicks the Proceed to Checkout button
    // TC-CART-013
    public void clickProceedToCheckout() {
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(checkoutBtn));
        js.executeScript("arguments[0].click()", btn);
    }
}
