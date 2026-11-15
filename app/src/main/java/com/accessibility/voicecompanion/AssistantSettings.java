package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.Set;
import java.util.TreeSet;

/** Provider preferences and API keys encrypted by a non-exportable Android Keystore key. */
public final class AssistantSettings {
    public static final String GEMINI = "Gemini", OPENAI = "OpenAI";
    private static final String ALIAS = "voice_companion_api_keys";
    private final SharedPreferences prefs;
    public AssistantSettings(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(context,"assistant_encrypted",key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            migrate(context.getSharedPreferences("assistant",Context.MODE_PRIVATE),prefs);
        } catch (Exception e) { throw new IllegalStateException("Could not open encrypted settings.",e); }
    }
    static synchronized void migrate(SharedPreferences legacy, SharedPreferences encrypted) {
        SharedPreferences.Editor editor = encrypted.edit();
        for (var entry : legacy.getAll().entrySet()) {
            if (encrypted.contains(entry.getKey())) continue;
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(),(String)value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(),(Boolean)value);
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
        Set<String> normalized = new TreeSet<>();
        for (String site : sites) normalized.add(SharingConsent.hostname(site));
        if (!prefs.edit().putBoolean(provider + ".share_all_sites",allSites).putStringSet(provider + ".approved_sites",normalized).commit()) throw new IllegalStateException("Could not save sharing consent.");
    }
    public void approveSite(String provider, String domain) {
        Set<String> sites = approvedSites(provider); sites.add(SharingConsent.hostname(domain));
        saveSharingConsent(provider,allSitesAllowed(provider),sites);
    }
    public void saveGeneral(boolean share, boolean screenshots, boolean remember, String notes) {
        if (notes.length()>4000) throw new IllegalArgumentException("Preferences must be at most 4000 characters.");
        if (!prefs.edit().putBoolean("share_page",share).putBoolean("share_screenshots",screenshots)
                .putBoolean("remember_preferences",remember).putString("user_preferences",notes.trim()).commit()) throw new IllegalStateException("Could not save preferences.");
    }
    public void saveUserPreferences(String notes) { saveGeneral(sharingEnabled(),screenshotsEnabled(),rememberPreferences(),notes); }
    private static synchronized SecretKey encryptionKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public String apiKey(String provider) throws Exception {
        String encoded = prefs.getString(provider + ".key", "");
        if (encoded.isEmpty()) return "";
        return decrypt(provider,encoded);
    }
    static String decrypt(String purpose, String encoded) throws Exception {
        String[] parts = encoded.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid encrypted value.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    static String encrypt(String purpose, String text) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,encryptionKey());
        cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
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
    }
    public void removeKey(String provider) { prefs.edit().remove(provider + ".key").apply(); }
}
