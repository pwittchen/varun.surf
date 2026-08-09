package com.github.pwittchen.varun.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public abstract class BaseE2eTest {

    protected static final String BASE_URL = "http://localhost:8080";
    protected static final int DEFAULT_TIMEOUT = 60000;
    protected static final int NAVIGATION_TIMEOUT = 90000;

    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final int STARTUP_ATTEMPTS = 30;
    private static final int STARTUP_POLL_INTERVAL_MS = 1000;
    private static final int WARM_UP_ATTEMPTS = 60;
    private static final int WARM_UP_POLL_INTERVAL_MS = 2000;

    protected static Playwright playwright;
    protected static Browser browser;
    protected static ConfigurableApplicationContext applicationContext;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void setUpAll() {
        startApplicationOnce();

        playwright = Playwright.create();
        boolean headless = Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setTimeout(60000)
        );
    }

    /**
     * The application is started once per JVM and shared by all test classes. Restarting it per
     * class made every class pay for the startup warm-up again and let a closing context race the
     * next one for port 8080.
     */
    private static void startApplicationOnce() {
        if (applicationContext != null) {
            return;
        }

        applicationContext = SpringApplication.run(
            com.github.pwittchen.varun.Application.class,
            "--server.port=8080"
        );
        Runtime.getRuntime().addShutdownHook(new Thread(() -> applicationContext.close()));

        waitForApplicationReady();
    }

    private static void waitForApplicationReady() {
        waitForHealthEndpoint();
        waitForFirstForecasts();
    }

    private static void waitForHealthEndpoint() {
        for (int attempt = 0; attempt < STARTUP_ATTEMPTS; attempt++) {
            try {
                if (get("/api/v1/health", null).getResponseCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
            }
            sleep(STARTUP_POLL_INTERVAL_MS);
        }
        throw new RuntimeException("Application failed to start within timeout");
    }

    /**
     * Forecasts are fetched from external services after startup, so spots are served with empty
     * forecasts for a while. As long as every spot is still empty, the frontend renders a loading
     * spinner instead of spot cards and only retries every few seconds, so any test waiting for a
     * spot card would race the warm-up and time out on a slow CI runner.
     */
    private static void waitForFirstForecasts() {
        String sessionCookie = createSession();
        for (int attempt = 0; attempt < WARM_UP_ATTEMPTS; attempt++) {
            if (hasAnyForecast(sessionCookie)) {
                return;
            }
            sleep(WARM_UP_POLL_INTERVAL_MS);
        }
        System.err.printf(
            "Warning: no spot has a forecast after %d seconds, tests relying on spot cards may fail%n",
            (WARM_UP_ATTEMPTS * WARM_UP_POLL_INTERVAL_MS) / 1000
        );
    }

    /**
     * The spots API is gated behind a session cookie, which is handed out on a regular page visit.
     */
    private static String createSession() {
        try {
            HttpURLConnection connection = get("/", null);
            connection.getResponseCode();
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                if (header.getKey() == null || !header.getKey().equalsIgnoreCase("set-cookie")) {
                    continue;
                }
                for (String cookie : header.getValue()) {
                    if (cookie.startsWith(SESSION_COOKIE_NAME + "=")) {
                        return cookie.split(";", 2)[0];
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean hasAnyForecast(String sessionCookie) {
        if (sessionCookie == null) {
            return false;
        }
        try {
            HttpURLConnection connection = get("/api/v1/spots", sessionCookie);
            if (connection.getResponseCode() != 200) {
                return false;
            }
            try (InputStream body = connection.getInputStream()) {
                JsonArray spots = JsonParser
                    .parseReader(new InputStreamReader(body, StandardCharsets.UTF_8))
                    .getAsJsonArray();
                for (JsonElement spot : spots) {
                    JsonArray forecast = spot.getAsJsonObject().getAsJsonArray("forecast");
                    if (forecast != null && !forecast.isEmpty()) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static HttpURLConnection get(String path, String sessionCookie) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(BASE_URL + path).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(15000);
        if (sessionCookie != null) {
            connection.setRequestProperty("Cookie", sessionCookie);
        }
        return connection;
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for the application", e);
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
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