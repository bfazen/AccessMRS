package org.odk.clinic.android.activities;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.CertificateAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;

import android.os.AsyncTask;
import android.os.Bundle;

/**
 * @author (Nikolay Elenkov) only imported here by Louis.Fazen@gmail.com // CODE
 *         Taken, more or less wholesale from: Nikolay Elenkov!
 *         https://github.com/nelenkov/custom-cert-https
 */

public class AddCertificatesActivity extends ManageSSLActivity {

	private static String trustStorePropDefault;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		TAG = AddCertificatesActivity.class.getSimpleName();	
		STORE_PASSWORD = "abcdef"; 
		mLocalStoreFileName = "mytruststore.bks";
		mLocalStoreResourceId = R.raw.mytruststore;
		mStoreString = "certificate";
		mStoreTitleString = "Certificate";
		trustStorePropDefault = System.getProperty("javax.net.ssl.trustStore");
		super.onCreate(savedInstanceState);
	}
	
	// this?
	@Override
	protected void showStoreItems() {
		new AsyncTask<Void, Void, Void>() {
			private MergeAdapter mAdapter;

			@Override
			protected Void doInBackground(Void... params) {
				// Local Certs:
				mAdapter = new MergeAdapter();
				System.setProperty("javax.net.ssl.trustStore", mLocalStoreFile.getAbsolutePath());
				CertificateAdapter localAdapter = new CertificateAdapter(mContext, R.layout.certificate_item, getStoreFiles(), true);
				mAdapter.addView(buildSectionLabel(R.layout.certificate_label, "Local Certificates"));
				mAdapter.addAdapter(localAdapter);

				// Android System Certs:
				if (trustStorePropDefault != null) {
					System.setProperty("javax.net.ssl.trustStore", trustStorePropDefault);
				} else {
					System.clearProperty("javax.net.ssl.trustStore");
				}
				CertificateAdapter systemAdapter = new CertificateAdapter(mContext, R.layout.certificate_item, getStoreFiles(), false);
				mAdapter.addView(buildSectionLabel(R.layout.section_label, "Android System Certificates"));
				mAdapter.addAdapter(systemAdapter);

				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				setListAdapter(mAdapter); // TODO! check on this!
			}

		}.execute();

		
	}
	
	
	
//	private File localTrustStoreFile;
//	private static String trustStorePropDefault;
//	private Context mContext;
//
//	@Override
//	protected void onCreate(Bundle savedInstanceState) {
//		super.onCreate(savedInstanceState);
//		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
//		localTrustStoreFile = new File(getFilesDir(), "mytruststore.bks");
//		setContentView(R.layout.add_certificates);
//		mContext = this;
//		setProgressBarIndeterminateVisibility(false);
//		trustStorePropDefault = System.getProperty("javax.net.ssl.trustStore");
//
//		getTrustStore();
//		setAndShowAllCerts();
//	}
//
//	@Override
//	protected void onListItemClick(ListView l, View v, int position, long id) {
//		super.onListItemClick(l, v, position, id);
//		Object o = l.getItemAtPosition(position);
//		if (o instanceof Certificate) {
//			showRemoveCertDialog((Certificate) o);
//		}
//
//	}
//
//	private void getTrustStore() {
//		new AsyncTask<Void, Void, Void>() {
//
//			@Override
//			protected Void doInBackground(Void... params) {
//				if (localTrustStoreFile.exists()) {
//					return null;
//				}
//
//				try {
//					InputStream in = getResources().openRawResource(R.raw.mytruststore);
//					FileOutputStream out = new FileOutputStream(localTrustStoreFile);
//					byte[] buff = new byte[1024];
//					int read = 0;
//
//					try {
//						while ((read = in.read(buff)) > 0) {
//							out.write(buff, 0, read);
//						}
//					} finally {
//						in.close();
//
//						out.flush();
//						out.close();
//					}
//				} catch (IOException e) {
//					throw new RuntimeException(e);
//				}
//
//				return null;
//			}
//		}.execute();
//
//	}
//
//	private KeyStore loadTrustStore() {
//		try {
//			KeyStore localTrustStore = KeyStore.getInstance("BKS");
//			InputStream in = new FileInputStream(localTrustStoreFile);
//			try {
//				localTrustStore.load(in, TRUSTSTORE_PASSWORD.toCharArray());
//			} finally {
//				in.close();
//			}
//
//			return localTrustStore;
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
//	}
//
//	private void addCertifciatesFromExternalStroage() {
//		new ManipulateTrustStoreTask() {
//
//			@Override
//			protected int manipulate() throws GeneralSecurityException, IOException {
//				String[] certs = listCertificateFiles();
//				KeyStore localTrustStore = loadTrustStore();
//
//				int certsAdded = 0;
//				for (String certFilename : certs) {
//					File certFile = new File(Environment.getExternalStorageDirectory(), certFilename);
//					X509Certificate cert = readCertificate(certFile);
//					String alias = hashName(cert.getSubjectX500Principal());
//					localTrustStore.setCertificateEntry(alias, cert);
//					certsAdded++;
//				}
//
//				saveTrustStore(localTrustStore);
//
//				return certsAdded;
//			}
//
//			@Override
//			protected String getSuccessMessage() {
//				return "Added %d certificate(s) to local trust store.";
//			}
//
//			@Override
//			protected String getAlertMessage() {
//				return "No certificates found.  Please place a certificate in DER format on the SD Card.";
//			}
//		}.execute();
//	}
//
//	private static String[] listCertificateFiles() {
//		File externalStorage = Environment.getExternalStorageDirectory();
//		FilenameFilter ff = new FilenameFilter() {
//
//			@Override
//			public boolean accept(File dir, String filename) {
//				if (filename.contains(".")) {
//					String[] filenameExt = filename.split("\\.");
//					String ext = filenameExt[filenameExt.length - 1].toLowerCase();
//					if (ext.equals("cer") || ext.equals("der")) {
//						return true;
//					}
//				}
//
//				return false;
//			}
//		};
//
//		return externalStorage.list(ff);
//	}
//
//	private void removeCertificate(String deleteAlias) {
//		final String alias = deleteAlias;
//		new ManipulateTrustStoreTask() {
//
//			@Override
//			protected int manipulate() throws GeneralSecurityException, IOException {
//				KeyStore localTrustStore = loadTrustStore();
//				int certsRemoved = 0;
//				localTrustStore.deleteEntry(alias);
//				certsRemoved++;
//				saveTrustStore(localTrustStore);
//				return certsRemoved;
//			}
//
//			@Override
//			protected String getSuccessMessage() {
//				return "Removed %d certificate(s) from local trust store.";
//			}
//
//			@Override
//			protected String getAlertMessage() {
//				return "There was a problem removing this certificate.";
//			}
//
//		}.execute();
//	}
//
//	private void setAndShowAllCerts() {
//		new AsyncTask<Void, Void, Void>() {
//			private MergeAdapter mAdapter;
//
//			@Override
//			protected Void doInBackground(Void... params) {
//				// Local Certs:
//				mAdapter = new MergeAdapter();
//				System.setProperty("javax.net.ssl.trustStore", localTrustStoreFile.getAbsolutePath());
//				CertificateAdapter localAdapter = new CertificateAdapter(mContext, R.layout.certificate_item, getCertificateFiles(), true);
//				mAdapter.addView(buildSectionLabel(R.layout.certificate_label, "Local Certificates"));
//				mAdapter.addAdapter(localAdapter);
//
//				// Android System Certs:
//				if (trustStorePropDefault != null) {
//					System.setProperty("javax.net.ssl.trustStore", trustStorePropDefault);
//				} else {
//					System.clearProperty("javax.net.ssl.trustStore");
//				}
//				CertificateAdapter systemAdapter = new CertificateAdapter(mContext, R.layout.certificate_item, getCertificateFiles(), false);
//				mAdapter.addView(buildSectionLabel(R.layout.section_label, "Android System Certificates"));
//				mAdapter.addAdapter(systemAdapter);
//
//				return null;
//			}
//
//			@Override
//			protected void onPostExecute(Void result) {
//				super.onPostExecute(result);
//				setListAdapter(mAdapter); // TODO! check on this!
//			}
//
//		}.execute();
//
//	}
//
//	protected View buildSectionLabel(int layoutId, String label) {
//		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//		View v = vi.inflate(layoutId, null);
//		TextView textView = (TextView) v.findViewById(R.id.name_text);
//		textView.setText(label);
//		v.setBackgroundResource(R.color.medium_gray);
//		if (layoutId == R.layout.section_label) {
//			ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);
//			sectionImage.setVisibility(View.GONE);
//		} else {
//			Button addBtn = (Button) v.findViewById(R.id.add_cert);
//			addBtn.setOnClickListener(new OnClickListener() {
//
//				@Override
//				public void onClick(View v) {
//					Log.e(TAG, "I have been clicked");
//					showAddCertDialog();
//				}
//			});
//		}
//		return (v);
//	}
//
//	private void showAddCertDialog() {
//		final AlertDialog.Builder alert = new AlertDialog.Builder(this);
//
//		alert.setTitle("Add A New Certificate");
//		alert.setIcon(android.R.drawable.ic_dialog_info);
//		alert.setMessage("To add a new certificate, please add the certificate in DER format to the SD Card.");
//		alert.setPositiveButton("Add", new DialogInterface.OnClickListener() {
//			public void onClick(DialogInterface dialog, int whichButton) {
//				addCertifciatesFromExternalStroage();
//				dialog.cancel();
//			}
//		});
//
//		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//			public void onClick(DialogInterface dialog, int whichButton) {
//				dialog.cancel();
//			}
//		});
//		alert.show();
//	}
//
//	private void showRemoveCertDialog(final Certificate cert) {
//		final AlertDialog.Builder alert = new AlertDialog.Builder(this);
//
//		alert.setTitle("Remove Certificate");
//		alert.setIcon(android.R.drawable.ic_dialog_info);
//		alert.setMessage("You are about to remove the certificate of: \n" + cert.getO());
//		alert.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
//			public void onClick(DialogInterface dialog, int whichButton) {
//				removeCertificate(cert.getAlias());
//				dialog.cancel();
//			}
//		});
//
//		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//			public void onClick(DialogInterface dialog, int whichButton) {
//				dialog.cancel();
//			}
//		});
//		alert.show();
//	}
//
//	private ArrayList<Certificate> getCertificateFiles() {
//		ArrayList<Certificate> androidCerts = new ArrayList<Certificate>();
//
//		try {
//			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//			tmf.init((KeyStore) null);
//			X509TrustManager xtm = (X509TrustManager) tmf.getTrustManagers()[0];
//
//			Certificate c;
//			for (X509Certificate cert : xtm.getAcceptedIssuers()) {
//				c = new Certificate();
//
//				c.setAlias(hashName(cert.getSubjectX500Principal()));
//
//				String certS = cert.getSubjectDN().getName();
//				c.setO(getSubString(certS, "O="));
//				c.setOU(getSubString(certS, "OU="));
//				c.setE(getSubString(certS, "E="));
//				c.setL(getSubString(certS, "L="));
//				c.setST(getSubString(certS, "ST="));
//				c.setC(getSubString(certS, "C="));
//				c.setCN(getSubString(certS, "CN="));
//
//				String certI = cert.getIssuerDN().getName();
//				c.setIO(getSubString(certI, "O="));
//
//				androidCerts.add(c);
//			}
//
//		} catch (GeneralSecurityException e) {
//			throw new RuntimeException(e);
//		}
//
//		return androidCerts;
//	}
//
//	private static String getSubString(String string, String prefix) {
//		String substring = null;
//
//		if (string.contains(prefix)) {
//			int offset = prefix.length();
//			int start = string.indexOf(prefix) + offset;
//			int end = string.indexOf(",", start);
//			if (end > 0)
//				substring = string.substring(start, end);
//			else
//				substring = string.substring(start);
//		}
//
//		return substring;
//	}
//
//	private static X509Certificate readCertificate(File file) {
//		if (!file.isFile()) {
//			return null;
//		}
//
//		InputStream is = null;
//		try {
//			CertificateFactory cf = CertificateFactory.getInstance("X509");
//			is = new BufferedInputStream(new FileInputStream(file));
//			return (X509Certificate) cf.generateCertificate(is);
//		} catch (IOException e) {
//			return null;
//		} catch (CertificateException e) {
//			throw new RuntimeException(e);
//		} finally {
//			try {
//				is.close();
//			} catch (IOException e) {
//			}
//		}
//	}
//
//	private static String hashName(X500Principal principal) {
//		try {
//			byte[] digest = MessageDigest.getInstance("MD5").digest(principal.getEncoded());
//
//			String result = Integer.toString(leInt(digest), 16);
//			if (result.length() > 8) {
//				StringBuffer buff = new StringBuffer();
//				int padding = 8 - result.length();
//				for (int i = 0; i < padding; i++) {
//					buff.append("0");
//				}
//				buff.append(result);
//
//				return buff.toString();
//			}
//
//			return result;
//		} catch (NoSuchAlgorithmException e) {
//			throw new AssertionError(e);
//		}
//	}
//
//	private static int leInt(byte[] bytes) {
//		int offset = 0;
//		return ((bytes[offset++] & 0xff) << 0) | ((bytes[offset++] & 0xff) << 8) | ((bytes[offset++] & 0xff) << 16) | ((bytes[offset] & 0xff) << 24);
//	}
//
//	private void refreshView() {
//		findViewById(android.R.id.content).invalidate();
//		setAndShowAllCerts();
//	}
//
//	abstract class ManipulateTrustStoreTask extends AsyncTask<Void, Void, Integer> {
//
//		Exception error;
//
//		@Override
//		protected void onPreExecute() {
//			setProgressBarIndeterminateVisibility(true);
//
//		}
//
//		@Override
//		protected Integer doInBackground(Void... params) {
//			try {
//				return manipulate();
//			} catch (GeneralSecurityException e) {
//				Log.e(TAG, "Security Error: " + e.getMessage(), e);
//				error = e;
//
//				return null;
//			} catch (IOException e) {
//				Log.e(TAG, "I/O Error: " + e.getMessage(), e);
//				error = e;
//
//				return null;
//			}
//		}
//
//		protected abstract int manipulate() throws GeneralSecurityException, IOException;
//
//		protected abstract String getSuccessMessage();
//
//		protected abstract String getAlertMessage();
//
//		protected void saveTrustStore(KeyStore localTrustStore) throws IOException, GeneralSecurityException {
//			FileOutputStream out = new FileOutputStream(localTrustStoreFile);
//			localTrustStore.store(out, TRUSTSTORE_PASSWORD.toCharArray());
//		}
//
//		@Override
//		protected void onPostExecute(Integer result) {
//			setProgressBarIndeterminateVisibility(false);
//			refreshView();
//
//			if (result != null) {
//				if (result < 1) {
//					Toast.makeText(AddCertificatesActivity.this, String.format(getAlertMessage(), result), Toast.LENGTH_LONG).show();
//				} else {
//					Toast.makeText(AddCertificatesActivity.this, String.format(getSuccessMessage(), result), Toast.LENGTH_LONG).show();
//				}
//			} else {
//				Toast.makeText(AddCertificatesActivity.this, "Error manipulating local trust store: " + error.getMessage(), Toast.LENGTH_LONG).show();
//			}
//
//		}
//	}

}
