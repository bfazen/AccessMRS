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
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.ui.admin.ClinicLauncherActivity;
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
	private ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(5);
	private String mTimeoutException;
	private Handler h;

	// private Handler h;

	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
	}

	// TODO! 3 ways to make this work:
	// 1. Thread Sleep for 1 second, then check again... simple
	// 2. Handler... this also posts a runnable as below, but it occurs on the
	// same thread (so should work better than scheduled executor? ... but not
	// sure I got it to work)
	// 3. ScheduledExecutor service (this did work... but the syncResult kept
	// going through (sync was considered ended because it posted it as a
	// delayed runnable on another thread

//	@Override
//	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, final SyncResult syncResult) {
//
//		mSyncManager = new SyncManager(mContext);
//		SyncManager.sStartSync = extras.getBoolean(SyncManager.MANUAL_SYNC, false);
//
//		Looper.prepare();
//		h = new Handler();
//		h.post(new Runnable() {
//			int count = 0;
//
//			public void run() {
//				if (SyncManager.sEndSync) {
//					// Sync was cancelled by user, so quit
//				} else if (SyncManager.sStartSync || count > 30) {
//					// Inform Activity
//					Intent broadcast = new Intent(SyncManager.SYNC_MESSAGE);
//					broadcast.putExtra(SyncManager.START_NEW_SYNC, true);
//					LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast); //
//					// Start the Sync
//					performSync(syncResult);
//					SyncManager.sEndSync = true;
//					SyncManager.sStartSync = false;
//				} else {
//					// repeat check every second up until 30 seconds
//					h.postDelayed(this, 1000);
//					count++;
//				}
//			}
//		});
//
//		Log.e(TAG, "sync is now ending");
//	}

	// @Override
//	public void onPerformSync2(Account account, Bundle extras, String authority, ContentProviderClient provider, final SyncResult syncResult) {
//		mSyncManager = new SyncManager(mContext);
//
//		SyncManager.sStartSync = extras.getBoolean(SyncManager.MANUAL_SYNC, false);
//		mExecutor.schedule(new Runnable() {
//
//			int count = 0;
//
//			public void run() {
//				if (SyncManager.sEndSync) {
//					// Sync was cancelled by user, so quit
//				} else if (SyncManager.sStartSync || count > 30) {
//					// Inform Activity
//					Intent broadcast = new Intent(SyncManager.SYNC_MESSAGE); //
//					broadcast.putExtra(SyncManager.START_NEW_SYNC, true); //
//					LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast); //
//					// Start the Sync
//					performSync(syncResult);
//					SyncManager.sEndSync = true;
//					SyncManager.sStartSync = false;
//				} else {
//					// repeat check every second up until 30 seconds
//					mExecutor.schedule(this, 1000, TimeUnit.MILLISECONDS);
//					count++;
//				}
//
//			}
//		}, 0, TimeUnit.MILLISECONDS);
//		while (!SyncManager.sEndSync) {
//		}
//		Log.e(TAG, "sync is now ending");
//	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, final SyncResult syncResult) {
		Log.i(TAG, "Sync Started");
		Thread.currentThread().setName(TAG);

		mSyncManager = new SyncManager(mContext);
		int count = 0;
		while(!SyncManager.sStartSync && !SyncManager.sEndSync && count < 20){
			android.os.SystemClock.sleep(1000);
			count++;
			Log.e(TAG, "Waiting for User with count=" + count);
		}
		
		
		if(!SyncManager.sEndSync) {	
			Log.e(TAG, "sync is now automatically starting");
			// Inform Activity
			Intent broadcast = new Intent(SyncManager.SYNC_MESSAGE);
			broadcast.putExtra(SyncManager.START_NEW_SYNC, true);
			LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);
			// Start the Sync
			performSync(syncResult);
			SyncManager.sEndSync = true;
			SyncManager.sStartSync = false;
		} else {
			Log.e(TAG, "User cancelled the sync");
		}

		Log.e(TAG, "sync is now ending");
	}

	
//if (askUserToSync(count)) 
//	private boolean askUserToSync(int count) {
//
//		// Sync was cancelled by user, so quit
//		if (SyncManager.sEndSync)
//			return false;
//
//		// Sync was accepted by User, or after wait of 30s
//		else if (SyncManager.sStartSync || count > 30)
//			return true;
//
//		// repeat check every second up until 30 seconds
//		else {
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//				Log.i(TAG, "Sync was Interrupted When Waiting for User");
//				e.printStackTrace();
//			}
//			return askUserToSync(count++);
//		}
//
//	}
	
//	private boolean askUserToSync2(int count) {
//
//		// Sync was cancelled by user, so quit
//		if (SyncManager.sEndSync)
//			return false;
//
//		// Sync was accepted by User, or after wait of 30s
//		else if (SyncManager.sStartSync || count > 30)
//			return true;
//
//		// repeat check every second up until 30 seconds
//		else {
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//				Log.i(TAG, "Sync was Interrupted When Waiting for User");
//				e.printStackTrace();
//			}
//			return askUserToSync(count++);
//		}
//
//	}

	private void performSync(SyncResult syncResult) {
		mTimeoutException = null;
		HttpClient client = NetworkUtils.getHttpClient();
		if (client == null) {
			// ++syncResult.stats.numIoExceptions;
			Log.e(TAG, "client is null!  Check the credential storage!");
			Intent i = new Intent(mContext, ClinicLauncherActivity.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(i);
			UiUtils.toastAlert(mContext.getString(R.string.sync_error), mContext.getString(R.string.no_connection));
			return;
		}

		// UPLOAD FORMS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_uploading_forms)); // 1
		String[] instancesToUpload = mSyncManager.getInstancesToUpload();
		String[] instancesUploaded = uploadInstances(client, instancesToUpload, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_uploading_forms)); // 2
		String dbError = mSyncManager.updateUploadedForms(instancesUploaded, syncResult);
		int uploadErrors = (int) syncResult.stats.numIoExceptions;
		mSyncManager.toastSyncUpdate(SyncManager.UPLOAD_FORMS, instancesUploaded.length, instancesToUpload.length, uploadErrors, dbError);
		Log.e(TAG, "End of Upload ConnectionTimeOutException=" + mTimeoutException);
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}

		// DOWNLOAD NEW FORMS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms)); // 3
		Form[] formsToDownload = findNewFormsOnServer(client, syncResult);
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}
		Form[] formsDownloaded = downloadNewForms(client, formsToDownload, syncResult);
		dbError = mSyncManager.updateDownloadedForms(formsDownloaded, syncResult);
		Log.e(TAG, "Downloaded New Forms with result errors=" + syncResult.stats.numIoExceptions);
		int downloadErrors = ((int) syncResult.stats.numIoExceptions) - uploadErrors;
		mSyncManager.toastSyncUpdate(SyncManager.DOWNLOAD_FORMS, formsDownloaded.length, formsToDownload.length, downloadErrors, dbError);
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}

		// DOWNLOAD NEW OBS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_data)); // 6
		File temp = downloadObsStream(client, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_updating_data)); // 7->10
		if (temp != null) {
			dbError = mSyncManager.readObsFile(temp, syncResult);
			FileUtils.deleteFile(temp.getAbsolutePath());
		}
		int downloadObsErrors = ((int) syncResult.stats.numIoExceptions) - (uploadErrors + downloadErrors);
		mSyncManager.toastSyncUpdate(SyncManager.DOWNLOAD_OBS, -1, -1, downloadObsErrors, dbError);
		Log.e(TAG, "Downloaded New Obs with result errors=" + syncResult.stats.numIoExceptions);

		// TOAST RESULT
		mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, null);

	}

	public String[] uploadInstances(HttpClient client, String[] instancePaths, SyncResult syncResult) {

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
				Log.e(TAG, "everything okay! added an instance...");
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

		ArrayList<Form> downloadedForms = new ArrayList<Form>();
		FileUtils.createFolder(FileUtils.getExternalFormsPath());

		for (int i = 0; i < newForms.length; i++) {

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

			} catch (IOException e) {
				mTimeoutException = e.getLocalizedMessage();
				Log.e(TAG, "Connection Timeout Exception... ");
				e.printStackTrace();
				++syncResult.stats.numIoExceptions;
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

		} catch (Exception e) {
			FileUtils.deleteFile(tempFile.getAbsolutePath());
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return null;
		}

		return tempFile;
	}

}
