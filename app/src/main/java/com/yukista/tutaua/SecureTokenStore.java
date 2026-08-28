package com.yukista.tutaua;

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

/** Stores the Jellyfin access token encrypted with a non-exportable Android Keystore key. */
final class SecureTokenStore {
    private static final String KEY_ALIAS = "tutaua_session_key_v1";
    private static final String CIPHER_KEY = "token_cipher_v1";
    private static final String IV_KEY = "token_iv_v1";
    private static final String LEGACY_KEY = "token";

    private final SharedPreferences preferences;

    SecureTokenStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    String read() {
        String cipherText = preferences.getString(CIPHER_KEY, "");
        String iv = preferences.getString(IV_KEY, "");
        if (!cipherText.isEmpty() && !iv.isEmpty()) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(),
                        new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
                return new String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)),
                        StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                clear();
                return "";
            }
        }

        String legacy = preferences.getString(LEGACY_KEY, "");
        if (!legacy.isEmpty() && write(legacy)) preferences.edit().remove(LEGACY_KEY).apply();
        return legacy;
    }

    boolean write(String token) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            String encrypted = Base64.encodeToString(
                    cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
            return preferences.edit()
                    .putString(CIPHER_KEY, encrypted)
                    .putString(IV_KEY, iv)
                    .remove(LEGACY_KEY)
                    .commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    void clear() {
        preferences.edit().remove(CIPHER_KEY).remove(IV_KEY).remove(LEGACY_KEY).apply();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
