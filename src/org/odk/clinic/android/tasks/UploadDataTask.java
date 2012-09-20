package org.odk.clinic.android.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.http.Header;
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
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.MySSLSocketFactory;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.clinic.android.utilities.MyTrustManager;
import org.odk.clinic.android.utilities.WebUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.clinic.services.EncryptionService;
import com.commonsware.cwac.wakeful.WakefulIntentService;

public class UploadDataTask extends AsyncTask<Void, String, String> {
	private static String tag = "UploadDataTask";

	private static final int CONNECTION_TIMEOUT = 60000;
	static final int MAX_CONN_PER_ROUTE = 20;
	static final int MAX_CONNECTIONS = 20;
	
	protected UploadFormListener mStateListener;
	private int mTotalCount = -1;
	private String[] mInstancesToUpload;
	private KeyStore keyStore;
	private KeyStore trustStore;
	protected String mStorePassword;

	@Override
	protected String doInBackground(Void... values) {
		mStorePassword = App.getPassword();
		String uploadResult = "No Completed Forms to Upload";
		int resultSize = 0;
		if (dataToUpload()) {

			// TODO! CHECK does this verify uploaded?
			// Encrypt all the instances successfully uploaded
			String s = WebUtils.getFormUploadUrl();
			Log.i(tag, "url to use=" + s);
			ArrayList<String> uploaded = uploadInstances(s);
			if (!uploaded.isEmpty() && uploaded.size() > 0) {
				// update the databases with new status submitted
				for (int i = 0; i < uploaded.size(); i++) {
					String path = uploaded.get(i);
					updateClinicDbPath(path);
					updateCollectDb(path);
				}
			}

			// return a toast message
			resultSize = uploaded.size();
			if (resultSize == mTotalCount)
				uploadResult = App.getApp().getString(R.string.upload_all_successful, resultSize);
			else
				uploadResult = App.getApp().getString(R.string.upload_some_failed, (mTotalCount - resultSize) + " of " + mTotalCount);
		}

		// Encrypt the uploaded data with wakelock to ensure it happens!
		if (resultSize > 0)
			WakefulIntentService.sendWakefulWork(App.getApp(), EncryptionService.class);

		return uploadResult;
	}

	public boolean dataToUpload() {

		boolean dataToUpload = true;
		ArrayList<String> selectedInstances = new ArrayList<String>();

		Cursor c = DbAdapter.openDb().fetchFormInstancesByStatus(DbAdapter.STATUS_UNSUBMITTED);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String dbPath = c.getString(c.getColumnIndex(DbAdapter.KEY_PATH));
					selectedInstances.add(dbPath);
				} while (c.moveToNext());
			}
			c.close();
		}

		if (!selectedInstances.isEmpty()) {
			mTotalCount = selectedInstances.size();
			if (mTotalCount < 1)
				dataToUpload = false;
			else
				mInstancesToUpload = selectedInstances.toArray(new String[mTotalCount]);
		} else
			dataToUpload = false;

		Log.i(tag, "Datatoupload= " + dataToUpload + " Number of Instances= " + mTotalCount);
		return dataToUpload;
	}

	private ArrayList<String> uploadInstances(String url) {
		// TODO! if truststore doesn't exist... initialize them... 
		// FileUtils.setupDefaultSslStore(R.raw.mytruststore);
		// FileUtils.setupDefaultSslStore(R.raw.mykeystore);
		
		
		// 1. get a new ssl context...
		SSLContext sslContext = null;
		try {
			sslContext = createSslContext();
		} catch (GeneralSecurityException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		MySSLSocketFactory socketFactory = new MySSLSocketFactory(sslContext, new BrowserCompatHostnameVerifier());

		ArrayList<String> uploadedInstances = new ArrayList<String>();
		
		for (int i = 0; i < mTotalCount; i++) {
			// TODO! Should this really be inside the for loop? should this also
			// be a DefaultClient? Sam's code?
			HttpClient httpclient = createHttpClient(socketFactory);
			try {

				HttpPost httppost = new HttpPost(url);
				String dbPath = mInstancesToUpload[i];
				String instancePath = FileUtils.getDecryptedFilePath(dbPath);
				MultipartEntity entity = createMultipartEntity(instancePath);
				if (entity == null) {
					continue;
				}

				httppost.setEntity(entity);
				HttpResponse response = null;
				response = httpclient.execute(httppost);

				String serverLocation = null;
				Header[] h = response.getHeaders("Location");
				if (h != null && h.length > 0) {
					serverLocation = h[0].getValue();
					Log.e(tag, "server location = " + serverLocation);
				} else {
					// something should be done here...
					Log.e(tag, "Location header was absent");
				}

				int responseCode = response.getStatusLine().getStatusCode();
				Log.d(tag, "httppost response=" + responseCode + " executed on path=" + instancePath);

				// verify that your response came from a known server
				if (serverLocation != null && url.contains(serverLocation)) {
					uploadedInstances.add(mInstancesToUpload[i]);
					Log.e(tag, "everything okay! adding some instances...");
				}

			} catch (Exception e) {
				Log.e(tag, "httpclient DID NOT execute httpost! caught an exception!");
				e.printStackTrace();
			}

		}
		return uploadedInstances;
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

		// TrustStore
		KeyStore trustStore = loadTrustStore();
		MyTrustManager myTrustManager = new MyTrustManager(trustStore);
		TrustManager[] tms = new TrustManager[] { myTrustManager };

		// KeyStore
		KeyManager[] kms = null;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		boolean useClientAuth = prefs.getBoolean(App.getApp().getString(R.string.key_client_auth), false);
		if (useClientAuth) {
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

		File localTrustStoreFile = new File(App.getApp().getFilesDir(), FileUtils.MY_TRUSTSTORE);
		if (!localTrustStoreFile.exists()) {
			Log.e(tag, "truststore does not exist... loading it from res raw!");
			localTrustStoreFile = FileUtils.setupDefaultSslStore(R.raw.mytruststore);
		}

		try {
			trustStore = KeyStore.getInstance("BKS");
			InputStream in = new FileInputStream(localTrustStoreFile);
			try {
				trustStore.load(in, mStorePassword.toCharArray());
			} finally {
				in.close();
			}

			return trustStore;
		} catch (Exception e) {
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

		File localKeyStoreFile = new File(App.getApp().getFilesDir(), FileUtils.MY_KEYSTORE);
		if (!localKeyStoreFile.exists()) {
			localKeyStoreFile = FileUtils.setupDefaultSslStore(R.raw.mykeystore);
		}
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

	private HttpClient createHttpClient(SocketFactory socketFactory) {
		// //

		// CredentialsProvider credentialsProvider = new
		// BasicCredentialsProvider();
		// //set the user credentials for our site "example.com"
		// credentialsProvider.setCredentials(new AuthScope("example.com",
		// AuthScope.ANY_PORT),
		// new UsernamePasswordCredentials("UserNameHere", "UserPasswordHere"));
		// clientConnectionManager = new ThreadSafeClientConnManager(params,
		// schemeRegistry);
		//
		// context = new BasicHttpContext();
		// context.setAttribute("http.auth.credentials-provider",
		// credentialsProvider);
		//
		// /

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

		return new DefaultHttpClient(cm, params);
	}

	private MultipartEntity createMultipartEntity(String path) {

		// find all files in parent directory
		File file = new File(path);
		File[] files = file.getParentFile().listFiles();
		System.out.println(file.getAbsolutePath());

		// mime post
		MultipartEntity entity = null;
		if (files != null) {
			entity = new MultipartEntity();
			for (int j = 0; j < files.length; j++) {
				File f = files[j];
				FileBody fb;
				if (f.getName().endsWith(".xml")) {
					fb = new FileBody(f, "text/xml");
					entity.addPart("xml_submission_file", fb);
					Log.i(tag, "added xml file " + f.getName());
				} else if (f.getName().endsWith(".jpg")) {
					fb = new FileBody(f, "image/jpeg");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added image file " + f.getName());
				} else if (f.getName().endsWith(".3gpp")) {
					fb = new FileBody(f, "audio/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added audio file " + f.getName());
				} else if (f.getName().endsWith(".3gp")) {
					fb = new FileBody(f, "video/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added video file " + f.getName());
				} else if (f.getName().endsWith(".mp4")) {
					fb = new FileBody(f, "video/mp4");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added video file " + f.getName());
				} else {
					Log.w(tag, "unsupported file type, not adding file: " + f.getName());
				}
			}
		} else {
			Log.e(tag, "no files to upload in instance");
		}

		return entity;
	}

	private boolean updateCollectDb(String path) {
		boolean updated = false;
		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
			String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
			String whereArgs[] = { path };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				Log.e(tag, "Error! updated more than one entry when tyring to update: " + path.toString());
			} else if (updatedrows == 1) {
				Log.i(tag, "Instance successfully updated to Submitted Status");
				updated = true;
			} else {
				Log.e(tag, "Error, Instance doesn't exist but we have its path!! " + path.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return updated;
	}

	private void updateClinicDbPath(String path) {
		// TODO! WHAT HAPPENED HERE? we should simply be deleting these in the
		// FormInstances Table, not updating them, no?
		Cursor c = DbAdapter.openDb().fetchFormInstancesByPath(path);
		if (c != null) {
			DbAdapter.openDb().updateFormInstance(path, DbAdapter.STATUS_SUBMITTED);
			c.close();
		}
	}

	@Override
	protected void onProgressUpdate(String... values) {
		Log.e(tag, "UploadInstanceTask.onProgressUpdate=" + values[0] + ", " + values[1] + ", " + values[2] + ", ");
		synchronized (this) {
			if (mStateListener != null)
				mStateListener.progressUpdate(values[0], Integer.valueOf(values[1]), Integer.valueOf(values[2]));
		}

	}

	@Override
	protected void onPostExecute(String result) {
		synchronized (this) {
			if (mStateListener != null)
				mStateListener.uploadComplete(result);
		}
	}

	public void setUploadListener(UploadFormListener sl) {
		synchronized (this) {
			mStateListener = sl;
		}
	}
}
