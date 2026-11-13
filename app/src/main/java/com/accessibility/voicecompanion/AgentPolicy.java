package com.accessibility.voicecompanion;

import org.json.*;
import java.net.URI;
import java.util.Locale;

/** Automatic operations are limited to recognizable navigation and search. */
public final class AgentPolicy {
    public static JSONObject target(JSONObject action, JSONObject snapshot) throws JSONException {
        JSONArray nodes = snapshot.getJSONArray("nodes");
        for (int i=0;i<nodes.length();i++) if (nodes.getJSONObject(i).getString("id").equals(action.getString("target_id"))) return nodes.getJSONObject(i);
        return null;
    }
    public static boolean needsConfirmation(JSONObject action, JSONObject snapshot) throws JSONException {
        String kind = action.getString("action");
        JSONObject node = target(action,snapshot);
        String label = action.getString("action_summary") + " " + (node == null ? "" : node.getString("label") + " " + node.optString("href"));
        if (new ActionSafetyGuard().requiresConfirmation(action.getBoolean("requires_confirmation"),label)) return true;
        if (kind.equals("TYPE")) return node == null || !node.optBoolean("search");
        if (kind.equals("SUBMIT")) return node == null || !node.optBoolean("search_form");
        if (kind.equals("CLICK")) {
            if (node == null || node.optBoolean("download")) return true;
            // Forms and unknown controls are never silently activated.
            if (node.optString("tag").equals("A") && node.optString("href").matches("https?://.*")) return false;
            if (node.optBoolean("search")) return false;
            return true;
        }
        return false;
    }
    public static boolean navigationAllowed(String goal, String url, JSONObject snapshot) throws Exception {
        String host = new URI(url).getHost();
        String normalized = goal.toLowerCase(Locale.ROOT).replaceAll("\\s+dot\\s+", ".");
        if (host != null && java.util.regex.Pattern.compile("(?<![a-z0-9.-])" + java.util.regex.Pattern.quote(host.toLowerCase(Locale.ROOT)) + "(?![a-z0-9.-])").matcher(normalized).find()) return true;
        for (int i=0;i<snapshot.getJSONArray("nodes").length();i++) if (url.equals(snapshot.getJSONArray("nodes").getJSONObject(i).optString("href"))) return true;
        return false;
    }
}
