package com.alphabetbloc.accessmrs.ui.admin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import com.alphabetbloc.accessmrs.adapters.CertificateAdapter;
import com.alphabetbloc.accessmrs.adapters.MergeAdapter;
import com.alphabetbloc.accessmrs.data.Certificate;
import com.alphabetbloc.accessmrs.utilities.EncryptionUtil;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.R;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author (Much code here is derived from Nikolay Elenkov
 *         https://github.com/nelenkov/custom-cert-https)
 */

public class SSLAddCertificatesActivity extends SSLBaseActivity {
	private Context mContext;
	private String TAG = SSLAddCertificatesActivity.class.getSimpleName();
	private File mLocalStoreFile;
	private String mStorePassword;
	private String trustStorePropDefault;
	private String mLocalStoreFileName;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.add_certificates);
		mContext = this;
		
		trustStorePropDefault = System.getProperty("javax.net.ssl.trustStore"); // System
		mStorePassword = EncryptionUtil.getPassword();
		mLocalStoreFileName = FileUtils.MY_TRUSTSTORE;
		setStoreString("certificate");
		setStoreTitleString("Certificate");
		setImportFormat("DER");
		mLocalStoreFile = new File(getFilesDir(), mLocalStoreFileName);
		if (!mLocalStoreFile.exists())
			FileUtils.setupDefaultSslStore(mLocalStoreFileName);

		TextView title = (TextView) findViewById(R.id.store_title);
		title.setText(String.format(getString(R.string.ssl_store_title), getStoreTitleString()));
		setProgressBarIndeterminateVisibility(false);
		showStoreItems();
	}

	@Override
	protected void onPause() {
		if (trustStorePropDefault != null)
			System.setProperty("javax.net.ssl.trustStore", trustStorePropDefault);
		else
			System.clearProperty("javax.net.ssl.trustStore");
		super.onPause();
	}

	@Override
	protected void showStoreItems() {

		new AsyncTask<Void, Void, Void>() {
			private MergeAdapter mAdapter;
			Comparator<Certificate> certComparator = new Comparator<Certificate>() {
				@Override
				public int compare(Certificate cert1, Certificate cert2) {
					return String.valueOf(cert1.getO()).compareTo(cert2.getO());
				}
			};

			@Override
			protected Void doInBackground(Void... params) {
				// Local Certs:
				mAdapter = new MergeAdapter();
				System.setProperty("javax.net.ssl.trustStore", mLocalStoreFile.getAbsolutePath());
				CertificateAdapter localAdapter = new CertificateAdapter(mContext, R.layout.certificate_item, getStoreFiles(), true);
				mAdapter.addView(buildSectionLabel(R.layout.certificate_label, "Local Certificates"));
				mAdapter.addAdapter(localAdapter);

				// Android System Certs:
				if (trustStorePropDefault != null)
					System.setProperty("javax.net.ssl.trustStore", trustStorePropDefault);
				else
					System.clearProperty("javax.net.ssl.trustStore");

				ArrayList<Certificate> systemCerts = getStoreFiles();
				Collections.sort(systemCerts, certComparator);
				CertificateAdapter systemAdapter = new CertificateAdapter(mContext, R.layout.certificate_item, systemCerts, false);
				mAdapter.addView(buildSectionLabel(R.layout.section_label, "Android System Certificates"));
				mAdapter.addAdapter(systemAdapter);

				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				setListAdapter(mAdapter);
			}

		}.execute();

	}

	@Override
	protected void addFromExternalStroage() {
		new ManipulateTrustStoreTask() {

			@Override
			protected int manipulate() throws GeneralSecurityException, IOException {
				String[] certs = listCertificateFiles();
				KeyStore localTrustStore = loadTrustStore();

				int certsAdded = 0;
				for (String certFilename : certs) {
					File certFile = new File(Environment.getExternalStorageDirectory(), certFilename);
					X509Certificate cert = readCertificate(certFile);
					String alias = hashName(cert.getSubjectX500Principal());
					localTrustStore.setCertificateEntry(alias, cert);
					certsAdded++;

					// dont need to delete the public certs (vs keys)
				}

				saveTrustStore(localTrustStore);

				return certsAdded;
			}

			@Override
			protected String getSuccessMessage() {
				return getString(R.string.ssl_add_success_message);
			}

			@Override
			protected String getAlertMessage() {
				return getString(R.string.ssl_add_alert_message);
			}
		}.execute();
	}

	private static String[] listCertificateFiles() {
		File externalStorage = Environment.getExternalStorageDirectory();
		FilenameFilter ff = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				if (filename.contains(".")) {
					String[] filenameExt = filename.split("\\.");
					String ext = filenameExt[filenameExt.length - 1].toLowerCase();
					if (ext.equals("cer") || ext.equals("der")) {
						return true;
					}
				}

				return false;
			}
		};

		return externalStorage.list(ff);
	}

	@Override
	protected void remove(String deleteAlias) {
		final String alias = deleteAlias;
		new ManipulateTrustStoreTask() {

			@Override
			protected int manipulate() throws GeneralSecurityException, IOException {
				KeyStore localTrustStore = loadTrustStore();
				int certsRemoved = 0;
				localTrustStore.deleteEntry(alias);
				certsRemoved++;
				saveTrustStore(localTrustStore);
				return certsRemoved;
			}

			@Override
			protected String getSuccessMessage() {
				return getString(R.string.ssl_remove_success_message);
			}

			@Override
			protected String getAlertMessage() {
				return getString(R.string.ssl_remove_alert_message);
			}

		}.execute();
	}

	protected KeyStore loadTrustStore() {
		if (!mLocalStoreFile.exists())
			FileUtils.setupDefaultSslStore(mLocalStoreFileName);

		try {
			Log.v(TAG, "localStoreFilePath=" + mLocalStoreFile.getAbsolutePath());
			KeyStore localTrustStore = KeyStore.getInstance("BKS");
			InputStream in = new FileInputStream(mLocalStoreFile);

			if (in != null)
				Log.v(TAG, "in is NOT NULL");
			if (localTrustStore == null)
				Log.e(TAG, "localTrustStore is NULL");
			if (mStorePassword == null)
				Log.e(TAG, "mStorePassword is NULL=");
			try {
				localTrustStore.load(in, mStorePassword.toCharArray());
			} finally {
				in.close();
			}

			return localTrustStore;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected ArrayList<Certificate> getStoreFiles() {

		ArrayList<Certificate> androidCerts = new ArrayList<Certificate>();

		try {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init((KeyStore) null);
			X509TrustManager xtm = (X509TrustManager) tmf.getTrustManagers()[0];

			Certificate c;
			for (X509Certificate cert : xtm.getAcceptedIssuers()) {
				c = new Certificate();

				c.setAlias(hashName(cert.getSubjectX500Principal()));

				String certS = cert.getSubjectDN().getName();
				c.setO(getSubString(certS, "O="));
				c.setOU(getSubString(certS, "OU="));
				c.setE(getSubString(certS, "E="));
				c.setL(getSubString(certS, "L="));
				c.setST(getSubString(certS, "ST="));
				c.setC(getSubString(certS, "C="));
				c.setCN(getSubString(certS, "CN="));

				String certI = cert.getIssuerDN().getName();
				c.setIO(getSubString(certI, "O="));

				androidCerts.add(c);
			}

		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}

		return androidCerts;
	}

	private static X509Certificate readCertificate(File file) {
		if (!file.isFile()) {
			return null;
		}

		InputStream is = null;
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X509");
			is = new BufferedInputStream(new FileInputStream(file));
			return (X509Certificate) cf.generateCertificate(is);
		} catch (IOException e) {
			return null;
		} catch (CertificateException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
			}
		}
	}

	private static String hashName(X500Principal principal) {
		try {
			byte[] digest = MessageDigest.getInstance("MD5").digest(principal.getEncoded());

			String result = Integer.toString(leInt(digest), 16);
			if (result.length() > 8) {
				StringBuffer buff = new StringBuffer();
				int padding = 8 - result.length();
				for (int i = 0; i < padding; i++) {
					buff.append("0");
				}
				buff.append(result);

				return buff.toString();
			}

			return result;
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	private static int leInt(byte[] bytes) {
		int offset = 0;
		return ((bytes[offset++] & 0xff) << 0) | ((bytes[offset++] & 0xff) << 8) | ((bytes[offset++] & 0xff) << 16) | ((bytes[offset] & 0xff) << 24);
	}

	abstract class ManipulateTrustStoreTask extends AsyncTask<Void, Void, Integer> {

		Exception error;

		@Override
		protected void onPreExecute() {
			setProgressBarIndeterminateVisibility(true);

		}

		@Override
		protected Integer doInBackground(Void... params) {
			try {
				return manipulate();
			} catch (GeneralSecurityException e) {
				Log.e(TAG, "Security Error: " + e.getMessage(), e);
				error = e;

				return null;
			} catch (IOException e) {
				Log.e(TAG, "I/O Error: " + e.getMessage(), e);
				error = e;

				return null;
			}
		}

		protected abstract int manipulate() throws GeneralSecurityException, IOException;

		protected abstract String getSuccessMessage();

		protected abstract String getAlertMessage();

		protected void saveTrustStore(KeyStore localTrustStore) throws IOException, GeneralSecurityException {
			FileOutputStream out = new FileOutputStream(mLocalStoreFile);
			localTrustStore.store(out, mStorePassword.toCharArray());
		}

		@Override
		protected void onPostExecute(Integer result) {
			setProgressBarIndeterminateVisibility(false);
			refreshView();
			String storeString = getStoreString();
			if (result != null) {
				if (result < 1) {
					Toast.makeText(SSLAddCertificatesActivity.this, String.format(getAlertMessage(), storeString, storeString), Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(SSLAddCertificatesActivity.this, String.format(getSuccessMessage(), result, storeString,storeString), Toast.LENGTH_LONG).show();
				}
			} else {
				Toast.makeText(SSLAddCertificatesActivity.this, String.format(getString(R.string.ssl_error_message), storeString) + error.getMessage(), Toast.LENGTH_LONG).show();
			}

		}
	}

	private static String getSubString(String string, String prefix) {
		String substring = null;

		if (string.contains(prefix)) {
			int offset = prefix.length();
			int start = string.indexOf(prefix) + offset;
			int end = string.indexOf(",", start);
			if (end > 0)
				substring = string.substring(start, end);
			else
				substring = string.substring(start);
		}

		return substring;
	}

}
