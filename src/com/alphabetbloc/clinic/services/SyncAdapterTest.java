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
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.NetworkUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;

public class SyncAdapterTest extends AbstractThreadedSyncAdapter {

	private static final String TAG = SyncAdapterTest.class.getSimpleName();
	private static final int UPLOAD_FORMS = 1;
	private static final int DOWNLOAD_FORMS = 2;
	private static final int SYNC_COMPLETE = 3;
	private String mFinalResult;

	public SyncAdapterTest(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		Log.i(TAG, "Sync Started");
		Thread.currentThread().setName(TAG);

		HttpClient client = NetworkUtils.getHttpClient();
		if (client != null) {

			// upload forms
			String[] toUpload = SyncManager.getInstancesToUpload();
			String[] uploaded = uploadInstances(client, toUpload, syncResult);
			String dbError = SyncManager.updateUploadedForms(uploaded, syncResult);
			toastResult(UPLOAD_FORMS, uploaded.length, toUpload.length, (int) syncResult.stats.numIoExceptions, dbError);

			// download new forms
			Form[] newForms = findNewFormsOnServer(client, syncResult);
			Form[] downloadedForms = downloadNewForms(client, newForms, syncResult);
			dbError = SyncManager.updateDownloadedForms(downloadedForms, syncResult);
			toastResult(DOWNLOAD_FORMS, downloadedForms.length, newForms.length, (int) syncResult.stats.numIoExceptions, dbError);

			// download new obs
			File temp = downloadObsStream(client, syncResult);
			dbError = SyncManager.readObsFile(temp, syncResult);
			toastResult(SYNC_COMPLETE, -1, -1, (int) syncResult.stats.numIoExceptions, dbError);

		} else {
			++syncResult.stats.numIoExceptions;
			UiUtils.toastAlert(App.getApp().getString(R.string.sync_error), App.getApp().getString(R.string.no_connection));
		}
	}

	public static String[] uploadInstances(HttpClient client, String[] instancePaths, SyncResult syncResult) {

		ArrayList<String> uploadedInstances = new ArrayList<String>();

		for (int i = 0; i < instancePaths.length; i++) {
			try {

				String instancePath = FileUtils.getDecryptedFilePath(instancePaths[i]);
				MultipartEntity entity = NetworkUtils.createMultipartEntity(instancePath);
				if (entity == null)
					continue;

				if (NetworkUtils.postEntity(client, NetworkUtils.getFormUploadUrl(), entity)) {
					uploadedInstances.add(instancePaths[i]);
					Log.e(TAG, "everything okay! adding some instances...");
				}

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
	private static Form[] findNewFormsOnServer(HttpClient client, SyncResult syncResult) {

		// find all forms from server
		ArrayList<Form> allServerForms = new ArrayList<Form>();
		try {
			// showProgress("Updating Forms");
			InputStream is = NetworkUtils.getStream(client, NetworkUtils.getFormListDownloadUrl());
			allServerForms = SyncManager.readFormListStream(is);
			is.close();

		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			UiUtils.toastAlert(App.getApp().getString(R.string.sync_error), App.getApp().getString(R.string.no_connection));
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
	private static Form[] downloadNewForms(HttpClient client, Form[] newForms, SyncResult syncResult) {
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
	private static File downloadObsStream(HttpClient client, SyncResult syncResult) {

		File tempFile = null;
		try {
			// showProgress("Downloading Clients");
			tempFile = File.createTempFile(".omrs", "-stream", App.getApp().getFilesDir());
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

	public void toastResult(int syncType, int success, int total, int totalErrors, String dbError) {

		String currentToast = createToastString(syncType, success, total);
		if (currentToast != null) {

			StringBuilder result = new StringBuilder();
			result.append(currentToast);
			if (totalErrors > 0)
				result.append(" (Total Errors= ").append(totalErrors).append(")");
			if (dbError != null)
				result.append(" : Db Errors= ").append(dbError);

			UiUtils.toastSyncMessage(result.toString(), (totalErrors == 0) ? false : true);
		}
	}
	
	private String createToastString(int syncType, int success, int total){
		
		//Get the strings
		String toastString = null;
		String defaultString = null;
		int successString = 0;
		int failString = 0;

		
		switch (syncType) {
		case UPLOAD_FORMS:
			defaultString = "No Forms to Upload";
			successString = R.string.upload_forms_successful;
			failString = R.string.upload_forms_failed;
			break;
		case DOWNLOAD_FORMS:
			defaultString = "No New Forms on Server";
			successString = R.string.download_forms_successful;
			failString = R.string.download_forms_failed;
			break;
		case SYNC_COMPLETE:
		default:
			toastString = mFinalResult;
			break;
		}

		// Aggregate Success Strings Until complete
		if (total == 0)
			mFinalResult = mFinalResult + defaultString;
		else if (total > 0 && success == total)
			mFinalResult = mFinalResult + App.getApp().getString(successString, success);
		//Toast error strings 
		else if (total > 0 && success != total)
			toastString = App.getApp().getString(failString, (total - success) + " of " + total);
		
		
		return toastString;
	}
	
}
