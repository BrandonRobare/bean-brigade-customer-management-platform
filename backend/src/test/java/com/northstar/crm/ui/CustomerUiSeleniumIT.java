package com.northstar.crm.ui;

import static com.northstar.crm.ui.SeleniumSupport.testId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class CustomerUiSeleniumIT {

  @Test
  void searchProfileRecordInteractionAndRejectEmptySummary() throws Exception {
    String baseUrl = System.getProperty("ui.baseUrl");
    assumeTrue(baseUrl != null && !baseUrl.isBlank(), "Set ui.baseUrl to run the UI test");
    String password = System.getenv("DEMO_AGENT_PASSWORD");
    assertNotNull(password, "DEMO_AGENT_PASSWORD must match the running dev API");

    ChromeDriver driver = SeleniumSupport.createDriver();
    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    try {
      driver.get(baseUrl);
      SeleniumSupport.signIn(driver, wait, "agent1", password);
      wait.until(ExpectedConditions.visibilityOfElementLocated(testId("customer-search-input")))
          .sendKeys("amina");
      driver.findElement(testId("customer-search-submit")).click();
      wait.until(ExpectedConditions.invisibilityOfElementLocated(testId("customer-search-loading")));
      wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-result"), "CUS-1001"));
      assertEquals("Amina Khan", driver.findElement(testId("customer-result-link")).getText());
      wait.until(ExpectedConditions.elementToBeClickable(testId("customer-result-link"))).click();
      wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("customer-profile-id"), "CUS-1001"));
      assertEquals("Amina Khan", driver.findElement(testId("customer-profile-name")).getText());

      wait.until(ExpectedConditions.elementToBeClickable(testId("interaction-submit"))).click();
      assertEquals("Summary is required.",
          wait.until(ExpectedConditions.visibilityOfElementLocated(testId("interaction-summary-error"))).getText());

      String summary = "Selenium interaction " + UUID.randomUUID();
      driver.findElement(testId("interaction-summary")).sendKeys(summary);
      wait.until(ExpectedConditions.elementToBeClickable(testId("interaction-submit"))).click();
      wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("interaction-timeline-item"), summary));
      assertTrue(driver.findElement(testId("interaction-timeline-item")).getText().contains(summary));
    } catch (Exception | AssertionError failure) {
      SeleniumSupport.saveFailure(driver, "critical-path", failure);
      throw failure;
    } finally {
      driver.quit();
    }
  }
}
