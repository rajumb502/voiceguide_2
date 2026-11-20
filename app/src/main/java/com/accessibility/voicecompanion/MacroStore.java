package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Encrypted, persistent store for multi-step browser macros ("Teach Once, Replay Forever").
 * Allows recording successful flows (e.g., "Check BESCOM Bill") and replaying them locally
 * via deterministic DOM clicks and inputs without invoking cloud LLM, falling back to Gemini
 * only if an element selector is missing or page structure changes.
 */
public final class MacroStore {
    private static final String PREF_NAME = "voice_companion_macros";
    private static final String KEY_INDEX = "macro_keys_index";
    private static final String PURPOSE_PREFIX = "macro_payload_";

    private final SharedPreferences prefs;
    private final Map<String, Macro> cache = new LinkedHashMap<>();

    public static class Step {
        public final String action;      // "CLICK", "TYPE", "NAVIGATE", "SCROLL", "SUBMIT"
        public final String selector;    // CSS selector e.g. "button#submit", "input[name='consumer_id']"
        public final String label;       // Accessible text / button label
        public final String tag;         // Element tag e.g. "BUTTON", "INPUT", "A"
        public final String role;        // ARIA role
        public final String name;        // Element name attribute
        public final String placeholder; // Input placeholder
        public final String text;        // Text to enter for TYPE action
        public final String url;         // URL for NAVIGATE action
        public final String direction;   // "down" / "up" for SCROLL action

        public Step(String action, String selector, String label, String tag, String role,
                    String name, String placeholder, String text, String url, String direction) {
            this.action = action == null || action.trim().isEmpty() ? "CLICK" : action.trim();
            this.selector = selector == null ? "" : selector.trim();
            this.label = label == null ? "" : label.trim();
            this.tag = tag == null ? "" : tag.trim();
            this.role = role == null ? "" : role.trim();
            this.name = name == null ? "" : name.trim();
            this.placeholder = placeholder == null ? "" : placeholder.trim();
            this.text = text == null ? "" : text;
            this.url = url == null ? "" : url.trim();
            this.direction = direction == null ? "" : direction.trim();
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("action", action);
                if (!selector.isEmpty()) json.put("selector", selector);
                if (!label.isEmpty()) json.put("label", label);
                if (!tag.isEmpty()) json.put("tag", tag);
                if (!role.isEmpty()) json.put("role", role);
                if (!name.isEmpty()) json.put("name", name);
                if (!placeholder.isEmpty()) json.put("placeholder", placeholder);
                if (!text.isEmpty()) json.put("text", text);
                if (!url.isEmpty()) json.put("url", url);
                if (!direction.isEmpty()) json.put("direction", direction);
                return json;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public static Step fromJson(JSONObject json) {
            if (json == null) return new Step("CLICK", "", "", "", "", "", "", "", "", "");
            return new Step(
                json.optString("action", "CLICK"),
                json.optString("selector", ""),
                json.optString("label", ""),
                json.optString("tag", ""),
                json.optString("role", ""),
                json.optString("name", ""),
                json.optString("placeholder", ""),
                json.optString("text", ""),
                json.optString("url", ""),
                json.optString("direction", "")
            );
        }
    }

    public static class Macro {
        public final String name;
        public final String initialUrl;
        public final List<Step> steps;

        public Macro(String name, String initialUrl, List<Step> steps) {
            this.name = name == null ? "" : name.trim();
            this.initialUrl = initialUrl == null ? "" : initialUrl.trim();
            this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("initial_url", initialUrl);
                JSONArray array = new JSONArray();
                for (Step s : steps) array.put(s.toJson());
                json.put("steps", array);
                return json;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public static Macro fromJson(JSONObject json) {
            if (json == null) return new Macro("", "", Collections.emptyList());
            String name = json.optString("name", "");
            String initialUrl = json.optString("initial_url", "");
            List<Step> steps = new ArrayList<>();
            JSONArray array = json.optJSONArray("steps");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject stepObj = array.optJSONObject(i);
                    if (stepObj != null) steps.add(Step.fromJson(stepObj));
                }
            }
            return new Macro(name, initialUrl, steps);
        }
    }

    public MacroStore() {
        this.prefs = null;
    }

    public MacroStore(Context context) {
        if (context != null) {
            this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            loadFromPrefs();
        } else {
            this.prefs = null;
        }
    }

    private void loadFromPrefs() {
        if (prefs == null) return;
        try {
            String indexJson = prefs.getString(KEY_INDEX, null);
            if (indexJson == null || indexJson.isEmpty()) return;
            JSONArray array = new JSONArray(indexJson);
            for (int i = 0; i < array.length(); i++) {
                String key = array.optString(i);
                if (key.isEmpty()) continue;
                String encryptedData = prefs.getString("data_" + key, null);
                if (encryptedData == null) continue;
                try {
                    String decrypted;
                    try {
                        decrypted = KeystoreCryptoHelper.decrypt(PURPOSE_PREFIX + key, encryptedData);
                    } catch (Exception e) {
                        decrypted = encryptedData; // fallback
                    }
                    Macro macro = Macro.fromJson(new JSONObject(decrypted));
                    if (!macro.name.isEmpty()) {
                        cache.put(normalizeKey(macro.name), macro);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void persistIndex() {
        if (prefs == null) return;
        try {
            JSONArray array = new JSONArray(cache.keySet());
            prefs.edit().putString(KEY_INDEX, array.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized void saveMacro(Macro macro) {
        if (macro == null || macro.name.isEmpty() || macro.steps.isEmpty()) return;
        String key = normalizeKey(macro.name);
        cache.put(key, macro);
        if (prefs != null) {
            try {
                String payload = macro.toJson().toString();
                String encrypted;
                try {
                    encrypted = KeystoreCryptoHelper.encrypt(PURPOSE_PREFIX + key, payload);
                } catch (Exception e) {
                    encrypted = payload; // fallback
                }
                prefs.edit().putString("data_" + key, encrypted).apply();
                persistIndex();
            } catch (Exception ignored) {}
        }
    }

    public synchronized Macro getMacro(String name) {
        if (name == null) return null;
        return cache.get(normalizeKey(name));
    }

    public synchronized boolean hasMacro(String name) {
        if (name == null) return false;
        return cache.containsKey(normalizeKey(name));
    }

    public synchronized boolean deleteMacro(String name) {
        if (name == null) return false;
        String key = normalizeKey(name);
        Macro removed = cache.remove(key);
        if (removed != null && prefs != null) {
            prefs.edit().remove("data_" + key).apply();
            persistIndex();
            return true;
        }
        return removed != null;
    }

    public synchronized List<Macro> listMacros() {
        return new ArrayList<>(cache.values());
    }

    public synchronized List<String> listMacroNames() {
        List<String> names = new ArrayList<>();
        for (Macro m : cache.values()) {
            names.add(m.name);
        }
        return names;
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized void clear() {
        cache.clear();
        if (prefs != null) {
            prefs.edit().clear().apply();
        }
    }

    /**
     * Finds a saved macro matching a spoken voice command.
     * Matches prefixes like "run macro <name>", "replay macro <name>", "start macro <name>",
     * or direct matching of the macro name in the spoken command.
     */
    public synchronized Macro findMatchingMacro(String spokenInput) {
        if (spokenInput == null || spokenInput.trim().isEmpty() || cache.isEmpty()) return null;
        String normalized = normalizeInput(spokenInput);

        // 1. Check for explicit trigger phrases
        String[] prefixes = {
            "run macro", "replay macro", "execute macro", "play macro", "start macro",
            "open macro", "trigger macro", "run the macro", "macro"
        };
        for (String p : prefixes) {
            if (normalized.startsWith(p)) {
                String remainder = normalized.substring(p.length()).trim();
                if (!remainder.isEmpty()) {
                    Macro directMatch = getMacro(remainder);
                    if (directMatch != null) return directMatch;
                    for (Map.Entry<String, Macro> entry : cache.entrySet()) {
                        if (remainder.equals(entry.getKey()) || remainder.contains(entry.getKey()) || entry.getKey().contains(remainder)) {
                            return entry.getValue();
                        }
                    }
                }
            }
        }

        // 2. Direct match against stored macro names
        for (Map.Entry<String, Macro> entry : cache.entrySet()) {
            String key = entry.getKey();
            if (normalized.equals(key) || normalized.equals("run " + key) || normalized.equals("replay " + key) || normalized.equals("play " + key)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public static String normalizeKey(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String normalizeInput(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
