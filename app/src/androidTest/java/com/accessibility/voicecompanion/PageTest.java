package com.accessibility.voicecompanion;

import android.content.Context;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import static org.junit.Assert.*;

public class PageTest {
    private String evaluate(WebView web, String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(script,value -> { result.set(value); done.countDown(); }));
        assertTrue("WebView callback timed out",done.await(10,TimeUnit.SECONDS)); return result.get();
    }
    @Test public void realDomRedactsFieldsAndClicksClosedShadowRootButton() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String script;
        try (InputStream in = context.getAssets().open("page.js"); java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer,0,count);
            script = new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
        String html = "<html><head><script>" + script + "</script></head><body><h1>Public heading</h1>"
            + "<p>Notes<textarea>private-textarea-default</textarea><span style='display:none'>hidden-private-text</span></p><div id='host'></div><script>"
            + "const root = document.getElementById('host').attachShadow({mode:'closed'});"
            + "root.innerHTML = '<input type=password value=secret-password><input autocomplete=one-time-code value=secret-otp><button>Open details</button>';"
            + "root.querySelector('button').onclick = () => window.clicked = true;"
            + "</script></body></html>";
        CountDownLatch loaded = new CountDownLatch(1); AtomicReference<WebView> holder = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView web = new WebView(context); holder.set(web); web.getSettings().setJavaScriptEnabled(true); web.layout(0,0,720,1280);
            web.setWebViewClient(new WebViewClient() { @Override public void onPageFinished(WebView view, String url) { loaded.countDown(); } });
            web.loadDataWithBaseURL("https://example.com/",html,"text/html","utf-8",null);
        });
        WebView web = holder.get();
        try {
            assertTrue("Page did not load",loaded.await(10,TimeUnit.SECONDS));
            String raw = (String)new JSONTokener(evaluate(web,"window.__vc.snapshot()")).nextValue();
            JSONObject snapshot = new JSONObject(raw);
            assertTrue(snapshot.getBoolean("sensitive")); assertTrue(raw.contains("Public heading"));
            assertFalse(raw.contains("secret-password")); assertFalse(raw.contains("secret-otp")); assertFalse(raw.contains("private-textarea-default"));
            assertFalse(raw.contains("hidden-private-text"));
            String id = null;
            for (int i=0;i<snapshot.getJSONArray("nodes").length();i++) {
                JSONObject node = snapshot.getJSONArray("nodes").getJSONObject(i);
                if (node.getString("tag").equals("BUTTON")) id = node.getString("id");
            }
            assertNotNull("Shadow button must be discoverable",id);
            assertEquals("true",evaluate(web,"window.__vc.click(" + JSONObject.quote(id) + "," + snapshot.getLong("revision") + ")"));
            assertEquals("true",evaluate(web,"window.clicked === true"));
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync(web::destroy); }
    }
    @Test public void voiceLifecycleCanBeCalledFromWorkerThread() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VoiceEngineManager voice = new VoiceEngineManager(context,new VoiceEngineManager.Callback() {
            @Override public void command(String text) { fail("This test never opens the microphone"); }
            @Override public void feedback(String text) { assertEquals(android.os.Looper.getMainLooper(),android.os.Looper.myLooper()); }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        voice.stop(); voice.destroy();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
