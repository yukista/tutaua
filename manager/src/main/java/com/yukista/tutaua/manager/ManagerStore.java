package com.yukista.tutaua.manager;

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

final class ManagerStore {
    private static final String PREFS = "manager_state_v1";
    private static final String KEY_ALIAS = "tutaua_manager_token_v1";
    private final SharedPreferences preferences;

    ManagerStore(Context context) { preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    boolean enrolled() { return !preferences.getString("device_id", "").isEmpty(); }
    String server() { return preferences.getString("server", ""); }
    String deviceId() { return preferences.getString("device_id", ""); }
    String lanAddress() { return preferences.getString("lan_address", ""); }
    int configVersion() { return preferences.getInt("config_version", 0); }
    String reportedConfig() { return preferences.getString("reported_config", "{}"); }
    void configuration(int version, String json) {
        preferences.edit().putInt("config_version", version).putString("reported_config", json).apply();
    }
    void enrollment(String server, String lanAddress, String deviceId, String token) throws Exception {
        preferences.edit().putString("server", trim(server)).putString("device_id", deviceId)
                .putString("lan_address", lanAddress.trim())
                .putString("token", encrypt(token)).commit();
    }
    String token() throws Exception { return decrypt(preferences.getString("token", "")); }

    private static String trim(String value) { return value.trim().replaceAll("/+$", ""); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(KEY_ALIAS, null);
    }
    private String encrypt(String clear) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                Base64.encodeToString(cipher.doFinal(clear.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    private String decrypt(String stored) throws Exception {
        String[] parts = stored.split("\\.", 2); if (parts.length != 2) throw new IllegalStateException("missing token");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
}
