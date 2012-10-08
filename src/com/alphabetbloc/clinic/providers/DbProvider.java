package com.alphabetbloc.clinic.providers;

import java.io.File;
import java.util.ArrayList;

import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteConstraintException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import net.sqlcipher.database.SQLiteQueryBuilder;

import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.alphabetbloc.clinic.data.ActivityLog;
import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.data.FormInstance;
import com.alphabetbloc.clinic.data.Observation;
import com.alphabetbloc.clinic.data.Patient;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.FileUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author Yaw Anokwa (I think the only one... but could be Sam Mbugua?)
 */
public class DbProvider extends ContentProvider {
	
	private final static String TAG = DbProvider.class.getSimpleName();
	private static DbProvider instance;
	private static SQLiteDatabase sDb;

	public static class DatabaseHelper extends SQLiteOpenHelper {

		// we create this once from the App singleton
		public DatabaseHelper(Context context) {
			super(context, Db.DATABASE_NAME, null, Db.DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.e(TAG, "creating tables");
			db.execSQL(Db.CREATE_PATIENTS_TABLE);
			db.execSQL(Db.CREATE_OBSERVATIONS_TABLE);
			db.execSQL(Db.CREATE_FORMS_TABLE);
			db.execSQL(Db.CREATE_FORMINSTANCES_TABLE);
			db.execSQL(Db.CREATE_FORM_LOG_TABLE);
			db.execSQL(Db.CREATE_DOWNLOAD_LOG_TABLE);
		}

		@Override
		// upgrading will destroy all old data
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + Db.PATIENTS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + Db.OBSERVATIONS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + Db.FORMS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + Db.FORMINSTANCES_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + Db.FORM_LOG_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + Db.DOWNLOAD_LOG_TABLE);
			onCreate(db);
		}
	}

	public static void createDb() {
		String password = EncryptionUtil.getPassword();
		if (password != null)
			sDb = App.getDb(password);
		else {
			// we lost our db password! : ask user for it...
			Log.e(TAG, "We lost the DB Password!");
		}

	}

	public static DbProvider openDb() {
		if (!isOpen()) {
			Log.w(TAG, "Database is not open! Opening Now!");
			createDb();
		}

		return getInstance();
	}

	public static synchronized DbProvider getInstance() {
		if (instance == null) {
			instance = new DbProvider();
		}
		return instance;
	}

	public static boolean isOpen() {
		if (sDb != null && sDb.isOpen())
			return true;
		else
			return false;
	}

	public static boolean rekeyDb(String password) {
		Exception error = null;

		try {
			sDb.execSQL("PRAGMA rekey = '" + password + "'");
			System.gc();
		} catch (Exception e) {
			error = e;
			Log.e(TAG, "Error rekeying the database: " + e.getMessage());
		}

		if (error == null)
			return true;
		else
			return false;
	}

	public static void lock() {
		sDb.close();
		sDb = null;
	}

	public static SQLiteDatabase getDb() {
		if (sDb == null)
			createDb();

		return sDb;
	}

	// TODO Remove the need to pass in Patient/Observation objects. Saves object
	// creation
	/**
	 * Generate text for the first level of the row display.
	 * 
	 * @param path
	 *            path to the file
	 * @param type
	 *            type of the file
	 * @return name of the file formatted as human readable string
	 */
	/*
	 * private String generateDisplay(String path) { String filename =
	 * path.substring(path.lastIndexOf("/") + 1);
	 * 
	 * //remove the form id filename=filename.substring(filename.indexOf('_') +
	 * 1);
	 * 
	 * // remove time stamp from instance String r =
	 * "\\_[0-9]{4}\\-[0-9]{2}\\-[0-9]{2}\\_[0-9]{2}\\-[0-9]{2}\\-[0-9]{2}\\.xml$"
	 * ; Pattern p = Pattern.compile(r); return "Patient (" +
	 * p.split(filename)[0] + ")"; }
	 */

	/**
	 * Create new client and add to local database.
	 * 
	 * @param patient
	 *            Patient object containing patient info
	 * @return database id of the new patient
	 */
	public long createPatient(Patient patient) {

		ContentValues cv = new ContentValues();
		cv.put(Db.KEY_PATIENT_ID, patient.getPatientId());
		cv.put(Db.KEY_IDENTIFIER, patient.getIdentifier());
		cv.put(Db.KEY_GIVEN_NAME, patient.getGivenName());
		cv.put(Db.KEY_FAMILY_NAME, patient.getFamilyName());
		cv.put(Db.KEY_MIDDLE_NAME, patient.getMiddleName());
		cv.put(Db.KEY_BIRTH_DATE, patient.getBirthdate());
		cv.put(Db.KEY_GENDER, patient.getGender());
		cv.put(Db.KEY_CLIENT_CREATED, patient.getCreateCode());
		cv.put(Db.KEY_UUID, patient.getUuid());

		long id = -1;
		try {
			id = sDb.insert(Db.PATIENTS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(TAG, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createObservation(Observation obs) {

		ContentValues cv = new ContentValues();
		cv.put(Db.KEY_PATIENT_ID, obs.getPatientId());
		cv.put(Db.KEY_VALUE_TEXT, obs.getValueText());
		cv.put(Db.KEY_VALUE_NUMERIC, obs.getValueNumeric());
		cv.put(Db.KEY_VALUE_DATE, obs.getValueDate());
		cv.put(Db.KEY_VALUE_INT, obs.getValueInt());
		cv.put(Db.KEY_FIELD_NAME, obs.getFieldName());
		cv.put(Db.KEY_DATA_TYPE, obs.getDataType());
		cv.put(Db.KEY_ENCOUNTER_DATE, obs.getEncounterDate());

		long id = -1;
		try {
			id = sDb.insert(Db.OBSERVATIONS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(TAG, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createForm(Form form) {

		ContentValues cv = new ContentValues();
		cv.put(Db.KEY_FORM_ID, form.getFormId());
		cv.put(Db.KEY_NAME, form.getName());
		cv.put(Db.KEY_PATH, form.getPath());

		long id = -1;
		try {
			id = sDb.insert(Db.FORMS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(TAG, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createFormInstance(FormInstance instance, String title) {

		ContentValues cv = new ContentValues();
		cv.put(Db.KEY_PATIENT_ID, instance.getPatientId());
		cv.put(Db.KEY_FORM_ID, instance.getFormId());
		cv.put(Db.KEY_FORMINSTANCE_STATUS, instance.getStatus());
		cv.put(Db.KEY_PATH, instance.getPath());
		cv.put(Db.KEY_FORMINSTANCE_DISPLAY, title);
		cv.put(Db.KEY_FORMINSTANCE_SUBTEXT, instance.getCompletionSubtext());

		long id = -1;
		try {
			id = sDb.insert(Db.FORMINSTANCES_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(TAG, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	// / ACTIVITY LOG SECTION /////////////////////////////
	public void createDownloadLog() {
		Long downloadTime = Long.valueOf(System.currentTimeMillis());
		ContentValues cv = new ContentValues();

		cv.put(Db.DOWNLOAD_TIME, downloadTime);

		try {
			sDb.insert(Db.DOWNLOAD_LOG_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(TAG, "Caught SQLiteConstraitException: " + e);
		}

	}

	public Long createActivityLog(ActivityLog activitylog) {

		ContentValues cv = new ContentValues();
		cv.put(Db.PROVIDER_ID, activitylog.getProviderId());
		cv.put(Db.PATIENT_ID, activitylog.getPatientId());
		cv.put(Db.FORM_NAME, activitylog.getFormId());
		cv.put(Db.FORM_PRIORITY_BOOLEAN, activitylog.getFormPriority());
		cv.put(Db.FORM_START_TIME, activitylog.getActivityStartTime());
		cv.put(Db.FORM_STOP_TIME, activitylog.getActivityStopTime());
		cv.put(Db.FORM_LAUNCH_TYPE, activitylog.getFormType());

		long id = -1;
		sDb.beginTransaction();
		try {
			id = sDb.insert(Db.FORM_LOG_TABLE, null, cv);
			sDb.setTransactionSuccessful();
		} catch (SQLiteConstraintException e) {
			Log.e(TAG, "Caught SQLiteConstraitException: " + e);

		} finally {
			sDb.endTransaction();
		}
		return id;
	}

	// ////// DELETE SECTION //////////////////////////////
	/**
	 * Remove all patients from the database.
	 * 
	 * @return number of affected rows
	 */

	public boolean deleteAllPatients() {
		return sDb.delete(Db.PATIENTS_TABLE, Db.KEY_CLIENT_CREATED + " IS NULL OR " + Db.KEY_CLIENT_CREATED + "=?", new String[] { "2" }) > 0;
	}

	public boolean deleteAllObservations() {
		return sDb.delete(Db.OBSERVATIONS_TABLE, null, null) > 0;
	}

	public boolean deleteAllForms() {
		return sDb.delete(Db.FORMS_TABLE, null, null) > 0;
	}

	public boolean deleteAllFormInstances() {
		return sDb.delete(Db.FORMINSTANCES_TABLE, null, null) > 0;
	}

	public boolean deleteFormInstance(Integer patientId, Integer formId) {
		return sDb.delete(Db.FORMINSTANCES_TABLE, Db.KEY_PATIENT_ID + "=" + patientId + " AND " + Db.KEY_FORM_ID + "=" + formId, null) > 0;
	}

	public boolean deleteFormInstance(long id) {
		return sDb.delete(Db.FORMINSTANCES_TABLE, Db.KEY_ID + "=" + id, null) > 0;
	}

	public boolean deleteFormInstance(String path) {
		return sDb.delete(Db.FORMINSTANCES_TABLE, Db.KEY_PATH + "=" + path, null) > 0;
	}

	// /////// FETCH SECTION ////////////////////////////
	/**
	 * Get a cursor to multiple patients from the database.
	 * 
	 * @param name
	 *            name matching a patient
	 * @param identifier
	 *            identifier matching a patient
	 * @return cursor to the file
	 * @throws SQLException
	 */
	public Cursor fetchPatients(String name, String identifier, int listtype) throws SQLException {
		Cursor c = null;
		String listSelection = "";
		String listOrder = Db.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
		switch (listtype) {
		case DashboardActivity.LIST_SUGGESTED:
			listSelection = " AND " + Db.KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL";
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listSelection = " AND " + Db.KEY_SAVED_FORM_NUMBER + " IS NOT NULL";
			break;
		case DashboardActivity.LIST_COMPLETE:
			listSelection = " AND " + Db.PATIENTS_TABLE + "." + Db.KEY_PATIENT_ID + "=" + Db.FORMINSTANCES_TABLE + "." + Db.KEY_PATIENT_ID;
			break;
		case DashboardActivity.LIST_SIMILAR_CLIENTS:
			listSelection = "";
			break;
		case DashboardActivity.LIST_ALL:
			listSelection = "";
			break;
		}

		if (name != null) {
			// search using name
			// remove all wildcard characters

			name = name.replaceAll("\\*", "");
			name = name.replaceAll("%", "");
			name = name.replaceAll("_", "");

			name = name.replaceAll("  ", " ");
			name = name.replace(", ", " ");
			String[] names = name.split(" ");

			StringBuilder expr = new StringBuilder();

			expr.append("(");
			for (int i = 0; i < names.length; i++) {
				String n = names[i];
				if (n != null && n.length() > 0) {
					if (listtype == DashboardActivity.LIST_SIMILAR_CLIENTS) {
						if (i == 0) {

							expr.append(Db.KEY_GIVEN_NAME + " LIKE '" + n + "%'");
						} else if (i == 1) {

							expr.append(" AND ");
							expr.append(Db.KEY_FAMILY_NAME + " LIKE '" + n + "%'");
						}
					} else {
						expr.append(Db.KEY_GIVEN_NAME + " LIKE '" + n + "%'");
						expr.append(" OR ");
						expr.append(Db.KEY_FAMILY_NAME + " LIKE '" + n + "%'");
						expr.append(" OR ");
						expr.append(Db.KEY_MIDDLE_NAME + " LIKE '" + n + "%'");
						if (i < names.length - 1)
							expr.append(" OR ");
					}
				}
			}
			expr.append(")");

			if (listtype == DashboardActivity.LIST_COMPLETE) {
				c = sDb.query(true, Db.PATIENTS_TABLE + ", " + Db.FORMINSTANCES_TABLE, new String[] { Db.PATIENTS_TABLE + "." + Db.KEY_ID, Db.PATIENTS_TABLE + "." + Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER,
						Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER, Db.KEY_SAVED_FORM_NUMBER, Db.KEY_SAVED_FORM_NAMES, Db.KEY_FORMINSTANCE_SUBTEXT, Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, "(" + expr.toString() + ")" + listSelection, null, Db.PATIENTS_TABLE + "." + Db.KEY_PATIENT_ID, null,
						listOrder, null);
			} else {
				c = sDb.query(true, Db.PATIENTS_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER, Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER, Db.KEY_SAVED_FORM_NUMBER,
						Db.KEY_SAVED_FORM_NAMES, Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, expr.toString() + listSelection, null, null, null, listOrder, null);
			}
		} else if (identifier != null) {
			// search using identifier

			// escape all wildcard characters
			identifier = identifier.replaceAll("\\*", "^*");
			identifier = identifier.replaceAll("%", "^%");
			identifier = identifier.replaceAll("_", "^_");

			if (listtype == DashboardActivity.LIST_COMPLETE) {
				c = sDb.query(true, Db.PATIENTS_TABLE + ", " + Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER, Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER,
						Db.KEY_SAVED_FORM_NUMBER, Db.KEY_SAVED_FORM_NAMES, Db.KEY_FORMINSTANCE_SUBTEXT, Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, Db.KEY_IDENTIFIER + " LIKE '" + identifier + "%' ESCAPE '^'" + listSelection, null, null, null, listOrder, null);
			} else {
				c = sDb.query(true, Db.PATIENTS_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER, Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER, Db.KEY_SAVED_FORM_NUMBER,
						Db.KEY_SAVED_FORM_NAMES, Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, Db.KEY_IDENTIFIER + " LIKE '" + identifier + "%' ESCAPE '^'" + listSelection, null, Db.PATIENTS_TABLE + "." + Db.KEY_PATIENT_ID, null, listOrder, null);
			}

		}

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPatient(Integer patientId) throws SQLException {
		Cursor c = null;
		String listSelection = Db.KEY_PATIENT_ID + "=" + patientId;
		String listOrder = Db.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";

		c = sDb.query(true, Db.PATIENTS_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER, Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER, Db.KEY_SAVED_FORM_NUMBER, Db.KEY_SAVED_FORM_NAMES,
				Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, listSelection, null, null, null, listOrder, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPatient(String uuidString) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.PATIENTS_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER, Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER, Db.KEY_SAVED_FORM_NUMBER, Db.KEY_SAVED_FORM_NAMES,
				Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, Db.KEY_UUID + "=" + uuidString, null, null, null, Db.KEY_PRIORITY_FORM_NUMBER + " DESC, " + Db.KEY_FAMILY_NAME + " COLLATE NOCASE ASC", null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchAllPatients(int listtype) throws SQLException {
		Cursor c = null;

		String listSelection = "";
		String listOrder = "";
		String groupBy = "";
		switch (listtype) {
		case DashboardActivity.LIST_SUGGESTED:
			listSelection = Db.KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL";
			listOrder = Db.KEY_PRIORITY_FORM_NUMBER + " DESC, " + Db.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listSelection = Db.KEY_SAVED_FORM_NUMBER + " IS NOT NULL";
			listOrder = Db.KEY_SAVED_FORM_NUMBER + " DESC, " + Db.KEY_PRIORITY_FORM_NUMBER + " DESC, " + Db.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			break;
		case DashboardActivity.LIST_COMPLETE:
			listSelection = Db.PATIENTS_TABLE + "." + Db.KEY_PATIENT_ID + "=" + Db.FORMINSTANCES_TABLE + "." + Db.KEY_PATIENT_ID;
			listOrder = Db.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			groupBy = Db.PATIENTS_TABLE + "." + Db.KEY_PATIENT_ID;
			break;
		case DashboardActivity.LIST_ALL:
			listSelection = "";
			listOrder = Db.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";

			break;
		}
		Log.e("louis.fazen", "ClinicAdapter listorder=" + listOrder);

		if (listtype == DashboardActivity.LIST_COMPLETE) {
			c = sDb.query(true, Db.PATIENTS_TABLE + ", " + Db.FORMINSTANCES_TABLE, new String[] { Db.PATIENTS_TABLE + "." + Db.KEY_ID, Db.PATIENTS_TABLE + "." + Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER,
					Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER, Db.KEY_SAVED_FORM_NUMBER, Db.KEY_SAVED_FORM_NAMES, Db.KEY_FORMINSTANCE_SUBTEXT, Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, listSelection, null, groupBy, null, listOrder, null);
		} else {
			c = sDb.query(true, Db.PATIENTS_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_IDENTIFIER, Db.KEY_GIVEN_NAME, Db.KEY_FAMILY_NAME, Db.KEY_MIDDLE_NAME, Db.KEY_BIRTH_DATE, Db.KEY_GENDER, Db.KEY_PRIORITY_FORM_NAMES, Db.KEY_PRIORITY_FORM_NUMBER, Db.KEY_SAVED_FORM_NUMBER, Db.KEY_SAVED_FORM_NAMES,
					Db.KEY_CLIENT_CREATED, Db.KEY_UUID }, listSelection, null, null, null, listOrder, null);
		}
		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchAllForms() throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMS_TABLE, new String[] { Db.KEY_ID, Db.KEY_FORM_ID, Db.KEY_NAME, Db.KEY_PATH }, null, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchAllObservations() throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.OBSERVATIONS_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID }, null, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	// moved this here, as wanted it in several places...!
	public ArrayList<Observation> fetchPatientObservationList(Integer patientId) {
		ArrayList<Observation> mObservations = new ArrayList<Observation>();
		mObservations.clear();

		Cursor c = fetchPatientObservations(patientId);

		if (c != null && c.getCount() > 0) {

			int valueTextIndex = c.getColumnIndex(Db.KEY_VALUE_TEXT);
			int valueIntIndex = c.getColumnIndex(Db.KEY_VALUE_INT);
			int valueDateIndex = c.getColumnIndex(Db.KEY_VALUE_DATE);
			int valueNumericIndex = c.getColumnIndex(Db.KEY_VALUE_NUMERIC);
			int fieldNameIndex = c.getColumnIndex(Db.KEY_FIELD_NAME);
			int encounterDateIndex = c.getColumnIndex(Db.KEY_ENCOUNTER_DATE);
			int dataTypeIndex = c.getColumnIndex(Db.KEY_DATA_TYPE);

			Observation obs;
			String prevFieldName = null;
			do {
				String fieldName = c.getString(fieldNameIndex);

				// We only want most recent observation, so only get first
				// observation
				if (!fieldName.equals(prevFieldName)) {

					obs = new Observation();
					obs.setFieldName(fieldName);
					obs.setEncounterDate(c.getString(encounterDateIndex));

					int dataType = c.getInt(dataTypeIndex);
					obs.setDataType((byte) dataType);
					switch (dataType) {
					case Db.TYPE_INT:
						obs.setValueInt(c.getInt(valueIntIndex));
						break;
					case Db.TYPE_DOUBLE:
						obs.setValueNumeric(c.getDouble(valueNumericIndex));
						break;
					case Db.TYPE_DATE:
						obs.setValueDate(c.getString(valueDateIndex));

						break;
					default:
						obs.setValueText(c.getString(valueTextIndex));
					}

					mObservations.add(obs);

					prevFieldName = fieldName;
				}

			} while (c.moveToNext());
		}

		if (c != null) {
			c.close();
		}

		return mObservations;

	}

	public Cursor fetchAllFormInstances() throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_FORM_ID }, null, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchForm(Integer formId) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMS_TABLE, new String[] { Db.KEY_ID, Db.KEY_NAME, Db.KEY_PATH }, Db.KEY_FORM_ID + "=" + formId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstance(Integer patientId, Integer formId) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_FORMINSTANCE_STATUS, Db.KEY_PATH, Db.KEY_FORMINSTANCE_DISPLAY }, Db.KEY_PATIENT_ID + "=" + patientId + " AND " + Db.KEY_FORM_ID + "=" + formId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPatientFormInstancesByStatus(Integer patientId, Integer formId, String status) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_FORMINSTANCE_STATUS, Db.KEY_PATH, Db.KEY_FORMINSTANCE_DISPLAY }, Db.KEY_PATIENT_ID + "=" + patientId + " AND " + Db.KEY_FORM_ID + "=" + formId + " AND " + Db.KEY_FORMINSTANCE_STATUS + "='" + status + "'", null, null,
				null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstances(Integer patientId) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_FORM_ID, Db.KEY_FORMINSTANCE_STATUS, Db.KEY_PATH, Db.KEY_FORMINSTANCE_DISPLAY }, Db.KEY_PATIENT_ID + "=" + patientId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchCompletedByPatientId(Integer patientId) throws SQLException {

		String query = SQLiteQueryBuilder.buildQueryString(
		// distinct
				true,
				// tables
				Db.FORMS_TABLE + ", " + Db.FORMINSTANCES_TABLE,
				// columns
				new String[] { Db.FORMINSTANCES_TABLE + "." + Db.KEY_ID + ", " + Db.FORMINSTANCES_TABLE + "." + Db.KEY_FORM_ID + ", " + Db.FORMINSTANCES_TABLE + "." + Db.KEY_FORMINSTANCE_DISPLAY + ", " + Db.FORMINSTANCES_TABLE + "." + Db.KEY_PATH + ", " + Db.FORMS_TABLE + "." + Db.KEY_FORM_NAME + ", "
						+ Db.FORMINSTANCES_TABLE + "." + Db.KEY_FORMINSTANCE_SUBTEXT },
				// where
				Db.FORMINSTANCES_TABLE + "." + Db.KEY_PATIENT_ID + "=" + patientId + " AND " + Db.FORMS_TABLE + "." + Db.KEY_FORM_ID + "=" + Db.FORMINSTANCES_TABLE + "." + Db.KEY_FORM_ID,
				// group by, having
				null, null,
				// order by Db.KEY_ID... not importing the completed date, since
				// these should be uploaded regularly and therefore there is no
				// reason to do it
				Db.FORMINSTANCES_TABLE + "." + Db.KEY_ID + " desc",
				// limit
				null);

		Cursor c = null;
		c = sDb.rawQuery(query, null);
		// .query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID },
		// Db.KEY_PATIENT_ID + "=" + patientId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPriorityFormIdByPatientId(Integer patientId) throws SQLException {
		Cursor c = null;
		String selection = Db.KEY_FIELD_NAME + "=" + Db.KEY_FIELD_FORM_VALUE + " and " + Db.KEY_PATIENT_ID + "=" + patientId;
		c = sDb.query(true, Db.OBSERVATIONS_TABLE, new String[] { Db.KEY_VALUE_INT }, selection, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchCompletedFormIdByPatientId(Integer patientId) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_FORM_ID }, Db.KEY_PATIENT_ID + "=" + patientId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstance(long id) throws SQLException {
		Cursor c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_FORM_ID, Db.KEY_FORMINSTANCE_STATUS, Db.KEY_PATH, Db.KEY_FORMINSTANCE_DISPLAY }, Db.KEY_ID + "= ?", new String[] { Long.toString(id) }, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstancesByStatus(String status) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_PATIENT_ID, Db.KEY_PATH, Db.KEY_FORMINSTANCE_DISPLAY, Db.KEY_FORMINSTANCE_STATUS }, Db.KEY_FORMINSTANCE_STATUS + "='" + status + "'", null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstancesByPath(String path) throws SQLException {
		Cursor c = null;
		c = sDb.query(true, Db.FORMINSTANCES_TABLE, new String[] { Db.KEY_ID, Db.KEY_FORM_ID, Db.KEY_FORMINSTANCE_STATUS, Db.KEY_PATH, Db.KEY_FORMINSTANCE_DISPLAY }, Db.KEY_PATH + "='" + path + "'", null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public int findLastClientCreatedId() throws SQLException {
		int lowestNegativeId = 0;
		Cursor c = null;
		c = sDb.query(Db.PATIENTS_TABLE, new String[] { "min(" + Db.KEY_PATIENT_ID + ") AS " + Db.KEY_PATIENT_ID }, null, null, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				lowestNegativeId = c.getInt(c.getColumnIndex(Db.KEY_PATIENT_ID));
			}
			c.close();
		}

		return lowestNegativeId;

	}

	public Cursor fetchPatientObservations(Integer patientId) throws SQLException {
		Cursor c = null;
		// TODO removing an extra Db.KEY_VALUE_TEXT doesn't screw things up?
		c = sDb.query(Db.OBSERVATIONS_TABLE, new String[] { Db.KEY_VALUE_TEXT, Db.KEY_DATA_TYPE, Db.KEY_VALUE_NUMERIC, Db.KEY_VALUE_DATE, Db.KEY_VALUE_INT, Db.KEY_FIELD_NAME, Db.KEY_ENCOUNTER_DATE }, Db.KEY_PATIENT_ID + "=" + patientId, null, null, null, Db.KEY_FIELD_NAME + "," + Db.KEY_ENCOUNTER_DATE + " desc");

		if (c != null)
			c.moveToFirst();

		return c;
	}

	public Cursor fetchPatientObservation(Integer patientId, String fieldName) throws SQLException {
		Cursor c = null;

		c = sDb.query(Db.OBSERVATIONS_TABLE, new String[] { Db.KEY_VALUE_TEXT, Db.KEY_DATA_TYPE, Db.KEY_VALUE_NUMERIC, Db.KEY_VALUE_DATE, Db.KEY_VALUE_INT, Db.KEY_ENCOUNTER_DATE }, Db.KEY_PATIENT_ID + "=" + patientId + " AND " + Db.KEY_FIELD_NAME + "='" + fieldName + "'", null, null, null,
				Db.KEY_ENCOUNTER_DATE + " desc");

		if (c != null)
			c.moveToFirst();

		return c;
	}

	public long fetchMostRecentDownload() {
		Cursor c = null;
		long datetime = 0;
		c = sDb.query(Db.DOWNLOAD_LOG_TABLE, new String[] { "MAX(" + Db.DOWNLOAD_TIME + ") AS " + Db.DOWNLOAD_TIME }, null, null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				do {
					datetime = c.getLong(c.getColumnIndex(Db.DOWNLOAD_TIME));
				} while (c.moveToNext());
			}
			c.close();
		}

		return datetime;
	}

	// PRIORITY FORMS SECTION //////////////////////////////////
	public void updatePriorityFormList() throws SQLException {
		Cursor c = null;

		String subquery = SQLiteQueryBuilder.buildQueryString(
		// include distinct
				true,
				// FROM tables
				Db.FORMS_TABLE + "," + Db.OBSERVATIONS_TABLE,
				// two columns (one of which is a group_concat()
				new String[] { Db.OBSERVATIONS_TABLE + "." + Db.KEY_PATIENT_ID + ", group_concat(" + Db.FORMS_TABLE + "." + Db.KEY_FORM_NAME + ",\", \") AS " + Db.KEY_PRIORITY_FORM_NAMES },
				// where
				Db.OBSERVATIONS_TABLE + "." + Db.KEY_FIELD_NAME + "=" + Db.KEY_FIELD_FORM_VALUE + " AND " + Db.FORMS_TABLE + "." + Db.KEY_FORM_ID + "=" + Db.OBSERVATIONS_TABLE + "." + Db.KEY_VALUE_INT,
				// group by
				Db.OBSERVATIONS_TABLE + "." + Db.KEY_PATIENT_ID, null, null, null);

		c = sDb.rawQuery(subquery, null);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(Db.KEY_PATIENT_ID));
					String formName = c.getString(c.getColumnIndex(Db.KEY_PRIORITY_FORM_NAMES));
					ContentValues cv = new ContentValues();
					cv.put(Db.KEY_PRIORITY_FORM_NAMES, formName);

					sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			}
			c.close();
		}
		
	}

	public void updatePriorityFormNumbers() throws SQLException {

		Cursor c = null;
		c = sDb.query(Db.OBSERVATIONS_TABLE, new String[] { Db.KEY_PATIENT_ID, "count(*) as " + Db.KEY_PRIORITY_FORM_NUMBER }, Db.KEY_FIELD_NAME + "=" + Db.KEY_FIELD_FORM_VALUE, null, Db.KEY_PATIENT_ID, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(Db.KEY_PATIENT_ID));
					int formNumber = c.getInt(c.getColumnIndex(Db.KEY_PRIORITY_FORM_NUMBER));
					ContentValues cv = new ContentValues();
					cv.put(Db.KEY_PRIORITY_FORM_NUMBER, formNumber);

					sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + patientId, null);

				} while (c.moveToNext());
			}
			c.close();
		}
		
	}

	public void updatePriorityFormsByPatientId(String updatePatientId, String formId) throws SQLException {
		// int deleted = sDb.delete(Db.OBSERVATIONS_TABLE, Db.KEY_PATIENT_ID +
		// "=? AND " + Db.KEY_FIELD_NAME + "=? AND " + Db.KEY_VALUE_INT + "=?", new
		// String[] { updatePatientId, Db.KEY_FIELD_FORM_VALUE, formId });
		int deleted = sDb.delete(Db.OBSERVATIONS_TABLE, Db.KEY_PATIENT_ID + "=" + updatePatientId + " AND " + Db.KEY_FIELD_NAME + "=" + Db.KEY_FIELD_FORM_VALUE + " AND " + Db.KEY_VALUE_INT + "=" + formId, null);
		if (deleted > 0) {

			Cursor c = null;
			// 1. Update the PriorityFormNumbers
			c = sDb.query(Db.OBSERVATIONS_TABLE, new String[] { Db.KEY_PATIENT_ID, "count(*) as " + Db.KEY_PRIORITY_FORM_NUMBER }, Db.KEY_FIELD_NAME + "=? AND " + Db.KEY_PATIENT_ID + "=?", new String[] { Db.KEY_FIELD_FORM_VALUE, updatePatientId }, Db.KEY_PATIENT_ID, null, null);
			ContentValues cv = new ContentValues();
			if (c != null) {
				if (c.moveToFirst()) {
					do {
						String patientId = c.getString(c.getColumnIndex(Db.KEY_PATIENT_ID));
						int formNumber = c.getInt(c.getColumnIndex(Db.KEY_PRIORITY_FORM_NUMBER));

						cv.put(Db.KEY_PRIORITY_FORM_NUMBER, formNumber);
						sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + patientId, null);

					} while (c.moveToNext());
				} else {
					// } else if
					// (c.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
					cv.putNull(Db.KEY_PRIORITY_FORM_NUMBER);
					sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + updatePatientId, null);

				}

				c.close();
			}

			// 2. Update the PriorityFormNames
			Cursor cname = null;
			String subquery = SQLiteQueryBuilder.buildQueryString(
			// include distinct
					true,
					// FROM tables
					Db.FORMS_TABLE + "," + Db.OBSERVATIONS_TABLE,
					// two columns (one of which is a group_concat()
					new String[] { Db.OBSERVATIONS_TABLE + "." + Db.KEY_PATIENT_ID + ", group_concat(" + Db.FORMS_TABLE + "." + Db.KEY_FORM_NAME + ",\", \") AS " + Db.KEY_PRIORITY_FORM_NAMES },
					// where
					Db.OBSERVATIONS_TABLE + "." + Db.KEY_FIELD_NAME + "=" + Db.KEY_FIELD_FORM_VALUE + " AND " + Db.FORMS_TABLE + "." + Db.KEY_FORM_ID + "=" + Db.OBSERVATIONS_TABLE + "." + Db.KEY_VALUE_INT + " AND " + Db.OBSERVATIONS_TABLE + "." + Db.KEY_PATIENT_ID + "=" + updatePatientId,
					// group by
					Db.OBSERVATIONS_TABLE + "." + Db.KEY_PATIENT_ID, null, null, null);

			cname = sDb.rawQuery(subquery, null);
			ContentValues cvname = new ContentValues();
			if (cname != null) {
				if (cname.moveToFirst()) {
					do {
						String patientId = cname.getString(c.getColumnIndex(Db.KEY_PATIENT_ID));
						String formName = cname.getString(c.getColumnIndex(Db.KEY_PRIORITY_FORM_NAMES));

						cvname.put(Db.KEY_PRIORITY_FORM_NAMES, formName);
						sDb.update(Db.PATIENTS_TABLE, cvname, Db.KEY_PATIENT_ID + "=" + patientId, null);
					} while (cname.moveToNext());
				} else {
					// } else if
					// (cname.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
					cvname.putNull(Db.KEY_PRIORITY_FORM_NAMES);
					sDb.update(Db.PATIENTS_TABLE, cvname, Db.KEY_PATIENT_ID + "=" + updatePatientId, null);

				}

				cname.close();
			}
		}
	}

	// SAVED FORMS SECTION ///////////////////////////////////
	public void updateSavedFormsList() throws SQLException {
		// SELECT observations.patient_id as patientid, group_concat(forms.name,
		// \", \") as name FROM observations INNER JOIN forms
		// WHERE (field_name = \"odkconnector.property.form\" AND value_int =
		// form_id) GROUP BY patient_id;

		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + " IS NOT NULL";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "group_concat( " + InstanceColumns.FORM_NAME + ") AS " + Db.KEY_SAVED_FORM_NAMES }, selection, selectionArgs, null);
		ContentValues cv = new ContentValues();
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
					String formName = c.getString(c.getColumnIndex(Db.KEY_SAVED_FORM_NAMES));
					cv.put(Db.KEY_SAVED_FORM_NAMES, formName);

					sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			}

			c.close();
		}

	}

	// saved forms are kept in the collect database...
	public void updateSavedFormNumbers() throws SQLException {
		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + " IS NOT NULL";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "count(*) as " + InstanceColumns.STATUS }, selection, selectionArgs, null);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
					int formNumber = c.getInt(c.getColumnIndex(InstanceColumns.STATUS));
					ContentValues cv = new ContentValues();
					cv.put(Db.KEY_SAVED_FORM_NUMBER, formNumber);

					sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			}

			c.close();
			
		}

	}

	public void updateSavedFormsListByPatientId(String updatePatientId) throws SQLException {

		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, updatePatientId };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "group_concat( " + InstanceColumns.FORM_NAME + ",\", \") AS " + Db.KEY_SAVED_FORM_NAMES }, selection, selectionArgs, null);

		ContentValues cv = new ContentValues();
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
					String formName = c.getString(c.getColumnIndex(Db.KEY_SAVED_FORM_NAMES));

					cv.put(Db.KEY_SAVED_FORM_NAMES, formName);

					sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			} else {
				// } else if
				// (c.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
				cv.putNull(Db.KEY_SAVED_FORM_NAMES);
				sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + updatePatientId, null);

			}

			c.close();
		}
		
	}

	// saved forms are kept in the collect database...
	public void updateSavedFormNumbersByPatientId(String updatePatientId) throws SQLException {
		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, updatePatientId };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "count(*) as " + InstanceColumns.STATUS }, selection, selectionArgs, null);
		ContentValues cv = new ContentValues();
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
					int formNumber = c.getInt(c.getColumnIndex(InstanceColumns.STATUS));
					cv.put(Db.KEY_SAVED_FORM_NUMBER, formNumber);

					sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			} else {
				cv.putNull(Db.KEY_SAVED_FORM_NUMBER);
				sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "=" + updatePatientId, null);

			}

			c.close();
		}

	}

	// /////////////////// COUNTING SECTION ////////////////////
	public int countAllPriorityFormNumbers() throws SQLException {
		int count = 0;
		Cursor c = null;

		// counting total patients with a priority form:
		c = sDb.query(Db.PATIENTS_TABLE, new String[] { "count(*) AS " + Db.KEY_PRIORITY_FORM_NUMBER }, Db.KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL", null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				count = c.getInt(c.getColumnIndex(Db.KEY_PRIORITY_FORM_NUMBER));
			}
			c.close();
		}
		return count;
	}

	public int countAllSavedFormNumbers() throws SQLException {
		int count = 0;
		Cursor c = null;

		// Count Total Patients with Saved Forms:
		c = sDb.query(Db.PATIENTS_TABLE, new String[] { "count(*) AS " + Db.KEY_SAVED_FORM_NUMBER }, Db.KEY_SAVED_FORM_NUMBER + " IS NOT NULL", null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				count = c.getInt(c.getColumnIndex(Db.KEY_SAVED_FORM_NUMBER));
			}
			c.close();
		}

		return count;
	}

	// Count all patients with completed forms (i.e. group by patient id)
	public int countAllCompletedUnsentForms() throws SQLException {
		int count = 0;

		Cursor c = null;
		c = sDb.query("(SELECT DISTINCT " + Db.KEY_PATIENT_ID + " FROM " + Db.FORMINSTANCES_TABLE + ")", new String[] { "count(" + Db.KEY_PATIENT_ID + ") AS " + Db.KEY_PATIENT_ID }, null, null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				count = c.getInt(c.getColumnIndex(Db.KEY_PATIENT_ID));
			}
			c.close();
		}

		return count;

	}

	public int countAllPatients() throws SQLException {
		long count = 0;
		count = DatabaseUtils.queryNumEntries(sDb, Db.PATIENTS_TABLE);
		int intcount = safeLongToInt(count);
		return intcount;
	}

	public int countAllForms() throws SQLException {
		long count = 0;
		count = DatabaseUtils.queryNumEntries(sDb, Db.FORMS_TABLE);
		int intcount = safeLongToInt(count);
		return intcount;
	}

	public static int safeLongToInt(long l) {
		if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(l + "is outside the bounds of int");
		}
		return (int) l;
	}

	// ///////// UPDATE TABLES SECTION ////////////////////////
	public boolean updateFormInstance(String path, String status) {

		ContentValues cv = new ContentValues();
		cv.put(Db.KEY_FORMINSTANCE_STATUS, status);

		return sDb.update(Db.FORMINSTANCES_TABLE, cv, Db.KEY_PATH + "='" + path + "'", null) > 0;
	}

	public boolean updateInstancePath(Integer id, String newPath) {

		ContentValues cv = new ContentValues();
		cv.put(Db.KEY_PATH, newPath);

		return sDb.update(Db.FORMINSTANCES_TABLE, cv, Db.KEY_ID + "=?", new String[] { String.valueOf(id) }) > 0;
	}

	/**
	 * Update patient in the database.
	 * 
	 * @param patient
	 *            Patient object containing patient info
	 * @return number of affected rows
	 */
	public boolean updatePatient(Patient patient) {

		ContentValues cv = new ContentValues();
		cv.put(Db.KEY_PATIENT_ID, patient.getPatientId());
		cv.put(Db.KEY_IDENTIFIER, patient.getIdentifier());
		cv.put(Db.KEY_GIVEN_NAME, patient.getGivenName());
		cv.put(Db.KEY_FAMILY_NAME, patient.getFamilyName());
		cv.put(Db.KEY_MIDDLE_NAME, patient.getMiddleName());
		cv.put(Db.KEY_BIRTH_DATE, patient.getBirthdate());
		cv.put(Db.KEY_GENDER, patient.getGender());

		return sDb.update(Db.PATIENTS_TABLE, cv, Db.KEY_PATIENT_ID + "='" + patient.getPatientId() + "'", null) > 0;
	}

	public boolean updateFormPath(Integer formId, String path) {
		ContentValues cv = new ContentValues();

		cv.put(Db.KEY_FORM_ID, formId);
		cv.put(Db.KEY_PATH, path);

		return sDb.update(Db.FORMS_TABLE, cv, Db.KEY_FORM_ID + "='" + formId.toString() + "'", null) > 0;
	}

	public void removeOrphanInstances(Context ctx) {
		if (FileUtils.storageReady()) {
			Cursor c = fetchFormInstancesByStatus(Db.STATUS_UNSUBMITTED);
			if (c != null)
				c.moveToFirst();
			while (!c.isAfterLast()) {
				String instancePath = c.getString(c.getColumnIndex(Db.KEY_PATH));
				File f = new File(instancePath);
				if (!f.exists()) {
					if (deleteFormInstance(c.getInt(c.getColumnIndex(Db.KEY_ID)))) {
						Log.i("ClinicAdapter", "Deleted orphan instance from db");
						if (FileUtils.deleteFile(instancePath.substring(0, instancePath.lastIndexOf(File.separator))))
							Log.i("ClinicAdapter", "Deleted orphan instance from filesystem");
						else
							Log.e("ClinicAdapter", "Error deleting orphan instance from filesystem");
					} else
						Log.e("ClinicAdapter", "Error deleting orphan instance from db");
				}
				c.moveToNext();
			}
		}
	}

	// ///////
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean onCreate() {
		// From Collect (this gets called before the app is created, so we do
		// nothing)
		// Collect has it set to true though?
		return false;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

}