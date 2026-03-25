package periplus.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

// page object for a Periplus product detail page
// add to cart button: button.btn-add-to-cart (calls willAddtoCart('productId') via onclick)
// uses JS click because a div.preloader overlay can intercept normal clicks
// used in TC-CART-002 (add to cart from product detail page) and TC-CART-004 (add same product twice)
public class ProductPage {

    private final WebDriverWait wait;
    private final JavascriptExecutor js;

    private final By addToCartBtn = By.cssSelector("button.btn-add-to-cart");

    public ProductPage(WebDriver driver) {
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        this.js   = (JavascriptExecutor) driver;
    }

    // JS-clicks the add to cart button and waits for the AJAX call to complete
    // willAddtoCart() is async, needs 3s wait before navigating away
    // JS click bypasses the preloader overlay that can intercept normal clicks
    public void addToCart() throws InterruptedException {
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(addToCartBtn));
        js.executeScript("arguments[0].click()", btn);
        Thread.sleep(5000); // willAddtoCart() AJAX, giving extra time to ensure server updates cart
    }
}
