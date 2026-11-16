package com.accessibility.voicecompanion;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class AssistantClientFailoverTest {
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private JSONObject createMockPayload() throws Exception {
        JSONObject node = new JSONObject()
            .put("id", "1")
            .put("label", "Search")
            .put("disabled", false)
            .put("editable", false)
            .put("clickable", true)
            .put("tag", "BUTTON")
            .put("x", 10)
            .put("y", 10)
            .put("width", 100)
            .put("height", 50);

        JSONObject snapshot = new JSONObject()
            .put("site", "example.com")
            .put("url", "https://example.com")
            .put("width", 400)
            .put("height", 800)
            .put("sensitive", false)
            .put("nodes", new JSONArray().put(node));

        return new JSONObject()
            .put("command", "Click search")
            .put("snapshot", snapshot)
            .put("image", "mock-base64-screenshot-data");
    }

    private JSONObject createSuccessfulAction() throws Exception {
        return new JSONObject()
            .put("action", "CLICK")
            .put("target_id", "1")
            .put("requires_confirmation", false)
            .put("action_summary", "Click search")
            .put("spoken_feedback", "Clicked search button");
    }

    @Test
    public void server503FallsBackFromMultimodalToTextOnlyDom() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));

        JSONObject payload = createMockPayload();
        AtomicInteger invocation = new AtomicInteger();

        JSONObject result = AssistantClient.withFallback(
            AssistantSettings.GEMINI,
            "gemini-2.5-flash",
            "test-key",
            payload,
            (provider, model, key, data) -> {
                int call = invocation.incrementAndGet();
                if (call == 1) {
                    assertTrue("Initial call must contain image", data.has("image"));
                    throw new AssistantClient.RequestFailure("Server 503", true);
                } else {
                    assertFalse("Fallback call must strip image", data.has("image"));
                    assertTrue("Fallback call must preserve snapshot DOM", data.has("snapshot"));
                    assertEquals("Fallback model must be text-only flash-lite", "gemini-2.5-flash-lite", model);
                    return createSuccessfulAction();
                }
            }
        );

        assertNotNull(result);
        assertEquals("CLICK", result.getString("action"));
        assertEquals(2, invocation.get());
        assertTrue("Original payload object must not be mutated", payload.has("image"));
    }

    @Test
    public void rateLimit429RetriesTextOnlyDomPayload() throws Exception {
        JSONObject payload = createMockPayload();
        AtomicInteger invocation = new AtomicInteger();

        JSONObject result = AssistantClient.withFallback(
            AssistantSettings.GEMINI,
            "gemini-2.5-flash",
            "test-key",
            payload,
            (provider, model, key, data) -> {
                int call = invocation.incrementAndGet();
                if (call == 1) {
                    assertTrue(data.has("image"));
                    throw new AssistantClient.RequestFailure("Quota 429", true);
                } else {
                    assertFalse(data.has("image"));
                    return createSuccessfulAction();
                }
            }
        );

        assertEquals("CLICK", result.getString("action"));
        assertEquals(2, invocation.get());
    }

    @Test
    public void client401DoesNotRetryAndFailsFast() throws Exception {
        JSONObject payload = createMockPayload();
        AtomicInteger invocation = new AtomicInteger();

        try {
            AssistantClient.withFallback(
                AssistantSettings.GEMINI,
                "gemini-2.5-flash",
                "test-key",
                payload,
                (provider, model, key, data) -> {
                    invocation.incrementAndGet();
                    throw new AssistantClient.RequestFailure("Unauthorized 401", false);
                }
            );
            fail("Non-retryable 401 must fail immediately");
        } catch (AssistantClient.RequestFailure expected) {
            assertEquals("Must only make 1 attempt on non-retryable 401", 1, invocation.get());
        }
    }
}
