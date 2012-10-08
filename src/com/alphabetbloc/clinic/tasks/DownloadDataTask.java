package com.alphabetbloc.clinic.tasks;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.sqlcipher.DatabaseUtils.InsertHelper;
import net.sqlcipher.database.SQLiteDatabase;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.SyncResult;
import android.util.Log;

import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.providers.Db;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.NetworkUtils;
import com.alphabetbloc.clinic.utilities.XformUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author Yaw Anokwa, Sam Mbugua (I think? ...starting version was from ODK
 *         Clinic)
 */
public class DownloadDataTask extends SyncDataTask {

	private static final String TAG = DownloadDataTask.class.getSimpleName();
	private DbProvider mDbHelper;

	@Override
	protected String doInBackground(SyncResult... values) {
		Thread.currentThread().setName(TAG);
		sSyncResult = values[0];
		Log.i(TAG, "DownloadDataTask Called");
		mDbHelper = DbProvider.openDb();
		mDownloadComplete = false;
		String error = null;

		// Update Clients Section:
		error = updateClients();
		if (error != null)
			return error;

		// Update Forms Section:
		error = updateForms();
		if (error != null)
			return error;

		return error;
	}

	// FORMS SECTION
	/**
	 * Compares the current form list on the server with the forms on disk
	 * before downloading new forms
	 * 
	 * @return error message or null if successful
	 */
	private String updateForms() {
		Log.i(TAG, "UpdateForms Called");
		// Create Form List

		ArrayList<Form> allServerForms = new ArrayList<Form>();
		try {
			showProgress("Updating Forms");
			InputStream is = getUrlStream(NetworkUtils.getFormListDownloadUrl());
			Document doc = null;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(is);
			if (doc != null)
				allServerForms = createFormList(doc);
			is.close();

		} catch (Exception e) {

			e.printStackTrace();
			++sSyncResult.stats.numIoExceptions;
			return e.toString();
		}

		// find new forms
		ArrayList<Form> newForms = new ArrayList<Form>();
		for (int i = 0; i < allServerForms.size(); i++) {
			Form f = allServerForms.get(i);
			String formId = f.getFormId() + "";
			if (!FileUtils.doesXformExist(formId))
				newForms.add(f);
		}

		// Download Forms
		String error = downloadForms(newForms);
		if (error != null)
			return error;

		return error;
	}

	/**
	 * Inserts a list of forms into the clinic form list database
	 * 
	 * @param doc
	 *            Document created from parsed input stream
	 * @throws Exception
	 */
	private ArrayList<Form> createFormList(Document doc) throws Exception {
		Log.i(TAG, "CreateFormList Called");
		mDbHelper.deleteAllForms();
		NodeList formElements = doc.getElementsByTagName("xform");
		int count = formElements.getLength();

		Form f;
		int progress = 0;
		ArrayList<Form> allForms = new ArrayList<Form>();
		for (int i = 0; i < count; i++) {
			Element n = (Element) formElements.item(i);

			String formName = n.getElementsByTagName("name").item(0).getChildNodes().item(0).getNodeValue();
			String formId = n.getElementsByTagName("id").item(0).getChildNodes().item(0).getNodeValue();

			f = new Form();
			f.setName(formName);
			f.setFormId(Integer.valueOf(formId));
			mDbHelper.createForm(f);
			allForms.add(f);

			long c = Math.round(((float) i / (float) count) * 10.0);
			int current = (int) c;
			if (current != progress) {
				Log.i(TAG, "i=" + i + " current=" + current + " progress=" + progress + " icount=" + count);
				progress = current;
				showProgress("Processing Forms", 1);
			}

		}

		return allForms;
	}

	/**
	 * Downloads forms from OpenMRS
	 * 
	 * @param serverForms
	 *            forms to download
	 * @return error message or null if successful
	 */
	private String downloadForms(ArrayList<Form> serverForms) {
		Log.i(TAG, "DownloadNewForms Called");
		showProgress("Downloading Forms");

		FileUtils.createFolder(FileUtils.getExternalFormsPath());

		int totalForms = serverForms.size();
		int progress = 0;
		for (int i = 0; i < totalForms; i++) {

			String formId = serverForms.get(i).getFormId() + "";

			try {
				// download
				StringBuilder url = (new StringBuilder(NetworkUtils.getFormDownloadUrl())).append("&formId=").append(formId);
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

				// update clinic
				mDbHelper.updateFormPath(Integer.valueOf(formId), path);

				// update collect
				if (!XformUtils.insertSingleForm(path))
					return "ODK Collect not initialized.";

				long c = Math.round(((float) i / (float) totalForms) * 10.0);
				int current = (int) c;
				if (current != progress) {
					Log.i(TAG, "i=" + i + " current=" + current + " progress=" + progress + " icount=" + totalForms);
					progress = current;
					showProgress("Processing Forms", 1);
				}
			} catch (Exception e) {
				++sSyncResult.stats.numIoExceptions;
				e.printStackTrace();
				return e.toString();
			}

		}
		return null;

	}

	// CLIENTS SECTION
	protected String updateClients() {
		Log.e(TAG, "DownloadClients Called");
		DataInputStream zdisServer = null;
		File tempFile = null;

		// DOWNLOAD
		try {
			showProgress("Downloading Clients");
			zdisServer = getUrlStreamFromOdkConnector();
			if (zdisServer != null) {
				tempFile = downloadStream(zdisServer);
				zdisServer.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			++sSyncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		if (mUploadComplete)
			dropHttpClient();

		// PROCESS
		try {
			if (tempFile != null) {
				DataInputStream zdis = new DataInputStream(new FileInputStream(tempFile));
				if (zdis != null) {
					insertData(zdis);
					zdis.close();
				}
				FileUtils.deleteFile(tempFile.getAbsolutePath());
			}

			updateFormNumbers();
			mDbHelper.createDownloadLog();
		} catch (Exception e) {
			e.printStackTrace();
			++sSyncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		return null;
	}

	private File downloadStream(final DataInputStream inputStream) throws Exception {
		Log.e(TAG, "DownloadStreamToZip Called");
		File tempFile = null;
		try {

			tempFile = File.createTempFile(".omrs", "-stream", App.getApp().getFilesDir());
			BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(tempFile));

			byte[] buffer = new byte[4096]; // TODO! CHECK: originally= 1024
			int count = 0;
			while ((count = inputStream.read(buffer)) > 0) {
				stream.write(buffer, 0, count);
			}

			stream.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			++sSyncResult.stats.numIoExceptions;
		} catch (IOException e) {
			e.printStackTrace();
			++sSyncResult.stats.numIoExceptions;
		}
		return tempFile;
	}

	private void insertData(DataInputStream dis) throws Exception {
		Log.i(TAG, "InsertData Called");

		// open db and clean entries
		showProgress("Processing", 10);
		mDbHelper.deleteAllPatients();
		mDbHelper.deleteAllObservations();
		mDbHelper.deleteAllFormInstances();

		// download and insert patients and obs
		insertPatients(dis);
		insertObservations(dis);
		insertPatientForms(dis);
	}

	private void updateFormNumbers() {
		// sync db
		mDbHelper.updatePriorityFormNumbers();
		mDbHelper.updatePriorityFormList();
		mDbHelper.updateSavedFormNumbers();
		mDbHelper.updateSavedFormsList();
	}

	// /////////////// DOWNLOAD AND UPDATE TABLE SECTION ///////////////////////
	// NB: louis.fazen is following Yaw's logic here, but in actuality Patient
	// Forms
	// and Observations are both going into obs, and therefore, the code is
	// identical...

	// (louis.fazen NOTE: Do NOT use this with Cursor indices in other code
	// Cursor has different indices than IH b/c it does not include _id
	// i.e. Cursor.getColumnIndex = (IH.getColumnIndex -1)
	// ih is 71% faster than Cursor (even w/ cursor limit=1)
	public void insertPatientForms(final DataInputStream zdis) throws Exception {
		long start = System.currentTimeMillis();

		showProgress("Processing Forms");
		SQLiteDatabase db = DbProvider.getDb();
		InsertHelper ih = new InsertHelper(db, Db.OBSERVATIONS_TABLE);

		int ptIdIndex = ih.getColumnIndex(Db.KEY_PATIENT_ID);
		int obsTextIndex = ih.getColumnIndex(Db.KEY_VALUE_TEXT);
		int obsNumIndex = ih.getColumnIndex(Db.KEY_VALUE_NUMERIC);
		int obsDateIndex = ih.getColumnIndex(Db.KEY_VALUE_DATE);
		int obsIntIndex = ih.getColumnIndex(Db.KEY_VALUE_INT);
		int obsFieldIndex = ih.getColumnIndex(Db.KEY_FIELD_NAME);
		int obsTypeIndex = ih.getColumnIndex(Db.KEY_DATA_TYPE);
		int obsEncDateIndex = ih.getColumnIndex(Db.KEY_ENCOUNTER_DATE);

		db.beginTransaction();
		int progress = 0;
		int icount = 0;
		try {
			icount = zdis.readInt();
			Log.i(TAG, "insertPatientForms icount: " + icount);
			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == Db.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == Db.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == Db.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == Db.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(zdis.readUTF()));
				}
				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(zdis.readUTF()));
				ih.execute();

				long c = Math.round(((float) i / (float) icount) * 10.0);
				int current = (int) c;

				if (current != progress) {
					progress = current;
					showProgress("Processing Forms", 1);
				}
			}
			db.setTransactionSuccessful();
		} finally {
			ih.close();
			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertPtsForms", String.format("InsertPtsForms Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) icount / (double) (end - start)));

	}

	public void insertPatients(DataInputStream zdis) throws Exception {
		showProgress("Processing Clients");
		long start = System.currentTimeMillis();
		SQLiteDatabase db = DbProvider.getDb();

		InsertHelper ih = new InsertHelper(db, Db.PATIENTS_TABLE);
		int ptIdIndex = ih.getColumnIndex(Db.KEY_PATIENT_ID);
		int ptIdentifierIndex = ih.getColumnIndex(Db.KEY_IDENTIFIER);
		int ptGivenIndex = ih.getColumnIndex(Db.KEY_GIVEN_NAME);
		int ptFamilyIndex = ih.getColumnIndex(Db.KEY_FAMILY_NAME);
		int ptMiddleIndex = ih.getColumnIndex(Db.KEY_MIDDLE_NAME);
		int ptBirthIndex = ih.getColumnIndex(Db.KEY_BIRTH_DATE);
		int ptGenderIndex = ih.getColumnIndex(Db.KEY_GENDER);

		db.beginTransaction();
		int progress = 0;
		int icount = 0;
		try {
			icount = zdis.readInt();
			Log.i(TAG, "insertPatients icount: " + icount);
			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();
				int win = zdis.readInt();
				ih.bind(ptIdIndex, win);
				ih.bind(ptFamilyIndex, zdis.readUTF());
				ih.bind(ptMiddleIndex, zdis.readUTF());
				ih.bind(ptGivenIndex, zdis.readUTF());
				ih.bind(ptGenderIndex, zdis.readUTF());
				ih.bind(ptBirthIndex, parseDate(zdis.readUTF()));
				ih.bind(ptIdentifierIndex, zdis.readUTF());
				ih.execute();

				long c = Math.round(((float) i / (float) icount) * 10.0);
				int current = (int) c;
				if (current != progress) {
					progress = current;
					showProgress("Processing Clients", 1);
				}

			}
			db.setTransactionSuccessful();
		} finally {
			ih.close();
			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertPts", String.format("InsertPts Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) icount / (double) (end - start)));

	}

	public void insertObservations(DataInputStream zdis) throws Exception {
		long start = System.currentTimeMillis();

		showProgress("Processing Data");
		SQLiteDatabase db = DbProvider.getDb();
		InsertHelper ih = new InsertHelper(db, Db.OBSERVATIONS_TABLE);
		int ptIdIndex = ih.getColumnIndex(Db.KEY_PATIENT_ID);
		int obsTextIndex = ih.getColumnIndex(Db.KEY_VALUE_TEXT);
		int obsNumIndex = ih.getColumnIndex(Db.KEY_VALUE_NUMERIC);
		int obsDateIndex = ih.getColumnIndex(Db.KEY_VALUE_DATE);
		int obsIntIndex = ih.getColumnIndex(Db.KEY_VALUE_INT);
		int obsFieldIndex = ih.getColumnIndex(Db.KEY_FIELD_NAME);
		int obsTypeIndex = ih.getColumnIndex(Db.KEY_DATA_TYPE);
		int obsEncDateIndex = ih.getColumnIndex(Db.KEY_ENCOUNTER_DATE);

		db.beginTransaction();
		int current = 0;
		int progress = 0;
		int icount = 0;
		try {
			icount = zdis.readInt();
			Log.i(TAG, "insertObservations icount: " + icount);

			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == Db.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == Db.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == Db.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == Db.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(zdis.readUTF()));
				}
				
				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(zdis.readUTF()));
				ih.execute();


				
//				current = (int) Math.round(((float) i / (float) icount) * 20.0);
				if (((int) Math.round(((float) i / (float) icount) * 20.0)) != progress) {
//					Log.i(TAG, "i=" + i + " current=" + current + " progress=" + progress + " icount=" + icount);
					progress = (int) Math.round(((float) i / (float) icount) * 20.0);
					showProgress("Processing Data", 1);
				}

			
			}

				db.setTransactionSuccessful();
		} finally {
			ih.close();

				db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertObs", String.format("InsertObs Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) icount / (double) (end - start)));

	}

	String date = null;
	SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy");
	SimpleDateFormat inputFormat =  new SimpleDateFormat("yyyy-MM-dd");
	private String parseDate(String s) {
		date = s.split("T")[0];
		try {
			return outputFormat.format(inputFormat.parse(date));
		} catch (ParseException e) {
			return "Unknown date";
		}
	}

	@Override
	protected void onPostExecute(String result) {
		mDownloadComplete = true;
		synchronized (this) {
			if (mStateListener != null) {
				if (mUploadComplete && mDownloadComplete) {
					dropHttpClient();
					mStateListener.syncComplete(result, sSyncResult);
				} else
					mStateListener.downloadComplete(result);
			}
		}
	}

}
