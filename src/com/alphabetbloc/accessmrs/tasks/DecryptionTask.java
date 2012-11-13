package com.alphabetbloc.accessmrs.tasks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


import android.content.ContentValues;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.listeners.DecryptionListener;
import com.alphabetbloc.accessmrs.services.EncryptionService;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.FileUtils;

/**
 * Decrypts Xform instances and their associated media files on the SD Card,
 * deletes all Cleartext files from parent AccessForms instance directory. Each
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
 * through a device manager.
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class DecryptionTask extends AsyncTask<Object, Void, Boolean> {

	private static final String TAG = DecryptionTask.class.getSimpleName();
	public static final String ACCESS_FORMS_INSTANCE_ID = "access_forms_instance_id";
	public static final String MAX_DECRYPT_TIME = "maximum_time_decrypted";
	private DecryptionListener mListener;
	private boolean anydone = false;

	@Override
	protected Boolean doInBackground(Object... params) {
		int id = (Integer) params[0];
		String dbPath = (String) params[1];
		
		String inPath = FileUtils.getEncryptedFilePath(dbPath);
		String outPath = FileUtils.getDecryptedFilePath(dbPath);
		
		boolean decrypted = false;
		if (id > 0)
			decrypted = decryptFormInstance(id, inPath, outPath);
		
		if (decrypted)
			if (App.DEBUG) Log.v(TAG, "Decryption Sucessful!");
		else
			Log.e(TAG, "Decryption Error with AccessForms Instance Id: " + String.valueOf(id) + " at path=" + inPath);

		return decrypted;
	}

	private boolean decryptFormInstance(Integer id, String inPath, String outPath) {
		if (inPath == null || outPath == null || id == null)
			return false;

		File encFile = new File(inPath);

		// get Cipher and Key
		Cipher c = generateCipher();
		SecretKeySpec key = fetchInstanceKey(id);
		if (c == null || key == null)
			return false;

		// get Encrypted Files
		List<File> filesToDecrypt = FileUtils.findAllFiles(encFile.getParent(), FileUtils.ENC_EXT);
		if (filesToDecrypt.isEmpty())
			return false;

		// ONLY proceed if we have logged decryption in db!
		if (!logDecryptionTime(id))
			return false;

		// Decrypt Files
		boolean alldone = true;
		for (File f : filesToDecrypt) {
			try {
				anydone = decryptFile(f.getAbsolutePath(), outPath, c, key);
				if (App.DEBUG) Log.v(TAG, "after decrypting... anydone=" + anydone);
				alldone = alldone & anydone;
			} catch (Exception e) {
				Log.e(TAG, "Error decrypting: " + f.getName());
				e.printStackTrace();
			}
		}

		return alldone;
	}

	private static Cipher generateCipher() {
		// get Cipher
		Cipher c = null;
		try {
			c = Cipher.getInstance(EncryptionService.CIPHER_ALGORITHM);
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		} catch (NoSuchPaddingException e1) {
			e1.printStackTrace();
		}
		return c;
	}

	private SecretKeySpec fetchInstanceKey(Integer id) {

		String selection = InstanceColumns._ID + "=?";
		String[] selectionArgs = new String[] { String.valueOf(id) };
		String[] projection = new String[] { InstanceColumns.SUBMISSION_KEY };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, projection, selection, selectionArgs, null);

		String keyString = null;
		if (c != null) {
			if (c.getCount() > 0) {
				int keyIndex = c.getColumnIndex(InstanceColumns.SUBMISSION_KEY);
				if (c.moveToFirst())
					keyString = c.getString(keyIndex);
			}
			c.close();
		}

		if (keyString != null) {
			byte[] key = Base64.decode(keyString, Base64.NO_WRAP);
			final SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
			return keySpec;
		} else {
			Log.e(TAG, "Problem retreiving the key");
			return null;
		}
	}

	
	/**
	 * We update the AccessForms Db with the time of decryption so that we can be
	 * sure to delete later!
	 * 
	 * @param id
	 * @param filepath
	 * @param base64key
	 */
	private boolean logDecryptionTime(Integer id) {
		boolean updated = false;
		Long now = System.currentTimeMillis();
		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.DECRYPTION_TIME, now);

			String where = InstanceColumns._ID + "=?";
			String[] whereArgs = { String.valueOf(id) };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				Log.w(TAG, "Updated more than one entry, something is wrong with query of :" + String.valueOf(id));
			} else if (updatedrows == 1) {
				if (App.DEBUG) Log.v(TAG, "Instance successfully updated with decryption time");
				updated = true;
			} else {
				Log.e(TAG, "Instance doesn't exist with id: " + String.valueOf(id));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return updated;
	}

	private boolean decryptFile(String inPath, String outPath, Cipher cipher, SecretKeySpec keySpec) throws Exception {
		boolean decrypted = false;
		
		//input file
		File inFile = new File(inPath);
		String outName = inPath.substring(inPath.lastIndexOf(File.separator), inPath.lastIndexOf(FileUtils.ENC_EXT));
		
		//output dir & file
		File outDir = (new File(outPath)).getParentFile();
		FileUtils.createFolder(outDir.getAbsolutePath());
		File outFile = new File(outDir.getAbsolutePath(), outName);
		if (outFile.exists()) {
			outFile = new File(outDir.getAbsolutePath(), outName + "-" + String.valueOf(System.currentTimeMillis()) + FileUtils.ENC_EXT);
			Log.w(TAG, "File already exists. File has been renamed to " + outFile.getName());
		}


		try {
			// make the streams
			InputStream in = new BufferedInputStream(new FileInputStream(inFile));
			OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));

			// find iv from prefix, then read
			final int blockSize = cipher.getBlockSize();
			byte[] iv = new byte[blockSize];
			in.read(iv);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

			in = new CipherInputStream(in, cipher);
			byte[] buf = new byte[1024];
			int numRead = 0;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}

			in.close();
			out.flush();
			out.close();
			if (App.DEBUG) Log.v(TAG, "Decrpyted:" + inFile.getName() + " -> " + outFile.getName());
			decrypted = true;
		} catch (IOException e) {
			Log.e(TAG, "Error encrypting: " + inFile.getName() + " -> " + outFile.getName());
			e.printStackTrace();
			throw e;
		}
		return decrypted;
	}

	public void setDecryptionListener(DecryptionListener listener) {
		synchronized (this) {
			mListener = listener;
		}
	}

	@Override
	protected void onPostExecute(Boolean alldone) {
		super.onPostExecute(alldone);
		if (mListener != null) {
			if (App.DEBUG) Log.v(TAG, "about to send to listener anydone =" + anydone);
			mListener.setDeleteDecryptedFilesAlarm(anydone);
			mListener.decryptComplete(alldone);
		}

	}

}
