package com.accessibility.voicecompanion;
import org.json.*;
public class AssistantContextTest {
    public static void main(String[] args) throws Exception {
        JSONObject node = new JSONObject("{\"id\":\"1\",\"label\":\"Search Amazon\",\"disabled\":false,\"editable\":true,\"x\":0,\"y\":0,\"width\":100,\"height\":50,\"tag\":\"INPUT\",\"type\":\"search\",\"name\":\"search\",\"placeholder\":\"Search Amazon\",\"value\":\"private-value\"}");
        JSONObject snapshot = new JSONObject().put("nodes",new JSONArray().put(node)).put("width",400).put("height",800).put("sensitive",false).put("url","private-url");
        JSONObject payload = new JSONObject().put("snapshot",snapshot).put("command","open that item").put("recent_context",new JSONArray().put("Read aloud: Product one"));
        for (String provider : new String[]{AssistantSettings.OPENAI,AssistantSettings.GEMINI}) {
            String body = AssistantClient.body(provider,"model",payload).toString();
            if (!body.contains("Product one") || !body.contains("Search Amazon") || !body.contains("placeholder") || body.contains("private-value") || body.contains("private-url")) throw new AssertionError(body);
        }
        System.out.println("Provider context and search metadata tests passed");
    }
}
