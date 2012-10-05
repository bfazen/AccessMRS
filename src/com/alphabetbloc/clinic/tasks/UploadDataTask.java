package com.alphabetbloc.clinic.tasks;

import java.io.File;
import java.util.ArrayList;

import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import com.alphabetbloc.clinic.R;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentValues;
import android.content.SyncResult;
import android.database.Cursor;
import android.util.Log;

import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.services.EncryptionService;
import com.alphabetbloc.clinic.services.WakefulIntentService;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.WebUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author Yaw Anokwa. Sam Mbugua (I think? starting version was from ODK
 *         Clinic)
 */
public class UploadDataTask extends SyncDataTask {

	private static final String TAG = UploadDataTask.class.getSimpleName();

	@Override
	protected String doInBackground(SyncResult... values) {
		sSyncResult = values[0];
		Log.e(TAG, "UploadDataTask Called");

		mUploadComplete = false;
		String uploadResult = "No Completed Forms to Upload";

		String[] instancesToUpload = getInstancesToUpload();
		showProgress("Uploading Forms");

		if (instancesToUpload.length > 0) {

			ArrayList<String> uploaded = uploadInstances(instancesToUpload);

			if (mDownloadComplete)
				dropHttpClient();

			if (!uploaded.isEmpty() && uploaded.size() > 0) {

				// update the databases with new status submitted
				int progress = 0;
				for (int i = 0; i < uploaded.size(); i++) {
					String path = uploaded.get(i);
					updateClinicDbPath(path);
					updateCollectDb(path);
					long c = Math.round(((float) i / (float) instancesToUpload.length) * 10.0);
					int current = (int) c;
					if (current != progress) {
						progress = current;
						showProgress("Uploading Forms", progress);
					}
					Log.e(TAG, "i=" + i + " current=" + current + " progress=" + progress + " total instances=" + instancesToUpload.length);
				}

				// Encrypt the uploaded data with wakelock to ensure it happens!
				WakefulIntentService.sendWakefulWork(App.getApp(), EncryptionService.class);
			}

			// return a toast message
			if (uploaded.size() == instancesToUpload.length)
				uploadResult = App.getApp().getString(R.string.upload_all_successful, uploaded.size());
			else
				uploadResult = App.getApp().getString(R.string.upload_some_failed, (instancesToUpload.length - uploaded.size()) + " of " + instancesToUpload.length);
		}

		return uploadResult;
	}

	public String[] getInstancesToUpload() {

		ArrayList<String> selectedInstances = new ArrayList<String>();

		Cursor c = DbProvider.openDb().fetchFormInstancesByStatus(DbProvider.STATUS_UNSUBMITTED);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String dbPath = c.getString(c.getColumnIndex(DbProvider.KEY_PATH));
					selectedInstances.add(dbPath);
				} while (c.moveToNext());
			}
			c.close();
		}

		return selectedInstances.toArray(new String[selectedInstances.size()]);
	}

	private ArrayList<String> uploadInstances(String[] instancePaths) {
		Log.e(TAG, "UploadInstances Called");
		ArrayList<String> uploadedInstances = new ArrayList<String>();
		for (int i = 0; i < instancePaths.length; i++) {
			try {

				String instancePath = FileUtils.getDecryptedFilePath(instancePaths[i]);
				MultipartEntity entity = createMultipartEntity(instancePath);
				if (entity == null)
					continue;

				if (postEntityToUrl(WebUtils.getFormUploadUrl(), entity)) {
					uploadedInstances.add(instancePaths[i]);
					Log.e(TAG, "everything okay! adding some instances...");
				}

			} catch (Exception e) {
				Log.e(TAG, "Exception on uploading instance =" + instancePaths[i]);
				e.printStackTrace();
				++sSyncResult.stats.numIoExceptions;
			}

		}
		return uploadedInstances;
	}

	private MultipartEntity createMultipartEntity(String path) {

		// find all files in parent directory
		File file = new File(path);
		File[] files = file.getParentFile().listFiles();
		System.out.println(file.getAbsolutePath());

		// mime post
		MultipartEntity entity = null;
		if (files != null) {
			entity = new MultipartEntity();
			for (int j = 0; j < files.length; j++) {
				File f = files[j];
				FileBody fb;
				if (f.getName().endsWith(".xml")) {
					fb = new FileBody(f, "text/xml");
					entity.addPart("xml_submission_file", fb);
					Log.i(TAG, "added xml file " + f.getName());
				} else if (f.getName().endsWith(".jpg")) {
					fb = new FileBody(f, "image/jpeg");
					entity.addPart(f.getName(), fb);
					Log.i(TAG, "added image file " + f.getName());
				} else if (f.getName().endsWith(".3gpp")) {
					fb = new FileBody(f, "audio/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(TAG, "added audio file " + f.getName());
				} else if (f.getName().endsWith(".3gp")) {
					fb = new FileBody(f, "video/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(TAG, "added video file " + f.getName());
				} else if (f.getName().endsWith(".mp4")) {
					fb = new FileBody(f, "video/mp4");
					entity.addPart(f.getName(), fb);
					Log.i(TAG, "added video file " + f.getName());
				} else {
					Log.w(TAG, "unsupported file type, not adding file: " + f.getName());
				}
			}
		} else {
			Log.e(TAG, "no files to upload in instance");
		}

		return entity;
	}

	private boolean updateCollectDb(String path) {
		boolean updated = false;
		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
			String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
			String whereArgs[] = { path };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				Log.e(TAG, "Error! updated more than one entry when tyring to update: " + path.toString());
			} else if (updatedrows == 1) {
				Log.i(TAG, "Instance successfully updated to Submitted Status");
				updated = true;
			} else {
				Log.e(TAG, "Error, Instance doesn't exist but we have its path!! " + path.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
			++sSyncResult.stats.numIoExceptions;
			;
		}

		return updated;
	}

	private void updateClinicDbPath(String path) {
		Cursor c = DbProvider.openDb().fetchFormInstancesByPath(path);
		if (c != null) {
			DbProvider.openDb().updateFormInstance(path, DbProvider.STATUS_SUBMITTED);
			c.close();
		}
	}

	@Override
	protected void onPostExecute(String result) {
		mUploadComplete = true;
		synchronized (this) {
			if (mStateListener != null) {
				if (mUploadComplete && mDownloadComplete) {
					dropHttpClient();
					mStateListener.syncComplete(result, sSyncResult);
				} else
					mStateListener.uploadComplete(result);
			}
		}
	}

}
