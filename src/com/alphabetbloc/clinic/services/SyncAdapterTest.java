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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.NetworkUtils;
import com.alphabetbloc.clinic.utilities.XformUtils;

public class SyncAdapterTest extends AbstractThreadedSyncAdapter {

	private static final String TAG = SyncAdapterTest.class.getSimpleName();

	public SyncAdapterTest(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		Log.e(TAG, "SyncDataTask Called");
		Thread.currentThread().setName(TAG);

		HttpClient client = NetworkUtils.getHttpClient();
		if (client != null) {

			// upload forms
			String[] toUpload = SyncManager.getInstancesToUpload();
			String[] uploaded = uploadInstances(client, toUpload, syncResult);
			SyncManager.updateFormDb(uploaded);
			toastUploadResult(uploaded.length, toUpload.length);

			// download new forms
			ArrayList<Form> newForms = findNewFormsOnServer(client, syncResult);
			downloadNewForms(client, newForms, syncResult);

			// download new obs
			File temp = downloadObsStream(client, syncResult);
			SyncManager.readObsFile(temp);

		} else
			Log.e(TAG, "why is the client null?!");

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
	private static ArrayList<Form> findNewFormsOnServer(HttpClient client, SyncResult syncResult) {

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
		return newForms;
	}

	/**
	 * Downloads forms from OpenMRS
	 * 
	 * @param serverForms
	 *            forms to download
	 * @return error message or null if successful
	 */
	private static String downloadNewForms(HttpClient client, ArrayList<Form> serverForms, SyncResult syncResult) {
		Log.i(TAG, "DownloadNewForms Called");
		// showProgress("Downloading Forms");

		FileUtils.createFolder(FileUtils.getExternalFormsPath());

		int totalForms = serverForms.size();
		for (int i = 0; i < totalForms; i++) {

			String formId = serverForms.get(i).getFormId() + "";

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

				// update clinic
				DbProvider.openDb().updateFormPath(Integer.valueOf(formId), path);

				// update collect
				if (!XformUtils.insertSingleForm(path))
					return "ODK Collect not initialized.";

			} catch (Exception e) {
				++syncResult.stats.numIoExceptions;
				e.printStackTrace();
				return e.toString();
			}

		}
		return null;

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

	private void toastUploadResult(int success, int total) {

		String uploadResult = "No Completed Forms to Upload";
		boolean error = true;
		
		if (total > 0) {
			if (success == total){
				uploadResult = App.getApp().getString(R.string.upload_all_successful, success);
				error = false;
			}else
				uploadResult = App.getApp().getString(R.string.upload_some_failed, (total - success) + " of " + total);
		}
		
		showSyncToast(uploadResult, error);

	}
	
	private void showSyncToast(String message, boolean error) {

		LayoutInflater inflater = (LayoutInflater) App.getApp().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(App.getApp());
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.BOTTOM, 0, -20);
		t.show();

	}
	
	
	
	

}
