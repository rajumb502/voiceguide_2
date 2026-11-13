package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Provider preferences and API keys encrypted by a non-exportable Android Keystore key. */
public final class AssistantSettings {
    public static final String GEMINI = "Gemini", OPENAI = "OpenAI";
    private static final String ALIAS = "voice_companion_api_keys";
    private final SharedPreferences prefs;
    public AssistantSettings(Context context) { prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE); }
    public String provider() { return prefs.getString("provider", GEMINI); }
    public String locale() { return prefs.getString("locale", "en-IN"); }
    public String model(String provider) { return prefs.getString(provider + ".model", provider.equals(OPENAI) ? "gpt-4.1-mini" : "gemini-2.5-flash"); }
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
        String[] parts = encoded.split(":", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        cipher.updateAAD(provider.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    public void save(String provider, String model, String key, String locale) throws Exception {
        if (!provider.equals(GEMINI) && !provider.equals(OPENAI)) throw new IllegalArgumentException("Invalid provider");
        if (!model.matches("[A-Za-z0-9._-]{1,100}")) throw new IllegalArgumentException("Enter a valid model ID.");
        if (!key.matches("[\\x21-\\x7E]{1,1024}")) throw new IllegalArgumentException("Enter an API key without spaces.");
        if (!locale.matches("[a-zA-Z]{2,3}(-[a-zA-Z]{2,8})*")) throw new IllegalArgumentException("Enter a language tag such as en-IN or hi-IN.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
        cipher.updateAAD(provider.getBytes(StandardCharsets.UTF_8));
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(key.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString("provider", provider).putString(provider + ".model", model)
                .putString(provider + ".key", encrypted).putString("locale", locale).commit()) throw new IllegalStateException("Could not save settings.");
    }
    public void removeKey(String provider) { prefs.edit().remove(provider + ".key").apply(); }
}
