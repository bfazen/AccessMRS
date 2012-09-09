package org.odk.clinic.android.activities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.odk.clinic.android.utilities.MyTrustManager;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = MainActivity.class.getSimpleName();

	private static final String CLIENT_AUTH_URL = "https://myserver.com/clientauth";
	private static final String SERVER_AUTH_URL = "https://yahoo.com";

	private static final String TRUSTSTORE_PASSWORD = "abcdef";
	private static final String KEYSTORE_PASSWORD = "abcdef";

	private static final int MAX_CONN_PER_ROUTE = 10;
	private static final int MAX_CONNECTIONS = 20;

	private static final int TIMEOUT = 10 * 1000;
	private CheckBox useClientAuthCb;
	private Button httpClientConnectButton;
	private File localTrustStoreFile;
	private KeyStore keyStore;
	private TextView resultText;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		resultText = (TextView) findViewById(R.id.result_text);
		localTrustStoreFile = new File(getFilesDir(), "mytruststore.bks");
		useClientAuthCb = (CheckBox) findViewById(R.id.use_client_auth_cb);  //Use Client Authentication
		httpClientConnectButton = (Button) findViewById(R.id.http_client_connect_button);
		httpClientConnectButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				httpClientConnect();
			}
		});
		getTrustStore();
	}
	

	private void getTrustStore() {
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				if (localTrustStoreFile.exists()) {
					return null;
				}

				try {
					InputStream in = getResources().openRawResource(R.raw.mytruststore);
					FileOutputStream out = new FileOutputStream(localTrustStoreFile);
					byte[] buff = new byte[1024];
					int read = 0;

					try {
						while ((read = in.read(buff)) > 0) {
							out.write(buff, 0, read);
						}
					} finally {
						in.close();

						out.flush();
						out.close();
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				return null;
			}
		}.execute();
	}

	//Dynamic update of SSLContext and Truststore...
	private void httpClientConnect(){
		new AsyncTask<Void, Void, String>() {
				Exception error;

			@Override
			protected String doInBackground(Void... arg0) {
				try {
					boolean useClientAuth = useClientAuthCb.isChecked();
					SSLContext sslContext = createSslContext(useClientAuth); //Using the httpsUrlConnection?!
					MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier()); //ONLY USE OF MySSLSocketFactory
					HttpClient client = createHttpClient(socketFactory);

					HttpGet get = new HttpGet(useClientAuth ? CLIENT_AUTH_URL : SERVER_AUTH_URL);
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
					Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		}.execute();
		
	}
	
	private SSLContext createSslContext(boolean clientAuth) throws GeneralSecurityException {
		KeyStore trustStore = loadTrustStore();
		KeyStore keyStore = loadKeyStore();

		MyTrustManager myTrustManager = new MyTrustManager(trustStore);  //ONLY USE OF TRUST MANAGER
		TrustManager[] tms = new TrustManager[] { myTrustManager };

		KeyManager[] kms = null;
		if (clientAuth) {
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
			kms = kmf.getKeyManagers();
		}

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(kms, tms, null);

		return context;
	}
	
	private KeyStore loadTrustStore() {
		try {
			KeyStore localTrustStore = KeyStore.getInstance("BKS");
			InputStream in = new FileInputStream(localTrustStoreFile);
			try {
				localTrustStore.load(in, TRUSTSTORE_PASSWORD.toCharArray());
			} finally {
				in.close();
			}

			return localTrustStore;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private KeyStore loadKeyStore() {
		if (keyStore != null) {
			return keyStore;
		}
		
		try {
			keyStore = KeyStore.getInstance("PKCS12");
			InputStream in = getResources().openRawResource(R.raw.mytruststore);
			try {
				keyStore.load(in, KEYSTORE_PASSWORD.toCharArray());
			} finally {
				in.close();
			}

			return keyStore;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private HttpClient createHttpClient(SocketFactory socketFactory) {
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
		schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
		ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

		return new DefaultHttpClient(cm, params);
	}
			
	
}
