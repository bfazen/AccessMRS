package org.odk.clinic.android.tasks;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
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
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
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
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.odk.clinic.android.R;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.listeners.SyncDataListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.clinic.android.utilities.MySSLSocketFactory;
import org.odk.clinic.android.utilities.MyTrustManager;
import org.odk.clinic.android.utilities.WebUtils;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

public class SyncDataTask extends AsyncTask<Void, String, String> {

	private static final String TAG = SyncDataTask.class.getSimpleName();
	private static final int CONNECTION_TIMEOUT = 60000;
	static final int MAX_CONN_PER_ROUTE = 20;
	static final int MAX_CONNECTIONS = 20;
	public static final String HTTP_CLIENT_SETUP = "http_client_setup";
	public static final String ERROR = "error";

	protected SyncDataListener mStateListener;
	protected DbAdapter mDb;
	protected String mError;
	protected static boolean mUploadComplete;
	protected static boolean mDownloadComplete;
	protected static HttpClient httpclient;

	private static int mStep = 0;
	private static int mUploadSteps = -1;
	private static int mDownloadSteps = -1;
	private static int mTotalSteps = 30;
	private KeyStore keyStore;
	private KeyStore trustStore;
	private String mStorePassword;

	@Override
	protected String doInBackground(Void... values) {
		mStorePassword = App.getPassword();

		try {

			if (httpclient == null) {
				Log.e(TAG, "httpclient is null, SyncData is creating a new client");
				SSLContext sslContext = createSslContext();
				MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier());
				httpclient = createHttpClient(socketFactory);
			}

			return HTTP_CLIENT_SETUP;

		} catch (GeneralSecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return ERROR;
		}

	}

	protected boolean postEntityToUrl(String url, MultipartEntity entity) {

		try {
			if (httpclient == null) {
				Log.e(TAG, "httpclient is null, upload is creating a new client");
				SSLContext sslContext = createSslContext();
				MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier());
				httpclient = createHttpClient(socketFactory);
			}

			HttpPost httppost = new HttpPost(url);
			httppost.setEntity(entity);
			HttpResponse response = httpclient.execute(httppost);

			int responseCode = response.getStatusLine().getStatusCode();
			Log.d(TAG, "httppost response=" + responseCode);

			// verify response is okay
			if (responseCode == HttpURLConnection.HTTP_OK) {
				return true;
			} else {
				return false;
			}

		} catch (Exception e) {
			Log.e(TAG, "httpclient DID NOT execute httpost! caught an exception!");
			mError = e.toString();
			e.printStackTrace();
			return false;
		}
	}

	// This is all we need for XformsHelper, because all auth is in the url
	protected InputStream getUrlStream(String url) {
		try {
			if (httpclient == null) {
				Log.e(TAG, "httpclient is null, download is creating a new client");
				SSLContext sslContext = createSslContext();
				MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier());
				httpclient = createHttpClient(socketFactory);
			}

			HttpGet get = new HttpGet(url);
			HttpResponse response = httpclient.execute(get);

			if (response.getStatusLine().getStatusCode() != 200) {
				Log.e(TAG, "Error: " + response.getStatusLine());
			} else {
				Log.e(TAG, "NO Error!: " + response.getStatusLine());
			}

			// Get hold of the response entity
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream is = entity.getContent();
				return is;
			}

		} catch (Exception e) {
			e.printStackTrace();
			mError = e.toString();
			return null;
		}

		return null;

	}

	private SSLContext createSslContext() throws GeneralSecurityException {

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
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
			String username = settings.getString(App.getApp().getString(R.string.key_username), App.getApp().getString(R.string.default_username));
			String password = settings.getString(App.getApp().getString(R.string.key_password), App.getApp().getString(R.string.default_password));
			boolean savedSearch = settings.getBoolean(App.getApp().getString(R.string.key_use_saved_searches), false);
			int cohort = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_saved_search), "0"));
			int program = Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_program), "0"));

			DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(outstream));
			dos.writeUTF(username);
			dos.writeUTF(password);
			dos.writeBoolean(savedSearch);
			if (cohort > 0)
				dos.writeInt(cohort);
			if (program > 0)
				dos.writeInt(program);
			dos.close();
		}

	};

	// This is for connecting to ODK Connector
	protected DataInputStream getUrlStreamFromOdkConnectorTest() throws Exception {
		if (httpclient == null) {
			Log.e(TAG, "httpclient is null, download is creating a new client");
			SSLContext sslContext = createSslContext();
			MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier());
			httpclient = createHttpClient(socketFactory);
		}
		// get prefs
		String url = WebUtils.getPatientDownloadUrl();
		HttpPost request = new HttpPost(url);
		request.setEntity(connectorEntity);
		
		HttpResponse response = httpclient.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();
		Log.d(TAG, "httppost response=" + responseCode);
		HttpEntity responseEntity = response.getEntity();
		DataInputStream zdis = new DataInputStream(new GZIPInputStream(responseEntity.getContent()));
		
//		InputStream is = getUrlStream(url);
		// read connection status (wins version...)
//		DataInputStream zdis = new DataInputStream(is);
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

	// /ASYNCTASK UPDATE METHODS
	protected void setUploadSteps(int upload) {
		mUploadSteps = upload;
		if (mDownloadSteps != -1) {
			mTotalSteps = mDownloadSteps + mUploadSteps;
		}
	}

	protected void setDownloadSteps(int download) {
		mDownloadSteps = download;
		if (mUploadSteps != -1) {
			mTotalSteps = mDownloadSteps + mUploadSteps;
		}
	}

	protected void showProgress(String title) {
		mStep++;

		// set the title based on the steps of both upload and download
		String progressTitle = "Connecting to Server";
		if (mDownloadSteps != -1 && mUploadSteps != -1) {
			// if (mStep < mUploadSteps)
			// progressTitle = "Uploading Forms";
			// else
			progressTitle = title;
		}

		String current = Integer.valueOf(mStep).toString();
		String total = Integer.valueOf(mTotalSteps).toString();
		publishProgress(progressTitle, current, total);
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
		synchronized (this) {
			if (mStateListener != null)
				mStateListener.sslSetupComplete(result);
		}
	}

	public void setSyncListener(SyncDataListener sl) {
		synchronized (this) {
			mStateListener = sl;
		}
	}

}

// private void setCredentialProvider(DefaultHttpClient httpClient){
// // set the user credentials for our site "example.com" instead of
// // ANY_HOST
// CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
// SharedPreferences settings =
// PreferenceManager.getDefaultSharedPreferences(App.getApp());
// String username =
// settings.getString(App.getApp().getString(R.string.key_username),
// App.getApp().getString(R.string.default_username));
// String password =
// settings.getString(App.getApp().getString(R.string.key_password),
// App.getApp().getString(R.string.default_password));
// credentialsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST,
// AuthScope.ANY_PORT), new UsernamePasswordCredentials("blah", "blah"));
// httpClient.setCredentialsProvider(credentialsProvider);
// }