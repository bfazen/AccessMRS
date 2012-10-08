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
import org.apache.http.client.methods.HttpGet;
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
import org.apache.http.entity.mime.MultipartEntity;
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
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.MySSLSocketFactory;
import com.alphabetbloc.clinic.utilities.MyTrustManager;
import com.alphabetbloc.clinic.utilities.NetworkUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class SyncDataTask extends AsyncTask<SyncResult, String, String> {

	private static final String TAG = SyncDataTask.class.getSimpleName();
	private static final int CONNECTION_TIMEOUT = 60000;
	static final int MAX_CONN_PER_ROUTE = 20;
	static final int MAX_CONNECTIONS = 20;

	protected SyncDataListener mStateListener;
	protected DbProvider mDb;
	protected String mError;
	protected static boolean mUploadComplete;
	protected static boolean mDownloadComplete;
	protected static HttpClient httpClient;

	private static int mStep = 0;
	private static int mUploadSteps = -1;
	private static int mDownloadSteps = -1;
	private static int mTotalSteps = 100;
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
		Thread.currentThread().setName(TAG);
		sSyncResult = values[0];
		mStorePassword = EncryptionUtil.getPassword();
		Log.e(TAG, "SyncDataTask Called");
		HttpClient client = setupHttpClient();
		if (client == null)
			return String.valueOf(false);
		else
			return String.valueOf(true);
	}

	protected boolean postEntityToUrl(String url, MultipartEntity entity) {
		Log.e(TAG, "postEntityToUrl Called");
		try {
			setupHttpClient();
			HttpPost httppost = new HttpPost(url);
			httppost.setEntity(entity);
			HttpResponse response = httpClient.execute(httppost);

			// verify response is okay
			int responseCode = response.getStatusLine().getStatusCode();
			Log.d(TAG, "httppost response=" + responseCode);
			if (responseCode == HttpURLConnection.HTTP_OK) {
				return true;
			} else {
				return false;
			}

		} catch (Exception e) {
			Log.e(TAG, "httpClient DID NOT execute httpost! caught an exception!");
			mError = e.toString();
			e.printStackTrace();
			++sSyncResult.stats.numIoExceptions;
			return false;
		}
	}

	// This is all we need for XformsHelper, because all auth is in the url
	protected InputStream getUrlStream(String url) {
		Log.e(TAG, "getUrlStream Called" + System.currentTimeMillis());
		try {
			setupHttpClient();
			Log.e(TAG, "httpClient is now SetUp! at=" + System.currentTimeMillis());
			HttpGet get = new HttpGet(url);
			Log.e(TAG, "got the url! at=" + System.currentTimeMillis());
			HttpResponse response = httpClient.execute(get);
			Log.e(TAG, "got the response at=" + System.currentTimeMillis());
			// verify response is okay
			if (response.getStatusLine().getStatusCode() != 200) {
				Log.e(TAG, "Error: " + response.getStatusLine());
			} else {
				Log.e(TAG, "NO Error!: " + response.getStatusLine());
			}

			// Get hold of the response entity
			HttpEntity entity = response.getEntity();
			Log.e(TAG, "got the entity! at=" + System.currentTimeMillis());
			if (entity != null) {
				InputStream is = entity.getContent();
				Log.e(TAG, "got the inputstream at=" + System.currentTimeMillis());
				return is;
			}

		} catch (Exception e) {
			e.printStackTrace();
			mError = e.toString();
			++sSyncResult.stats.numIoExceptions;
			return null;
		}

		return null;

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
	
	protected void dropHttpClient(){
//		Log.e(TAG, "upload and download are complete, so dropping httpClient, trustore and keystore reference.");
//		httpClient = null;
//		keyStore = null;
//		trustStore = null;
//		System.gc();
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
		Log.e(TAG, "getUrlStreamFromODkConnectorTest Called" + System.currentTimeMillis());

		setupHttpClient();
		Log.e(TAG, "httpClient is now SetUp! at=" + System.currentTimeMillis());

		getServerCredentials();
		// get prefs
		String url = NetworkUtils.getPatientDownloadUrl();
		Log.e(TAG, "Got the url! at=" + System.currentTimeMillis());
		HttpPost request = new HttpPost(url);
		Log.e(TAG, "hposted the url at=" + System.currentTimeMillis());
		request.setEntity(connectorEntity);
		Log.e(TAG, "requested the entity=" + System.currentTimeMillis());
		HttpResponse response = httpClient.execute(request);
		Log.e(TAG, "got the repsonse=" + System.currentTimeMillis());
		int responseCode = response.getStatusLine().getStatusCode();
		Log.d(TAG, "httppost response=" + responseCode);
		HttpEntity responseEntity = response.getEntity();
		Log.e(TAG, "got the entity=" + System.currentTimeMillis());
		long size = responseEntity.getContentLength();
		Log.e(TAG, "getContentLength=" + size);

		DataInputStream zdis = new DataInputStream(new GZIPInputStream(responseEntity.getContent()));
		Log.e(TAG, "got the inputstream at=" + System.currentTimeMillis());
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

	protected void getServerCredentials() {
		
		if (mUsername == null || mPassword == null) {
			mUsername = NetworkUtils.getServerUsername();
			mPassword = NetworkUtils.getServerPassword();
		}
		
		if (mCohort == null || mProgram == null) {
			// String username =
			// settings.getString(App.getApp().getString(R.string.key_username),
			// App.getApp().getString(R.string.default_username));
			// String password =
			// settings.getString(App.getApp().getString(R.string.key_password),
			// App.getApp().getString(R.string.default_password));
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
			mSavedSearch = settings.getBoolean(App.getApp().getString(R.string.key_use_saved_searches), false);
			mCohort = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_saved_search), "0"));
//			mProgram = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_program), "0"));
			mProgram = 1;
		}
	}

	// /ASYNCTASK UPDATE METHODS
	// protected void setUploadSteps(int upload) {
	// mUploadSteps = upload;
	// if (mDownloadSteps != -1) {
	// mTotalSteps = mDownloadSteps + mUploadSteps;
	// }
	// }
	//
	// protected void setDownloadSteps(int download) {
	// mDownloadSteps = download;
	// if (mUploadSteps != -1) {
	// mTotalSteps = mDownloadSteps + mUploadSteps;
	// }
	// }

	protected void showProgress(String title) {
		showProgress(title, 0);
	}

	protected void showProgress(String title, int increment) {

		if (increment == 0) {
			// round up to the nearest 10 to set next sync stage
			int roundStepUp = ((mStep + 5) / 10) * 10;
			if (roundStepUp <= mStep)
				roundStepUp = roundStepUp + 10;
			mStep = roundStepUp;

			if (mStep == 0)
				mStep = mStep + 10;

		} else {
			// increment by one usually
			mStep = mStep + increment;
		}

		String current = Integer.valueOf(mStep).toString();
		String total = Integer.valueOf(mTotalSteps).toString();
		publishProgress(title, current, total);
		Log.e(TAG, "showProgress Called! Title=" + title + " mStep= " + mStep + " of " + mTotalSteps + " (U=" + mUploadSteps + " D=" + mDownloadSteps + ")");
	}

	@Override
	protected void onProgressUpdate(String... values) {
		synchronized (this) {
			if (mStateListener != null) {
				// update progress and total
				mStateListener.progressUpdate(values[0], Integer.valueOf(values[1]), Integer.valueOf(values[2]));
			}
		}
	}

	@Override
	protected void onPostExecute(String result) {
		Log.e(TAG, "onPostExecute called");
		synchronized (this) {
			if (mStateListener != null)
				mStateListener.sslSetupComplete(result, sSyncResult);
		}
	}

	public void setSyncListener(SyncDataListener sl) {
		synchronized (this) {
			mStateListener = sl;
		}
	}

}