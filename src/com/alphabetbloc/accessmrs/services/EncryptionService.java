/*
 * Copyright (C) 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alphabetbloc.accessmrs.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI;
import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.listeners.EncryptDataListener;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.ui.admin.LauncherActivity;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.LauncherUtil;
import com.alphabetbloc.accessmrs.utilities.FileUtils;

/**
 * Encrypts Xform instances and their associated media files on the SD Card, and
 * deletes all Cleartext files from parent AccessForms (i.e ODK Collect) instance directory. Each
 * Form and its media files share a unique 256-bit AES key with different IVs.
 * Key is stored locally on phone database, allowing for easy decryption for
 * viewing old files through the DecryptionTask. <br>
 * <br>
 * This does NOT protect the Xforms when SD is kept with the device and
 * associated keys, and is entirely insecure for devices that allow user access
 * to phone data through rooting etc. <br>
 * <br>
 * The primary goal of this service is to protect the Xforms and media files on
 * the SD card if the SD should be removed from the phone, and can not be wiped
 * through a device manager.<br>
 * <br>
 * Do NOT call this service directly, but rather through a call to
 * WakefulIntentService:<br>
 * <br>
 * e.g.<br>
 * 
 * WakefulIntentService.sendWakefulWork(mContext, EncryptionService.class);
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class EncryptionService extends WakefulIntentService {

	private static final String TAG = EncryptionService.class.getSimpleName();
	public static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"; // "AES/CFB/PKCS5Padding"
	public static final String KEYSPEC_ALGORITHM = "AES";
	public static final String DECRYPTED_HIDDEN_DIR = ".dec";
	public static final int KEY_LENGTH = 256;
	private static final String INSTANCE_ID = "id";
	private static final String INSTANCE_PATH = "path";
	private Context mContext;

	public EncryptionService() {
		super("EncryptionService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		mContext = this;
		if (App.DEBUG) Log.v(TAG, "Starting service to encrypt all submitted files.");
		if (!LauncherUtil.isSetupComplete()) {
			if (!LauncherActivity.sLaunching) {
				if (App.DEBUG) Log.v(TAG, "AccessMRS is Not Setup... and not currently active... so EncryptionService is requesting setup");
				Intent i = new Intent(App.getApp(), LauncherActivity.class);
				i.putExtra(LauncherActivity.LAUNCH_DASHBOARD, false);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(i);
			}
			if (App.DEBUG) Log.v(TAG, "AccessMRS is Not Setup... so EncryptionService is ending");
			stopSelf();
			return;
		}
		// get all recently submitted files
		ArrayList<Map<String, Object>> submittedFiles = findSubmittedFiles();
		if (submittedFiles.isEmpty()){
			cancelAlarms(WakefulIntentService.ENCRYPT_DATA, mContext);
			if (App.DEBUG) Log.v(TAG, "No Files Found to Encrypt. Ending service and canceling future alarms.");
			stopSelf();
			return;
		}
		
		// in case this service is interrupted, make sure we resume it later.
		scheduleAlarms(new EncryptDataListener(), WakefulIntentService.ENCRYPT_DATA, mContext, true);

		boolean allEncrypted = true;
		int count = 0;
		for (Map<String, Object> current : submittedFiles) {
			String dbPath = (String) current.get(INSTANCE_PATH);
			int id = (Integer) current.get(INSTANCE_ID);

			// construct the instance path from db path
			String inPath = FileUtils.getDecryptedFilePath(dbPath);
			String outPath = FileUtils.getEncryptedFilePath(dbPath);
			boolean currentEncrypted = encryptFormInstance(id, inPath, outPath);
			allEncrypted = allEncrypted & currentEncrypted;
			if (currentEncrypted)
				count++;
		}

		Db.open().deleteTemporaryPatients();
		// TODO ?Security? add cancel threshold to prevent looping unsuccessful alarms?
		// BUT if not encrypting, better to keep trying, negatively effect
		// performance, and have user complain as we want to know about this ...
		if (allEncrypted) {
			cancelAlarms(WakefulIntentService.ENCRYPT_DATA, mContext);
			if (App.DEBUG) Log.v(TAG, count + " files successfully encrypted. Ending service and canceling future alarms.");
		} else {
			Log.e(TAG, "An error occurred while attempting to encrypt a recently submitted file!");
		}
	}

	/**
	 * This searches the database for any record of a recently submitted file as
	 * labeled under AccessForms Db Status 'submitted'. These files need to be
	 * encrypted, and status then updated to 'encrypted'.
	 * 
	 * @return ArrayList of Maps that contain both the path and AccessForms Instance
	 *         Id of the decrypted instance file.
	 */
	private static ArrayList<Map<String, Object>> findSubmittedFiles() {

		// Find any recently submitted files from collect db
		String selection = InstanceColumns.STATUS + "=?";
		String[] selectionArgs = new String[] { InstanceProviderAPI.STATUS_SUBMITTED };
		String[] projection = new String[] { InstanceColumns._ID, InstanceColumns.INSTANCE_FILE_PATH };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, projection, selection, selectionArgs, null);

		ArrayList<Map<String, Object>> decryptedList = new ArrayList<Map<String, Object>>();
		int instanceId = 0;
		String instanceDbPath = null;

		if (c != null) {
			if (c.getCount() > 0) {
				int idIndex = c.getColumnIndex(InstanceColumns._ID);
				int pathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);

				if (App.DEBUG) Log.v(TAG, "path index is= " + String.valueOf(pathIndex) + " idindex = " + String.valueOf(idIndex));
				if (c.moveToFirst()) {
					do {
						instanceDbPath = c.getString(pathIndex);
						instanceId = c.getInt(idIndex);

						Map<String, Object> temp = new HashMap<String, Object>();
						temp.put(INSTANCE_ID, instanceId);
						temp.put(INSTANCE_PATH, instanceDbPath);
						decryptedList.add(temp);

					} while (c.moveToNext());
				}
			}
			c.close();
		}
		return decryptedList;
	}

	/**
	 * This will encrypt an individual form instance, including all the
	 * associated files in the same directory. All files will be saved in an
	 * encrypted form on the SD.
	 * 
	 * @param file
	 * @param id
	 * @return true if encryption was successful
	 */
	public static boolean encryptFormInstance(Integer id, String inPath, String outPath) {
		File file = new File(inPath);
		if (!file.exists()) {
			Log.w(TAG, "No file found to encrypt at: " + file.getName());
			return false;
		}
		File parentDir = file.getParentFile();

		// 1. get a Cipher and a Key
		Cipher c = generateCipher();
		byte[] key = generateKey();
		if (c == null || key == null)
			return false;

		final SecretKeySpec keySpec = new SecretKeySpec(key, KEYSPEC_ALGORITHM);

		// 2. update AccessFormsDb with key and new path
		boolean logged = false;

		String keyString = Base64.encodeToString(key, Base64.NO_WRAP);
		if (id != null && keyString != null)
			logged = updateAccessFormsDb(id, keyString);

		// ONLY proceed if we have logged key!
		if (!logged) {
			Log.e(TAG, "Database error when attempting to encrypt file " + file.getName());
			return false;
		}

		// get Files from instance directory and encrypt
		File[] allFiles = parentDir.listFiles();
		boolean result = true;
		for (File f : allFiles) {
			try {
				result = result & encryptFile(f.getAbsolutePath(), outPath, c, keySpec);
			} catch (Exception e) {
				Log.e(TAG, "Error encrypting: " + file.getName());
				e.printStackTrace();
			}
		}

		// we have now encrypted and stored the key, so safe to delete cleartext
		if (result)
			result = FileUtils.deleteAllFiles(parentDir.getAbsolutePath());

		return result;
	}

	public static Cipher generateCipher() {
		// get Cipher
		Cipher c = null;
		try {
			c = Cipher.getInstance(CIPHER_ALGORITHM);
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		} catch (NoSuchPaddingException e1) {
			e1.printStackTrace();
		}
		return c;
	}

	public static byte[] generateKey() {
		SecureRandom rand = new SecureRandom();
		byte[] key = new byte[KEY_LENGTH / 8];
		rand.nextBytes(key);
		return key;
	}

	/**
	 * We update the AccessForms Db with a phantom de-crypted file path and the
	 * keyString to make that de-crypted file on demand
	 * 
	 * @param id
	 * @param filepath
	 * @param base64key
	 */
	private static boolean updateAccessFormsDb(Integer id, String base64key) {
		boolean updated = false;

		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.SUBMISSION_KEY, base64key);
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_ENCRYPTED);

			String where = InstanceColumns._ID + "=?";
			String[] whereArgs = { String.valueOf(id) };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				Log.w(TAG, "Updated more than one entry, that's not good: id=" + id);
			} else if (updatedrows == 1) {
				if (App.DEBUG) Log.v(TAG, "Instance successfully updated");
				updated = true;
			} else {
				Log.e(TAG, "Instance doesn't exist but we have its path!!: id= " + id);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return updated;
	}

	private static boolean encryptFile(String inPath, String outPath, Cipher cipher, SecretKeySpec keySpec) throws Exception {
		boolean encrypted = false;

		// input file
		File inFile = new File(inPath);
		String inName = inPath.substring(inPath.lastIndexOf(File.separator));

		// output dir & file
		File outDir = (new File(outPath)).getParentFile();
		FileUtils.createFolder(outDir.getAbsolutePath());
		File outFile = new File(outDir.getAbsolutePath(), inName + FileUtils.ENC_EXT);
		if (outFile.exists()) {
			outFile = new File(outDir.getAbsolutePath(), inName + "-" + String.valueOf(System.currentTimeMillis()) + FileUtils.ENC_EXT);
			Log.w(TAG, "File already exists. File has been renamed to " + outFile.getName());
		}

		try {
			// make the streams
			InputStream in = new BufferedInputStream(new FileInputStream(inFile));
			OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));

			// make new iv and write as prefix
			final int blockSize = cipher.getBlockSize(); // = 128 bits, 16 bytes
			SecureRandom r = new SecureRandom();
			final byte[] iv = new byte[blockSize];
			r.nextBytes(iv);
			out.write(iv);
			out.flush();
			// if(App.DEBUG) Log.e(TAG, "block size =" + blockSize + " iv=" +
			// Arrays.toString(iv));
			final IvParameterSpec ivSpec = new IvParameterSpec(iv);

			// Encrypt (each directory file has same key but different iv)
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
			out = new CipherOutputStream(out, cipher);
			byte[] buf = new byte[1024];
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}
			in.close();
			out.flush();
			out.close();
			if (App.DEBUG) Log.v(TAG, "Encrpyted:" + inFile.getName() + " -> " + outFile.getName());
			encrypted = true;
		} catch (IOException e) {
			Log.e(TAG, "Error encrypting: " + inFile.getName() + " -> " + outFile.getName());
			e.printStackTrace();
			throw e;
		}
		return encrypted;
	}

}