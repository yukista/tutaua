package com.yukista.tutaua.box;

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

/**
 * Stores the Jellyfin password used by the Box for automatic sign-in.
 *
 * The password is encrypted with a non-exportable Android Keystore key and is
 * never exposed through the configuration content provider. Only the Box can
 * decrypt it to hand it to the Tutaua app over a signature-protected broadcast.
 */
final class BoxCredentials {
    private static final String CIPHER_KEY = "jellyfin.password_cipher";
    private static final String IV_KEY = "jellyfin.password_iv";
    private static final String KEY_ALIAS = "tutaua_box_jellyfin_v1";

    private BoxCredentials() {}

    static void write(Context context, String password) {
        if (password == null || password.isEmpty()) { clear(context); return; }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            String encrypted = Base64.encodeToString(
                    cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
            BoxConfig.preferences(context).edit()
                    .putString(CIPHER_KEY, encrypted).putString(IV_KEY, iv).commit();
        } catch (Exception ignored) { }
    }

    static String read(Context context) {
        SharedPreferences preferences = BoxConfig.preferences(context);
        String encrypted = preferences.getString(CIPHER_KEY, "");
        String iv = preferences.getString(IV_KEY, "");
        if (encrypted.isEmpty() || iv.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            clear(context);
            return "";
        }
    }

    static boolean has(Context context) { return !read(context).isEmpty(); }

    static void clear(Context context) {
        BoxConfig.preferences(context).edit().remove(CIPHER_KEY).remove(IV_KEY).commit();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
