package com.alphabetbloc.clinic.services;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.odk.clinic.android.tasks.DecryptionTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.AlarmManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * 
 * 
 * @author Louis.Fazen@gmail.com
 * 
 */
public class DeleteDecryptedFilesService extends WakefulIntentService {

	private Context mContext;
	private static final String TAG = "RefreshClientService";
	private static final String INSTANCE_ID = "id";
	private static final String INSTANCE_PATH = "path";

	public DeleteDecryptedFilesService() {
		super("DeleteDecryptedService");
		mContext = this;
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		mContext = this;
		Log.e(TAG, "DeleteDecrypted files doWakefulWork called! ");
		ArrayList<Map<String, Object>> decryptedFiles = findDecryptedFiles();
		boolean allDeleted = true;
		for (Map<String, Object> file : decryptedFiles) {
			// delete files from the instance main directory
			String path = (String) file.get(INSTANCE_PATH); // =instanceDir/.dec/instance-date-xml
			File parentDir = (new File(path)).getParentFile().getParentFile(); // =instanceDir/
			boolean thisDeleted = false;
			if (parentDir.exists())
				thisDeleted = FileUtils.deleteAllCleartextFiles(parentDir);

			// update db
			int id = (Integer) file.get(INSTANCE_ID);
			if (thisDeleted)
				thisDeleted = clearDecryptionLog(id);
			else
				Log.e(TAG, "An error occurred when attempting to delete decrypted files in the directory: " + parentDir.getPath());

			// catalog all complete deletions (no short circuit!)
			allDeleted = thisDeleted & allDeleted;
		}
		
		// TODO? add cancel threshold to prevent looping unsuccessful alarms?
		// BUT if not deleting, better to keep trying, negatively effect
		// performance, and have user complain as we want to know about this ...
		if (allDeleted && !decryptedFilesExist())
			cancelAlarms(WakefulIntentService.DELETE_DECRYPTED_DATA, mContext);
	}

	/**
	 * This searches the database for any record of a decrypted file that has
	 * remained beyond the maximum decrypted time, set in preferences. This does
	 * NOT return directories that were recently created and should remain
	 * decrypted for some time (i.e. the user may be actively using them, and we
	 * do not want to constantly decrypt/encrypt, esp. if there are large files
	 * like multiple images, audio recordings, or video on encryption each
	 * pass).
	 * 
	 * @return ArrayList of Maps that contain both the path and Collect Instance
	 *         Id of the decrypted instance file.
	 */
	private ArrayList<Map<String, Object>> findDecryptedFiles() {
		// calculate datetime threshold for deciding whether to delete a
		// decrypted file.
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		Long maxDecryptTime = prefs.getLong(DecryptionTask.MAX_DECRYPT_TIME, AlarmManager.INTERVAL_FIFTEEN_MINUTES/5);
		Long now = System.currentTimeMillis();
		Long deleteThreshold = now - maxDecryptTime;

		// Find any decrypted files that were decrypted > deleteThreshold
		String selection = InstanceColumns.DECRYPTION_TIME + " IS NOT NULL AND " + InstanceColumns.DECRYPTION_TIME + " < " + String.valueOf(deleteThreshold);
		String[] projection = new String[] { InstanceColumns._ID, InstanceColumns.INSTANCE_FILE_PATH };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, projection, selection, null, null);

		ArrayList<Map<String, Object>> decryptedList = new ArrayList<Map<String, Object>>();
		int instanceId = 0;
		String instancePath = null;

		if (c != null) {
			if (c.getCount() > 0) {
				int idIndex = c.getColumnIndex(InstanceColumns._ID);
				int pathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);

				if (c.moveToFirst()) {
					do {
						instancePath = c.getString(pathIndex);
						instanceId = c.getInt(idIndex);
						Log.e(TAG, "DeleteDecrypted files found id=" + instanceId + " path=" + instancePath);
						Map<String, Object> temp = new HashMap<String, Object>();
						temp.put(INSTANCE_ID, instanceId);
						temp.put(INSTANCE_PATH, instancePath);
						decryptedList.add(temp);

					} while (c.moveToNext());
					c.close();
				}
			}
		}
		return decryptedList;
	}

	
	private boolean decryptedFilesExist() {
		boolean anyDecryptedFile = false;
		String selection = InstanceColumns.DECRYPTION_TIME + " IS NOT NULL";
		String[] projection = new String[] { InstanceColumns._ID };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, projection, selection, null, null);

		if (c != null) {
			if (c.getCount() > 0) 
				anyDecryptedFile = true;
		}
		
		return anyDecryptedFile;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.e(TAG, "RefreshClientService OnDestroy is called");
	}

	/**
	 * We update the collect Db with null to show there are no remaining files
	 * to be decrypted.
	 * 
	 * @param id
	 * @param filepath
	 * @param base64key
	 */
	private boolean clearDecryptionLog(Integer id) {
		boolean updated = false;
		try {
			ContentValues insertValues = new ContentValues();
			insertValues.putNull(InstanceColumns.DECRYPTION_TIME);

			String where = InstanceColumns._ID + "=?";
			String[] whereArgs = { String.valueOf(id) };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				Log.e(TAG, "Updated more than one entry, something is wrong with query of :" + String.valueOf(id));
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

}
