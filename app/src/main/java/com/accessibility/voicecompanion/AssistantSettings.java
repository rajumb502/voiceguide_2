package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.util.Set;
import java.util.TreeSet;

/** Provider preferences and API keys encrypted by a non-exportable Android Keystore key. */
public final class AssistantSettings {
    public static final String GEMINI = "Gemini", OPENAI = "OpenAI";
    private final SharedPreferences prefs;
    public AssistantSettings(Context context) {
        SharedPreferences p = null;
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            p = EncryptedSharedPreferences.create(context,"assistant_encrypted",key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            migrate(context.getSharedPreferences("assistant",Context.MODE_PRIVATE),p);
        } catch (Exception e) {
            try {
                p = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE);
            } catch (Exception e2) {
                throw new IllegalStateException("Could not open encrypted settings.", e);
            }
        }
        prefs = p;
        for (String pkg : approvedPackages(GEMINI)) {
            try { SharingConsent.setPackageAllowed(GEMINI, pkg, true); } catch (Exception ignored) {}
        }
        for (String pkg : approvedPackages(OPENAI)) {
            try { SharingConsent.setPackageAllowed(OPENAI, pkg, true); } catch (Exception ignored) {}
        }
        SharingConsent.setActiveProvider(provider());
    }
    static synchronized void migrate(SharedPreferences legacy, SharedPreferences encrypted) {
        SharedPreferences.Editor editor = encrypted.edit();
        for (var entry : legacy.getAll().entrySet()) {
            if (encrypted.contains(entry.getKey())) continue;
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(),(String)value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(),(Boolean)value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(),(Float)value);
            else throw new IllegalStateException("Unexpected legacy setting; migration stopped.");
        }
        if (!editor.commit()) throw new IllegalStateException("Could not migrate settings.");
        if (!legacy.edit().clear().commit()) throw new IllegalStateException("Could not remove legacy settings.");
    }
    public String provider() { return prefs.getString("provider", GEMINI); }
    public String locale() { return prefs.getString("locale", "en-IN"); }
    public String model(String provider) { return prefs.getString(provider + ".model", provider.equals(OPENAI) ? "gpt-4.1-mini" : "gemini-2.5-flash-lite"); }
    public boolean sharingEnabled() { return prefs.getBoolean("share_page", true); }
    public boolean screenshotsEnabled() { return prefs.getBoolean("share_screenshots", true); }
    public boolean rememberPreferences() { return prefs.getBoolean("remember_preferences", true); }
    public boolean audioTonesEnabled() { return prefs.getBoolean("audio_tones", true); }
    public float speechRate() { return prefs.getFloat("speech_rate", 1.0f); }
    public String summary() {
        String p = provider();
        return String.format(java.util.Locale.ROOT,
            "AI provider: %s, model: %s. Language: %s. Speech speed: %.1f times. "
            + "Page sharing: %s. Screenshots: %s. Remember preferences: %s. Audio tones: %s.",
            p, model(p), locale(), speechRate(),
            sharingEnabled() ? "on" : "off",
            screenshotsEnabled() ? "on" : "off",
            rememberPreferences() ? "on" : "off",
            audioTonesEnabled() ? "on" : "off");
    }
    public String userPreferences() { return prefs.getString("user_preferences", ""); }
    public boolean allSitesAllowed(String provider) { return prefs.getBoolean(provider + ".share_all_sites",false); }
    public Set<String> approvedSites(String provider) { return new TreeSet<>(prefs.getStringSet(provider + ".approved_sites",java.util.Collections.emptySet())); }
    public boolean siteSharingAllowed(String domain, String provider) {
        if (!sharingEnabled()) return false;
        try { String host = SharingConsent.hostname(domain); return allSitesAllowed(provider) || approvedSites(provider).contains(host); }
        catch (IllegalArgumentException e) { return false; }
    }
    public void saveSharingConsent(String provider, boolean allSites, Set<String> sites) {
        if (!provider.equals(GEMINI) && !provider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        SharedPreferences.Editor editor = prefs.edit().putBoolean(provider + ".share_all_sites",allSites);
        editor.putStringSet(provider + ".approved_sites",new TreeSet<>(sites));
        if (!editor.commit()) throw new IllegalStateException("Could not save site approvals.");
    }
    public Set<String> approvedPackages(String provider) {
        return new TreeSet<>(prefs.getStringSet(provider + ".approved_packages", java.util.Collections.emptySet()));
    }
    public void saveApprovedPackages(String provider, Set<String> packages) {
        if (!provider.equals(GEMINI) && !provider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(provider + ".approved_packages", new TreeSet<>(packages));
        if (!editor.commit()) throw new IllegalStateException("Could not save approved packages.");
        for (String pkg : packages) {
            try { SharingConsent.setPackageAllowed(provider, pkg, true); } catch (Exception ignored) {}
        }
    }
    public void approvePackage(String provider, String pkg) {
        if (!provider.equals(GEMINI) && !provider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        Set<String> set = approvedPackages(provider);
        set.add(pkg.trim().toLowerCase(java.util.Locale.ROOT));
        saveApprovedPackages(provider, set);
    }
    public void revokePackage(String provider, String pkg) {
        if (!provider.equals(GEMINI) && !provider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        Set<String> set = approvedPackages(provider);
        set.remove(pkg.trim().toLowerCase(java.util.Locale.ROOT));
        saveApprovedPackages(provider, set);
        SharingConsent.setPackageAllowed(provider, pkg, false);
    }
    public void approveSite(String provider, String domain) {
        if (!provider.equals(GEMINI) && !provider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        String host = SharingConsent.hostname(domain);
        Set<String> set = approvedSites(provider); set.add(host);
        if (!prefs.edit().putStringSet(provider + ".approved_sites",set).commit()) throw new IllegalStateException("Could not save site approval.");
    }
    public void saveGeneral(boolean sharePage, boolean shareScreenshots, boolean rememberPreferences, String userPreferences) {
        saveGeneral(sharePage, shareScreenshots, rememberPreferences, audioTonesEnabled(), speechRate(), userPreferences);
    }
    public void saveGeneral(boolean sharePage, boolean shareScreenshots, boolean rememberPreferences, boolean audioTones, String userPreferences) {
        saveGeneral(sharePage, shareScreenshots, rememberPreferences, audioTones, speechRate(), userPreferences);
    }
    public void saveGeneral(boolean sharePage, boolean shareScreenshots, boolean rememberPreferences, boolean audioTones, float speechRate, String userPreferences) {
        if (userPreferences != null && userPreferences.length() > 4000) throw new IllegalArgumentException("Preferences exceed maximum length.");
        float clampedRate = Math.max(0.5f, Math.min(3.0f, speechRate));
        SharedPreferences.Editor editor = prefs.edit().putBoolean("share_page",sharePage)
            .putBoolean("share_screenshots",shareScreenshots)
            .putBoolean("remember_preferences",rememberPreferences)
            .putBoolean("audio_tones",audioTones)
            .putFloat("speech_rate",clampedRate);
        if (userPreferences != null) editor.putString("user_preferences",userPreferences);
        if (!editor.commit()) throw new IllegalStateException("Could not save settings.");
    }
    public void saveAudioTones(boolean enabled) {
        if (!prefs.edit().putBoolean("audio_tones", enabled).commit()) throw new IllegalStateException("Could not save audio tones setting.");
    }
    public void saveSpeechRate(float rate) {
        float clamped = Math.max(0.5f, Math.min(3.0f, rate));
        if (!prefs.edit().putFloat("speech_rate", clamped).commit()) throw new IllegalStateException("Could not save speech rate setting.");
    }
    public void saveSharingEnabled(boolean enabled) {
        if (!prefs.edit().putBoolean("share_page", enabled).commit()) throw new IllegalStateException("Could not save page sharing setting.");
    }
    public void saveScreenshotsEnabled(boolean enabled) {
        if (!prefs.edit().putBoolean("share_screenshots", enabled).commit()) throw new IllegalStateException("Could not save screenshot sharing setting.");
    }
    public void saveRememberPreferences(boolean enabled) {
        if (!prefs.edit().putBoolean("remember_preferences", enabled).commit()) throw new IllegalStateException("Could not save remember preferences setting.");
    }
    public void saveLocale(String newLocale) {
        if (!newLocale.matches("[a-zA-Z]{2,3}(-[a-zA-Z]{2,8})*")) throw new IllegalArgumentException("Enter a language tag such as en-IN or hi-IN.");
        if (!prefs.edit().putString("locale", newLocale).commit()) throw new IllegalStateException("Could not save language.");
    }
    public void saveProvider(String newProvider) {
        if (!newProvider.equals(GEMINI) && !newProvider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        if (!prefs.edit().putString("provider", newProvider).commit()) throw new IllegalStateException("Could not save provider.");
        SharingConsent.setActiveProvider(newProvider);
    }
    public void saveUserPreferences(String notes) { saveGeneral(sharingEnabled(),screenshotsEnabled(),rememberPreferences(),audioTonesEnabled(),speechRate(),notes); }
    public String apiKey(String provider) throws Exception {
        String encoded = prefs.getString(provider + ".key", "");
        if (encoded.isEmpty()) return "";
        return decrypt(provider,encoded);
    }
    static String decrypt(String purpose, String encoded) throws Exception {
        return KeystoreCryptoHelper.decrypt(purpose, encoded);
    }
    static String encrypt(String purpose, String text) throws Exception {
        return KeystoreCryptoHelper.encrypt(purpose, text);
    }
    public void save(String provider, String model, String key, String locale) throws Exception {
        if (!provider.equals(GEMINI) && !provider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        if (!model.matches("[A-Za-z0-9._-]{1,100}")) throw new IllegalArgumentException("Enter a valid model ID.");
        if (!key.isEmpty() && !key.matches("[\\x21-\\x7E]{1,1024}")) throw new IllegalArgumentException("Enter an API key without spaces.");
        if (!locale.matches("[a-zA-Z]{2,3}(-[a-zA-Z]{2,8})*")) throw new IllegalArgumentException("Enter a language tag such as en-IN or hi-IN.");
        SharedPreferences.Editor editor = prefs.edit().putString("provider",provider).putString(provider + ".model",model).putString("locale",locale);
        if (!key.isEmpty()) {
            editor.putString(provider + ".key",encrypt(provider,key));
        }
        if (!editor.commit()) throw new IllegalStateException("Could not save settings.");
        SharingConsent.setActiveProvider(provider);
    }
    public void removeKey(String provider) { prefs.edit().remove(provider + ".key").apply(); }
}
