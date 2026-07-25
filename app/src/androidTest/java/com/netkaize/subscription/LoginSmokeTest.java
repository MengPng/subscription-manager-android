package com.netkaize.subscription;

import android.os.Bundle;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LoginSmokeTest {
    @Test
    public void productionAccountCanLogInAndReachTheLedger() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String email = arguments.getString("smokeEmail", "");
        String password = arguments.getString("smokePassword", "");
        Assume.assumeTrue("Smoke credentials are required for the production login test",
                !email.isEmpty() && !password.isEmpty());

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<WebView> webViewRef = new AtomicReference<>();
            scenario.onActivity(activity -> webViewRef.set(activity.getWebViewForTesting()));
            WebView webView = webViewRef.get();

            assertTrue("Login page did not become ready",
                    waitForJavascript(scenario, webView,
                            "Boolean(document.querySelector('#auth-email') && document.querySelector('#auth-submit'))",
                            "true", 30));

            String loginScript = "(() => {"
                    + "const email=document.querySelector('#auth-email');"
                    + "const password=document.querySelector('#auth-password');"
                    + "email.value=" + JSONObject.quote(email) + ";"
                    + "password.value=" + JSONObject.quote(password) + ";"
                    + "email.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "password.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "document.querySelector('#auth-submit').click();"
                    + "return true;"
                    + "})()";
            assertEquals("true", evaluateJavascript(scenario, webView, loginScript));

            assertTrue("The production account did not reach the authenticated ledger",
                    waitForJavascript(scenario, webView,
                            "Boolean(localStorage.getItem('subscription_manager_auth_v1'))"
                                    + " && !document.querySelector('#auth-screen').classList.contains('show')",
                            "true", 30));
        }
    }

    private static boolean waitForJavascript(
            ActivityScenario<MainActivity> scenario,
            WebView webView,
            String script,
            String expected,
            int timeoutSeconds
    ) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (expected.equals(evaluateJavascript(scenario, webView, script))) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private static String evaluateJavascript(
            ActivityScenario<MainActivity> scenario,
            WebView webView,
            String script
    ) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(activity ->
                webView.evaluateJavascript(script, value -> {
                    result.set(value);
                    latch.countDown();
                })
        );
        assertTrue("JavaScript evaluation timed out", latch.await(10, TimeUnit.SECONDS));
        return result.get();
    }
}

