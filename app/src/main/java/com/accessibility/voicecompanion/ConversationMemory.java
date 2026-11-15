package com.accessibility.voicecompanion;

import org.json.*;
import java.net.URI;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Bounded, encrypted conversation storage; task steps remain in AgentSession. */
public final class ConversationMemory implements AutoCloseable {
    private static final int MAX_TURNS = 24, MAX_CHARACTERS = 16000, MAX_TEXT = 2000;
    private JSONArray turns = new JSONArray();
    private SQLiteDatabase database;
    public ConversationMemory() { }
    public ConversationMemory(Context context) {
        database = context.openOrCreateDatabase("conversation.db",Context.MODE_PRIVATE,null);
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS memory (id INTEGER PRIMARY KEY CHECK(id = 1), content TEXT NOT NULL)");
            try (Cursor cursor = database.rawQuery("SELECT content FROM memory WHERE id = 1",null)) {
                if (cursor.moveToFirst()) turns = new JSONArray(AssistantSettings.decrypt("conversation",cursor.getString(0)));
            }
        } catch (Exception e) { database.close(); throw new IllegalStateException("Could not read saved conversation.",e); }
    }
    private void persist(JSONArray content) {
        if (database == null) return;
        try {
            ContentValues row = new ContentValues(); row.put("id",1); row.put("content",AssistantSettings.encrypt("conversation",content.toString()));
            if (database.insertWithOnConflict("memory",null,row,SQLiteDatabase.CONFLICT_REPLACE) == -1) throw new IllegalStateException("Could not save conversation.");
        } catch (Exception e) { throw new IllegalStateException("Could not save conversation.",e); }
    }
    public void add(String role, String text, String url, String title, long pageId) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            JSONObject page = page(url,title,pageId);
            turns.put(new JSONObject().put("role",role).put("text",text.substring(0,Math.min(text.length(),MAX_TEXT))).put("page",page));
            while (turns.length()>MAX_TURNS || (turns.length()>1 && turns.toString().length()>MAX_CHARACTERS)) turns.remove(0);
            persist(turns);
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
    public JSONArray snapshot(String site) {
        JSONArray result = new JSONArray(), copy = snapshot();
        for (int i=0;i<copy.length();i++) {
            JSONObject turn = copy.optJSONObject(i);
            if (turn != null && turn.optJSONObject("page") != null && site.equals(turn.optJSONObject("page").optString("site"))) result.put(turn);
        }
        return result;
    }
    public void clear() { JSONArray empty = new JSONArray(); persist(empty); turns = empty; }
    @Override public void close() { if (database != null) database.close(); }
}
