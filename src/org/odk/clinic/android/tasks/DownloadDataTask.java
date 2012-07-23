package org.odk.clinic.android.tasks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.util.Log;

public class DownloadDataTask extends DownloadTask {

	public static final String KEY_ERROR = "error";

	public static final String KEY_PATIENTS = "patients";

	public static final String KEY_OBSERVATIONS = "observations";

	private static final String TAG = "Clinic.DownloadDataTask";

	private ArrayList<Form> mForms = new ArrayList<Form>();

	@Override
	protected String doInBackground(String... values) {

		String url = values[0];
		String username = values[1];
		String password = values[2];
		boolean savedSearch = Boolean.valueOf(values[3]);
		int cohort = Integer.valueOf(values[4]);
		int program = Integer.valueOf(values[5]);
		String formListUrl = values[6];
		String formUrl = values[7];

		int step = 0;
		int totalstep = 10;
		File zipFile = null;
		
		try {

			DataInputStream zdisServer = connectToServer(url, username, password, savedSearch, cohort, program);
			publishProgress("Downloading Clients", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
			if (zdisServer != null) {
				zipFile = downloadStreamToZip(zdisServer);
				zdisServer.close();
			}

			publishProgress("Downloading Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
			String formListResult = downloadFormList(formListUrl);
			if (formListResult != null) {
				return formListResult;
			}

			String formResult = downloadNewForms(formUrl);
			if (formResult != null) {
				return formResult;
			}

			publishProgress("Processing", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
			if (zipFile != null) {
				DataInputStream zdis = new DataInputStream(new BufferedInputStream(new FileInputStream(zipFile)));

				if (zdis != null) {
					publishProgress("Processing", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					// open db and clean entries
					mPatientDbAdapter.open();
					mPatientDbAdapter.deleteAllPatients();
					mPatientDbAdapter.deleteAllObservations();
					mPatientDbAdapter.deleteAllFormInstances();

					// download and insert patients and obs
					publishProgress("Processing Clients", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.insertPatients(zdis);
					publishProgress("Processing Data", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.insertObservations(zdis);
					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.insertPatientForms(zdis);

					// close zip stream
					zdis.close();
					zipFile.delete();
					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());

					// TODO: louis.fazen is adding this...it is a bit of hack of
					// bringing the various db into sync
					mPatientDbAdapter.updatePriorityFormNumbers();
					mPatientDbAdapter.updatePriorityFormList();

					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.updateSavedFormNumbers();
					mPatientDbAdapter.updateSavedFormsList();

					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());

					// close db and stream
					mPatientDbAdapter.createDownloadLog();
					mPatientDbAdapter.close();

				}
			}

			else {
				Log.e(TAG, "FileInputStream Could not Retrieve Data from ZipFile");
			}

		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}

		return null;
	}
	
	

	// DOWNLOAD FORM DATA
	// TODO: should NOT be downloading and inserting forms at same time...!
	// Downloads all forms to clinic database
	private String downloadFormList(String formUrl) {

		try {
			URL u = new URL(formUrl);
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			InputStream is = c.getInputStream();

			Document doc = null;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(is);

			if (doc != null) {
				// open db and clean entries
				mPatientDbAdapter.open();
				mPatientDbAdapter.deleteAllForms();

				// download forms to file, and insert reference to db
				insertForms(doc);

				// close db
				mPatientDbAdapter.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (mPatientDbAdapter != null) {
				mPatientDbAdapter.close();
			}
			return e.getLocalizedMessage();
		}

		return null;

	}

	// Inserts a list of forms on the server into clinic database
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

	// Check to see if form exists in the Collect Db. If so, we already have the
	// instance...
	// TODO: this is so poorly done... could all be accomplished with an inner
	// join if it were the same Db!!!!
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

				} else {
					System.out.println("Form " + formId + " already downloaded");
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
				// TODO: handle exception
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

	// DOWNLOAD CLIENT DATA
	private File downloadStreamToZip(final DataInputStream inputStream) throws Exception {
		File tempFile = null;
		try {
			File odkRoot = new File(Environment.getExternalStorageDirectory() + File.separator + "odk");
			tempFile = File.createTempFile("pts", ".zip", odkRoot);
			BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(tempFile));

			// TODO: louis.fazen this next may cause problems:?
			// int totalSize = inputStream.readInt();

			byte[] buffer = new byte[1024];
			int count = 0;
			int progress = 0;
			while ((count = inputStream.read(buffer)) > 0) {
				stream.write(buffer, 0, count);
				progress++;
				Log.i("ODK clinic", "progresss:" + progress);
				// publishProgress("Downloading Data", Integer.valueOf(progress)
				// .toString(), Integer.valueOf(totalSize).toString());

			}
			stream.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tempFile;
	}

}