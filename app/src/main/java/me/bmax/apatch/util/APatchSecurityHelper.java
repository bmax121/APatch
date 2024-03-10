package me.bmax.apatch.util;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import dev.utils.app.AppUtils;

public class APatchSecurityHelper {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String RAND_IV = "rand_iv";
    private static final String KEY_ALIAS = "APatchSecurityKey";
    private static final String ENCRYPT_MODE = "AES/GCM/NoPadding";

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
            Log.e("APatch", "[APatchSecurityHelper] Failed to generateSecretKey: " + e);
            e.printStackTrace();
        }
    }

    public static void checkAndGenerateSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateSecretKey();
            }
        } catch (Exception e) {
            Log.e("APatch", "[APatchSecurityHelper] Failed to checkAndGenerateSecretKey: " + e);
            e.printStackTrace();
        }
    }


    private static String getRandomIV() {
        String randIV = AppUtils.getSharedPreferences("config", Context.MODE_PRIVATE).getString(RAND_IV, null);
        if (randIV == null) {
            SecureRandom secureRandom = new SecureRandom();
            byte[] generated = secureRandom.generateSeed(12);
            randIV = Base64.encodeToString(generated, Base64.DEFAULT);
            AppUtils.getSharedPreferences("config", Context.MODE_PRIVATE).edit().putString(RAND_IV, randIV).apply();
        }
        return randIV;
    }

    public static String encrypt(String orig) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

            Cipher cipher = Cipher.getInstance(ENCRYPT_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, Base64.decode(getRandomIV(), Base64.DEFAULT)));

            return Base64.encodeToString(cipher.doFinal(orig.getBytes(StandardCharsets.UTF_8)), Base64.DEFAULT);
        } catch (Exception e) {
            Log.e("APatch", "[APatchSecurityHelper] Failed to encrypt: " + e);
            e.printStackTrace();

            return null;
        }
    }

    public static String decrypt(String encryptedData) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

            Cipher cipher = Cipher.getInstance(ENCRYPT_MODE);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, Base64.decode(getRandomIV(), Base64.DEFAULT)));

            return new String(cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e("APatch", "[APatchSecurityHelper] Failed to decrypt: " + e);
            e.printStackTrace();
            return null;
        }
    }
}
