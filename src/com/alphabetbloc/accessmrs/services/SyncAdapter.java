/*
 * Copyright (C) 2012 Louis Fazen
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
package com.alphabetbloc.accessmrs.services;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.HttpClient;
import org.apache.http.entity.mime.MultipartEntity;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.alphabetbloc.accessmrs.R;
import com.alphabetbloc.accessmrs.data.Form;
import com.alphabetbloc.accessmrs.ui.admin.LauncherActivity;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.utilities.NetworkUtils;
import com.alphabetbloc.accessmrs.utilities.UiUtils;

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
	private String mTimeoutException;
	private ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(5);
	private SimpleDateFormat sTimeLog = new SimpleDateFormat("mm:ss.SSS");

	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, final SyncResult syncResult) {

		long before = System.currentTimeMillis();
		if (App.DEBUG)
			Log.v(TAG, "Sync Requested... asking user.");
		Thread.currentThread().setName(TAG);

		mSyncManager = new SyncManager(mContext);

		// ask user to sync
		int count = 0;
		while (!SyncManager.sStartSync.get() && !SyncManager.sCancelSync.get() && count < 20) {
			android.os.SystemClock.sleep(1000);
			count++;
			if (App.DEBUG)
				Log.v(TAG, "Waiting for User with count=" + count);
		}

		// sync
		if (!SyncManager.sCancelSync.get()) {
			if (App.DEBUG)
				Log.v(TAG, "Starting an actual sync");
			// Inform Activity
			Intent broadcast = new Intent(SyncManager.SYNC_MESSAGE);
			broadcast.putExtra(SyncManager.START_NEW_SYNC, true);
			LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);

			// Start the Sync
			performSync(syncResult);
		} else {
			if (App.DEBUG)
				Log.v(TAG, "User cancelled the sync");
		}

		SyncManager.sEndSync.set(true);
		SyncManager.sStartSync.set(false);
		SyncManager.sCancelSync.set(false);

		if (App.DEBUG)
			Log.v(TAG, "sync is now ending");
		if (App.DEBUG)
			Log.v("SYNC BENCHMARK", "Total Sync Time: \n" + sTimeLog.format(new Date(System.currentTimeMillis() - before)));
	}

	private void performSync(SyncResult syncResult) {
		mTimeoutException = null;
		HttpClient client = NetworkUtils.getHttpClient();
		if (client == null) {
			// ++syncResult.stats.numIoExceptions;
			Log.e(TAG, "HttpClient is null!  Check the credential storage!");
			Intent i = new Intent(mContext, LauncherActivity.class);
			i.putExtra(LauncherActivity.LAUNCH_DASHBOARD, false);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(i);
			UiUtils.toastAlert(mContext.getString(R.string.sync_error), mContext.getString(R.string.no_connection));
			return;
		}
		// UPLOAD FORMS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_uploading_forms), false); // 0%
		String[] instancesToUpload = mSyncManager.getInstancesToUpload();
		String[] instancesUploaded = uploadInstances(client, instancesToUpload, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_uploading_forms), (instancesUploaded.length < 1) ? true : false); // 10%
		String dbError = mSyncManager.updateUploadedForms(instancesUploaded, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_uploading_forms), (instancesUploaded.length < 1) ? true : false); // 20%
		int uploadErrors = (int) syncResult.stats.numIoExceptions;
		mSyncManager.toastSyncUpdate(SyncManager.UPLOAD_FORMS, instancesUploaded.length, instancesToUpload.length, uploadErrors, dbError);
		if (App.DEBUG)
			Log.v(TAG, "End of Upload ConnectionTimeOutException=" + mTimeoutException);
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}

		// DOWNLOAD NEW FORMS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), false); // No
																								// Change
		Form[] formsToDownload = findNewFormsOnServer(client, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), (formsToDownload.length < 1) ? true : false); // 30%
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}
		Form[] formsDownloaded = downloadNewForms(client, formsToDownload, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), (formsDownloaded.length < 1) ? true : false); // 40%
		dbError = mSyncManager.updateDownloadedForms(formsDownloaded, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), (formsDownloaded.length < 1) ? true : false); // 50%
		if (App.DEBUG)
			Log.v(TAG, "Downloaded New Forms with result errors=" + syncResult.stats.numIoExceptions);
		int downloadErrors = ((int) syncResult.stats.numIoExceptions) - uploadErrors;
		mSyncManager.toastSyncUpdate(SyncManager.DOWNLOAD_FORMS, formsDownloaded.length, formsToDownload.length, downloadErrors, dbError);
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}

		// DOWNLOAD NEW OBS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_data), false); // No
																								// Change
		long before = System.currentTimeMillis();
		File temp = downloadObsStream(client, syncResult);
		if (App.DEBUG)
			Log.v("SYNC BENCHMARK", "Download Time: \n" + sTimeLog.format(new Date(System.currentTimeMillis() - before)));
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_updating_data), true); // 60%
		if (temp != null) {
			dbError = mSyncManager.readObsFile(temp, syncResult); // 70-100%
			FileUtils.deleteFile(temp.getAbsolutePath());
		}
		int downloadObsErrors = ((int) syncResult.stats.numIoExceptions) - (uploadErrors + downloadErrors);
		mSyncManager.toastSyncUpdate(SyncManager.DOWNLOAD_OBS, -1, -1, downloadObsErrors, dbError);
		if (App.DEBUG)
			Log.v(TAG, "Downloaded New Obs with result errors=" + syncResult.stats.numIoExceptions);

		// TOAST RESULT
		mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, null);

	}

	public String[] uploadInstances(HttpClient client, String[] instancePaths, SyncResult syncResult) {
		SyncManager.sLoopProgress.set(0);
		SyncManager.sLoopCount.set(instancePaths.length);
		ArrayList<String> uploadedInstances = new ArrayList<String>();
		for (int i = 0; i < instancePaths.length; i++) {

			if (mTimeoutException != null)
				break;

			try {
				String instancePath = FileUtils.getDecryptedFilePath(instancePaths[i]);
				MultipartEntity entity = NetworkUtils.createMultipartEntity(instancePath);
				if (entity == null)
					continue;

				NetworkUtils.postEntity(client, NetworkUtils.getFormUploadUrl(), entity);
				if (App.DEBUG)
					Log.v(TAG, "everything okay! added an instance...");
				uploadedInstances.add(instancePaths[i]);

			} catch (IOException e) {
				mTimeoutException = e.getLocalizedMessage();
				Log.e(TAG, "Connection Timeout Exception... ");
				e.printStackTrace();
				++syncResult.stats.numIoExceptions;
			} catch (Exception e) {
				Log.e(TAG, "Exception on uploading instance =" + instancePaths[i]);
				e.printStackTrace();
				++syncResult.stats.numIoExceptions;
			}

			SyncManager.sLoopProgress.getAndIncrement();
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
			InputStream is = NetworkUtils.getStream(client, NetworkUtils.getFormListDownloadUrl());
			allServerForms = mSyncManager.readFormListStream(is);
			is.close();

		} catch (IOException e) {
			mTimeoutException = e.getLocalizedMessage();
			Log.e(TAG, "Connection Timeout Exception... ");
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
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

		SyncManager.sLoopProgress.set(0);
		SyncManager.sLoopCount.set(newForms.length);
		ArrayList<Form> downloadedForms = new ArrayList<Form>();
		FileUtils.createFolder(FileUtils.getExternalFormsPath());

		for (int i = 0; i < newForms.length; i++) {

			String formId = newForms[i].getFormId() + "";

			try {
				// download
				StringBuilder url = (new StringBuilder(NetworkUtils.getFormDownloadUrl())).append("&formId=").append(formId);
				if (App.DEBUG)
					Log.v(TAG, "Will try to download form " + formId + " url=" + url);
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

			} catch (IOException e) {
				mTimeoutException = e.getLocalizedMessage();
				Log.e(TAG, "Connection Timeout Exception... ");
				e.printStackTrace();
				++syncResult.stats.numIoExceptions;
			} catch (Exception e) {
				++syncResult.stats.numIoExceptions;
				e.printStackTrace();
			}

			SyncManager.sLoopProgress.getAndIncrement();
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

		// No accurate download size on stream, so hack periodic update
		SyncManager.sLoopCount.set(10);
		SyncManager.sLoopProgress.set(0);
		mExecutor.schedule(new Runnable() {
			public void run() {
				// increase 1%/7s (i.e. slower than 1min timeout)
				SyncManager.sLoopProgress.getAndIncrement();
				if (SyncManager.sLoopProgress.get() < 9)
					mExecutor.schedule(this, 7000, TimeUnit.MILLISECONDS);
			}
		}, 1000, TimeUnit.MILLISECONDS);

		// Download File
		File tempFile = null;
		try {
			tempFile = File.createTempFile(".omrs", "-stream", mContext.getFilesDir());
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile));
			DataInputStream dis = NetworkUtils.getOdkStream(client, NetworkUtils.getPatientDownloadUrl());

			if (App.DEBUG)
				Log.v("SYNC BENCHMARK", "Download with buffer size=\n" + 8192);
			if (dis != null) {

				byte[] buffer = new byte[8192]; // increasing this from 4096 to
												// improve performance (testing)
				int count = 0;
				while ((count = dis.read(buffer)) > 0) {
					bos.write(buffer, 0, count);
				}

				bos.close();
				dis.close();
			}

		} catch (Exception e) {
			FileUtils.deleteFile(tempFile.getAbsolutePath());
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return null;
		}

		return tempFile;
	}
}
