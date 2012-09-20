package org.odk.clinic.android.activities;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
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
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.odk.clinic.android.R;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.clinic.android.utilities.MySSLSocketFactory;
import org.odk.clinic.android.utilities.MyTrustManager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class TestSslConnectionActivity extends Activity {

	private static final String TAG = TestSslConnectionActivity.class.getSimpleName();
	private static final int MAX_CONN_PER_ROUTE = 10;
	private static final int MAX_CONNECTIONS = 20;
	private static final int TIMEOUT = 10 * 1000;
	private Context mContext;;
	private KeyStore keyStore;
	private KeyStore trustStore;
	protected String mStorePassword;

	private TextView resultText;
	private EditText editText;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test_ssl);
		mContext = this;
		resultText = (TextView) findViewById(R.id.result_text);
		editText = (EditText) findViewById(R.id.edit_text);
		editText.setText(getString(R.string.default_server));
		Button httpClientConnectButton = (Button) findViewById(R.id.http_client_connect_button);
		httpClientConnectButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String userEntry = editText.getText().toString();
				httpClientConnect(userEntry);
			}
		});
	}

	// Dynamic update of SSLContext and Truststore...
	private void httpClientConnect(final String userEntry) {
		new AsyncTask<Void, Void, String>() {
			Exception error;

			@Override
			protected String doInBackground(Void... arg0) {
				try {
					mStorePassword = App.getPassword();

					// 1. get a new ssl context...
					SSLContext sslContext = createSslContext();
					MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier());

					// 2. create HttpClient
					HttpClient client = createHttpClient(socketFactory);
					HttpGet get = new HttpGet(userEntry);
					HttpResponse response = client.execute(get);
					if (response.getStatusLine().getStatusCode() != 200) {
						return "Error: " + response.getStatusLine();
					} else {
						return EntityUtils.toString(response.getEntity());
					}
				} catch (Exception e) {
					Log.d(TAG, "Error: " + e.getMessage(), e);

					error = e;
					return null;
				}
			}

			@Override
			protected void onPostExecute(String result) {
				setProgressBarIndeterminateVisibility(false);

				if (result != null) {
					resultText.setText(Html.fromHtml(result));
				} else {
					Toast.makeText(TestSslConnectionActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		}.execute();

	}

	private HttpClient createHttpClient(SocketFactory socketFactory) {
		///
		java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
		java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);

		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "debug");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "debug");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "debug");
		///
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
		HttpConnectionParams.setConnectionTimeout(params, TIMEOUT);
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

		return new DefaultHttpClient(cm, params);
	}

	/**
	 * make the SSLContext and initialize with the local keyStore (ifClientAuth)
	 * and the combined default and local trustStore via custom MyTrustManager.
	 * I.e. context now initialized with a local key, and both local and default
	 * trust stores.
	 * 
	 * @param clientAuth
	 * @return
	 * @throws GeneralSecurityException
	 */
	private SSLContext createSslContext() throws GeneralSecurityException {

		//TrustStore
		KeyStore trustStore = loadTrustStore();
		MyTrustManager myTrustManager = new MyTrustManager(trustStore);
		TrustManager[] tms = new TrustManager[] { myTrustManager };

		//KeyStore
		KeyManager[] kms = null;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		boolean useClientAuth = prefs.getBoolean(getString(R.string.key_client_auth), false);
		if (useClientAuth){
			KeyStore keyStore = loadKeyStore();
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(keyStore, mStorePassword.toCharArray());
			kms = kmf.getKeyManagers();
		}

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(kms, tms, null);

		return context;
	}

	/**
	 * Load the truststoreFile field (after import from the file itself) into a
	 * KeyStore object.
	 * 
	 * @return the KeyStore object of the localTrustStore
	 */
	private KeyStore loadTrustStore() {
		if (trustStore != null) {
			return trustStore;
		}

		File localTrustStoreFile = new File(getFilesDir(), FileUtils.MY_TRUSTSTORE);
		try {
			trustStore = KeyStore.getInstance("BKS");
			InputStream in = new FileInputStream(localTrustStoreFile);
			try {
				Log.e(TAG, "loading truststore with truststore =" + trustStore);
				Log.e(TAG, "loading truststore with in=" + in);
				Log.e(TAG, "loading truststore with mStorePassword=" + mStorePassword);
				trustStore.load(in, "***REMOVED***".toCharArray());
			} finally {
				in.close();
			}

			return trustStore;
		} catch (Exception e) {
			Log.e(TAG, "loading truststore error!");
			e.printStackTrace();
			throw new RuntimeException(e);
			
		}
	}

	/**
	 * This goes direct from the local KeyStore Resource into a keyStore without
	 * asynctask, because we just hold onto this one, and don't ned to do
	 * dynamic switching between truststores?
	 * 
	 * @return
	 */
	private KeyStore loadKeyStore() {
		if (keyStore != null) {
			return keyStore;
		}

		File localKeyStoreFile = new File(getFilesDir(), FileUtils.MY_KEYSTORE);
		try {
			keyStore = KeyStore.getInstance("PKCS12");
			// keyStore = KeyStore.getInstance("PKCS12");
			InputStream in = new FileInputStream(localKeyStoreFile);
			try {
				keyStore.load(in, mStorePassword.toCharArray());
			} finally {
				in.close();
			}

			return keyStore;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
