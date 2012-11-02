package com.alphabetbloc.clinic.ui.admin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.adapters.CertificateAdapter;
import com.alphabetbloc.clinic.adapters.MergeAdapter;
import com.alphabetbloc.clinic.data.Certificate;
import com.alphabetbloc.clinic.utilities.FileUtils;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author (Some code here is derived from Nikolay Elenkov
 *         https://github.com/nelenkov/custom-cert-https)
 */

public class SSLClientAuthActivity extends SSLBaseActivity {
	
//	private static final String TAG = ClientAuthenticationActivity.class.getSimpleName();
	private Context mContext;
	private File mLocalStoreFile;
	private String trustStorePropDefault;
	private String mLocalStoreFileName;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.add_certificates);
		mContext = this;
		
		trustStorePropDefault = System.getProperty("javax.net.ssl.trustStore"); // System
		mLocalStoreFileName = FileUtils.MY_KEYSTORE;
		setStoreString("key");
		setStoreTitleString("Key");
		setImportFormat("BKS");

		mLocalStoreFile = new File(getFilesDir(), mLocalStoreFileName);
		if(!mLocalStoreFile.exists())
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

			@Override
			protected Void doInBackground(Void... params) {
				// Local Keys:
				mAdapter = new MergeAdapter();
				System.setProperty("javax.net.ssl.trustStore", mLocalStoreFile.getAbsolutePath());
				CertificateAdapter localAdapter = new CertificateAdapter(mContext, R.layout.key_item, getStoreFiles(), true);
				mAdapter.addView(buildSectionLabel(R.layout.certificate_label, "Local Keys"));
				mAdapter.addAdapter(localAdapter);
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
	protected ArrayList<Certificate> getStoreFiles() {
		ArrayList<Certificate> androidKeys = new ArrayList<Certificate>();
		List<File> allFiles = FileUtils.findAllFiles(getFilesDir().getAbsolutePath(), "keystore.bks");
		try {
			Certificate c;
			for (File file : allFiles) {
				c = new Certificate();
				String keyS = file.getName();
				c.setAlias(keyS);
				c.setO(keyS.substring(0, keyS.lastIndexOf(".")));
				c.setName(keyS.substring(0, keyS.lastIndexOf(".")));
				Date date = new Date();
				date.setTime(file.lastModified());
				String dateString = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date);
				c.setDate(dateString);
				androidKeys.add(c);
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return androidKeys;
	}

	@Override
	protected void remove(String name) {
		File file = new File(getFilesDir(), name);
		if (file.delete())
			Toast.makeText(mContext, "Deleted 1 keystore file", Toast.LENGTH_SHORT).show();
		refreshView();
	}

	@Override
	protected void addFromExternalStroage() {
		String[] certs = listKeyStoreFiles();
		// int certsAdded = 0;
		if (certs.length > 1) {
			Toast.makeText(mContext, "You can only have one keystore file", Toast.LENGTH_SHORT).show();
		} else {

			for (String certFilename : certs) {
				File keyFile = new File(Environment.getExternalStorageDirectory(), certFilename);

				try {
					FileInputStream in = new FileInputStream(keyFile);
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
					//delete so we don't leave keys hanging around...!
					keyFile.delete();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				// certsAdded++;
			}
		}

		Toast.makeText(mContext, "Replaced the current keystore file", Toast.LENGTH_SHORT).show();
		refreshView();
	}

	private static String[] listKeyStoreFiles() {
		File externalStorage = Environment.getExternalStorageDirectory();
		FilenameFilter ff = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				if (filename.equals("mykeystore.bks"))
					return true;
				else
					return false;
			}
		};

		return externalStorage.list(ff);
	}
}
