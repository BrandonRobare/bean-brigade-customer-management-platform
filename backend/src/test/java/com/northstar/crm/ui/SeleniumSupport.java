package com.northstar.crm.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class SeleniumSupport {

  static ChromeDriver createDriver() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new", "--window-size=1440,1000");
    options.setExperimentalOption("prefs", Map.of(
        "credentials_enable_service", false,
        "profile.password_manager_enabled", false,
        "profile.password_manager_leak_detection", false));
    return new ChromeDriver(options);
  }

  static By testId(String id) {
    return By.cssSelector("[data-testid='" + id + "']");
  }

  static void signIn(ChromeDriver driver, WebDriverWait wait, String username, String password) {
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("username"))).sendKeys(username);
    driver.findElement(testId("password")).sendKeys(password);
    wait.until(ExpectedConditions.elementToBeClickable(testId("sign-in"))).click();
  }

  static void navigate(ChromeDriver driver, String route) {
    ((JavascriptExecutor) driver).executeScript(
        "history.pushState({}, '', arguments[0]); dispatchEvent(new PopStateEvent('popstate'));", route);
  }

  static void saveFailure(ChromeDriver driver, String name, Throwable failure) {
    try {
      Path screenshot = Path.of("target", "selenium-failure-" + name + ".png");
      Files.createDirectories(screenshot.getParent());
      Files.write(screenshot, driver.getScreenshotAs(OutputType.BYTES));
    } catch (Exception screenshotFailure) {
      failure.addSuppressed(screenshotFailure);
    }
  }
}
