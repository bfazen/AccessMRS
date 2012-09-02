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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.PreferencesActivity;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.DownloadListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.clinic.android.utilities.ODKLocalKeyStore;
import org.odk.clinic.android.utilities.ODKSSLSocketFactory;
import org.odk.clinic.android.utilities.WebUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class DownloadDataTask extends AsyncTask<Void, String, String> {

	public static final String KEY_ERROR = "error";
	public static final String KEY_PATIENTS = "patients";
	public static final String KEY_OBSERVATIONS = "observations";
	private static final String TAG = "Clinic.DownloadDataTask";

	private static final String NAMESPACE = "org.odk.clinic.android";
	private static final String DATA_DIR = "/data/" + NAMESPACE;

	private static final int CONNECTION_TIMEOUT = 60000;
	protected DownloadListener mStateListener;
	protected ClinicAdapter mPatientDbAdapter = new ClinicAdapter();

	private int mStep = 0;
	private int mTotalStep = 10;

	private ArrayList<Form> mForms = new ArrayList<Form>();

	@Override
	protected String doInBackground(Void... values) {
		// DOWNLOAD
		
		// FormlistSection:
		// TODO! HTTPS NEW CODE.. but what about other downloads
		// initialize @ODKSSLSocketFactory to authorize local
		// certificates for all subsequent
		// @HttpsURLConnection
		try {
			HttpURLConnection urlConnection = connectURL(WebUtils.getFormListDownloadUrl());
			InputStream is = urlConnection.getInputStream();
			showProgress("Downloading Forms");
			String formListResult = downloadFormList(is);
			if (formListResult != null)
				return formListResult;

		} catch (IOException e1) {
			e1.printStackTrace();
			return e1.getLocalizedMessage();
		}

		// Forms and Clients Section:
		File zipFile = null;
		try {
			String formResult = downloadNewForms(WebUtils.getFormDownloadUrl());
			if (formResult != null)
				return formResult;

			showProgress("Downloading Clients");
			DataInputStream zdisServer = connectToServer();
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
			mPatientDbAdapter.open();
			if (zipFile != null) {
				DataInputStream zdis = new DataInputStream(new FileInputStream(zipFile));
				// OLD: (new BufferedInputStream(new FileInputStream(zipFile)));
				if (zdis != null) {
					insertData(zdis);
					zdis.close();
				}
				FileUtils.deleteFile(zipFile.getAbsolutePath()); // zipFile.delete();
			}

			publishProgress("Processing Forms");
			updateFormNumbers();
			mPatientDbAdapter.createDownloadLog();
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}

		return null;
	}

	private HttpURLConnection connectURL(String urlString) throws IOException {
		HttpURLConnection urlConnection = null;
		URL url = new URL(urlString);

		try {
			if (url.getProtocol().toLowerCase().equals("https")) {
				new ODKSSLSocketFactory(ODKLocalKeyStore.getKeyStore());
				HttpsURLConnection urlHttpsConnection = null;
				urlHttpsConnection = (HttpsURLConnection) url.openConnection();
				urlConnection = urlHttpsConnection;
			} else
				urlConnection = (HttpURLConnection) url.openConnection();

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return urlConnection;
	}

	private void showProgress(String title) {
		String current = Integer.valueOf(mStep++).toString();
		String total = Integer.valueOf(mTotalStep).toString();
		publishProgress(title, current, total);
	}

	private void insertData(DataInputStream dis) throws Exception {
		publishProgress("Processing");
		// open db and clean entries

		mPatientDbAdapter.deleteAllPatients();
		mPatientDbAdapter.deleteAllObservations();
		mPatientDbAdapter.deleteAllFormInstances();

		mPatientDbAdapter.makeIndices();
		// download and insert patients and obs
		showProgress("Processing Clients");
		mPatientDbAdapter.insertPatients(dis);
		publishProgress("Processing Data");
		mPatientDbAdapter.insertObservations(dis);
		publishProgress("Processing Forms");
		mPatientDbAdapter.insertPatientForms(dis);
	}

	private void updateFormNumbers() {
		// NB: basically a hack to bring various db into sync
		mPatientDbAdapter.updatePriorityFormNumbers();
		mPatientDbAdapter.updatePriorityFormList();

		mPatientDbAdapter.updateSavedFormNumbers();
		mPatientDbAdapter.updateSavedFormsList();
	}

	// DOWNLOAD FORM DATA
	/**
	 * Downloads a list of the current forms located on the server
	 * 
	 * @param is
	 *            Inputstream from a https connection
	 * @return null if successful, and error message if not
	 */
	private String downloadFormList(InputStream is) {

		try {
			Document doc = null;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(is);

			// clean db, insert reference to forms
			if (doc != null) {
				mPatientDbAdapter.open();
				mPatientDbAdapter.deleteAllForms();
				insertForms(doc);
			}

		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
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
			mPatientDbAdapter.createForm(f);
			mForms.add(f);

		}
	}

	/**
	 * Checks if a form exists in Collect Db. If it does, we already have the
	 * form, and there is no need to download!
	 * 
	 * @param formId
	 * @return true if form exists in Collect Db.
	 */
	private boolean doesFormExist(String formId) {

		boolean alreadyExists = false;

		Cursor mCursor;
		try {
			mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
		} catch (SQLiteException e) {
			Log.e("DownloadFormActivity", e.getLocalizedMessage());
			return false;
		}

		if (mCursor == null) {
			return false;
		}

		mCursor.moveToPosition(-1);
		while (mCursor.moveToNext()) {

			String dbFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

			// if the exact form exists, leave it be. else, insert it.
			if (dbFormId.equalsIgnoreCase(formId + "") && (new File(FileUtils.FORMS_PATH + "/" + formId + ".xml")).exists()) {
				alreadyExists = true;
			}

		}

		if (mCursor != null) {
			mCursor.close();
		}

		return alreadyExists;

	}

	private String downloadNewForms(String baseUrl) {
		// Ensure directory exists
		FileUtils.createFolder(FileUtils.FORMS_PATH);
		try {
			// Open db for editing
			mPatientDbAdapter.open();
			String formId;

			for (int i = 0; i < mForms.size(); i++) {
				formId = mForms.get(i).getFormId() + "";
				if (!doesFormExist(formId)) {
					try {
						System.out.println("Will try to download form " + formId);

						StringBuilder url = new StringBuilder(baseUrl);
						url.append("&formId=");
						url.append(formId);

						URL u = new URL(url.toString());
						
						HttpURLConnection c = (HttpURLConnection) u.openConnection();
						InputStream is = c.getInputStream();

						String path = FileUtils.FORMS_PATH + formId + ".xml";

						File f = new File(path);
						OutputStream os = new FileOutputStream(f);
						byte buf[] = new byte[1024];
						int len;
						while ((len = is.read(buf)) > 0) {
							os.write(buf, 0, len);
						}
						os.flush();
						os.close();
						is.close();

						mPatientDbAdapter.updateFormPath(Integer.valueOf(formId), path);

						// insert path into collect db
						if (!insertSingleForm(path)) {
							return "ODK Collect not initialized.";
						}

					} catch (Exception e) {
						e.printStackTrace();
					}

					// } else {
					// System.out.println("Form " + formId +
					// " already downloaded");
				}
			}
			mPatientDbAdapter.close();

		} catch (Exception e) {
			e.printStackTrace();
			if (mPatientDbAdapter != null)
				mPatientDbAdapter.close();

			return e.getLocalizedMessage();
		}
		return null;
	}

	private boolean insertSingleForm(String formPath) {

		ContentValues values = new ContentValues();
		File addMe = new File(formPath);

		// Ignore invisible files that start with periods.
		if (!addMe.getName().startsWith(".") && (addMe.getName().endsWith(".xml") || addMe.getName().endsWith(".xhtml"))) {

			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			Document document = null;

			try {
				documentBuilder = documentBuilderFactory.newDocumentBuilder();
				document = documentBuilder.parse(addMe);
				document.normalize();
			} catch (Exception e) {
				Log.e("DownloadFormTask", e.getLocalizedMessage());
			}

			String name = null;
			int id = -1;
			String version = null;
			String md5 = FileUtils.getMd5Hash(addMe);

			NodeList form = document.getElementsByTagName("form");
			NamedNodeMap nodemap = form.item(0).getAttributes();
			for (int i = 0; i < nodemap.getLength(); i++) {
				Attr attribute = (Attr) nodemap.item(i);
				if (attribute.getName().equalsIgnoreCase("id")) {
					id = Integer.valueOf(attribute.getValue());
				}
				if (attribute.getName().equalsIgnoreCase("version")) {
					version = attribute.getValue();
				}
				if (attribute.getName().equalsIgnoreCase("name")) {
					name = attribute.getValue();
				}
			}

			if (name != null) {
				if (getNameFromId(id) == null) {
					values.put(FormsColumns.DISPLAY_NAME, name);
				} else {
					values.put(FormsColumns.DISPLAY_NAME, getNameFromId(id));
				}
			}
			if (id != -1 && version != null) {
				values.put(FormsColumns.JR_FORM_ID, id);
			}
			values.put(FormsColumns.FORM_FILE_PATH, addMe.getAbsolutePath());

			boolean alreadyExists = false;

			Cursor mCursor;
			try {
				mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
			} catch (SQLiteException e) {
				Log.e("DownloadFormTask", e.getLocalizedMessage());
				return false;
				// TODO handle exception
			}

			if (mCursor == null) {
				System.out.println("Something bad happened");
				mPatientDbAdapter.open();
				mPatientDbAdapter.deleteAllForms();
				mPatientDbAdapter.close();
				return false;
			}

			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				String dbmd5 = mCursor.getString(mCursor.getColumnIndex(FormsColumns.MD5_HASH));
				String dbFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

				// if the exact form exists, leave it be. else, insert it.
				if (dbmd5.equalsIgnoreCase(md5) && dbFormId.equalsIgnoreCase(id + "")) {
					alreadyExists = true;
				}

			}

			if (!alreadyExists) {
				App.getApp().getContentResolver().delete(FormsColumns.CONTENT_URI, "md5Hash=?", new String[] { md5 });
				App.getApp().getContentResolver().delete(FormsColumns.CONTENT_URI, "jrFormId=?", new String[] { id + "" });
				App.getApp().getContentResolver().insert(FormsColumns.CONTENT_URI, values);
			}

			if (mCursor != null) {
				mCursor.close();
			}

		}

		return true;

	}

	private String getNameFromId(Integer id) {
		String formName = null;
		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		Cursor c = ca.fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);

			if (c.getCount() > 0) {
				do {
					if (c.getInt(formIdIndex) == id) {
						formName = c.getString(nameIndex);
						break;
					}
				} while (c.moveToNext());
			}
		}

		if (c != null)
			c.close();

		ca.close();
		return formName;
	}

	// DOWNLOAD CLIENT DATA
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

	// url, username, password, serializer, locale, action, cohort
	protected DataInputStream connectToServer() throws Exception {

		//get prefs
		String url = WebUtils.getPatientDownloadUrl();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		String username = settings.getString(PreferencesActivity.KEY_USERNAME, App.getApp().getString(R.string.default_username));
		String password = settings.getString(PreferencesActivity.KEY_PASSWORD, App.getApp().getString(R.string.default_password));
		boolean savedSearch = settings.getBoolean(PreferencesActivity.KEY_USE_SAVED_SEARCHES, false);
		int cohort = settings.getInt(PreferencesActivity.KEY_SAVED_SEARCH, 0);
		int program = settings.getInt(PreferencesActivity.KEY_PROGRAM, 0);
		
		// compose url
		URL u = new URL(url);

		// setup http url connection
		HttpURLConnection c = (HttpURLConnection) u.openConnection();
		c.setDoOutput(true);
		c.setRequestMethod("POST");
		c.setConnectTimeout(CONNECTION_TIMEOUT);
		c.setReadTimeout(CONNECTION_TIMEOUT);
		c.addRequestProperty("Content-type", "application/octet-stream");

		// write auth details to connection
		DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(c.getOutputStream()));
		dos.writeUTF(username); // username
		dos.writeUTF(password); // password
		dos.writeBoolean(savedSearch);
		if (cohort > 0)
			dos.writeInt(cohort);
		dos.writeInt(program);
		dos.flush();
		dos.close();

		// read connection status
		DataInputStream zdis = new DataInputStream(new GZIPInputStream(c.getInputStream()));
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
				mStateListener.downloadComplete(result);
		}
	}

	public void setDownloadListener(DownloadListener sl) {
		synchronized (this) {
			mStateListener = sl;
		}
	}

}