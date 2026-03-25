package periplus.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

// page object for the Periplus home/search results page
// covers the "find one product" and "add one product to cart" steps (TC-CART-001)
// search results are rendered inside owl carousels (.single-product cards)
// search form submits to /index.php?route=product/opensearch with name="filter_name"
// add-to-cart buttons have class="addtocart" and call update_total(id) via JS onclick

public class HomePage {

    private final WebDriverWait wait;
    private final JavascriptExecutor js;

    // confirmed from live page inspection
    private final By searchBox    = By.name("filter_name");
    private final By productCards = By.cssSelector(".single-product");

    public HomePage(WebDriver driver) {
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.js = (JavascriptExecutor) driver;
    }

    // types a keyword in the search bar, submits, and waits for the search results URL
    // "Find one product" step from TC-CART-001
    public void searchFor(String keyword) {
        WebElement search = wait.until(ExpectedConditions.elementToBeClickable(searchBox));
        search.clear();
        search.sendKeys(keyword);
        search.sendKeys(Keys.RETURN);
        // wait until the browser has actually navigated to the search results page
        wait.until(ExpectedConditions.urlContains("filter_name"));
    }

    // returns the name of the first visible product in the search results
    // strips trailing whitespace and "..." truncation markers from the HTML
    public String getFirstProductName() {
        WebElement card = getFirstVisibleCard();
        String name = card.findElement(By.cssSelector("h3 a")).getText().trim();

        // periplus truncates long titles with "..." in the html text itself
        if (name.endsWith("...")) {
            name = name.substring(0, name.length() - 3).trim();
        }
        return name;
    }

    // clicks "add to cart" on the first visible product card
    // uses JS click because the button is inside an owl carousel slide
    // waits 3 seconds after clicking for the update_total() AJAX to finish
    // "add one product to the cart" step from TC-CART-001

    public void addFirstProductToCart() throws InterruptedException {
        // retry the whole find+click in case the carousel rotates between lookup and click
        StaleElementReferenceException lastStale = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                WebElement card = getFirstVisibleCard();
                WebElement btn = card.findElement(By.cssSelector("a.addtocart"));
                js.executeScript("arguments[0].click()", btn);
                // update_total() is async, give it time to complete before navigating away
                Thread.sleep(3000);
                return;
            } catch (StaleElementReferenceException e) {
                lastStale = e;
                Thread.sleep(500); // brief pause before re-fetching the carousel card
            }
        }
        throw lastStale;
    }

    // returns the product detail page URL of the first visible product.
    // used in TC-CART-002 and TC-CART-004.
    public String getFirstProductUrl() {
        WebElement card = getFirstVisibleCard();
        return card.findElement(By.cssSelector("a[href*='/p/']")).getAttribute("href");
    }

    private WebElement getFirstVisibleCard() {
        // scroll a bit to trigger lazy-loaded carousels before waiting for cards
        js.executeScript("window.scrollTo(0, 200)");
        List<WebElement> cards = wait.until(
            ExpectedConditions.presenceOfAllElementsLocatedBy(productCards)
        );
        // carousel may rotate between lookup and isDisplayed, skip stale cards
        for (WebElement card : cards) {
            try {
                if (card.isDisplayed()) return card;
            } catch (StaleElementReferenceException ignored) {
                // card was removed by carousel rotation, try the next one
            }
        }
        throw new RuntimeException("no visible product cards found on page");
    }
}
