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
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.odk.clinic.android.openmrs.Form;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.os.Environment;
import android.util.Log;

public class DownloadPatientTaskLouis extends DownloadTask {

	public static final String KEY_ERROR = "error";

	public static final String KEY_PATIENTS = "patients";

	public static final String KEY_OBSERVATIONS = "observations";

	private static final String TAG = "Clinic.DownloadPatientTask";

	@Override
	protected String doInBackground(String... values) {

		String url = values[0];
		String username = values[1];
		String password = values[2];
		boolean savedSearch = Boolean.valueOf(values[3]);
		int cohort = Integer.valueOf(values[4]);
		int program = Integer.valueOf(values[5]);

		int step = 0;
		int totalstep = 10;
		File zipFile = null;

		// Louis (louis.fazen@gmail.com) is changing how things download by
		// adding the downloadStreamToZip Method
		try {

			DataInputStream zdisServer = connectToServer(url, username, password, savedSearch, cohort, program);
			publishProgress("Downloading", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
			if (zdisServer != null) {
				zipFile = downloadStreamToZip(zdisServer);
				zdisServer.close();
			}
			publishProgress("Downloading", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
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
					publishProgress("Processing Patients", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
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

		/////////////////////////////////// DOWNLOAD FORMLIST TASK /////////////////////////////////////////

		String formUrl = values[6];

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

	// ///////////////////////////////// DOWNLOAD FORMLIST TASK
	// ///////////////////////////////////////

	private void insertForms(Document doc) throws Exception {

		NodeList formElements = doc.getElementsByTagName("xform");
		int count = formElements.getLength();

		for (int i = 0; i < count; i++) {
			Element n = (Element) formElements.item(i);

			String formName = n.getElementsByTagName("name").item(0).getChildNodes().item(0).getNodeValue();
			String formId = n.getElementsByTagName("id").item(0).getChildNodes().item(0).getNodeValue();

			Form f = new Form();
			f.setName(formName);
			f.setFormId(Integer.valueOf(formId));

			mPatientDbAdapter.createForm(f);

			publishProgress("forms", Integer.valueOf(i).toString(), Integer.valueOf(count).toString());
		}
	}

}