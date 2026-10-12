package com.northstar.crm.ui;

import static com.northstar.crm.ui.SeleniumSupport.testId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

class CustomerUiFullSeleniumIT {

  private String baseUrl;
  private ChromeDriver driver;
  private WebDriverWait wait;

  @RegisterExtension
  final AfterTestExecutionCallback screenshotOnFailure = context -> {
    context.getExecutionException().ifPresent(failure ->
        SeleniumSupport.saveFailure(driver, context.getRequiredTestMethod().getName(), failure));
  };

  @BeforeEach
  void setUp() {
    baseUrl = System.getProperty("ui.baseUrl");
    assumeTrue(baseUrl != null && !baseUrl.isBlank(), "Set ui.baseUrl to run the UI test");
    driver = SeleniumSupport.createDriver();
    wait = new WebDriverWait(driver, Duration.ofSeconds(30));
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  void wrongPasswordStaysOnLogin() {
    driver.get(baseUrl);
    SeleniumSupport.signIn(driver, wait, "agent1", "wrong-password-" + UUID.randomUUID());
    assertEquals("Wrong username or password.",
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role='alert']"))).getText());
    assertEquals(baseUrl + "/login", driver.getCurrentUrl());
  }

  @Test
  void signedOutProfileRedirectsToLogin() {
    driver.get(baseUrl + "/customers/CUS-1001");
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("username")));
    assertEquals(baseUrl + "/login", driver.getCurrentUrl());
    assertTrue(driver.findElement(testId("sign-in")).isDisplayed());
  }

  @Test
  void adminCanSearchForRavi() {
    signIn("admin1", "DEMO_ADMIN_PASSWORD");
    search("CUS-1002");
    wait.until(ExpectedConditions.numberOfElementsToBe(testId("customer-result"), 1));
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-result"), "CUS-1002"));
    assertEquals("Ravi Singh", driver.findElement(testId("customer-result-link")).getText());
  }

  @Test
  void unmatchedSearchShowsTermInEmptyState() {
    signIn("agent1", "DEMO_AGENT_PASSWORD");
    search("zzz-nobody");
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-search-empty"), "zzz-nobody"));
    assertEquals("No customers match \"zzz-nobody\".", driver.findElement(testId("customer-search-empty")).getText());
  }

  @Test
  void backToSearchPreservesQueryAndResults() {
    signIn("agent1", "DEMO_AGENT_PASSWORD");
    search("amina");
    wait.until(ExpectedConditions.numberOfElementsToBe(testId("customer-result"), 1));
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-result"), "CUS-1001"));
    wait.until(ExpectedConditions.elementToBeClickable(testId("customer-result-link"))).click();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-profile-id"), "CUS-1001"));
    assertEquals("Back to search", driver.findElement(testId("customer-profile-back")).getText());
    wait.until(ExpectedConditions.elementToBeClickable(testId("customer-profile-back"))).click();
    wait.until(ExpectedConditions.urlToBe(baseUrl + "/customers?q=amina"));
    assertEquals("amina", wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")))
        .getDomProperty("value"));
    wait.until(ExpectedConditions.numberOfElementsToBe(testId("customer-result"), 1));
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-result"), "CUS-1001"));
  }

  @Test
  void unknownCustomerShowsNotFound() {
    signIn("agent1", "DEMO_AGENT_PASSWORD");
    SeleniumSupport.navigate(driver, "/customers/CUS-9999");
    assertEquals("Customer CUS-9999 was not found.",
        wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-profile-not-found"))).getText());
    assertEquals(baseUrl + "/customers/CUS-9999", driver.getCurrentUrl());
  }

  @Test
  void invalidCustomerIdHasNoRetry() {
    signIn("agent1", "DEMO_AGENT_PASSWORD");
    SeleniumSupport.navigate(driver, "/customers/abc");
    assertEquals("Customer ID abc is not valid.",
        wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-profile-invalid-id"))).getText());
    assertTrue(driver.findElements(testId("customer-profile-retry")).isEmpty());
  }

  @Test
  void emailInteractionAppearsFirstInTimeline() {
    signIn("agent1", "DEMO_AGENT_PASSWORD");
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    new Select(wait.until(ExpectedConditions.elementToBeClickable(testId("interaction-type"))))
        .selectByValue("EMAIL");
    String summary = "Selenium email " + UUID.randomUUID();
    driver.findElement(testId("interaction-summary")).sendKeys(summary);
    wait.until(ExpectedConditions.elementToBeClickable(testId("interaction-submit"))).click();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), summary));
    WebElement firstItem = driver.findElement(testId("interaction-timeline-item"));
    assertEquals("EMAIL", firstItem.findElement(By.tagName("strong")).getText());
    assertEquals(summary, firstItem.findElement(By.tagName("p")).getText());
  }

  private void signIn(String username, String passwordVariable) {
    String password = System.getenv(passwordVariable);
    assertNotNull(password, passwordVariable + " must match the running dev API");
    driver.get(baseUrl);
    SeleniumSupport.signIn(driver, wait, username, password);
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")));
  }

  private void search(String query) {
    WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")));
    input.clear();
    input.sendKeys(query);
    driver.findElement(testId("customer-search-submit")).click();
  }
}
