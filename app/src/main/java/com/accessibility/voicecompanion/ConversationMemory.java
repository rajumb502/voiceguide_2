package com.accessibility.voicecompanion;

import org.json.*;
import java.net.URI;

/** Bounded conversation memory, separate from the action history of any one task. */
public final class ConversationMemory {
    private static final int MAX_TURNS = 24, MAX_CHARACTERS = 16000, MAX_TEXT = 2000;
    private final JSONArray turns = new JSONArray();
    public void add(String role, String text, String url, String title, long pageId) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            JSONObject page = page(url,title,pageId);
            turns.put(new JSONObject().put("role",role).put("text",text.substring(0,Math.min(text.length(),MAX_TEXT))).put("page",page));
            while (turns.length()>MAX_TURNS || (turns.length()>1 && turns.toString().length()>MAX_CHARACTERS)) turns.remove(0);
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    public static JSONObject page(String url, String title, long pageId) {
        JSONObject page = new JSONObject();
        try {
            page.put("page_id",pageId).put("title",title == null ? "" : title.substring(0,Math.min(300,title.length())));
            if (url != null) {
                URI uri = new URI(url);
                if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null) {
                    // Strip query strings, fragments and credentials; never store the raw URL.
                    String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
                    page.put("site",uri.getHost()).put("path",path.substring(0,Math.min(500,path.length())));
                }
            }
        } catch (Exception ignored) { }
        return page;
    }
    public JSONArray snapshot() {
        try { return new JSONArray(turns.toString()); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }
    public void clear() { while (turns.length()>0) turns.remove(0); }
}
