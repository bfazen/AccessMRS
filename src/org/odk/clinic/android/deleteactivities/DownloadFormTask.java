package org.odk.clinic.android.deleteactivities;

import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.tasks.DownloadTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class DownloadFormTask extends DownloadTask {

	@Override
	protected String doInBackground(String... values) {

		if (values.length == 0) {
			return null;
		}
		String baseUrl = values[0];
		int count = values.length - 1;
		if (count > 0) {
			try {
				// Ensure directory exists
				FileUtils.createFolder(FileUtils.FORMS_PATH);

				// Open db for editing
				mPatientDbAdapter.open();

				for (int i = 1; i < values.length; i++) {
					try {
						String formId = values[i];
						publishProgress("forms", Integer.valueOf(i).toString(), Integer.valueOf(count).toString());
						// publishProgress("form " + formId, Integer.valueOf(i)
						// .toString(), Integer.valueOf(count).toString());

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
				}

				mPatientDbAdapter.close();
			} catch (Exception e) {
				e.printStackTrace();
				if (mPatientDbAdapter != null) {
					mPatientDbAdapter.close();
				}
				return e.getLocalizedMessage();
			}
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

}
