package com.alphabetbloc.clinic.services;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.sqlcipher.DatabaseUtils.InsertHelper;
import net.sqlcipher.database.SQLiteDatabase;

import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.ContentValues;
import android.content.SyncResult;
import android.database.Cursor;
import android.util.Log;

import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.providers.DataModel;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.providers.Db;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;
import com.alphabetbloc.clinic.utilities.XformUtils;

public class SyncManager {

	private static final String TAG = SyncManager.class.getSimpleName();
	static String date = null;
	private static final SimpleDateFormat OUTPUT_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
	private static final SimpleDateFormat INPUT_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	
	// UPLOAD SECTION:
	public static String[] getInstancesToUpload() {

		ArrayList<String> selectedInstances = new ArrayList<String>();

		Cursor c = Db.open().fetchFormInstancesByStatus(DataModel.STATUS_UNSUBMITTED);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String dbPath = c.getString(c.getColumnIndex(DataModel.KEY_PATH));
					selectedInstances.add(dbPath);
				} while (c.moveToNext());
			}
			c.close();
		}

		return selectedInstances.toArray(new String[selectedInstances.size()]);
	}

	public static String updateUploadedForms(String[] uploaded, SyncResult syncResult) {
		
		StringBuilder error = new StringBuilder();
		String e = null;
		
		if (uploaded.length > 0) {
			// update the databases with new status submitted
			for (int i = 0; i < uploaded.length; i++) {
				String path = uploaded[i];
				
				// update clinic
				e = SyncManager.updateClinicInstances(path, syncResult);
				if (e != null)
					error.append(" Clinic Form ").append(i).append(": ").append(e);
				
				// update collect
				e = SyncManager.updateCollectInstances(path, syncResult);
				if (e != null)
					error.append(" Collect Form ").append(i).append(": ").append(e);
			}

			// Encrypt the uploaded data with wakelock to ensure it happens!
			WakefulIntentService.sendWakefulWork(App.getApp(), EncryptionService.class);
		}

		return error.toString();

	}

	public static String updateClinicInstances(String path, SyncResult syncResult) {

		try {
			Cursor c = Db.open().fetchFormInstancesByPath(path);
			if (c != null) {
				// TODO! Make sure we are deleting the submitted instances from
				// Db
				// after encryption!
				Db.open().updateFormInstance(path, DataModel.STATUS_SUBMITTED);
				c.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		return null;
	}

	public static String updateCollectInstances(String path, SyncResult syncResult) {

		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
			String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
			String whereArgs[] = { path };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				++syncResult.stats.numIoExceptions;
				Log.e(TAG, "Error! updated more than one entry when tyring to update: " + path.toString());
			} else if (updatedrows == 1) {
				Log.i(TAG, "Instance successfully updated to Submitted Status");
			} else {
				++syncResult.stats.numIoExceptions;
				Log.e(TAG, "Error, Instance doesn't exist but we have its path!! " + path.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		return null;
	}

	// DOWNLOAD FORMS SECTION
	/**
	 * Inserts a list of forms into the clinic form list database
	 * 
	 * @param doc
	 *            Document created from parsed input stream
	 * @throws Exception
	 */
	public static ArrayList<Form> readFormListStream(InputStream is) throws Exception {

		ArrayList<Form> allForms = new ArrayList<Form>();
		Document doc = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		doc = db.parse(is);
		if (doc == null)
			return allForms;

		// clean existing
		DbProvider dbHelper = DbProvider.openDb();
		dbHelper.delete(DataModel.FORMS_TABLE, null, null);

		// make new form list
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
			Db.open().createForm(f);
			allForms.add(f);
		}

		return allForms;
	}

	public static String updateDownloadedForms(Form[] downloaded, SyncResult syncResult) {
		StringBuilder error = new StringBuilder();
		if (downloaded.length > 0) {

			// update the databases with new status submitted
			for (int i = 0; i < downloaded.length; i++) {
				try {
					// update clinic
					Db.open().updateFormPath(downloaded[i].getFormId(), downloaded[i].getPath());

					// update collect
					if (!XformUtils.insertSingleForm(downloaded[i].getPath()))
						UiUtils.toastSyncMessage("ODK Collect not initialized.", true);
				} catch (Exception e) {
					e.printStackTrace();
					++syncResult.stats.numIoExceptions;
					error.append(" Download Form ").append(i).append(": ").append(e.getLocalizedMessage());
				}
			}
		}

		return error.toString();

	}

	// DOWNLOAD OBS SECTION
	public static String readObsFile(File tempFile, SyncResult syncResult) {

		if (tempFile == null)
			return "error";

		try {

			DataInputStream dis = new DataInputStream(new FileInputStream(tempFile));

			if (dis != null) {
				DbProvider dbHelper = DbProvider.openDb();
				// open db and clean entries
				dbHelper.delete(DataModel.PATIENTS_TABLE, DataModel.KEY_CLIENT_CREATED + " IS NULL OR " + DataModel.KEY_CLIENT_CREATED + "=?", new String[] { "2" });
				dbHelper.delete(DataModel.OBSERVATIONS_TABLE, null, null);
				dbHelper.delete(DataModel.FORMINSTANCES_TABLE, null, null);

				insertPatients(dis);
				insertObservations(dis);
				insertPatientForms(dis);

				dis.close();
			}
			FileUtils.deleteFile(tempFile.getAbsolutePath());

			SyncManager.updateClinicObs();
		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		return null;
	}

	public static void updateClinicObs() {
		// sync db

		Db.open().updatePriorityFormNumbers();
		Db.open().updatePriorityFormList();
		Db.open().updateSavedFormNumbers();
		Db.open().updateSavedFormsList();

		// log the event
		Db.open().createDownloadLog();
	}

	private static void insertPatientForms(final DataInputStream zdis) throws Exception {
		long start = System.currentTimeMillis();

		SQLiteDatabase db = DbProvider.getDb();
		InsertHelper ih = new InsertHelper(db, DataModel.OBSERVATIONS_TABLE);

		int ptIdIndex = ih.getColumnIndex(DataModel.KEY_PATIENT_ID);
		int obsTextIndex = ih.getColumnIndex(DataModel.KEY_VALUE_TEXT);
		int obsNumIndex = ih.getColumnIndex(DataModel.KEY_VALUE_NUMERIC);
		int obsDateIndex = ih.getColumnIndex(DataModel.KEY_VALUE_DATE);
		int obsIntIndex = ih.getColumnIndex(DataModel.KEY_VALUE_INT);
		int obsFieldIndex = ih.getColumnIndex(DataModel.KEY_FIELD_NAME);
		int obsTypeIndex = ih.getColumnIndex(DataModel.KEY_DATA_TYPE);
		int obsEncDateIndex = ih.getColumnIndex(DataModel.KEY_ENCOUNTER_DATE);

		db.beginTransaction();
		int icount = 0;
		try {
			icount = zdis.readInt();
			Log.i(TAG, "insertPatientForms icount: " + icount);
			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == DataModel.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == DataModel.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == DataModel.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == DataModel.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(zdis.readUTF()));
				}
				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(zdis.readUTF()));
				ih.execute();

			}
			db.setTransactionSuccessful();
		} finally {
			ih.close();
			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertPtsForms", String.format("InsertPtsForms Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) icount / (double) (end - start)));

	}

	private static void insertPatients(DataInputStream zdis) throws Exception {

		long start = System.currentTimeMillis();
		SQLiteDatabase db = DbProvider.getDb();

		InsertHelper ih = new InsertHelper(db, DataModel.PATIENTS_TABLE);
		int ptIdIndex = ih.getColumnIndex(DataModel.KEY_PATIENT_ID);
		int ptIdentifierIndex = ih.getColumnIndex(DataModel.KEY_IDENTIFIER);
		int ptGivenIndex = ih.getColumnIndex(DataModel.KEY_GIVEN_NAME);
		int ptFamilyIndex = ih.getColumnIndex(DataModel.KEY_FAMILY_NAME);
		int ptMiddleIndex = ih.getColumnIndex(DataModel.KEY_MIDDLE_NAME);
		int ptBirthIndex = ih.getColumnIndex(DataModel.KEY_BIRTH_DATE);
		int ptGenderIndex = ih.getColumnIndex(DataModel.KEY_GENDER);

		db.beginTransaction();
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

			}
			db.setTransactionSuccessful();
		} finally {
			ih.close();
			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertPts", String.format("InsertPts Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) icount / (double) (end - start)));

	}

	private static void insertObservations(DataInputStream zdis) throws Exception {
		long start = System.currentTimeMillis();

		SQLiteDatabase db = DbProvider.getDb();
		InsertHelper ih = new InsertHelper(db, DataModel.OBSERVATIONS_TABLE);
		int ptIdIndex = ih.getColumnIndex(DataModel.KEY_PATIENT_ID);
		int obsTextIndex = ih.getColumnIndex(DataModel.KEY_VALUE_TEXT);
		int obsNumIndex = ih.getColumnIndex(DataModel.KEY_VALUE_NUMERIC);
		int obsDateIndex = ih.getColumnIndex(DataModel.KEY_VALUE_DATE);
		int obsIntIndex = ih.getColumnIndex(DataModel.KEY_VALUE_INT);
		int obsFieldIndex = ih.getColumnIndex(DataModel.KEY_FIELD_NAME);
		int obsTypeIndex = ih.getColumnIndex(DataModel.KEY_DATA_TYPE);
		int obsEncDateIndex = ih.getColumnIndex(DataModel.KEY_ENCOUNTER_DATE);

		db.beginTransaction();
		int icount = 0;
		try {
			icount = zdis.readInt();
			Log.i(TAG, "insertObservations icount: " + icount);

			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == DataModel.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == DataModel.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == DataModel.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == DataModel.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(zdis.readUTF()));
				}

				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(zdis.readUTF()));
				ih.execute();

			}

			db.setTransactionSuccessful();
		} finally {
			ih.close();

			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertObs", String.format("InsertObs Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) icount / (double) (end - start)));

	}

	private static String parseDate(final String s) {
		date = s.split("T")[0];
		try {
			return OUTPUT_FORMAT.format(INPUT_FORMAT.parse(date));
		} catch (ParseException e) {
			return "Unknown date";
		}
	}

}
