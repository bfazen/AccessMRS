/*
 * Copyright 2012 Timelappse
 * Copyright 2012 Andlytics Project

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

   
package com.alphabetbloc.accessmrs.utilities;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import android.util.Log;
/**
 * 
 * @author Nikolay Nelenkov from the keystore application:
 * https://github.com/nelenkov/android-keystore
 *
 */
public class Crypto {

    private static final String TAG = Crypto.class.getSimpleName();

    private static String DELIMITER = "]";

    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static int KEY_LENGTH = 256;

    private static SecureRandom random = new SecureRandom();

    private Crypto() {
    }

    public static void listAlgorithms(String algFilter) {
        Provider[] providers = Security.getProviders();
        for (Provider p : providers) {
            String providerStr = String.format("%s/%s/%f\n", p.getName(),
                    p.getInfo(), p.getVersion());
//            if (App.DEBUG) Log.v(TAG, providerStr);
            Set<Service> services = p.getServices();
            List<String> algs = new ArrayList<String>();
            for (Service s : services) {
                boolean match = true;
                if (algFilter != null) {
                    match = s.getAlgorithm().toLowerCase()
                            .contains(algFilter.toLowerCase());
                }

                if (match) {
                    String algStr = String.format("\t%s/%s/%s", s.getType(),
                            s.getAlgorithm(), s.getClassName());
                    algs.add(algStr);
                }
            }

            Collections.sort(algs);
            for (String alg : algs) {
                if (App.DEBUG) Log.v(TAG, "\t" + alg);
            }
            if (App.DEBUG) Log.v(TAG, "");
        }
    }

    public static SecretKey generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(KEY_LENGTH);
            SecretKey key = kg.generateKey();

            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    public static byte[] generateIv(int length) {
        byte[] b = new byte[length];
        random.nextBytes(b);

        return b;
    }

    public static String encrypt(String plaintext, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

            byte[] iv = generateIv(cipher.getBlockSize());
//            if (App.DEBUG) Log.v(TAG, "IV: " + toHex(iv));
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
//            if (App.DEBUG) Log.v(TAG, "Cipher IV: "
//                    + (cipher.getIV() == null ? null : toHex(cipher.getIV())));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes("UTF-8"));

            return String.format("%s%s%s", toBase64(iv), DELIMITER,
                    toBase64(cipherText));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuffer buff = new StringBuffer();
        for (byte b : bytes) {
            buff.append(String.format("%02X", b));
        }

        return buff.toString();
    }

    public static String toBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static byte[] fromBase64(String base64) {
        return Base64.decode(base64, Base64.NO_WRAP);
    }

    public static String decrypt(String ciphertext, SecretKey key) {
        try {
            String[] fields = ciphertext.split(DELIMITER);
            if (fields.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid encypted text format");
            }

            byte[] iv = fromBase64(fields[0]);
            byte[] cipherBytes = fromBase64(fields[1]);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, key, ivParams);
//            if (App.DEBUG) Log.v(TAG, "Cipher IV: " + toHex(cipher.getIV()));
            byte[] plaintext = cipher.doFinal(cipherBytes);
            String plainrStr = new String(plaintext, "UTF-8");

            return plainrStr;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
