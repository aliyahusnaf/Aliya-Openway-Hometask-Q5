package periplus.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

// page object for the Periplus login page.
// covers the login precondition for TC-CART-001 through TC-CART-013 = "user is logged in".
// login URL: https://www.periplus.com/account/Login
//
// notes on the login form:
// - form element has id="login"
// - password field id="ps" may be disabled on load (toggled by their JS)
// - submit button id="button-login" or id="btn-login"
// - after successful login, page redirects away from /account/Login

public class LoginPage {

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final JavascriptExecutor js;

    private final By loginForm     = By.id("login");
    private final By passwordField = By.id("ps");
    private final By submitBtn     = By.cssSelector("#button-login, #btn-login");

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        this.js = (JavascriptExecutor) driver;
    }

    // navigates to the login page, fills in credentials, and submits
    // if the user is already logged in, Periplus redirects away from the login URL
    // so this method returns immediately, skipping redundant logins and avoiding rate limits
    // used in TC-CART-001

    public void login(String email, String password) throws InterruptedException {
        driver.get("https://www.periplus.com/account/Login");
        // brief pause so the server can process the redirect for already-logged-in sessions
        Thread.sleep(1500);
        // if Periplus redirected us away from the login page we are already authenticated
        if (!driver.getCurrentUrl().toLowerCase().contains("login")) {
            return;
        }
        // fast 429 detection, skip the 15 s form-wait if Periplus is rate-limiting
        String src = driver.getPageSource();
        if (src != null && src.contains("429")) {
            throw new RuntimeException(
                "429 Too Many Requests — Periplus is rate-limiting this IP. ");
        }

        wait.until(ExpectedConditions.visibilityOfElementLocated(loginForm));

        // fill email, try multiple selectors since field id is not always predictable
        WebElement emailInput = findEmailField();
        emailInput.clear();
        emailInput.sendKeys(email);

        // the password field (#ps) can be disabled by the page's toggle JS
        // using JS to remove the attribute before typing
        WebElement passInput = driver.findElement(passwordField);
        js.executeScript("arguments[0].removeAttribute('disabled')", passInput);
        passInput.clear();
        passInput.sendKeys(password);

        driver.findElement(submitBtn).click();
    }

    // logs out by navigating to the Periplus logout URL
    // used in TC-CART-009 (cart persists after re-login)
    public void logout() throws InterruptedException {
        driver.get("https://www.periplus.com/_index_/Logout");
        Thread.sleep(2000);
    }

    // returns true if login succeeded by checking that the URL has left the login page
    // more reliable than looking for a specific element on the redirected page
    public boolean isLoggedIn() {
        try {
            wait.until(ExpectedConditions.not(
                ExpectedConditions.urlContains("Login")
            ));
            return !driver.getCurrentUrl().toLowerCase().contains("login");
        } catch (Exception e) {
            return false;
        }
    }

    // tries a few common selectors for the email field in order
    private WebElement findEmailField() {
        String[] selectors = {
            "#login input[type='email']",
            "input[name='email']",
            "#login input[type='text']",
            "#login input:not([type='password']):not([type='hidden'])"
        };

        for (String selector : selectors) {
            try {
                WebElement el = driver.findElement(By.cssSelector(selector));
                if (el.isDisplayed()) return el;
            } catch (Exception ignored) {
                // try next selector
            }
        }

        throw new RuntimeException("could not locate the email input on the login page");
    }
}
