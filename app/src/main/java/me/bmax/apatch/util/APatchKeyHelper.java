package me.bmax.apatch.util;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;


public class APatchKeyHelper {
    protected static final String SUPER_KEY = "super_key";
    protected static final String SUPER_KEY_ENC = "super_key_enc";
    private static final String TAG = "APatchSecurityHelper";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String SKIP_STORE_SUPER_KEY = "skip_store_super_key";
    private static final String SUPER_KEY_IV = "super_key_iv";
    private static final String KEY_ALIAS = "APatchSecurityKey";
    private static final String ENCRYPT_MODE = "AES/GCM/NoPadding";
    private static SharedPreferences prefs = null;

    static {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateSecretKey();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to checkAndGenerateSecretKey", e);
        }
    }

    public static void setSharedPreferences(SharedPreferences sp) {
        prefs = sp;
    }

    private static void generateSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

                AlgorithmParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(false)
                        .build();

                keyGenerator.init(spec);
                keyGenerator.generateKey();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to generateSecretKey", e);
        }
    }

    private static String encrypt(String orig) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

            // GCM loses confidentiality and authenticity if a (key, nonce) pair is
            // ever reused, so every encryption gets a fresh IV. The IV is not a
            // secret and travels prepended to the ciphertext.
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ENCRYPT_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

            byte[] ciphertext = cipher.doFinal(orig.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt: ", e);
            return null;
        }
    }

    private static String decrypt(String encryptedData) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

            byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);

            Cipher cipher = Cipher.getInstance(ENCRYPT_MODE);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt: ", e);
            return null;
        }
    }

    // Ciphertexts written before the IV was prepended to the payload used a single
    // IV stored in SUPER_KEY_IV; read them once and let the caller re-encrypt.
    private static String decryptLegacy(String encryptedData) {
        String randIV = prefs.getString(SUPER_KEY_IV, null);
        if (randIV == null) {
            return null;
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

            Cipher cipher = Cipher.getInstance(ENCRYPT_MODE);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, Base64.decode(randIV, Base64.DEFAULT)));

            return new String(cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt legacy data: ", e);
            return null;
        }
    }

    public static boolean shouldSkipStoreSuperKey() {
        return prefs.getInt(SKIP_STORE_SUPER_KEY, 0) != 0;
    }

    public static void clearConfigKey() {
        prefs.edit().remove(SUPER_KEY).apply();
        prefs.edit().remove(SUPER_KEY_ENC).apply();
        prefs.edit().remove(SUPER_KEY_IV).apply();
    }

    public static void setShouldSkipStoreSuperKey(boolean should) {
        clearConfigKey();
        prefs.edit().putInt(SKIP_STORE_SUPER_KEY, should ? 1 : 0).apply();
    }

    public static String readSPSuperKey() {
        String encKey = prefs.getString(SUPER_KEY_ENC, "");
        if (!encKey.isEmpty()) {
            String key = decrypt(encKey);
            if (key == null) {
                key = decryptLegacy(encKey);
                if (key != null) {
                    writeSPSuperKey(key);
                    prefs.edit().remove(SUPER_KEY_IV).apply();
                }
            }
            return key;
        }

        @Deprecated()
        String key = prefs.getString(SUPER_KEY, "");
        writeSPSuperKey(key);
        prefs.edit().remove(SUPER_KEY).apply();
        return key;
    }

    public static void writeSPSuperKey(String key) {
        if (shouldSkipStoreSuperKey()) return;
        key = APatchKeyHelper.encrypt(key);
        prefs.edit().putString(SUPER_KEY_ENC, key).apply();
    }

}
