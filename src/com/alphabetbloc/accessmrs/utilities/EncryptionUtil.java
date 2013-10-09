/*
 * Copyright (C) 2012 Louis Fazen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alphabetbloc.accessmrs.utilities;

import javax.crypto.spec.SecretKeySpec;

import com.alphabetbloc.accessmrs.R;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Wrapper function for the generic Crypto class, allows for easy access to
 * universal key from Android credential sotrage.
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class EncryptionUtil {

	private static final String TAG = EncryptionUtil.class.getSimpleName();
	private static KeyStoreUtil keyStoreUtil;

	/**
	 * We are using one universal password for this application to keep things
	 * simpler. This holds for SqlCipherDb in both AccessMRS and AccessForms
	 * (which pulls in the same password), as well as for both the keystore and
	 * the trustore for SSL Server and Client Authentication.
	 * 
	 * @return The common password for keystore and truststore.
	 */
	public static String getPassword() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		String encryptedPwd = prefs.getString(App.getApp().getString(R.string.key_encryption_password), null);
		String decryptedPwd = decryptString(encryptedPwd);
		return decryptedPwd;
	}

	public static String encryptString(String string) {
		SecretKeySpec key = getKey(App.getApp().getString(R.string.key_encryption_key));
		String encryptedString = null;
		if (string != null && key != null) {
			encryptedString = Crypto.encrypt(string, key);
		} else {
			Log.w(TAG, "Encryption key not found in keystore: " + App.getApp().getString(R.string.key_encryption_key));
		}

		return encryptedString;
	}

	public static String decryptString(String string) {
		SecretKeySpec key = getKey(App.getApp().getString(R.string.key_encryption_key));
		String decryptedString = null;
		if (string != null && key != null) {
			decryptedString = Crypto.decrypt(string, key);
		} else {
			Log.w(TAG, "Encryption key not found in keystore: " + App.getApp().getString(R.string.key_encryption_key));
		}
		return decryptedString;
	}

	public static SecretKeySpec getKey(String keyName) {
		if (keyStoreUtil == null)
			keyStoreUtil = KeyStoreUtil.getInstance();
		byte[] keyBytes = keyStoreUtil.get(keyName);
		if (keyBytes == null) {
			Log.w(TAG, "Encryption key not found in keystore: " + keyName);
			return null;
		}
		SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
		return key;
	}

}