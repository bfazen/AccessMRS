package com.alphabetbloc.accessmrs.services;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.AlarmManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.accessmrs.tasks.DecryptionTask;
import com.alphabetbloc.accessmrs.ui.admin.AccessMrsLauncherActivity;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.AccessMrsLauncher;
import com.alphabetbloc.accessmrs.utilities.FileUtils;

/**
 * 
 * 
 * @author Louis.Fazen@gmail.com
 * 
 */
public class DeleteDecryptedFilesService extends WakefulIntentService {

	private Context mContext;
	private static final String TAG = DeleteDecryptedFilesService.class.getSimpleName();
	private static final String INSTANCE_ID = "id";
	private static final String INSTANCE_PATH = "path";

	public DeleteDecryptedFilesService() {
		super("DeleteDecryptedService");
		mContext = this;
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		Log.v(TAG, "Service starting to find and delete any decrypted files.");
		if (!AccessMrsLauncher.isSetupComplete()) {
			if (!AccessMrsLauncherActivity.sLaunching) {
				Log.v(TAG, "AccessMRS is Not Setup... and not currently active... so DeleteDecryptedFilesService is requesting setup");
				Intent i = new Intent(App.getApp(), AccessMrsLauncherActivity.class);
				i.putExtra(AccessMrsLauncherActivity.LAUNCH_DASHBOARD, false);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(i);
			}
			Log.v(TAG, "AccessMRS is Not Setup... so DeleteDecryptedFilesService is ending");
			stopSelf();
			return;
		}
		mContext = this;

		// get all recently submitted files
		ArrayList<Map<String, Object>> decryptedInstances = findDecryptedInstances();
		if (decryptedInstances.isEmpty()){
			if (!decryptedFilesExist()){
				cancelAlarms(WakefulIntentService.DELETE_DECRYPTED_DATA, mContext);
				Log.v(TAG, "No decrypted files found. Service is now ending and canceling future alarms.");
			} else {
				Log.v(TAG, "No files require deletion at the time. Service is now ending.");
			}
			stopSelf();
			return;
		}
		boolean allDeleted = true;
		for (Map<String, Object> current : decryptedInstances) {
			String dbPath = (String) current.get(INSTANCE_PATH);
			int id = (Integer) current.get(INSTANCE_ID);

			// get the decrypted instance's parent directory
			String inPath = FileUtils.getDecryptedFilePath(dbPath);
			File parentDir = (new File(inPath)).getParentFile();

			// delete everything in the directory
			boolean thisDeleted = false;
			if (parentDir != null && parentDir.exists())
				thisDeleted = FileUtils.deleteAllFiles(parentDir.getAbsolutePath());

			// update db
			if (thisDeleted)
				thisDeleted = clearDecryptionLog(id);
			else
				Log.e(TAG, "An error occurred when attempting to delete decrypted files in the directory: " + parentDir);

			// catalog all deletions (no short circuit!)
			allDeleted = thisDeleted & allDeleted;
		}

		// Cancel this service IF there are no more decrypted instances on disk
		if (allDeleted && !decryptedFilesExist()){
			cancelAlarms(WakefulIntentService.DELETE_DECRYPTED_DATA, mContext);
			Log.v(TAG, "Successfully deleted all decrypted files. Service is now ending and canceling future alarms.");
		}
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
	private ArrayList<Map<String, Object>> findDecryptedInstances() {
		// calculate datetime threshold for deciding whether to delete
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		Long maxDecryptTime = prefs.getLong(DecryptionTask.MAX_DECRYPT_TIME, AlarmManager.INTERVAL_FIFTEEN_MINUTES / 5);
		Long now = System.currentTimeMillis();
		Long deleteThreshold = now - maxDecryptTime;

		// Find any decrypted files that were decrypted > deleteThreshold
		String selection = InstanceColumns.DECRYPTION_TIME + " IS NOT NULL AND " + InstanceColumns.DECRYPTION_TIME + " < " + String.valueOf(deleteThreshold);
		String[] projection = new String[] { InstanceColumns._ID, InstanceColumns.INSTANCE_FILE_PATH };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, projection, selection, null, null);

		ArrayList<Map<String, Object>> decryptedList = new ArrayList<Map<String, Object>>();
		int instanceId = 0;
		String dbPath = null;

		if (c != null) {
			if (c.getCount() > 0) {
				int idIndex = c.getColumnIndex(InstanceColumns._ID);
				int pathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);

				if (c.moveToFirst()) {
					do {
						dbPath = c.getString(pathIndex);
						instanceId = c.getInt(idIndex);
						Log.v(TAG, "DeleteDecrypted files found id=" + instanceId + " path=" + dbPath);
						Map<String, Object> temp = new HashMap<String, Object>();
						temp.put(INSTANCE_ID, instanceId);
						temp.put(INSTANCE_PATH, dbPath);
						decryptedList.add(temp);

					} while (c.moveToNext());

				}
			}
			c.close();
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
			
			c.close();
		}

		return anyDecryptedFile;
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
