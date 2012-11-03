package com.alphabetbloc.accessmrs.tasks;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;

import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.alphabetbloc.accessmrs.listeners.SyncDataListener;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.NetworkUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class CheckConnectivityTask extends AsyncTask<SyncResult, String, String> {

	private String mServer;
	static final int MAX_CONN_PER_ROUTE = 20;
	static final int MAX_CONNECTIONS = 20;
//	private static final String TAG = CheckConnectivityTask.class.getSimpleName();

	protected SyncDataListener mStateListener;
	private HttpClient httpClient;

	protected static String mUsername;
	protected static String mPassword;
	protected static Integer mCohort;
	protected static boolean mSavedSearch;
	protected static Integer mProgram;
	protected static SyncResult sSyncResult;

	@Override
	protected String doInBackground(SyncResult... values) {
		sSyncResult = values[0];
		boolean connected = false;

		try {
			getServerCredentials();
			httpClient = NetworkUtils.getHttpClient();
			DataInputStream dis = getServerStream();
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
	
	public void setSyncListener(SyncDataListener sl) {
		synchronized (this) {
			mStateListener = sl;
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

	protected void getServerCredentials() {
		if (mServer == null)
			mServer = NetworkUtils.getPatientDownloadUrl();
		if (mUsername == null)
			mUsername = NetworkUtils.getServerUsername();
		if (mPassword == null)
			mPassword = NetworkUtils.getServerPassword();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		mSavedSearch = settings.getBoolean(App.getApp().getString(R.string.key_use_saved_searches), false);
		mCohort = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_saved_search), "0"));
		mProgram = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_program), "0"));
	}

	

	protected DataInputStream getServerStream() throws Exception {

		HttpPost request = new HttpPost(mServer);
		request.setEntity(new OdkAuthEntity());
		HttpResponse response = httpClient.execute(request);
		response.getStatusLine().getStatusCode();
		HttpEntity responseEntity = response.getEntity();
		responseEntity.getContentLength();

		DataInputStream zdis = new DataInputStream(new GZIPInputStream(responseEntity.getContent()));

		int status = zdis.readInt();
		if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
			zdis.close();
			throw new IOException("Access denied. Check your username and password.");
		} else if (status <= 0 || status >= HttpURLConnection.HTTP_BAD_REQUEST) {
			zdis.close();
			throw new IOException("Connection Failed. Please Try Again.");
		} else {
			assert (status == HttpURLConnection.HTTP_OK); // success
			return zdis;
		}
	}
	
	private static class OdkAuthEntity extends AbstractHttpEntity {

		public boolean isRepeatable() {
			return false;
		}

		public long getContentLength() {
			return -1;
		}

		public boolean isStreaming() {
			return false;
		}

		public InputStream getContent() throws IOException {
			// Should be implemented as well but is irrelevant for this case
			throw new UnsupportedOperationException();
		}

		public void writeTo(final OutputStream outstream) throws IOException {
			DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(outstream));
			dos.writeUTF(mUsername);
			dos.writeUTF(mPassword);
			dos.writeBoolean(mSavedSearch);
			if (mCohort > 0)
				dos.writeInt(mCohort);
			if (mProgram > 0)
				dos.writeInt(mProgram);
			dos.close();
		}

	};

}
