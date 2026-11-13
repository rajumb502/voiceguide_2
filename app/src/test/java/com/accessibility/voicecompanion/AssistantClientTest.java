package com.accessibility.voicecompanion;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AssistantClientTest {
    private JSONObject payload() throws Exception {
        return new JSONObject("{\"command\":\"help\",\"snapshot\":{\"url\":\"https://private.example/?token=secret\",\"width\":400,\"height\":800,\"sensitive\":false,\"nodes\":[{\"id\":\"1\",\"label\":\"Pay\",\"disabled\":false,\"editable\":false,\"value\":\"secret\",\"x\":10,\"y\":10,\"width\":90,\"height\":50}]},\"image\":\"jpeg-data\"}");
    }
    private JSONObject action() throws Exception {
        return new JSONObject("{\"action\":\"CLICK\",\"target_id\":\"1\",\"requires_confirmation\":false,\"action_summary\":\"pay\",\"spoken_feedback\":\"Found Pay\"}");
    }
    @Test public void bothProvidersOmitUrlsAndFieldValues() throws Exception {
        for (String provider : new String[]{AssistantSettings.GEMINI, AssistantSettings.OPENAI}) {
            String body = AssistantClient.body(provider,"model",payload()).toString();
            assertFalse(body.contains("secret")); assertFalse(body.contains("private.example"));
        }
    }
    @Test public void openAiUsesResponsesImagesAndStrictSchema() throws Exception {
        JSONObject body = AssistantClient.body(AssistantSettings.OPENAI,"model",payload());
        assertFalse(body.getBoolean("store")); assertEquals("model",body.getString("model"));
        assertEquals("input_image",body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(1).getString("type"));
        JSONObject format = body.getJSONObject("text").getJSONObject("format");
        assertTrue(format.getBoolean("strict")); assertFalse(format.getJSONObject("schema").getBoolean("additionalProperties"));
    }
    @Test public void sensitivePagesNeverSendImages() throws Exception {
        JSONObject payload = payload(); payload.getJSONObject("snapshot").put("sensitive",true);
        for (String provider : new String[]{AssistantSettings.GEMINI, AssistantSettings.OPENAI}) assertFalse(AssistantClient.body(provider,"model",payload).toString().contains("jpeg-data"));
    }
    @Test public void clickValidationForcesConfirmationAndRejectsBadTargets() throws Exception {
        JSONObject snapshot = payload().getJSONObject("snapshot");
        assertTrue(AgentPolicy.needsConfirmation(AssistantClient.validate(action(),snapshot),snapshot));
        for (String flag : new String[]{"disabled","editable"}) {
            JSONObject changed = new JSONObject(snapshot.toString()); changed.getJSONArray("nodes").getJSONObject(0).put(flag,true);
            try { AssistantClient.validate(action(),changed); fail("Rejected target expected"); } catch (JSONException expected) { }
        }
        JSONObject bad = action().put("target_id","missing");
        try { AssistantClient.validate(bad,snapshot); fail("Missing target should fail"); } catch (JSONException expected) { }
    }
    @Test public void openAiParsesMessagesAfterReasoningAndRejectsIncomplete() throws Exception {
        JSONObject response = new JSONObject().put("status","completed").put("output",new JSONArray()
            .put(new JSONObject().put("type","reasoning"))
            .put(new JSONObject().put("type","message").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",action().toString())))));
        assertEquals("CLICK",AssistantClient.parse(AssistantSettings.OPENAI,response).getString("action"));
        response.put("status","incomplete");
        try { AssistantClient.parse(AssistantSettings.OPENAI,response); fail("Incomplete response should fail"); } catch (JSONException expected) { }
    }
    @Test public void geminiSkipsThoughts() throws Exception {
        JSONObject response = new JSONObject().put("candidates",new JSONArray().put(new JSONObject().put("finishReason","STOP")
            .put("content",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("thought",true).put("text","private reasoning"))
            .put(new JSONObject().put("text",action().toString()))))));
        assertEquals("CLICK",AssistantClient.parse(AssistantSettings.GEMINI,response).getString("action"));
    }
}
