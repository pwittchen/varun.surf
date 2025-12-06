package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.HttpURLConnection;
import java.net.URI;

public abstract class BaseE2eTest {

    protected static final String BASE_URL = "http://localhost:8080";
    protected static final int DEFAULT_TIMEOUT = 60000;
    protected static final int NAVIGATION_TIMEOUT = 90000;

    protected static Playwright playwright;
    protected static Browser browser;
    protected static ConfigurableApplicationContext applicationContext;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void setUpAll() {
        applicationContext = SpringApplication.run(
            com.github.pwittchen.varun.Application.class,
            "--server.port=8080"
        );

        waitForApplicationReady();

        playwright = Playwright.create();
        boolean headless = Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setTimeout(60000)
        );
    }

    private static void waitForApplicationReady() {
        int maxAttempts = 30;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(BASE_URL + "/api/v1/health").toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    Thread.sleep(2000);
                    return;
                }
            } catch (Exception ignored) {
            }
            attempt++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new RuntimeException("Application failed to start within timeout");
    }

    @AfterAll
    static void tearDownAll() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @BeforeEach
    void setUp() {
        context = browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(1920, 1080));
        page = context.newPage();
        page.setDefaultTimeout(DEFAULT_TIMEOUT);
        page.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    protected void waitForPageLoad() {
        page.waitForLoadState();
    }

    protected void waitForNetworkIdle() {
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    }
}