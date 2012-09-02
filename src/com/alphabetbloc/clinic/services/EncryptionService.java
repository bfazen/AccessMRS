package com.alphabetbloc.clinic.services;

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
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.commonsware.cwac.wakeful.EncryptDataListener;
import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * Encrypts Xform instances and their associated media files on the SD Card, and
 * deletes all Cleartext files from parent ODK Collect instance directory. Each
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

	private static final String TAG = "EncryptionService";
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

		ArrayList<Map<String, Object>> submittedFiles = findSubmittedFiles();
		if (submittedFiles.isEmpty())
			stopSelf();

		// in case this service is interrupted, make sure we resume it later.
		scheduleAlarms(new EncryptDataListener(), WakefulIntentService.ENCRYPT_DATA, mContext, true);

		boolean allEncrypted = true;
		for (Map<String, Object> file : submittedFiles) {
			String path = (String) file.get(INSTANCE_PATH);
			File instance = new File(path);
			int id = (Integer) file.get(INSTANCE_ID);
			Log.e(TAG, "attempting to encrypt:" + path);

			Log.e(TAG, "dowakefulwork forloop allencrypted before encrypt=:" + allEncrypted);
			allEncrypted = allEncrypted & encryptFormInstance(instance, id);
			Log.e(TAG, "dowakefulwork forloop allencrypted after encrypt=:" + allEncrypted);
		}

		// TODO? add cancel threshold to prevent looping unsuccessful alarms?
		// BUT if not encrypting, better to keep trying, negatively effect
		// performance, and have user complain as we want to know about this ...
		if (allEncrypted) {
			cancelAlarms(WakefulIntentService.ENCRYPT_DATA, mContext);
			Log.e(TAG, "Encryption Sucessful!");
		} else {
			Log.e(TAG, "An error occurred while attempting to encrypt a recently submitted file! ");
		}
	}

	/**
	 * This searches the database for any record of a recently submitted file as
	 * labelled under Collect Db Status 'submitted'. These files need to be
	 * encrypted, and status updated to 'encrypted'.
	 * 
	 * @return ArrayList of Maps that contain both the path and Collect Instance
	 *         Id of the decrypted instance file.
	 */
	private ArrayList<Map<String, Object>> findSubmittedFiles() {

		// Find any recently submitted files
		String selection = InstanceColumns.STATUS + "=?";
		String[] selectionArgs = new String[] { InstanceProviderAPI.STATUS_COMPLETE };
		String[] projection = new String[] { InstanceColumns._ID, InstanceColumns.INSTANCE_FILE_PATH };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, projection, selection, selectionArgs, null);

		ArrayList<Map<String, Object>> decryptedList = new ArrayList<Map<String, Object>>();
		int instanceId = 0;
		String instancePath = null;

		if (c != null) {
			if (c.getCount() > 0) {
				int idIndex = c.getColumnIndex(InstanceColumns._ID);
				int pathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);

				Log.e(TAG, "path index is= " + String.valueOf(pathIndex) + " idindex = " + String.valueOf(idIndex));
				if (c.moveToFirst()) {
					do {
						instancePath = c.getString(pathIndex);
						instanceId = c.getInt(idIndex);

						Map<String, Object> temp = new HashMap<String, Object>();
						temp.put(INSTANCE_ID, instanceId);
						temp.put(INSTANCE_PATH, instancePath);
						decryptedList.add(temp);

					} while (c.moveToNext());
				}
			}
			c.close();
		}
		return decryptedList;
	}

	public boolean encryptFormInstance(File file, Integer id) {
		if (!file.exists()) {
			System.out.println("No file found to encrypt at: " + file.getName());
			return false;
		}
		File parentDir = file.getParentFile();

		// get a Cipher and a Key
		Cipher c = generateCipher();
		byte[] key = generateKey();
		if (c == null || key == null)
			return false;

		final SecretKeySpec keySpec = new SecretKeySpec(key, KEYSPEC_ALGORITHM);

		// update CollectDb with key and new path
		boolean logged = false;
		String decryptedPath = parentDir.getPath() + File.separator + DECRYPTED_HIDDEN_DIR + File.separator + file.getName();
		String keyString = Base64.encodeToString(key, Base64.NO_WRAP);
		if (id != null && decryptedPath != null && keyString != null)
			logged = updateCollectDb(id, decryptedPath, keyString);

		// ONLY proceed if we have logged key!
		if (!logged) {
			Log.e(TAG, "Database error when attempting to encrypt file " + file.getName());
			return false;
		}

		// get Files from instance directory and encrypt
		List<File> filesToEncrypt = FileUtils.findCleartextFiles(parentDir);
		boolean result = true;
		for (File f : filesToEncrypt) {
			try {
				result = result & encryptFile(f, c, keySpec);
				Log.e(TAG, "for loop!:" + result);
			} catch (Exception e) {
				Log.e(TAG, "Error encrypting: " + file.getName());
				e.printStackTrace();
			}
		}
		Log.e(TAG, "end of for loop!:" + result);
		// we have now encrypted and stored the key, so safe to delete cleartext
		if (result)
			result = FileUtils.deleteAllCleartextFiles(parentDir);
		Log.e(TAG, "end of encryptforminstance method with result=:" + result);
		return result;
	}

	private static Cipher generateCipher() {
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

	private static byte[] generateKey() {
		SecureRandom rand = new SecureRandom();
		byte[] key = new byte[KEY_LENGTH / 8];
		rand.nextBytes(key);
		return key;
	}

	/**
	 * We update the collect Db with a phantom de-crypted file path and the
	 * keyString to make that de-crypted file on demand
	 * 
	 * @param id
	 * @param filepath
	 * @param base64key
	 */
	private static boolean updateCollectDb(Integer id, String filepath, String base64key) {
		boolean updated = false;

		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.INSTANCE_FILE_PATH, filepath);
			insertValues.put(InstanceColumns.SUBMISSION_KEY, base64key);
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_ENCRYPTED);

			String where = InstanceColumns._ID + "=?";
			String[] whereArgs = { String.valueOf(id) };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				Log.w(TAG, "Updated more than one entry, that's not good: " + filepath.toString());
			} else if (updatedrows == 1) {
				Log.i(TAG, "Instance successfully updated");
				updated = true;
			} else {
				Log.e(TAG, "Instance doesn't exist but we have its path!! " + filepath.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return updated;
	}

	private static boolean encryptFile(File file, Cipher cipher, SecretKeySpec keySpec) throws Exception {
		boolean encrypted = false;
		// make the encrypted file
		File encFile = new File(file.getPath() + ".enc");
		if (encFile.exists()) {
			encFile = new File(file.getPath() + "-" + String.valueOf(System.currentTimeMillis()) + ".enc");
			System.out.println("File has already been encrypted.  File has been renamed to -datetime.enc");
		}

		try {
			// make the streams
			InputStream in = new BufferedInputStream(new FileInputStream(file));
			OutputStream out = new BufferedOutputStream(new FileOutputStream(encFile));

			// make new iv and write as prefix
			final int blockSize = cipher.getBlockSize(); // = 128 bits, 16 bytes
			SecureRandom r = new SecureRandom();
			final byte[] iv = new byte[blockSize];
			r.nextBytes(iv);
			out.write(iv);
			out.flush();
			// Log.e(TAG, "block size =" + blockSize + " iv=" +
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
			Log.i(TAG, "Encrpyted:" + file.getName() + " -> " + encFile.getName());
			encrypted = true;
		} catch (IOException e) {
			Log.e(TAG, "Error encrypting: " + file.getName() + " -> " + encFile.getName());
			e.printStackTrace();
			throw e;
		}
		return encrypted;
	}

	// private String getInstancePath(Integer id) {
	//
	// String selection = InstanceColumns._ID + "=?";
	// String selectionArgs[] = { String.valueOf(id) };
	// String projection[] = { InstanceColumns.INSTANCE_FILE_PATH };
	// Cursor c =
	// App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI,
	// projection, selection, selectionArgs, null);
	// String path = null;
	// if (c != null) {
	// int pathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
	// if (c.moveToFirst())
	// path = c.getString(pathIndex);
	// c.close();
	// }
	//
	// if (path == null)
	// Log.e(TAG, "Error retreiving the path of Collect instance with id: " +
	// String.valueOf(id));
	//
	// return path;
	// }
}