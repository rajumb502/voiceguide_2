package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Only direct user speech is eligible for saved preferences, never page/model text. */
public final class UserPreferences {
    public static final String PREFS_NAME = "user_preferences_store";
    public static final String KEY_ENCRYPTED = "encrypted_profile";
    public static final String KEY_FACTS_ENCRYPTED = "encrypted_facts";
    public static final String KEY_LEGACY = "user_preferences";
    private static final int MAX_FACTS = 30;
    private static final Pattern FACT_PATTERN = Pattern.compile(
        "^(?:please\\s+)?(?:remember(?:\\s+that)?|save|store)\\s+my\\s+([a-zA-Z][a-zA-Z0-9 _-]{1,40}?)\\s+(?:is|as|=|:)\\s+(.{1,200})$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SENSITIVE_FACT_KEY = Pattern.compile(
        "(?i).*(?:password|otp|cvv|mpin|\\bpin\\b|secret|card.?number).*"
    );

    public record Update(String text, String category) {}
    public record Fact(String key, String value) {}

    public static Fact extractFact(String spoken) {
        if (spoken == null) return null;
        String cleaned = spoken.trim().replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        Matcher m = FACT_PATTERN.matcher(cleaned);
        if (!m.matches()) return null;
        String key = m.group(1).trim();
        String value = m.group(2).trim();
        if (key.isEmpty() || value.isEmpty() || SENSITIVE_FACT_KEY.matcher(key).matches()) return null;
        return new Fact(key, value);
    }

    public static Update feedback(String spoken) {
        String normalized = VoiceCommands.normalize(spoken);
        if (normalized.matches("(?:please )?(?:keep (?:your )?answers short|be brief|be concise|give (?:me )?short answers)")) return new Update("Response detail: brief", "Response detail:");
        if (normalized.matches("(?:please )?(?:give (?:me )?more detail|give detailed answers|explain in detail)")) return new Update("Response detail: detailed", "Response detail:");
        String text = spoken.trim().replaceFirst("(?i)^(?:please )?remember(?: that| this preference| my preference)?[ :,-]+", "");
        if (text.equals(spoken.trim())) {
            if (normalized.startsWith("i prefer ")) text = spoken.trim();
            else text = spoken.trim().replaceFirst("(?i)^(?:from now on|for future conversations)[ ,:]+", "");
        }
        if ((text.equals(spoken.trim()) && !normalized.startsWith("i prefer ")) || text.isEmpty()) return null;
        return new Update(text, "");
    }

    public static String merge(String existing, Update update) {
        List<String> notes = new ArrayList<>();
        String catLower = update.category().toLowerCase(Locale.ROOT);
        for (String line : existing.split("\\n")) {
            String item = line.trim();
            if (item.isEmpty() || item.equalsIgnoreCase(update.text())
                    || (!catLower.isEmpty() && item.toLowerCase(Locale.ROOT).startsWith(catLower))) continue;
            notes.add(item);
        }
        String sanitized = update.text().replaceAll("[\\r\\n]+", " ");
        notes.add(sanitized.substring(0, Math.min(500, sanitized.length())));
        while (notes.size() > 20 || String.join("\n", notes).length() > 4000) notes.remove(0);
        return String.join("\n", notes);
    }

    /** Load encrypted preferences from SharedPreferences, migrating and wiping legacy plaintext. */
    public static String load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(KEY_LEGACY)) {
            String legacy = prefs.getString(KEY_LEGACY, "");
            try {
                save(context, legacy);
                prefs.edit().remove(KEY_LEGACY).commit();
            } catch (Exception ignored) {}
        }
        String encrypted = prefs.getString(KEY_ENCRYPTED, "");
        if (encrypted.isEmpty()) return "";
        try {
            return KeystoreCryptoHelper.decrypt("user_preferences", encrypted);
        } catch (Exception e) {
            return "";
        }
    }

    /** Save preferences encrypted via KeystoreCryptoHelper into standard SharedPreferences. */
    public static void save(Context context, String plaintext) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (plaintext == null || plaintext.trim().isEmpty()) {
            prefs.edit().remove(KEY_ENCRYPTED).remove(KEY_LEGACY).commit();
            return;
        }
        String encrypted = KeystoreCryptoHelper.encrypt("user_preferences", plaintext.trim());
        prefs.edit().putString(KEY_ENCRYPTED, encrypted).remove(KEY_LEGACY).commit();
    }

    /** Load Keystore-encrypted key-value user facts (e.g. consumer ID, voter ID, email). */
    public static Map<String, String> loadFacts(Context context) {
        Map<String, String> facts = new LinkedHashMap<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encrypted = prefs.getString(KEY_FACTS_ENCRYPTED, "");
        if (encrypted.isEmpty()) return facts;
        try {
            String json = KeystoreCryptoHelper.decrypt("user_facts", encrypted);
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = obj.optString(k, "").trim();
                if (!k.trim().isEmpty() && !v.isEmpty()) facts.put(k.trim(), v);
            }
        } catch (Exception ignored) {}
        return facts;
    }

    /** Persist key-value user facts encrypted via KeystoreCryptoHelper. */
    public static void saveFacts(Context context, Map<String, String> facts) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (facts == null || facts.isEmpty()) {
            prefs.edit().remove(KEY_FACTS_ENCRYPTED).commit();
            return;
        }
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().trim().isEmpty()
                    && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                obj.put(entry.getKey().trim(), entry.getValue().trim());
            }
        }
        if (obj.length() == 0) {
            prefs.edit().remove(KEY_FACTS_ENCRYPTED).commit();
            return;
        }
        String encrypted = KeystoreCryptoHelper.encrypt("user_facts", obj.toString());
        prefs.edit().putString(KEY_FACTS_ENCRYPTED, encrypted).commit();
    }

    public static void putFact(Context context, String key, String value) throws Exception {
        if (key == null || value == null || key.trim().isEmpty() || value.trim().isEmpty()) return;
        Map<String, String> facts = loadFacts(context);
        String targetLower = key.trim().toLowerCase(Locale.ROOT);
        facts.entrySet().removeIf(e -> e.getKey().toLowerCase(Locale.ROOT).equals(targetLower));
        facts.put(key.trim(), value.trim());
        while (facts.size() > MAX_FACTS) {
            String oldest = facts.keySet().iterator().next();
            facts.remove(oldest);
        }
        saveFacts(context, facts);
    }

    public static void clearFacts(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_FACTS_ENCRYPTED).commit();
    }

    public static String formatFacts(Context context) {
        Map<String, String> facts = loadFacts(context);
        if (facts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }
}
