package com.accessibility.voicecompanion;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Shared helper for Android Keystore AES/GCM encryption without relying on deprecated libraries. */
public final class KeystoreCryptoHelper {
    public static final String KEY_ALIAS = "voice_companion_api_keys";
    private static final Map<String, SecretKey> FALLBACK_KEYS = new ConcurrentHashMap<>();

    private KeystoreCryptoHelper() {}

    public static synchronized SecretKey getOrCreateKey(String alias) throws Exception {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(alias)) {
                return (SecretKey) store.getKey(alias, null);
            }
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            return generator.generateKey();
        } catch (Exception e) {
            // Fallback for JVM unit tests where AndroidKeyStore provider is absent
            SecretKey cached = FALLBACK_KEYS.get(alias);
            if (cached != null) return cached;
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey key = generator.generateKey();
            FALLBACK_KEYS.put(alias, key);
            return key;
        }
    }

    public static String encrypt(String purpose, String text) throws Exception {
        return encrypt(KEY_ALIAS, purpose, text);
    }

    public static String encrypt(String alias, String purpose, String text) throws Exception {
        if (text == null) return null;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias));
        if (purpose != null) {
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
               Base64.encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    public static String decrypt(String purpose, String encoded) throws Exception {
        return decrypt(KEY_ALIAS, purpose, encoded);
    }

    public static String decrypt(String alias, String purpose, String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return "";
        int colon = encoded.indexOf(':');
        if (colon < 0) return encoded; // Legacy unencrypted fallback
        byte[] iv = Base64.decode(encoded.substring(0, colon), Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(encoded.substring(colon + 1), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), spec);
        if (purpose != null) {
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
        }
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}
