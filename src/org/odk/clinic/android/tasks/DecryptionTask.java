package org.odk.clinic.android.tasks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.odk.clinic.android.listeners.DecryptionListener;
import org.odk.clinic.android.listeners.DownloadListener;
import org.odk.clinic.android.utilities.App;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import com.alphabetbloc.clinic.services.EncryptionService;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

/**
 * Encrypts Xform instances and their associated media files on the SD Card,
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
 * through a device manager.
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class DecryptionTask extends AsyncTask<Integer, Void, Boolean> {

	private static final String TAG = "DecryptionTask";
	public static final String COLLECT_INSTANCE_ID = "collect_instance_id";
	public static final String MAX_DECRYPT_TIME = "maximum_time_decrypted";
	private DecryptionListener mListener;
	private boolean anydone;

	@Override
	protected Boolean doInBackground(Integer... params) {
		anydone = false;
		boolean decrypted = false;
		int id = params[0];
		Log.e(TAG, " doInBackground with id=" + id);

		if (id > 0) {
			if (decryptFormInstance(id)) {
				Log.e(TAG, "Decryption Sucessful!");
				decrypted = true;
			} else
				Log.e(TAG, "Decryption Error with Collect Instance Id: " + String.valueOf(id));
		} else
			Log.e(TAG, "File to decrypt is not fully specified. Ensure Path and Id are included in intent.");
		Log.e(TAG, "before onpostexecute... anydone=" + anydone);
		return decrypted;
	}

	private boolean decryptFormInstance(Integer id) {
		String path = getInstancePath(id);
		if (path == null)
			return false;

		// This is path to decrypted form: instanceDir/.dec/instance-date.xml
		// NB: file does not exist, b/c we have not decrypted!
		File decryptedfile = new File(path);
		File decryptHiddenDir = decryptedfile.getParentFile();
		File instanceDir = decryptHiddenDir.getParentFile();

		// get Cipher and Key
		Cipher c = generateCipher();
		SecretKeySpec key = fetchInstanceKey(id);
		if (c == null || key == null)
			return false;

		// get Files
		List<File> filesToDecrypt = findEncryptedFiles(instanceDir);
		if (filesToDecrypt.isEmpty())
			return false;

		// ONLY proceed if we have logged decryption in db!
		if (!logDecryptionTime(id))
			return false;

		// Decrypt Files
		boolean alldone = true;
		for (File f : filesToDecrypt) {
			try {
				anydone = decryptFile(f, c, key);
				Log.e(TAG, "after decrypting... anydone=" + anydone);
				alldone = alldone & anydone;
			} catch (Exception e) {
				Log.e(TAG, "Error decrypting: " + f.getName());
				e.printStackTrace();
			}
		}

		return alldone;
	}

	// TODO? put all the contentresolver queries into a CollectUtil
	private String getInstancePath(Integer id) {

		String selection = InstanceColumns._ID + "=?";
		String selectionArgs[] = { String.valueOf(id) };
		String projection[] = { InstanceColumns.INSTANCE_FILE_PATH };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, projection, selection, selectionArgs, null);
		String path = null;

		if (c != null) {
			if (c.getCount() > 0) {
				int pathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
				if (c.moveToFirst())
					path = c.getString(pathIndex);
			}
			c.close();
		}

		if (path == null)
			Log.e(TAG, "Error retreiving the path of Collect instance with id: " + String.valueOf(id));

		return path;
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

	private List<File> findEncryptedFiles(File instanceDir) {
		File[] allFiles = instanceDir.listFiles();
		List<File> directoryFiles = new ArrayList<File>();
		for (File f : allFiles) {
			if (f.getName().endsWith(".enc"))
				directoryFiles.add(f);
		}
		return directoryFiles;
	}

	/**
	 * We update the collect Db with the time of decryption so that we can be
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
				Log.i(TAG, "Instance successfully updated with decryption time");
				updated = true;
			} else {
				Log.e(TAG, "Instance doesn't exist with id: " + String.valueOf(id));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return updated;
	}

	private boolean decryptFile(File encFile, Cipher cipher, SecretKeySpec keySpec) throws Exception {
		boolean decrypted = false;
		// make the decrypted file
		String name = encFile.getName();
		int ext = name.lastIndexOf(".");
		String xmltitle = name.substring(0, ext); // drop the .enc
		String decryptedFilePath = encFile.getParent() + File.separator + EncryptionService.DECRYPTED_HIDDEN_DIR + File.separator + xmltitle;
		File decFile = new File(decryptedFilePath);

		try {
			// make the streams
			InputStream in = new BufferedInputStream(new FileInputStream(encFile));
			OutputStream out = new BufferedOutputStream(new FileOutputStream(decFile));

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
			Log.i(TAG, "Decrpyted:" + encFile.getName() + " -> " + decFile.getName());
			decrypted = true;
		} catch (IOException e) {
			Log.e(TAG, "Error encrypting: " + encFile.getName() + " -> " + decFile.getName());
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
			Log.e(TAG, "about to send to listener anydone =" + anydone);
			mListener.setDeleteDecryptedFilesAlarm(anydone);
			mListener.decryptComplete(alldone);
		}

	}

}
