package com.alphabetbloc.clinic.tasks;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.odk.clinic.android.openmrs.Constants;

import android.content.SharedPreferences;
import android.content.SyncResult;
import android.preference.PreferenceManager;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.WebUtils;

public class CheckConnectivityTask extends SyncDataTask {

	private String mServer;

	@Override
	protected String doInBackground(SyncResult... values) {
		sSyncResult = values[0];
		boolean connected = false;

		try {
			DataInputStream dis = getUrlStreamFromOdkConnector();
			if (dis != null) {
				dis.close();
				connected = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return String.valueOf(connected);
	}

	@Override
	protected void onPostExecute(String result) {
		// reset the credentials
		mUsername = null;
		mPassword = null;
		mCohort = null;
		mProgram = null;
		mServer = null;

		synchronized (this) {
			if (mStateListener != null)
				mStateListener.syncComplete(result, sSyncResult);

		}
	}

	public void setServerCredentials(String username, String password) {
		setServerCredentials(null, username, password);
	}

	public void setServerCredentials(String server, String username, String password) {
		mServer = server;
		mUsername = username;
		mPassword = password;
	}

	@Override
	protected void getServerCredentials() {
		if (mServer == null)
			mServer = WebUtils.getPatientDownloadUrl();
		if (mUsername == null)
			mUsername = WebUtils.getServerUsername();
		if (mPassword == null)
			mPassword = WebUtils.getServerPassword();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		mSavedSearch = settings.getBoolean(App.getApp().getString(R.string.key_use_saved_searches), false);
		mCohort = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_saved_search), "0"));
		mProgram = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_program), "0"));
	}

	@Override
	protected DataInputStream getUrlStreamFromOdkConnector() throws Exception {
		setupHttpClient();
		getServerCredentials();

		HttpPost request = new HttpPost(mServer);
		request.setEntity(connectorEntity);
		HttpResponse response = httpClient.execute(request);
		response.getStatusLine().getStatusCode();
		HttpEntity responseEntity = response.getEntity();
		responseEntity.getContentLength();

		DataInputStream zdis = new DataInputStream(new GZIPInputStream(responseEntity.getContent()));

		int status = zdis.readInt();
		if (status == Constants.STATUS_FAILURE) {
			zdis.close();
			throw new IOException("Connection failed. Please try again.");
		} else if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
			zdis.close();
			throw new IOException("Access denied. Check your username and password.");
		} else {
			assert (status == HttpURLConnection.HTTP_OK); // success
			return zdis;
		}
	}

}
