package com.northstar.crm.ui;

import static com.northstar.crm.ui.SeleniumSupport.testId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

class CustomerUiFullSeleniumIT {

  private String baseUrl;
  private ChromeDriver driver;
  private WebDriverWait wait;
  private final List<String> customerIds = new ArrayList<>();

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
    try {
      if (driver != null) driver.quit();
    } finally {
      for (String id : customerIds) {
        jdbc().update("DELETE FROM customer_interaction WHERE customer_id = (SELECT customer_id FROM customer WHERE public_id = ?)", id);
        jdbc().update("DELETE FROM customer WHERE public_id = ?", id);
      }
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

  @Test
  void agentCanEditInteractionAndReloadTimeline() {
    signIn("agent1", "DEMO_AGENT_PASSWORD");
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    WebElement item = recordInteraction("Selenium edit " + UUID.randomUUID());
    assertTrue(item.findElements(testId("interaction-delete")).isEmpty());
    item.findElement(testId("interaction-edit")).click();
    new Select(wait.until(ExpectedConditions.elementToBeClickable(testId("interaction-edit-type"))))
        .selectByValue("CALL");
    WebElement summary = driver.findElement(testId("interaction-edit-summary"));
    summary.clear();
    String edited = "Selenium edited " + UUID.randomUUID();
    summary.sendKeys(edited);
    driver.findElement(testId("interaction-edit-save")).click();
    wait.until(ExpectedConditions.invisibilityOfElementLocated(testId("interaction-edit-form")));
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), edited));
    assertEquals("CALL", driver.findElement(testId("interaction-timeline-item"))
        .findElement(By.tagName("strong")).getText());
    SeleniumSupport.navigate(driver, "/customers");
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")));
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), edited));
  }

  @Test
  void adminCanDeleteInteractionAndReloadTimeline() {
    signIn("admin1", "DEMO_ADMIN_PASSWORD");
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    String kept = "Selenium kept " + UUID.randomUUID();
    recordInteraction(kept);
    String deleted = "Selenium delete " + UUID.randomUUID();
    WebElement item = recordInteraction(deleted);
    item.findElement(testId("interaction-delete")).click();
    wait.until(ExpectedConditions.alertIsPresent()).accept();
    wait.until(ExpectedConditions.stalenessOf(item));
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), kept));
    assertTrue(driver.findElements(testId("interaction-timeline-item")).stream()
        .noneMatch(row -> row.getText().contains(deleted)));
    assertEquals("CUS-1001", driver.findElement(testId("customer-profile-id")).getText());
    SeleniumSupport.navigate(driver, "/customers");
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")));
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), kept));
    assertTrue(driver.findElements(testId("interaction-timeline-item")).stream()
        .noneMatch(row -> row.getText().contains(deleted)));
  }

  @Test
  void cancellingInteractionDeletionSendsNoRequest() {
    signIn("admin1", "DEMO_ADMIN_PASSWORD");
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    String summary = "Selenium cancel delete " + UUID.randomUUID();
    WebElement item = recordInteraction(summary);
    ((JavascriptExecutor) driver).executeScript("""
        window.interactionDeleteRequests = 0;
        const send = XMLHttpRequest.prototype.send;
        const open = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url, ...args) {
          this.interactionDelete = method.toUpperCase() === 'DELETE' && String(url).includes('/api/v1/interactions/');
          return open.call(this, method, url, ...args);
        };
        XMLHttpRequest.prototype.send = function(...args) {
          if (this.interactionDelete) window.interactionDeleteRequests++;
          return send.apply(this, args);
        };
        const fetch = window.fetch;
        window.fetch = function(input, init) {
          const method = init?.method ?? input?.method ?? 'GET';
          const url = input?.url ?? String(input);
          if (method.toUpperCase() === 'DELETE' && url.includes('/api/v1/interactions/')) window.interactionDeleteRequests++;
          return fetch.call(this, input, init);
        };
        """);
    item.findElement(testId("interaction-delete")).click();
    wait.until(ExpectedConditions.alertIsPresent()).dismiss();
    wait.until(ExpectedConditions.elementToBeClickable(testId("interaction-delete")));
    assertEquals(0L, ((JavascriptExecutor) driver).executeScript("return window.interactionDeleteRequests;"));
    assertTrue(item.getText().contains(summary));
    SeleniumSupport.navigate(driver, "/customers");
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")));
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), summary));
    assertEquals(0L, ((JavascriptExecutor) driver).executeScript("return window.interactionDeleteRequests;"));
  }

  @Test
  void adminCanCreateRenameActivateCloseAndDeleteCustomer() {
    signIn("admin1", "DEMO_ADMIN_PASSWORD");
    String id = createCustomer();
    Map<String, Object> created = customerRow(id);
    assertEquals("PROSPECT", created.get("status"));
    assertEquals(0L, created.get("version"));
    wait.until(ExpectedConditions.elementToBeClickable(testId("customer-edit"))).click();
    WebElement name = wait.until(ExpectedConditions.elementToBeClickable(testId("customer-edit-name")));
    name.clear();
    String updated = "Selenium updated " + UUID.randomUUID();
    name.sendKeys(updated);
    driver.findElement(testId("customer-edit-save")).click();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-profile-name"), updated));
    assertEquals(updated, customerRow(id).get("full_name"));
    assertEquals(created.get("created_at"), customerRow(id).get("created_at"));
    assertEquals(1L, customerRow(id).get("version"));
    for (String status : List.of("ACTIVE", "CLOSED")) {
      new Select(wait.until(ExpectedConditions.elementToBeClickable(testId("customer-status")))).selectByValue(status);
      driver.findElement(testId("customer-status-submit")).click();
      wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-profile-status"), status));
      assertEquals(status, customerRow(id).get("status"));
    }
    assertEquals(3L, customerRow(id).get("version"));
    SeleniumSupport.navigate(driver, "/customers");
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")));
    SeleniumSupport.navigate(driver, "/customers/" + id);
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-profile-name"), updated));
    assertEquals("CLOSED", driver.findElement(testId("customer-profile-status")).getText());
    ((JavascriptExecutor) driver).executeScript("""
        window.customerDeleteRequests = 0;
        const send = XMLHttpRequest.prototype.send;
        const open = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url, ...args) {
          this.customerDelete = method.toUpperCase() === 'DELETE' && String(url).includes('/api/v1/customers/');
          return open.call(this, method, url, ...args);
        };
        XMLHttpRequest.prototype.send = function(...args) {
          if (this.customerDelete) window.customerDeleteRequests++;
          return send.apply(this, args);
        };
        const fetch = window.fetch;
        window.fetch = function(input, init) {
          const method = init?.method ?? input?.method ?? 'GET';
          const url = input?.url ?? String(input);
          if (method.toUpperCase() === 'DELETE' && url.includes('/api/v1/customers/')) window.customerDeleteRequests++;
          return fetch.call(this, input, init);
        };
        """);
    driver.findElement(testId("customer-delete")).click();
    wait.until(ExpectedConditions.alertIsPresent()).dismiss();
    wait.until(ExpectedConditions.elementToBeClickable(testId("customer-delete")));
    assertEquals(0L, ((JavascriptExecutor) driver).executeScript("return window.customerDeleteRequests;"));
    assertEquals(updated, customerRow(id).get("full_name"));
    driver.findElement(testId("customer-delete")).click();
    wait.until(ExpectedConditions.alertIsPresent()).accept();
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")));
    search(id);
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-empty")));
    assertEquals(1L, ((JavascriptExecutor) driver).executeScript("return window.customerDeleteRequests;"));
    assertEquals(0, jdbc().queryForObject("SELECT count(*) FROM customer WHERE public_id = ?", Integer.class, id));
    SeleniumSupport.navigate(driver, "/customers/" + id);
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-profile-not-found")));
  }

  @Test
  void agentNeverSeesCustomerManagementControls() {
    signIn("agent1", "DEMO_AGENT_PASSWORD");
    assertTrue(driver.findElements(testId("customer-create-form")).isEmpty());
    SeleniumSupport.navigate(driver, "/customers/CUS-1001");
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-profile-name")));
    for (String control : List.of("customer-edit", "customer-status-form", "customer-delete")) {
      assertTrue(driver.findElements(testId(control)).isEmpty());
    }
  }

  @Test
  void customerHistoryBlocksDeletionAndRemainsReadable() {
    signIn("admin1", "DEMO_ADMIN_PASSWORD");
    String id = createCustomer();
    recordInteraction("Customer history " + UUID.randomUUID());
    Map<String, Object> before = customerRow(id);
    driver.findElement(testId("customer-delete")).click();
    wait.until(ExpectedConditions.alertIsPresent()).accept();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-mutation-error"), "interaction history"));
    assertEquals(before, customerRow(id));
    assertEquals(1, jdbc().queryForObject("SELECT count(*) FROM customer_interaction WHERE customer_id = ?", Integer.class, before.get("customer_id")));
    assertTrue(driver.findElement(testId("interaction-timeline-item")).getText().contains("Customer history"));
  }

  @Test
  void staleCustomerEditKeepsDraftUntilExplicitReload() {
    signIn("admin1", "DEMO_ADMIN_PASSWORD");
    String id = createCustomer();
    wait.until(ExpectedConditions.elementToBeClickable(testId("customer-edit"))).click();
    WebElement name = wait.until(ExpectedConditions.elementToBeClickable(testId("customer-edit-name")));
    name.clear();
    name.sendKeys("Unsaved customer draft");
    jdbc().update("UPDATE customer SET full_name = 'Concurrent customer', version = version + 1 WHERE public_id = ?", id);
    driver.findElement(testId("customer-edit-save")).click();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-mutation-error"), "Reload customer"));
    assertEquals("Unsaved customer draft", driver.findElement(testId("customer-edit-name")).getDomProperty("value"));
    assertTrue(!driver.findElement(testId("customer-edit-save")).isEnabled());
    assertEquals("Concurrent customer", customerRow(id).get("full_name"));
    driver.findElement(testId("customer-reload")).click();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-profile-name"), "Concurrent customer"));
    assertTrue(driver.findElement(testId("customer-edit")).isEnabled());
  }

  private String createCustomer() {
    String name = "Selenium customer " + UUID.randomUUID();
    wait.until(ExpectedConditions.elementToBeClickable(testId("customer-create-name"))).sendKeys(name);
    driver.findElement(testId("customer-create-submit")).click();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-result"), name));
    wait.until(ExpectedConditions.elementToBeClickable(testId("customer-result-link"))).click();
    String id = wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-profile-id"))).getText();
    customerIds.add(id);
    assertTrue(id.matches("CUS-\\d+"));
    return id;
  }

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://localhost:5432/crm", "crm", "change-me"));
  }

  private Map<String, Object> customerRow(String id) {
    return jdbc().queryForMap("SELECT * FROM customer WHERE public_id = ?", id);
  }

  private WebElement recordInteraction(String summary) {
    wait.until(ExpectedConditions.elementToBeClickable(testId("interaction-summary"))).sendKeys(summary);
    driver.findElement(testId("interaction-submit")).click();
    wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), summary));
    return driver.findElement(testId("interaction-timeline-item"));
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
