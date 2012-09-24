package org.odk.clinic.android.tasks;

import java.io.File;
import java.util.ArrayList;

import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.odk.clinic.android.R;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.clinic.android.utilities.WebUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.alphabetbloc.clinic.services.EncryptionService;
import com.commonsware.cwac.wakeful.WakefulIntentService;

public class UploadDataTask extends SyncDataTask {

	private static String tag = "UploadDataTask";
	private int mTotalCount = -1;
	private String[] mInstancesToUpload;

	@Override
	protected String doInBackground(Void... values) {
		mUploadComplete = false;
		String uploadResult = "No Completed Forms to Upload";
		int resultSize = 0;

		if (dataToUpload()) {

			setUploadSteps(mTotalCount);
			String s = WebUtils.getFormUploadUrl();
			Log.i(tag, "url to use=" + s);
			ArrayList<String> uploaded = uploadInstances(s);
			if (!uploaded.isEmpty() && uploaded.size() > 0) {
				// update the databases with new status submitted
				for (int i = 0; i < uploaded.size(); i++) {
					String path = uploaded.get(i);
					updateClinicDbPath(path);
					updateCollectDb(path);
					showProgress("Uploading Completed Forms");
				}
			}

			// return a toast message
			resultSize = uploaded.size();
			if (resultSize == mTotalCount)
				uploadResult = App.getApp().getString(R.string.upload_all_successful, resultSize);
			else
				uploadResult = App.getApp().getString(R.string.upload_some_failed, (mTotalCount - resultSize) + " of " + mTotalCount);
		}

		// Encrypt the uploaded data with wakelock to ensure it happens!
		if (resultSize > 0)
			WakefulIntentService.sendWakefulWork(App.getApp(), EncryptionService.class);

		return uploadResult;
	}

	public boolean dataToUpload() {

		boolean dataToUpload = true;
		ArrayList<String> selectedInstances = new ArrayList<String>();

		Cursor c = DbAdapter.openDb().fetchFormInstancesByStatus(DbAdapter.STATUS_UNSUBMITTED);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String dbPath = c.getString(c.getColumnIndex(DbAdapter.KEY_PATH));
					selectedInstances.add(dbPath);
				} while (c.moveToNext());
			}
			c.close();
		}

		if (!selectedInstances.isEmpty()) {
			mTotalCount = selectedInstances.size();
			if (mTotalCount < 1)
				dataToUpload = false;
			else
				mInstancesToUpload = selectedInstances.toArray(new String[mTotalCount]);
		} else
			dataToUpload = false;

		Log.i(tag, "Datatoupload= " + dataToUpload + " Number of Instances= " + mTotalCount);
		return dataToUpload;
	}

	private ArrayList<String> uploadInstances(String url) {

		ArrayList<String> uploadedInstances = new ArrayList<String>();
		for (int i = 0; i < mTotalCount; i++) {
			try {

				String instancePath = FileUtils.getDecryptedFilePath(mInstancesToUpload[i]);
				MultipartEntity entity = createMultipartEntity(instancePath);
				if (entity == null)
					continue;

				if (postEntityToUrl(url, entity)) {
					uploadedInstances.add(mInstancesToUpload[i]);
					Log.e(tag, "everything okay! adding some instances...");
				}

			} catch (Exception e) {
				Log.e(tag, "Exception on uploading instance =" + mInstancesToUpload[i]);
				e.printStackTrace();
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
					Log.i(tag, "added xml file " + f.getName());
				} else if (f.getName().endsWith(".jpg")) {
					fb = new FileBody(f, "image/jpeg");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added image file " + f.getName());
				} else if (f.getName().endsWith(".3gpp")) {
					fb = new FileBody(f, "audio/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added audio file " + f.getName());
				} else if (f.getName().endsWith(".3gp")) {
					fb = new FileBody(f, "video/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added video file " + f.getName());
				} else if (f.getName().endsWith(".mp4")) {
					fb = new FileBody(f, "video/mp4");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added video file " + f.getName());
				} else {
					Log.w(tag, "unsupported file type, not adding file: " + f.getName());
				}
			}
		} else {
			Log.e(tag, "no files to upload in instance");
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
				Log.e(tag, "Error! updated more than one entry when tyring to update: " + path.toString());
			} else if (updatedrows == 1) {
				Log.i(tag, "Instance successfully updated to Submitted Status");
				updated = true;
			} else {
				Log.e(tag, "Error, Instance doesn't exist but we have its path!! " + path.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return updated;
	}

	private void updateClinicDbPath(String path) {
		Cursor c = DbAdapter.openDb().fetchFormInstancesByPath(path);
		if (c != null) {
			DbAdapter.openDb().updateFormInstance(path, DbAdapter.STATUS_SUBMITTED);
			c.close();
		}
	}

	@Override
	protected void onPostExecute(String result) {
		mUploadComplete = true;
		synchronized (this) {
			if (mStateListener != null) {
				if (mUploadComplete && mDownloadComplete)
					mStateListener.syncComplete(result);
				else
					mStateListener.uploadComplete(result);
			}
		}
	}

}
