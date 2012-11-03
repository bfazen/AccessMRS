package com.alphabetbloc.accessmrs.utilities;

/*
 * Copyright (C) 2009 University of Washington
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.util.Log;

import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.R;

/**
 * Static methods used for common file operations. LF Added common deletion
 * operations used by encryption services.
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) (All methods except where
 *         otherwise noted)
 * @author Yaw Anokwa, Carl Hartung (carlhartung@gmail.com) (StorageReady(),
 *         deleteFile(), createFolder(), getMD5Hash(), and doesXFormExist taken
 *         from ODK Clinic/Collect)
 * 
 */
public class FileUtils {

	private static final String TAG = FileUtils.class.getSimpleName();

	// Used to validate and display valid form names.
	public static final String VALID_FILENAME = "[ _\\-A-Za-z0-9]*.x[ht]*ml";

	// Storage paths
	public static final String SD_ROOT_DIR = "clinic";
	public static final String INSTANCES = "instances";
	public static final String FORMS = "forms";
	public static final String XML_EXT = ".xml";
	public static final String ENC_EXT = ".enc";
	public static final String MY_TRUSTSTORE = "mytruststore.bks";
	public static final String MY_KEYSTORE = "mykeystore.bks";

	public static boolean storageReady() {
		String cardstatus = Environment.getExternalStorageState();
		if (cardstatus.equals(Environment.MEDIA_REMOVED) || cardstatus.equals(Environment.MEDIA_UNMOUNTABLE) || cardstatus.equals(Environment.MEDIA_UNMOUNTED) || cardstatus.equals(Environment.MEDIA_MOUNTED_READ_ONLY) || cardstatus.equals(Environment.MEDIA_SHARED)) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Non-recursive deletion of a file. If the file is a directory, it will
	 * delete every file in the directory, but not sub-directories.
	 * 
	 * @param path
	 *            of the file
	 * @return true if successfully deletes all files. Returns false if it is
	 *         not able to delete the file (for example, will return false for
	 *         any directory that has a subdirectory).
	 */
	public static final boolean deleteFile(String path) {
		// not recursive
		if (path != null && storageReady()) {
			File folder = new File(path);
			if (folder.exists()) {
				if (folder.isDirectory()) {
					File[] files = folder.listFiles();
					for (File file : files) {
						if (!file.delete()) {
							Log.i(TAG, "Failed to delete " + file);
						} else
							Log.v(TAG, "successfully deleted a file from:" + path);
					}
				}
				return folder.delete();
			}
		}
		return false;
	}

	public static boolean createFolder(String path) {
		boolean made = true;
		File dir = new File(path);
		if (!dir.exists()) {
			made = dir.mkdirs();
		}
		return made;
	}

	/**
	 * Convenience method for finding all files in a path recursively,
	 * regardless of extension.
	 * 
	 * @param path
	 * @return The list of Files in the directory.
	 */
	public static List<File> findAllFiles(String path) {
		return findAllFiles(path, null);
	}

	/**
	 * Find all files in a directory matching an extension recursively.
	 * 
	 * @param path
	 *            Path to search in.
	 * @param ext
	 *            Extension of the file. If null is passed, then will return all
	 *            files.
	 * @return
	 */
	public static List<File> findAllFiles(String path, String ext) {

		File file = new File(path);
		if (file != null && file.exists()) {
			List<File> allFiles = new ArrayList<File>();

			if (file.isDirectory()) {
				File[] dirFiles = file.listFiles();
				for (File f : dirFiles) {
					if (f.isDirectory()) {

						List<File> subFiles = new ArrayList<File>();
						subFiles = findAllFiles(f.getAbsolutePath(), ext);
						if (!subFiles.isEmpty() && subFiles.size() > 0) {
							allFiles.addAll(subFiles);
						}

					} else {
						if (ext != null) {
							if (f.getName().endsWith(ext))
								allFiles.add(f);

						} else {
							allFiles.add(f);
						}
					}
				}
			} else {
				if (ext != null) {
					if (file.getName().endsWith(ext))
						allFiles.add(file);

				} else {
					allFiles.add(file);
				}
			}

			return allFiles;
		}

		return null;
	}

	/**
	 * Recursive deletion of all files on this path.
	 * 
	 * @param path
	 * @return
	 */
	public static final boolean deleteAllFiles(String path) {
		// not recursive
		if (path != null && storageReady()) {
			File folder = new File(path);
			if (folder.exists()) {

				if (folder.isDirectory()) {
					File[] files = folder.listFiles();

					for (File file : files) {
						if (file.isDirectory())
							deleteAllFiles(file.getAbsolutePath());
						else if (!file.delete())
							Log.i(TAG, "Failed to delete " + file);
						else
							Log.v(TAG, "successfully deleted a file from:" + path);
					}

				}
				return folder.delete();
			}
		}
		return false;
	}

	public static String getMd5Hash(File file) {
		try {
			// CTS (6/15/2010) : stream file through digest instead of handing
			// it the byte[]
			MessageDigest md = MessageDigest.getInstance("MD5");
			int chunkSize = 256;

			byte[] chunk = new byte[chunkSize];

			// Get the size of the file
			long lLength = file.length();

			if (lLength > Integer.MAX_VALUE) {
				Log.e(TAG, "File " + file.getName() + "is too large");
				return null;
			}

			int length = (int) lLength;

			InputStream is = null;
			is = new FileInputStream(file);

			int l = 0;
			for (l = 0; l + chunkSize < length; l += chunkSize) {
				is.read(chunk, 0, chunkSize);
				md.update(chunk, 0, chunkSize);
			}

			int remaining = length - l;
			if (remaining > 0) {
				is.read(chunk, 0, remaining);
				md.update(chunk, 0, remaining);
			}
			byte[] messageDigest = md.digest();

			BigInteger number = new BigInteger(1, messageDigest);
			String md5 = number.toString(16);
			while (md5.length() < 32)
				md5 = "0" + md5;
			is.close();
			return md5;

		} catch (NoSuchAlgorithmException e) {
			Log.e("MD5", e.getMessage());
			return null;

		} catch (FileNotFoundException e) {
			Log.e("No Cache File", e.getMessage());
			return null;
		} catch (IOException e) {
			Log.e("Problem reading from file", e.getMessage());
			return null;
		}

	}

	/**
	 * Checks if a form exists in Collect Db and on Sd. If it does, we already
	 * have the form, and there is no need to download!
	 * 
	 * @param formId
	 * @return true if form exists in Collect Db.
	 */
	public static boolean doesXformExist(String formId) {

		boolean alreadyExists = false;

		Cursor mCursor;
		try {
			mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
		} catch (SQLiteException e) {
			Log.e("DownloadFormActivity", e.getLocalizedMessage());
			return false;
		}

		if (mCursor == null) {
			return false;
		}

		mCursor.moveToPosition(-1);
		while (mCursor.moveToNext()) {

			String dbFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

			// if the exact form exists, leave it be. else, insert it.
			if (dbFormId.equalsIgnoreCase(formId + "") && (new File(FileUtils.getExternalFormsPath() + File.separator + formId + FileUtils.XML_EXT)).exists()) {
				alreadyExists = true;
			}

		}

		if (mCursor != null) {
			mCursor.close();
		}

		return alreadyExists;

	}

	// PATHS
	// INTERNAL (COLLECT)
	public static File getInternalInstanceDirectory() {
		File instanceDir = null;
		try {
			Context collectContext = App.getApp().createPackageContext("org.odk.collect.android", Context.CONTEXT_RESTRICTED);
			instanceDir = new File(collectContext.getFilesDir(), INSTANCES);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return instanceDir;
	}

	public static String getInternalInstancePath() {
		String path = getInternalInstanceDirectory().getAbsolutePath();
		return path;
	}

	public static String getDecryptedFilePath(String dbPath) {
		if (dbPath == null)
			Log.e(TAG, "Error retreiving the db path");

		File instanceDir = getInternalInstanceDirectory();
		String path = instanceDir.getAbsolutePath() + dbPath;

		return path;
	}

	// EXTERNAL (GLOBAL)
	public static File getExternalRootDirectory() {
		File sdRootDir = new File(Environment.getExternalStorageDirectory(), SD_ROOT_DIR);
		return sdRootDir;
	}

	public static String getExternalInstancesPath() {
		File instances = new File(getExternalRootDirectory(), INSTANCES);
		return instances.getAbsolutePath();
	}

	public static String getExternalFormsPath() {
		File forms = new File(getExternalRootDirectory(), FORMS);
		return forms.getAbsolutePath();
	}

	public static String getEncryptedFilePath(String dbPath) {
		if (dbPath == null)
			Log.e(TAG, "Error retreiving the db path");
		String outPath = getExternalInstancesPath() + dbPath;
		return outPath;
	}

	/**
	 * imports the local truststore from R.raw to local files directory... so as
	 * to edit them later..
	 */
	public static File setupDefaultSslStore(String sslStore) {

		File file = new File(App.getApp().getFilesDir(), sslStore);
		if (file.exists())
			return file;

		try {
			File sdFile = new File(FileUtils.getExternalRootDirectory(), file.getName());
			InputStream in = null;

			if (sdFile.exists()) {
				// if a file exists on sd, import
				in = new FileInputStream(sdFile);
			} else {
				// load app default from res/raw
				int resourceId = 0;
				if (sslStore.equalsIgnoreCase(MY_TRUSTSTORE))
					resourceId = R.raw.mytruststore;
				if (sslStore.equalsIgnoreCase(MY_KEYSTORE))
					resourceId = R.raw.mykeystore;
				in = App.getApp().getResources().openRawResource(resourceId);
			}
			FileOutputStream out = new FileOutputStream(file);
			byte[] buff = new byte[1024];
			int read = 0;

			try {
				while ((read = in.read(buff)) > 0) {
					out.write(buff, 0, read);
				}
			} finally {
				in.close();

				out.flush();
				out.close();
			}

			if (sdFile.exists()) {
				// delete so we don't leave keys around!
				sdFile.delete();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return file;

	}

	/**
	 * 
	 * Load the truststore or keystore object, loading default from SD or
	 * res/raw if it does not exist
	 * 
	 * @param sslStoreType
	 *            The type of Store (Either FileUtils.MY_TRUSTORE or
	 *            FileUtils.MY_KEYSTORE)
	 * @return the KeyStore object of the local Trust or Key Store
	 */
	public static KeyStore loadSslStore(String sslStoreType) {

		File localTrustStoreFile = new File(App.getApp().getFilesDir(), sslStoreType);
		if (!localTrustStoreFile.exists()) {
			localTrustStoreFile = FileUtils.setupDefaultSslStore(sslStoreType);
		}

		String instance = null;
		if (sslStoreType.equalsIgnoreCase(MY_KEYSTORE))
			instance = "PKCS12";
		if (sslStoreType.equalsIgnoreCase(MY_TRUSTSTORE))
			instance = "BKS";
		try {
			KeyStore sslStore = KeyStore.getInstance(instance);
			InputStream in = new FileInputStream(localTrustStoreFile);
			try {
				String password = EncryptionUtil.getPassword();
				if (password != null)
					sslStore.load(in, password.toCharArray());
				else
					sslStore = null;
			} finally {
				in.close();
			}

			return sslStore;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isDataWiped() {

		File AccessMrsDb = App.getApp().getDatabasePath(DataModel.DATABASE_NAME);
		if (AccessMrsDb != null && AccessMrsDb.exists()) {
			Log.e(TAG, "AccessMRS data was not wiped properly");
			return false;
		}
		Context collectCtx = null;
		try {
			collectCtx = App.getApp().createPackageContext("org.odk.collect.android", Context.CONTEXT_RESTRICTED);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		File collectDb = collectCtx.getDatabasePath(InstanceProviderAPI.DATABASE_NAME);
		if (collectDb != null && collectDb.exists()) {
			Log.e(TAG, "collect data was not wiped properly");
			return false;
		}
		return true;
	}
}
