package com.alphabetbloc.clinic.tasks;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.MySSLSocketFactory;
import com.alphabetbloc.clinic.utilities.MyTrustManager;
import com.alphabetbloc.clinic.utilities.NetworkUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class CheckConnectivityTask extends AsyncTask<SyncResult, String, String> {

	private String mServer;
	private static final int CONNECTION_TIMEOUT = 60000;
	static final int MAX_CONN_PER_ROUTE = 20;
	static final int MAX_CONNECTIONS = 20;
	private static final String TAG = CheckConnectivityTask.class.getSimpleName();

	protected SyncDataListener mStateListener;
	protected DbProvider mDb;
	protected String mError;
	protected static boolean mUploadComplete;
	protected static boolean mDownloadComplete;
	protected static HttpClient httpClient;

	private KeyStore keyStore;
	private KeyStore trustStore;
	private String mStorePassword;
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

	AbstractHttpEntity connectorEntity = new AbstractHttpEntity() {

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

	protected HttpClient setupHttpClient() {
		if (httpClient == null) {
			try {

				Log.e(TAG, "httpClient is null, download is creating a new client");
				SSLContext sslContext = createSslContext();
				MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier());
				httpClient = createHttpClient(socketFactory);

			} catch (GeneralSecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				++sSyncResult.stats.numIoExceptions;
			}
		}

		return httpClient;
	}

	private SSLContext createSslContext() throws GeneralSecurityException {
		Log.e(TAG, "create SSL Context Called");

		// TrustStore
		if (trustStore == null)
			trustStore = FileUtils.loadSslStore(FileUtils.MY_TRUSTSTORE);
		MyTrustManager myTrustManager = new MyTrustManager(trustStore);
		TrustManager[] tms = new TrustManager[] { myTrustManager };

		// KeyStore
		KeyManager[] kms = null;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		boolean useClientAuth = prefs.getBoolean(App.getApp().getString(R.string.key_client_auth), false);
		if (useClientAuth) {
			if (keyStore == null)
				keyStore = FileUtils.loadSslStore(FileUtils.MY_KEYSTORE);
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(keyStore, mStorePassword.toCharArray());
			kms = kmf.getKeyManagers();
		}

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(kms, tms, null);

		return context;
	}

	private HttpClient createHttpClient(SocketFactory socketFactory) {
		Log.e(TAG, "CreateHttpClient Called");
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1); // yaw
		HttpProtocolParams.setContentCharset(params, HTTP.UTF_8); // yaw
		HttpProtocolParams.setUseExpectContinue(params, false); // yaw
		HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
		HttpConnectionParams.setSoTimeout(params, CONNECTION_TIMEOUT); // yaw
		HttpClientParams.setRedirecting(params, false); // yaw

		ConnManagerParams.setTimeout(params, CONNECTION_TIMEOUT); // yaw
		ConnPerRoute connPerRoute = new ConnPerRouteBean(MAX_CONN_PER_ROUTE);
		ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);
		ConnManagerParams.setMaxTotalConnections(params, MAX_CONNECTIONS);

		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		SocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
		if (socketFactory != null) {
			sslSocketFactory = socketFactory;
		}
		schemeRegistry.register(new Scheme("https", sslSocketFactory, 8443));
		ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
		DefaultHttpClient httpClient = new DefaultHttpClient(cm, params);

		return httpClient;
	}



}
