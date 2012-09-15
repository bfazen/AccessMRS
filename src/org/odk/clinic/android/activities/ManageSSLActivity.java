package org.odk.clinic.android.activities;

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

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.odk.clinic.android.R;
import org.odk.clinic.android.openmrs.Certificate;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author (Nikolay Elenkov) only imported here by Louis.Fazen@gmail.com // CODE
 *         Taken, more or less wholesale from: Nikolay Elenkov!
 *         https://github.com/nelenkov/custom-cert-https
 */

public abstract class ManageSSLActivity extends ListActivity {
	protected Context mContext;
	protected File mLocalStoreFile;
	
	//OVERRIDE THE FOLLOWING/ OR SWITCH DYNAMICALLY
	//TODO! what to do about password?
	protected String TAG;
	protected String STORE_PASSWORD; 
	protected String mLocalStoreFileName;
	protected int mLocalStoreResourceId;
	protected String mStoreString;
	protected String mStoreTitleString;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		mLocalStoreFile = new File(getFilesDir(), mLocalStoreFileName);
		setContentView(R.layout.add_certificates);
		TextView title = (TextView) findViewById(R.id.store_title);
		title.setText(String.format(getString(R.string.ssl_store_title), mStoreTitleString));
		mContext = this;
		setProgressBarIndeterminateVisibility(false);
		
		getTrustStore();
		showStoreItems();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Object o = l.getItemAtPosition(position);
		if (o instanceof Certificate) {
			showRemoveDialog((Certificate) o);
		}

	}

	private void getTrustStore() {
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				if (mLocalStoreFile.exists()) {
					return null;
				}

				try {
					InputStream in = getResources().openRawResource(mLocalStoreResourceId);
					FileOutputStream out = new FileOutputStream(mLocalStoreFile);
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

	private KeyStore loadTrustStore() {
		try {
			KeyStore localTrustStore = KeyStore.getInstance("BKS");
			InputStream in = new FileInputStream(mLocalStoreFile);
			try {
				localTrustStore.load(in, STORE_PASSWORD.toCharArray());
			} finally {
				in.close();
			}

			return localTrustStore;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void addFromExternalStroage() {
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

	private void removeCertificate(String deleteAlias) {
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

	protected abstract void showStoreItems();

	protected View buildSectionLabel(int layoutId, String label) {
		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = vi.inflate(layoutId, null);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		textView.setText(label);
		v.setBackgroundResource(R.color.medium_gray);
		if (layoutId == R.layout.section_label) {
			ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);
			sectionImage.setVisibility(View.GONE);
		} else {
			Button addBtn = (Button) v.findViewById(R.id.add_cert);
			addBtn.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					showAddDialog();
				}
			});
		}
		return (v);
	}

	private void showAddDialog() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(String.format(getString(R.string.ssl_add_alert_title), mStoreTitleString));
		alert.setIcon(android.R.drawable.ic_dialog_info);
		alert.setMessage(String.format(getString(R.string.ssl_add_alert_body), mStoreString,  mStoreString));
		alert.setPositiveButton("Add", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				addFromExternalStroage();
				dialog.cancel();
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}

	private void showRemoveDialog(final Certificate cert) {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(String.format(getString(R.string.ssl_remove_alert_title), mStoreTitleString));
		alert.setIcon(android.R.drawable.ic_dialog_info);
		alert.setMessage(String.format(getString(R.string.ssl_remove_alert_body), mStoreString) + cert.getO());
		alert.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				removeCertificate(cert.getAlias());
				dialog.cancel();
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}

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

	private void refreshView() {
		findViewById(android.R.id.content).invalidate();
		showStoreItems();
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
			localTrustStore.store(out, STORE_PASSWORD.toCharArray());
		}

		@Override
		protected void onPostExecute(Integer result) {
			setProgressBarIndeterminateVisibility(false);
			refreshView();

			if (result != null) {
				if (result < 1) {
					Toast.makeText(ManageSSLActivity.this, String.format(getAlertMessage(), mStoreString, mStoreString), Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(ManageSSLActivity.this, String.format(getSuccessMessage(), result, mStoreString, mStoreString), Toast.LENGTH_LONG).show();
				}
			} else {
				Toast.makeText(ManageSSLActivity.this, String.format(getString(R.string.ssl_error_message), mStoreString) + error.getMessage(), Toast.LENGTH_LONG).show();
			}

		}
	}

}
