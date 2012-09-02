package org.odk.clinic.android.utilities;

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

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.alphabetbloc.clinic.services.EncryptionService;

/**
 * Static methods used for common file operations. LF Added common deletion
 * operations used by encryption services.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Louis Fazen (louis.fazen@gmail.com)
 */
public class FileUtils {
	private final static String t = "FileUtils";

	// Used to validate and display valid form names.
	public static final String VALID_FILENAME = "[ _\\-A-Za-z0-9]*.x[ht]*ml";

	// Storage paths
	public static final String ODK_CLINIC_ROOT = Environment.getExternalStorageDirectory() + "/odk/clinic/";
	public static final String FORMS_PATH = ODK_CLINIC_ROOT + "forms/";
	public static final String INSTANCES_PATH = ODK_CLINIC_ROOT + "instances/";
	public static final String DATABASE_PATH = ODK_CLINIC_ROOT + "databases/";

	private static final String TAG = "FileUtils";

	public static boolean storageReady() {
		String cardstatus = Environment.getExternalStorageState();
		if (cardstatus.equals(Environment.MEDIA_REMOVED) || cardstatus.equals(Environment.MEDIA_UNMOUNTABLE) || cardstatus.equals(Environment.MEDIA_UNMOUNTED) || cardstatus.equals(Environment.MEDIA_MOUNTED_READ_ONLY) || cardstatus.equals(Environment.MEDIA_SHARED)) {
			return false;
		} else {
			return true;
		}
	}

	public static final boolean deleteFile(String path) {
		// not recursive
		if (path != null && storageReady()) {
			File folder = new File(path);
			if (folder.exists()) {
				if (folder.isDirectory()) {
					File[] files = folder.listFiles();
					for (File file : files) {
						if (!file.delete()) {
							Log.i(t, "Failed to delete " + file);
						} else
							Log.e(TAG, "successfully deleted a file from:" + path);
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
	 * Find Cleartext files in this directory. This is not recursive, and does
	 * not find hidden "." files.
	 * 
	 * @param parentDir
	 * @return
	 */
	public static List<File> findCleartextFiles(File parentDir) {
		// catalog files to encrypt (skip hidden ".", ".enc", dirs)
		File[] allFiles = parentDir.listFiles();
		List<File> directoryFiles = new ArrayList<File>();
		for (File f : allFiles) {
			if (f.isDirectory())
				continue;
			if (f.getName().endsWith(".enc"))
				continue;
			if (f.getName().startsWith("."))
				continue;
			else
				directoryFiles.add(f);
		}
		return directoryFiles;
	}

	/**
	 * Deletes all the Cleartext files in this directory. This is not recursive,
	 * but does delete the decrypted directory ".dec"
	 * 
	 * @param parentDir
	 * @return
	 */
	public static boolean deleteCleartextFiles(File parentDir) {

		boolean allSuccessful = true;

		File[] allFiles = parentDir.listFiles();
		for (File f : allFiles) {
			Log.e(TAG, "deleteCleartextfiles for loop! all successful:" + allSuccessful);
			if (f.isDirectory() && f.getName().equals(EncryptionService.DECRYPTED_HIDDEN_DIR))
				allSuccessful = allSuccessful & deleteFile(f.getPath());
			else if (!f.getName().endsWith(".enc"))
				allSuccessful = allSuccessful & f.delete();
			Log.e(TAG, "deleteCleartextfiles for loop! all successful:" + allSuccessful + " for file=" + f.getPath());
		}
		return allSuccessful;
	}

	/**
	 * Finds and deletes all the Cleartext files in this directory in a
	 * recursive method. Only use for small directories that are unlikely to
	 * cause stack overflow!
	 * 
	 * @param parentDir
	 * @return
	 */
	public static boolean deleteAllCleartextFiles(File parentDir) {
		// catalog files to encrypt (skip hidden ".", ".enc", dirs)
		boolean allDeleted = true;

		if (parentDir != null && storageReady()) {
			try {
				File[] allFiles = parentDir.listFiles();
				for (File f : allFiles) {
					if (f.getName().endsWith(".enc"))
						continue;
					else if (f.isDirectory())
						deleteAllCleartextFiles(f);
					else{
						Log.e(TAG, "deleteAllCleartextfiles for loop! found a file... allDeleted=" + allDeleted);
						allDeleted = allDeleted & f.delete();
						Log.e(TAG, "deleteAllCleartextfiles for loop! updated file... allDeleted=" + allDeleted);
					}
				}
				Log.e(TAG, "deleteAllCleartextfiles End of for loop! found a file... allDeleted=" + allDeleted);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return allDeleted;
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
				Log.e(t, "File " + file.getName() + "is too large");
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

}
