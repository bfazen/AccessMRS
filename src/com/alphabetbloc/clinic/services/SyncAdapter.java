package com.alphabetbloc.clinic.services;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.apache.http.client.HttpClient;
import org.apache.http.entity.mime.MultipartEntity;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.NetworkUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * Class that coordinates all Sync interaction with the server. Manages the
 * connectivity and passes streams off to SyncManager to handle.
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

	private static final String TAG = SyncAdapter.class.getSimpleName();
	private Context mContext;
	private SyncManager mSyncManager;

	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		Log.i(TAG, "Sync Started");
		Thread.currentThread().setName(TAG);

		// if(isSyncNeeded(syncResult)){ //Dont need this if the manual syncs
		// are also using SyncAdapter?

		mSyncManager = new SyncManager(mContext);

		HttpClient client = NetworkUtils.getHttpClient();
		if (client != null) {

			// upload forms
			mSyncManager.addSyncStep(mContext.getString(R.string.sync_uploading_forms)); // 1
			String[] instancesToUpload = mSyncManager.getInstancesToUpload();
			String[] instancesUploaded = uploadInstances(client, instancesToUpload, syncResult);
			mSyncManager.addSyncStep(mContext.getString(R.string.sync_uploading_forms)); // 2
			String dbError = mSyncManager.updateUploadedForms(instancesUploaded, syncResult);
			int uploadErrors = (int) syncResult.stats.numIoExceptions;
			mSyncManager.toastSyncMessage(SyncManager.UPLOAD_FORMS, instancesUploaded.length, instancesToUpload.length, uploadErrors, dbError);

			// download new forms
			mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms)); // 3
			Form[] formsToDownload = findNewFormsOnServer(client, syncResult);
			// mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms));
			// // 4
			Form[] formsDownloaded = downloadNewForms(client, formsToDownload, syncResult);
			// mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms));
			// // 5
			dbError = mSyncManager.updateDownloadedForms(formsDownloaded, syncResult);
			Log.e(TAG, "Downloaded New Forms with result errors=" + syncResult.stats.numIoExceptions);
			int downloadErrors = ((int) syncResult.stats.numIoExceptions) - uploadErrors;
			mSyncManager.toastSyncMessage(SyncManager.DOWNLOAD_FORMS, formsDownloaded.length, formsToDownload.length, downloadErrors, dbError);

			// download new obs
			mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_data)); // 6
			File temp = downloadObsStream(client, syncResult);
			mSyncManager.addSyncStep(mContext.getString(R.string.sync_updating_data)); // 7->10
			dbError = mSyncManager.readObsFile(temp, syncResult);
			Log.e(TAG, "Downloaded New Obs with result errors=" + syncResult.stats.numIoExceptions);
			mSyncManager.toastSyncMessage(SyncManager.SYNC_COMPLETE, -1, -1, (int) syncResult.stats.numIoExceptions, dbError);

		} else {
			++syncResult.stats.numIoExceptions;
			UiUtils.toastAlert(mContext.getString(R.string.sync_error), mContext.getString(R.string.no_connection));
		}

		SyncManager.sSyncComplete = true;
	}

	public String[] uploadInstances(HttpClient client, String[] instancePaths, SyncResult syncResult) {

		ArrayList<String> uploadedInstances = new ArrayList<String>();
		for (int i = 0; i < instancePaths.length; i++) {
			try {
				String instancePath = FileUtils.getDecryptedFilePath(instancePaths[i]);
				MultipartEntity entity = NetworkUtils.createMultipartEntity(instancePath);
				if (entity == null)
					continue;

				NetworkUtils.postEntity(client, NetworkUtils.getFormUploadUrl(), entity);
				Log.e(TAG, "everything okay! added an instance...");
				uploadedInstances.add(instancePaths[i]);		

			} catch (Exception e) {
				Log.e(TAG, "Exception on uploading instance =" + instancePaths[i]);
				e.printStackTrace();
				++syncResult.stats.numIoExceptions;
			}

		}

		return uploadedInstances.toArray(new String[uploadedInstances.size()]);
	}

	/**
	 * Compares the current form list on the server with the forms on disk
	 * 
	 * @return a list of the new forms on the server
	 */
	private Form[] findNewFormsOnServer(HttpClient client, SyncResult syncResult) {

		// find all forms from server
		ArrayList<Form> allServerForms = new ArrayList<Form>();
		try {
			// showProgress("Updating Forms");
			InputStream is = NetworkUtils.getStream(client, NetworkUtils.getFormListDownloadUrl());
			allServerForms = mSyncManager.readFormListStream(is);
			is.close();

		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
		}

		// compare to existing forms
		ArrayList<Form> newForms = new ArrayList<Form>();
		for (int i = 0; i < allServerForms.size(); i++) {
			Form f = allServerForms.get(i);
			String formId = f.getFormId() + "";
			if (!FileUtils.doesXformExist(formId))
				newForms.add(f);
		}

		// return new forms
		return newForms.toArray(new Form[newForms.size()]);
	}

	/**
	 * Downloads forms from OpenMRS
	 * 
	 * @param serverForms
	 *            forms to download
	 * @return error message or null if successful
	 */
	private Form[] downloadNewForms(HttpClient client, Form[] newForms, SyncResult syncResult) {
		Log.i(TAG, "DownloadNewForms Called");
		// showProgress("Downloading Forms");
		ArrayList<Form> downloadedForms = new ArrayList<Form>();
		FileUtils.createFolder(FileUtils.getExternalFormsPath());

		int totalForms = newForms.length;

		for (int i = 0; i < totalForms; i++) {

			String formId = newForms[i].getFormId() + "";

			try {
				// download
				StringBuilder url = (new StringBuilder(NetworkUtils.getFormDownloadUrl())).append("&formId=").append(formId);
				Log.i(TAG, "Will try to download form " + formId + " url=" + url);
				InputStream is = NetworkUtils.getStream(client, url.toString());

				File f = new File(FileUtils.getExternalFormsPath(), formId + FileUtils.XML_EXT);
				String path = f.getAbsolutePath();
				OutputStream os = new FileOutputStream(f);
				byte buf[] = new byte[1024];
				int len;
				while ((len = is.read(buf)) > 0) {
					os.write(buf, 0, len);
				}
				os.flush();
				os.close();
				is.close();

				newForms[i].setFormId(Integer.valueOf(formId));
				newForms[i].setPath(path);
				downloadedForms.add(newForms[i]);

			} catch (Exception e) {
				++syncResult.stats.numIoExceptions;
				e.printStackTrace();
			}

		}

		return downloadedForms.toArray(new Form[downloadedForms.size()]);

	}

	/**
	 * Downloads a stream of Patient Table and Obs Table from OpenMRS, stores it
	 * to temp file
	 * 
	 * @param httpclient
	 * 
	 * @return the temporary file
	 */
	private File downloadObsStream(HttpClient client, SyncResult syncResult) {

		File tempFile = null;
		try {
			// showProgress("Downloading Clients");
			tempFile = File.createTempFile(".omrs", "-stream", mContext.getFilesDir());
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile));
			DataInputStream dis = NetworkUtils.getOdkStream(client, NetworkUtils.getPatientDownloadUrl());

			if (dis != null) {

				byte[] buffer = new byte[4096];
				int count = 0;
				while ((count = dis.read(buffer)) > 0) {
					bos.write(buffer, 0, count);
				}

				bos.close();
				dis.close();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
		} catch (IOException e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
		}

		return tempFile;
	}

	// TODO! Check... we dont need this method anymore, because sync is
	// automated?!
	// private boolean isSyncNeeded(SyncResult syncResult) {
	//
	// // establish threshold for syncing (i.e. do not sync continuously)
	// long recentDownload = Db.open().fetchMostRecentDownload();
	// long timeSinceRefresh = System.currentTimeMillis() - recentDownload;
	// SharedPreferences prefs =
	// PreferenceManager.getDefaultSharedPreferences(App.getApp());
	// String maxRefreshSeconds =
	// prefs.getString(App.getApp().getString(R.string.key_max_refresh_seconds),
	// App.getApp().getString(R.string.default_max_refresh_seconds));
	// long maxRefreshMs = 1000L * Long.valueOf(maxRefreshSeconds);
	//
	// Log.e(TAG, "Minutes since last refresh: " + timeSinceRefresh / (1000 *
	// 60));
	// if (timeSinceRefresh < maxRefreshMs) {
	//
	// long timeToNextSync = maxRefreshMs - timeSinceRefresh;
	// syncResult.delayUntil = timeToNextSync;
	// Log.e(TAG, "Synced recently... lets delay the sync until ! timetosync=" +
	// timeToNextSync);
	// return false;
	//
	// } else {
	// return true;
	// }
	//
	// }

}
