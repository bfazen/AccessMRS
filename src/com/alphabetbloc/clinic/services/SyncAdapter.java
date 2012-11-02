package com.alphabetbloc.clinic.services;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.http.entity.mime.MultipartEntity;

import android.accounts.Account;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
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
	private String mTimeoutException;

	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, final SyncResult syncResult) {
		Log.i(TAG, "Sync Requested... asking user.");
		Thread.currentThread().setName(TAG);

		mSyncManager = new SyncManager(mContext);
		
		//check user activity
		if(isUserEnteringData())
			SyncManager.sCancelSync = true;

		// ask user to sync
		int count = 0;
		while(!SyncManager.sStartSync && !SyncManager.sCancelSync && count < 20){
			android.os.SystemClock.sleep(1000);
			count++;
			Log.v(TAG, "Waiting for User with count=" + count);
		}
		
		// sync
		if(!SyncManager.sCancelSync) {	
			Log.i(TAG, "Starting an actual sync");
			// Inform Activity
			Intent broadcast = new Intent(SyncManager.SYNC_MESSAGE);
			broadcast.putExtra(SyncManager.START_NEW_SYNC, true);
			LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);
			
			// Start the Sync
			performSync(syncResult);
		} else {
			Log.i(TAG, "User cancelled the sync");
		}
		
		SyncManager.sEndSync = true;
		SyncManager.sStartSync = false;
		SyncManager.sCancelSync = false;

		Log.i(TAG, "sync is now ending");
	}

	private boolean isUserEnteringData() {
		RunningAppProcessInfo currentApp = null;
		String collectPackage = "org.odk.collect.android";
		
		ActivityManager	am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> l = am.getRunningAppProcesses();
		Iterator<RunningAppProcessInfo> i = l.iterator();
		
		while (i.hasNext()) {
			currentApp = i.next();
			if (currentApp.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND){
				if(currentApp.processName.equalsIgnoreCase(collectPackage)) 
					return true;
				else
					return false;
			}
		}

		return false;
	}
	
	private void performSync(SyncResult syncResult) {
		mTimeoutException = null;
		HttpClient client = NetworkUtils.getHttpClient();
		if (client == null) {
			// ++syncResult.stats.numIoExceptions;
			Log.e(TAG, "client is null!  Check the credential storage!");
			Intent i = new Intent(mContext, ClinicLauncherActivity.class);
			i.putExtra(ClinicLauncherActivity.LAUNCH_DASHBOARD, false);
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
		Log.v(TAG, "End of Upload ConnectionTimeOutException=" + mTimeoutException);
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}

		// DOWNLOAD NEW FORMS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), false); // No Change
		Form[] formsToDownload = findNewFormsOnServer(client, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), (formsToDownload.length < 1) ? true : false); //30%
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}
		Form[] formsDownloaded = downloadNewForms(client, formsToDownload, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), (formsDownloaded.length < 1) ? true : false); //40%
		dbError = mSyncManager.updateDownloadedForms(formsDownloaded, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_forms), (formsDownloaded.length < 1) ? true : false); //50%
		Log.v(TAG, "Downloaded New Forms with result errors=" + syncResult.stats.numIoExceptions);
		int downloadErrors = ((int) syncResult.stats.numIoExceptions) - uploadErrors;
		mSyncManager.toastSyncUpdate(SyncManager.DOWNLOAD_FORMS, formsDownloaded.length, formsToDownload.length, downloadErrors, dbError);
		if (mTimeoutException != null) {
			mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, mTimeoutException);
			return;
		}

		// DOWNLOAD NEW OBS
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_downloading_data), false); // No Change
		File temp = downloadObsStream(client, syncResult);
		mSyncManager.addSyncStep(mContext.getString(R.string.sync_updating_data), true); //60%  
		if (temp != null) {
			dbError = mSyncManager.readObsFile(temp, syncResult); //70-100%
			FileUtils.deleteFile(temp.getAbsolutePath());
		}
		int downloadObsErrors = ((int) syncResult.stats.numIoExceptions) - (uploadErrors + downloadErrors);
		mSyncManager.toastSyncUpdate(SyncManager.DOWNLOAD_OBS, -1, -1, downloadObsErrors, dbError);
		Log.v(TAG, "Downloaded New Obs with result errors=" + syncResult.stats.numIoExceptions);

		// TOAST RESULT
		mSyncManager.toastSyncResult((int) syncResult.stats.numIoExceptions, null);

	}

	public String[] uploadInstances(HttpClient client, String[] instancePaths, SyncResult syncResult) {
		SyncManager.sLoopProgress = 0;
		SyncManager.sLoopCount = instancePaths.length;
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

			SyncManager.sLoopProgress++;
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

		SyncManager.sLoopProgress = 0;
		SyncManager.sLoopCount = newForms.length;
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

			SyncManager.sLoopProgress++;
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
