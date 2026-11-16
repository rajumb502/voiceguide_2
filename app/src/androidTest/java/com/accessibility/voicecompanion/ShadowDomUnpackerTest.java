package com.accessibility.voicecompanion;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Instrumented test verifying:
 * 1. Overriding closed shadow roots (`attachShadow({ mode: 'closed' })`) to enable deep inspection.
 * 2. Password, OTP, and hidden field redaction to "[REDACTED]".
 * 3. Accurate DOM click triggers through shadow boundaries.
 * 4. Action confirmation requiring physical Volume Up double-press before execution.
 */
public class ShadowDomUnpackerTest {

    private String evaluate(WebView web, String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            web.evaluateJavascript(script, value -> {
                result.set(value);
                done.countDown();
            })
        );
        assertTrue("WebView script evaluation timed out", done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    @Test
    public void testClosedShadowDomPiercingPasswordRedactionAndHardwareConfirmation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 1. Read page.js
        String pageScript;
        try (InputStream in = context.getAssets().open("page.js"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            pageScript = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }

        // Hostile / closed Shadow DOM HTML with password and sensitive OTP input
        String html = "<!DOCTYPE html><html><head><script>" + pageScript + "</script></head><body>"
            + "<h1>Secure Portal</h1>"
            + "<div id='host'></div>"
            + "<script>"
            + "  const host = document.getElementById('host');"
            + "  const shadow = host.attachShadow({ mode: 'closed' });"
            + "  shadow.innerHTML = '"
            + "    <label for=\"pwd\">Password</label>"
            + "    <input id=\"pwd\" type=\"password\" value=\"super_secret_pwd\">"
            + "    <input id=\"otp\" autocomplete=\"one-time-code\" placeholder=\"123456\" value=\"654321\">"
            + "    <button id=\"btn-submit\">Pay ₹500</button>'"
            + "  ;"
            + "  shadow.querySelector('#btn-submit').onclick = () => { window.paymentExecuted = true; };"
            + "</script>"
            + "</body></html>";

        CountDownLatch loaded = new CountDownLatch(1);
        AtomicReference<WebView> holder = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView web = new WebView(context);
            holder.set(web);
            web.getSettings().setJavaScriptEnabled(true);
            web.layout(0, 0, 720, 1280);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    loaded.countDown();
                }
            });
            web.loadDataWithBaseURL("https://secure.example.com/", html, "text/html", "utf-8", null);
        });

        WebView web = holder.get();
        try {
            assertTrue("Page load timed out", loaded.await(10, TimeUnit.SECONDS));

            // 2. Take DOM snapshot
            String rawSnapshot = (String) new JSONTokener(evaluate(web, "window.__vc.snapshot()")).nextValue();
            JSONObject snapshot = new JSONObject(rawSnapshot);

            // Assert page marked sensitive
            assertTrue("Page with password/OTP must be marked sensitive", snapshot.getBoolean("sensitive"));

            // Assert raw secrets never leak in DOM snapshot text
            assertFalse(rawSnapshot.contains("super_secret_pwd"));
            assertFalse(rawSnapshot.contains("654321"));

            JSONArray nodes = snapshot.getJSONArray("nodes");
            String payButtonId = null;
            boolean foundRedactedPassword = false;
            boolean foundRedactedOtp = false;

            for (int i = 0; i < nodes.length(); i++) {
                JSONObject n = nodes.getJSONObject(i);
                if ("Pay ₹500".equals(n.optString("label"))) {
                    payButtonId = n.getString("id");
                }
                if (n.optBoolean("sensitive")) {
                    if ("[REDACTED]".equals(n.optString("label")) || "[REDACTED]".equals(n.optString("placeholder"))) {
                        if ("password".equalsIgnoreCase(n.optString("type"))) foundRedactedPassword = true;
                        if ("one-time-code".equalsIgnoreCase(n.optString("autocomplete"))) foundRedactedOtp = true;
                    }
                }
            }

            assertTrue("Password field must be found and masked as [REDACTED]", foundRedactedPassword);
            assertTrue("OTP field must be found and masked as [REDACTED]", foundRedactedOtp);
            assertNotNull("Button inside closed shadow DOM must be pierced and discoverable", payButtonId);

            // 3. Test Action Safety Guard: Volume Up double-press confirmation requirement
            ActionSafetyGuard guard = new ActionSafetyGuard();
            assertTrue(guard.requiresConfirmation(false, "Pay ₹500"));

            long now = SystemClock.elapsedRealtime();
            guard.queue(new ActionSafetyGuard.Pending(payButtonId, snapshot.getLong("revision"), 1, now, "Pay ₹500"));

            // Spoken confirmations rejected
            assertNull(guard.take("Yes", now + 100, 1));
            assertEquals(ActionSafetyGuard.SpokenResult.REJECTED, guard.evaluateSpoken("Haan", now + 100, 1));
            assertTrue(guard.hasPending());

            // First Volume Up press - pending, not yet confirmed
            assertNull(guard.onVolumeUp(now + 200, 1));
            assertTrue(guard.hasPending());

            // Second Volume Up press within 400ms - confirms!
            ActionSafetyGuard.Pending confirmed = guard.onVolumeUp(now + 600, 1);
            assertNotNull("Second Volume Up within 1,200ms must confirm action", confirmed);
            assertFalse(guard.hasPending());

            // 4. Execute click on the closed shadow DOM button
            JSONObject clickAction = new JSONObject()
                .put("action", "CLICK")
                .put("target_id", confirmed.targetId());

            String clickResult = evaluate(web, "window.__vc.perform(" + clickAction.toString() + "," + snapshot.getLong("revision") + ", true)");
            assertEquals("true", clickResult);

            String clickedState = evaluate(web, "String(window.paymentExecuted)");
            assertEquals("true", clickedState);

        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(web::destroy);
        }
    }
}
