package org.odk.clinic.android.tasks;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.clinic.android.utilities.WebUtils;
import org.odk.clinic.android.utilities.XformUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class DownloadDataTask extends SyncDataTask {

	public static final String KEY_ERROR = "error";
	public static final String KEY_PATIENTS = "patients";
	public static final String KEY_OBSERVATIONS = "observations";
	private static final String NAMESPACE = "org.odk.clinic.android";
	private static final String DATA_DIR = "/data/" + NAMESPACE;

	private static final int CONNECTION_TIMEOUT = 60000;
	private static final String TAG = DownloadDataTask.class.getSimpleName();

	protected DbAdapter mDb;
	private ArrayList<Form> mForms = new ArrayList<Form>();

	@Override
	protected String doInBackground(Void... values) {
		mDownloadComplete = false;
		setDownloadSteps(20);

		// DOWNLOAD
		mDb = DbAdapter.openDb();
//		// DownloadFormList
//		String formListResult = downloadFormList();
//		if (formListResult != null)
//			return formListResult;
//
//		// Download Forms
//		String formResult = downloadNewForms(WebUtils.getFormDownloadUrl());
//		if (formResult != null)
//			return formResult;

		// Download Clients Section:
		String clientResult = downloadClients();
		if (clientResult != null)
			return clientResult;

		return null;
	}

	private void insertData(DataInputStream dis) throws Exception {
		// open db and clean entries
		showProgress("Processing");
		mDb.deleteAllPatients();
		mDb.deleteAllObservations();
		mDb.deleteAllFormInstances();
		mDb.makeIndices();

		// download and insert patients and obs
		showProgress("Processing Clients");
		mDb.insertPatients(dis);
		showProgress("Processing Data");
		mDb.insertObservations(dis);
		showProgress("Processing Forms");
		mDb.insertPatientForms(dis);
	}

	private void updateFormNumbers() {
		// sync db
		showProgress("Processing");
		mDb.updatePriorityFormNumbers();
		mDb.updatePriorityFormList();
		mDb.updateSavedFormNumbers();
		mDb.updateSavedFormsList();
	}

	private String downloadFormList() {
		try {
			InputStream is = getUrlStream(WebUtils.getFormListDownloadUrl());
			showProgress("Downloading Forms");
			Document doc = null;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(is);

			// clean db, insert reference to forms
			if (doc != null) {
				mDb.deleteAllForms();
				insertForms(doc);
			}
			is.close();

		} catch (Exception e) {
			e.printStackTrace();
			return e.toString();
		}

		return null;
	}

	private String downloadNewForms(String baseUrl) {

		FileUtils.createFolder(FileUtils.getExternalFormsPath());
		for (int i = 0; i < mForms.size(); i++) {
			String formId = mForms.get(i).getFormId() + "";

			if (!FileUtils.doesXformExist(formId)) {
				try {

					StringBuilder url = (new StringBuilder(baseUrl)).append("&formId=").append(formId);
					Log.i(TAG, "Will try to download form " + formId + " url=" + url);
					InputStream is = getUrlStream(url.toString());

					File f = new File(FileUtils.getExternalFormsPath(), formId + FileUtils.XML_EXT);
					String path = f.getAbsolutePath();
					OutputStream os = new FileOutputStream(f);
					byte buf[] = new byte[1024];
					int len;
					while ((len = is.read(buf)) > 0) {
						os.write(buf, 0, len);
					}
					os.flush();
					os.close();
					is.close();

					mDb.updateFormPath(Integer.valueOf(formId), path);

					// insert path into collect db
					if (!XformUtils.insertSingleForm(path)) {
						return "ODK Collect not initialized.";
					}

				} catch (Exception e) {

					e.printStackTrace();
					return e.toString();
				}
			}
		}
		return null;

	}

	/**
	 * Inserts a list of forms into the clinic form list database (not the forms
	 * themselves)
	 * 
	 * @param doc
	 *            Document created from parsed input stream
	 * @throws Exception
	 */
	private void insertForms(Document doc) throws Exception {

		NodeList formElements = doc.getElementsByTagName("xform");
		int count = formElements.getLength();

		Form f;
		for (int i = 0; i < count; i++) {
			Element n = (Element) formElements.item(i);

			String formName = n.getElementsByTagName("name").item(0).getChildNodes().item(0).getNodeValue();
			String formId = n.getElementsByTagName("id").item(0).getChildNodes().item(0).getNodeValue();

			f = new Form();
			f.setName(formName);
			f.setFormId(Integer.valueOf(formId));
			long id = mDb.createForm(f);
			mForms.add(f);

		}
	}

	// DOWNLOAD CLIENT DATA
	// url, username, password, serializer, locale, action, cohort
	protected String downloadClients() {

		DataInputStream zdisServer = null;
		File zipFile = null;
		try {
			showProgress("Downloading Clients");
			zdisServer = getUrlStreamFromOdkConnectorTest();
			if (zdisServer != null) {
				zipFile = downloadStreamToZip(zdisServer);
				zdisServer.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}

		// PROCESS
		try {
			showProgress("Processing");
			if (zipFile != null) {
				DataInputStream zdis = new DataInputStream(new FileInputStream(zipFile));
				// OLD: (new BufferedInputStream(new FileInputStream(zipFile)));
				if (zdis != null) {
					insertData(zdis);
					zdis.close();
				}
				FileUtils.deleteFile(zipFile.getAbsolutePath()); // zipFile.delete();
			}

			showProgress("Processing Forms");
			updateFormNumbers();
			mDb.createDownloadLog();
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}
		
		return null;
	}

	private File downloadStreamToZip(final DataInputStream inputStream) throws Exception {
		File tempFile = null;
		try {
			// TODO CHECK downloads to data dir?
			File datadir = new File(Environment.getDataDirectory() + DATA_DIR);
			tempFile = File.createTempFile(".omrs", "-stream", datadir);
			BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(tempFile));

			byte[] buffer = new byte[4096]; // TODO! CHECK: originally= 1024
			int count = 0;
			while ((count = inputStream.read(buffer)) > 0) {
				stream.write(buffer, 0, count);
			}

			stream.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tempFile;
	}

	@Override
	protected void onPostExecute(String result) {
		mDownloadComplete = true;
		synchronized (this) {
			if (mStateListener != null) {
				if (mUploadComplete && mDownloadComplete) {
					mStateListener.syncComplete(result);
				} else
					mStateListener.downloadComplete(result);
			}
		}
	}

}

// // url, username, password, serializer, locale, action, cohort
// protected DataInputStream connectToServer() throws Exception {
//
// // get prefs
// String url = WebUtils.getPatientDownloadUrl();
// SharedPreferences settings =
// PreferenceManager.getDefaultSharedPreferences(App.getApp());
// String username =
// settings.getString(App.getApp().getString(R.string.key_username),
// App.getApp().getString(R.string.default_username));
// String password =
// settings.getString(App.getApp().getString(R.string.key_password),
// App.getApp().getString(R.string.default_password));
// boolean savedSearch =
// settings.getBoolean(App.getApp().getString(R.string.key_use_saved_searches),
// false);
// int cohort =
// Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_saved_search),
// "0"));
// int program =
// Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_program),
// "0"));
//
// // compose url
// URL u = new URL(url);
//
// // setup http url connection
// HttpURLConnection c = (HttpURLConnection) u.openConnection();
// c.setDoOutput(true);
// c.setRequestMethod("POST");
// c.setConnectTimeout(CONNECTION_TIMEOUT);
// c.setReadTimeout(CONNECTION_TIMEOUT);
// c.addRequestProperty("Content-type", "application/octet-stream");
//
// // write auth details to connection
// DataOutputStream dos = new DataOutputStream(new
// GZIPOutputStream(c.getOutputStream()));
// dos.writeUTF(username); // username
// dos.writeUTF(password); // password
// dos.writeBoolean(savedSearch);
// if (cohort > 0)
// dos.writeInt(cohort);
// dos.writeInt(program);
// dos.flush();
// dos.close();
//
// // read connection status
// DataInputStream zdis = new DataInputStream(new
// GZIPInputStream(c.getInputStream()));
// int status = zdis.readInt();
// if (status == Constants.STATUS_FAILURE) {
// zdis.close();
// throw new IOException("Connection failed. Please try again.");
// } else if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
// zdis.close();
// throw new IOException("Access denied. Check your username and password.");
// } else {
// assert (status == HttpURLConnection.HTTP_OK); // success
// return zdis;
// }
// }
