package org.odk.clinic.android.database;

import java.io.DataInputStream;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.odk.clinic.android.activities.DashboardActivity;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Cohort;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.DatabaseUtils.InsertHelper;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

public class ClinicAdapter {
	private final static String t = "PatientDbAdapter";

	// general columns
	public static final String KEY_PATH = "path";
	public static final String KEY_NAME = "name";
	public static final String KEY_INSTANCES = "instances";

	// patient columns
	public static final String KEY_ID = "_id";
	public static final String KEY_PATIENT_ID = "patient_id";
	public static final String KEY_IDENTIFIER = "identifier";
	public static final String KEY_GIVEN_NAME = "given_name";
	public static final String KEY_FAMILY_NAME = "family_name";
	public static final String KEY_MIDDLE_NAME = "middle_name";
	public static final String KEY_BIRTH_DATE = "birth_date";
	public static final String KEY_GENDER = "gender";
	public static final String KEY_CLIENT_CREATED = "client_created";
	public static final String KEY_UUID = "uuid";
	public static final String KEY_PRIORITY_FORM_NAMES = "priority_forms";
	public static final String KEY_PRIORITY_FORM_NUMBER = "priority_number";
	public static final String KEY_SAVED_FORM_NAMES = "saved_forms";
	public static final String KEY_SAVED_FORM_NUMBER = "saved_number";

	// observation columns
	public static final String KEY_VALUE_TEXT = "value_text";
	public static final String KEY_VALUE_NUMERIC = "value_numeric";
	public static final String KEY_VALUE_DATE = "value_date";
	public static final String KEY_VALUE_INT = "value_int";
	public static final String KEY_FIELD_NAME = "field_name";
	public static final String KEY_ENCOUNTER_DATE = "encounter_date";
	public static final String KEY_DATA_TYPE = "data_type";

	// observation values
	public static final String KEY_FIELD_FORM_VALUE = "\"odkconnector.property.form\"";

	// cohort columns
	public static final String KEY_COHORT_ID = "cohort_id";

	// form columns
	public static final String KEY_FORM_ID = "form_id";
	public static final String KEY_FORM_NAME = "name";

	// instance columns
	public static final String KEY_FORMINSTANCE_STATUS = "status";
	public static final String KEY_FORMINSTANCE_DISPLAY = "display";
	public static final String KEY_FORMINSTANCE_SUBTEXT = "date_subtext";

	// status for instances
	public static final String STATUS_UNSUBMITTED = "pending-sync";
	public static final String STATUS_SUBMITTED = "submitted";

	// private DateFormat mDateFormat = new
	// SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	// private String mZeroDate = "0000-00-00 00:00:00";

	// louis.fazen is putting in new events table...
	static final String PROVIDER_ID = "provider";
	private static final String PATIENT_ID = "patient";
	private static final String FORM_PRIORITY_BOOLEAN = "form_priority";
	private static final String FORM_NAME = "form_name";
	private static final String FORM_START_TIME = "start_time";
	private static final String FORM_STOP_TIME = "stop_time";
	private static final String FORM_LAUNCH_TYPE = "launch_type";
	private static final String FORM_LOG_TABLE = "form_log";

	// private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	private static final String DOWNLOAD_TIME = "download_time";

	private static final String DATABASE_NAME = "clinic.sqlite3";
	private static final String PATIENTS_TABLE = "patients";
	private static final String OBSERVATIONS_TABLE = "observations";
	private static final String COHORTS_TABLE = "cohorts";
	private static final String FORMS_TABLE = "forms";
	private static final String FORMINSTANCES_TABLE = "instances";
	private static final String DOWNLOAD_LOG_TABLE = "download_log";
	private static final int DATABASE_VERSION = 8;

	private static boolean updateIndices = true;
	// Patient Table
	// static int ptIdIndex_pt = indexcursor.getColumnIndex(KEY_PATIENT_ID_PT);
	private static int ptIdentifierIndex = 0;
	private static int ptGivenIndex = 0;
	private static int ptFamilyIndex = 0;
	private static int ptMiddleIndex = 0;
	private static int ptBirthIndex = 0;
	private static int ptGenderIndex = 0;
	private static int ptFormIndex = 0;
	private static int ptFormNumberIndex = 0;

	// Obs Table
	// note that the KEY_PATIENT_ID has the same Index in all Tables, so this
	// seems to be how Yaw has coded it...?!
	private static int ptIdIndex = 0;
	private static int obsTextIndex = 0;
	private static int obsNumIndex = 0;
	private static int obsDateIndex = 0;
	private static int obsIntIndex = 0;
	private static int obsFieldIndex = 0;
	private static int obsTypeIndex = 0;
	private static int obsEncDateIndex = 0;

	private static final String CREATE_PATIENTS_TABLE = "create table " + PATIENTS_TABLE + " (_id integer primary key autoincrement, " + KEY_PATIENT_ID + " integer not null, " + KEY_IDENTIFIER + " text, " + KEY_GIVEN_NAME + " text, " + KEY_FAMILY_NAME + " text, "
			+ KEY_MIDDLE_NAME + " text, " + KEY_BIRTH_DATE + " text, " + KEY_GENDER + " text, " + KEY_CLIENT_CREATED + " integer, " + KEY_UUID + " text, " + KEY_PRIORITY_FORM_NUMBER + " integer, " + KEY_PRIORITY_FORM_NAMES + " text, " + KEY_SAVED_FORM_NUMBER + " integer, "
			+ KEY_SAVED_FORM_NAMES + " text);";

	private static final String CREATE_OBSERVATIONS_TABLE = "create table " + OBSERVATIONS_TABLE + " (_id integer primary key autoincrement, " + KEY_PATIENT_ID + " integer not null, " + KEY_DATA_TYPE + " integer not null, " + KEY_VALUE_TEXT + " text, " + KEY_VALUE_NUMERIC
			+ " double, " + KEY_VALUE_DATE + " text, " + KEY_VALUE_INT + " integer, " + KEY_FIELD_NAME + " text not null, " + KEY_ENCOUNTER_DATE + " text not null);";

	private static final String CREATE_COHORTS_TABLE = "create table " + COHORTS_TABLE + " (_id integer primary key autoincrement, " + KEY_COHORT_ID + " integer not null, " + KEY_NAME + " text);";

	private static final String CREATE_FORMS_TABLE = "create table " + FORMS_TABLE + " (_id integer primary key autoincrement, " + KEY_FORM_ID + " integer not null, " + KEY_NAME + " text, " + KEY_PATH + " text);";

	private static final String CREATE_FORMINSTANCES_TABLE = "create table " + FORMINSTANCES_TABLE + " (_id integer primary key autoincrement, " + KEY_PATIENT_ID + " integer not null, " + KEY_FORM_ID + " integer not null, " + KEY_FORMINSTANCE_DISPLAY + " text, "
			+ KEY_FORMINSTANCE_SUBTEXT + " text, " + KEY_FORMINSTANCE_STATUS + " text, " + KEY_PATH + " text);";

	private static final String CREATE_FORM_LOG_TABLE = "create table " + FORM_LOG_TABLE + " (_id integer primary key autoincrement, " + PATIENT_ID + " text, " + PROVIDER_ID + " text, " + FORM_NAME + " text, " + FORM_START_TIME + " integer, " + FORM_STOP_TIME + " integer, "
			+ FORM_LAUNCH_TYPE + " text, " + FORM_PRIORITY_BOOLEAN + " text);";

	private static final String CREATE_DOWNLOAD_LOG_TABLE = "create table " + DOWNLOAD_LOG_TABLE + " (_id integer primary key autoincrement, " + DOWNLOAD_TIME + " integer);";

	public static class DatabaseHelper extends SQLiteOpenHelper {
		// private static class DatabaseHelper extends ODKSQLiteOpenHelper {

		// we create this once from the App singleton
		public DatabaseHelper(Context context) {
			// DatabaseHelper() {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
			createStorage();
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_PATIENTS_TABLE);
			db.execSQL(CREATE_OBSERVATIONS_TABLE);
			db.execSQL(CREATE_COHORTS_TABLE);
			db.execSQL(CREATE_FORMS_TABLE);
			db.execSQL(CREATE_FORMINSTANCES_TABLE);
			db.execSQL(CREATE_FORM_LOG_TABLE);
			db.execSQL(CREATE_DOWNLOAD_LOG_TABLE);
		}

		@Override
		// upgrading will destroy all old data
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + PATIENTS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + OBSERVATIONS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + COHORTS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + FORMS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + FORMINSTANCES_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + FORM_LOG_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + DOWNLOAD_LOG_TABLE);
			onCreate(db);
		}
	}

	public ClinicAdapter open() throws SQLException {
		// mDbHelper = new DatabaseHelper(); //this is the problem with Yaws
		// code... many DbHelpers...!
		// mDb = mDbHelper.getWritableDatabase();
		
		mDb = App.getDB();
		Log.e(t, "open is called");
		return this;
	}

	// TODO go through code and delete all instances of close(), but for now,
	// this suffices!
	public void close() {
		// mDbHelper = App.getHelper();

		// the following should always be null, so we comment out & never close!
		// if (mDbHelper != null) {
		// mDbHelper.close();
		// Log.e(t, "closed is called");
		// }
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
		long id = -1;

		ContentValues cv = new ContentValues();

		cv.put(KEY_PATIENT_ID, patient.getPatientId());
		cv.put(KEY_IDENTIFIER, patient.getIdentifier());

		cv.put(KEY_GIVEN_NAME, patient.getGivenName());
		cv.put(KEY_FAMILY_NAME, patient.getFamilyName());
		cv.put(KEY_MIDDLE_NAME, patient.getMiddleName());

		cv.put(KEY_BIRTH_DATE, patient.getBirthdate());
		cv.put(KEY_GENDER, patient.getGender());

		cv.put(KEY_CLIENT_CREATED, patient.getCreateCode());
		cv.put(KEY_UUID, patient.getUuid());

		try {
			id = mDb.insert(PATIENTS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createObservation(Observation obs) {
		long id = -1;

		ContentValues cv = new ContentValues();

		cv.put(KEY_PATIENT_ID, obs.getPatientId());
		cv.put(KEY_VALUE_TEXT, obs.getValueText());

		cv.put(KEY_VALUE_NUMERIC, obs.getValueNumeric());
		cv.put(KEY_VALUE_DATE, obs.getValueDate());
		cv.put(KEY_VALUE_INT, obs.getValueInt());

		cv.put(KEY_FIELD_NAME, obs.getFieldName());

		cv.put(KEY_DATA_TYPE, obs.getDataType());

		cv.put(KEY_ENCOUNTER_DATE, obs.getEncounterDate());

		try {
			id = mDb.insert(OBSERVATIONS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createCohort(Cohort cohort) {
		ContentValues cv = new ContentValues();

		cv.put(KEY_COHORT_ID, cohort.getCohortId());
		cv.put(KEY_NAME, cohort.getName());

		long id = -1;
		try {
			id = mDb.insert(COHORTS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createForm(Form form) {
		long id = -1;

		ContentValues cv = new ContentValues();

		cv.put(KEY_FORM_ID, form.getFormId());
		cv.put(KEY_NAME, form.getName());
		cv.put(KEY_PATH, form.getPath());

		try {
			id = mDb.insert(FORMS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createFormInstance(FormInstance instance, String title) {

		ContentValues cv = new ContentValues();

		cv.put(KEY_PATIENT_ID, instance.getPatientId());
		cv.put(KEY_FORM_ID, instance.getFormId());
		cv.put(KEY_FORMINSTANCE_STATUS, instance.getStatus());
		cv.put(KEY_PATH, instance.getPath());
		cv.put(KEY_FORMINSTANCE_DISPLAY, title);
		cv.put(KEY_FORMINSTANCE_SUBTEXT, instance.getCompletionSubtext());

		long id = -1;
		try {
			id = mDb.insert(FORMINSTANCES_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public Long createActivityLog(ActivityLog activitylog) {
		long id = -1;

		Log.e("ClinicAdapter", "adding newActivity to db!");
		ContentValues cv = new ContentValues();
		// Long now = Long.valueOf(System.currentTimeMillis());
		cv.put(PROVIDER_ID, activitylog.getProviderId());
		cv.put(PATIENT_ID, activitylog.getPatientId());
		cv.put(FORM_NAME, activitylog.getFormId());
		cv.put(FORM_PRIORITY_BOOLEAN, activitylog.getFormPriority());
		cv.put(FORM_START_TIME, activitylog.getActivityStartTime());
		cv.put(FORM_STOP_TIME, activitylog.getActivityStopTime());
		cv.put(FORM_LAUNCH_TYPE, activitylog.getFormType());

		mDb.beginTransaction();
		try {
			id = mDb.insert(FORM_LOG_TABLE, null, cv);
			mDb.setTransactionSuccessful();
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);

		} finally {
			mDb.endTransaction();
		}
		return id;
	}

	/**
	 * Remove all patients from the database.
	 * 
	 * @return number of affected rows
	 */
	public boolean deleteAllPatients() {
		return mDb.delete(PATIENTS_TABLE, KEY_CLIENT_CREATED + " IS NULL OR " + KEY_CLIENT_CREATED + "=?", new String[] { "2" }) > 0;
	}

	public boolean deleteAllObservations() {
		return mDb.delete(OBSERVATIONS_TABLE, null, null) > 0;
	}

	public boolean deleteAllCohorts() {
		return mDb.delete(COHORTS_TABLE, null, null) > 0;
	}

	public boolean deleteAllForms() {
		return mDb.delete(FORMS_TABLE, null, null) > 0;
	}

	public boolean deleteAllFormInstances() {
		return mDb.delete(FORMINSTANCES_TABLE, null, null) > 0;
	}

	public boolean deleteFormInstance(Integer patientId, Integer formId) {
		return mDb.delete(FORMINSTANCES_TABLE, KEY_PATIENT_ID + "=" + patientId + " AND " + KEY_FORM_ID + "=" + formId, null) > 0;
	}

	public boolean deleteFormInstance(long id) {
		return mDb.delete(FORMINSTANCES_TABLE, KEY_ID + "=" + id, null) > 0;
	}

	public boolean deleteFormInstance(String path) {
		return mDb.delete(FORMINSTANCES_TABLE, KEY_PATH + "=" + path, null) > 0;
	}

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
		String listOrder = KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
		switch (listtype) {
		case DashboardActivity.LIST_SUGGESTED:
			listSelection = " AND " + KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL";
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listSelection = " AND " + KEY_SAVED_FORM_NUMBER + " IS NOT NULL";
			break;
		case DashboardActivity.LIST_COMPLETE:
			listSelection = " AND " + PATIENTS_TABLE + "." + KEY_PATIENT_ID + "=" + FORMINSTANCES_TABLE + "." + KEY_PATIENT_ID;
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

							expr.append(KEY_GIVEN_NAME + " LIKE '" + n + "%'");
						} else if (i == 1) {

							expr.append(" AND ");
							expr.append(KEY_FAMILY_NAME + " LIKE '" + n + "%'");
						}
					} else {
						expr.append(KEY_GIVEN_NAME + " LIKE '" + n + "%'");
						expr.append(" OR ");
						expr.append(KEY_FAMILY_NAME + " LIKE '" + n + "%'");
						expr.append(" OR ");
						expr.append(KEY_MIDDLE_NAME + " LIKE '" + n + "%'");
						if (i < names.length - 1)
							expr.append(" OR ");
					}
				}
			}
			expr.append(")");

			if (listtype == DashboardActivity.LIST_COMPLETE) {
				c = mDb.query(true, PATIENTS_TABLE + ", " + FORMINSTANCES_TABLE, new String[] { PATIENTS_TABLE + "." + KEY_ID, PATIENTS_TABLE + "." + KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER,
						KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER, KEY_SAVED_FORM_NUMBER, KEY_SAVED_FORM_NAMES, KEY_FORMINSTANCE_SUBTEXT, KEY_CLIENT_CREATED, KEY_UUID }, "(" + expr.toString() + ")" + listSelection, null, PATIENTS_TABLE + "." + KEY_PATIENT_ID, null, listOrder, null);
			} else {
				c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER, KEY_SAVED_FORM_NUMBER,
						KEY_SAVED_FORM_NAMES, KEY_CLIENT_CREATED, KEY_UUID }, expr.toString() + listSelection, null, null, null, listOrder, null);
			}
		} else if (identifier != null) {
			// search using identifier

			// escape all wildcard characters
			identifier = identifier.replaceAll("\\*", "^*");
			identifier = identifier.replaceAll("%", "^%");
			identifier = identifier.replaceAll("_", "^_");

			if (listtype == DashboardActivity.LIST_COMPLETE) {
				c = mDb.query(true, PATIENTS_TABLE + ", " + FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER,
						KEY_SAVED_FORM_NUMBER, KEY_SAVED_FORM_NAMES, KEY_FORMINSTANCE_SUBTEXT, KEY_CLIENT_CREATED, KEY_UUID }, KEY_IDENTIFIER + " LIKE '" + identifier + "%' ESCAPE '^'" + listSelection, null, null, null, listOrder, null);
			} else {
				c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER, KEY_SAVED_FORM_NUMBER,
						KEY_SAVED_FORM_NAMES, KEY_CLIENT_CREATED, KEY_UUID }, KEY_IDENTIFIER + " LIKE '" + identifier + "%' ESCAPE '^'" + listSelection, null, PATIENTS_TABLE + "." + KEY_PATIENT_ID, null, listOrder, null);
			}

		}

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPatient(Integer patientId) throws SQLException {
		Cursor c = null;
		String listSelection = KEY_PATIENT_ID + "=" + patientId;
		String listOrder = KEY_FAMILY_NAME + " COLLATE NOCASE ASC";

		c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER, KEY_SAVED_FORM_NUMBER, KEY_SAVED_FORM_NAMES,
				KEY_CLIENT_CREATED, KEY_UUID }, listSelection, null, null, null, listOrder, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPatient(String uuidString) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER, KEY_SAVED_FORM_NUMBER, KEY_SAVED_FORM_NAMES,
				KEY_CLIENT_CREATED, KEY_UUID }, KEY_UUID + "=" + uuidString, null, null, null, KEY_PRIORITY_FORM_NUMBER + " DESC, " + KEY_FAMILY_NAME + " COLLATE NOCASE ASC", null);

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
			listSelection = KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL";
			listOrder = KEY_PRIORITY_FORM_NUMBER + " DESC, " + KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listSelection = KEY_SAVED_FORM_NUMBER + " IS NOT NULL";
			listOrder = KEY_SAVED_FORM_NUMBER + " DESC, " +  KEY_PRIORITY_FORM_NUMBER + " DESC, " + KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			break;
		case DashboardActivity.LIST_COMPLETE:
			listSelection = PATIENTS_TABLE + "." + KEY_PATIENT_ID + "=" + FORMINSTANCES_TABLE + "." + KEY_PATIENT_ID;
			listOrder = KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			groupBy = PATIENTS_TABLE + "." + KEY_PATIENT_ID; 
			break;
		case DashboardActivity.LIST_ALL:
			listSelection = "";
			listOrder = KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			
			break;
		}
		Log.e("louis.fazen", "ClinicAdapter listorder=" + listOrder);

		if (listtype == DashboardActivity.LIST_COMPLETE) {
			c = mDb.query(true, PATIENTS_TABLE + ", " + FORMINSTANCES_TABLE, new String[] { PATIENTS_TABLE + "." + KEY_ID, PATIENTS_TABLE + "." + KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER,
					KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER, KEY_SAVED_FORM_NUMBER, KEY_SAVED_FORM_NAMES, KEY_FORMINSTANCE_SUBTEXT, KEY_CLIENT_CREATED, KEY_UUID }, listSelection, null, groupBy, null, listOrder, null);
		} else {
			c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER, KEY_SAVED_FORM_NUMBER, KEY_SAVED_FORM_NAMES,
					KEY_CLIENT_CREATED, KEY_UUID }, listSelection, null, null, null, listOrder, null);
		}
		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchAllCohorts() throws SQLException {
		Cursor c = null;
		c = mDb.query(true, COHORTS_TABLE, new String[] { KEY_ID, KEY_COHORT_ID, KEY_NAME }, null, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchAllForms() throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMS_TABLE, new String[] { KEY_ID, KEY_FORM_ID, KEY_NAME, KEY_PATH }, null, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}
	
	public Cursor fetchAllObservations() throws SQLException {
		Cursor c = null;
		c = mDb.query(true, OBSERVATIONS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID }, null, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}
	
	public Cursor fetchAllFormInstances() throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_FORM_ID }, null, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchForm(Integer formId) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMS_TABLE, new String[] { KEY_ID, KEY_NAME, KEY_PATH }, KEY_FORM_ID + "=" + formId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstance(Integer patientId, Integer formId) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_FORMINSTANCE_STATUS, KEY_PATH, KEY_FORMINSTANCE_DISPLAY }, KEY_PATIENT_ID + "=" + patientId + " AND " + KEY_FORM_ID + "=" + formId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPatientFormInstancesByStatus(Integer patientId, Integer formId, String status) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_FORMINSTANCE_STATUS, KEY_PATH, KEY_FORMINSTANCE_DISPLAY }, KEY_PATIENT_ID + "=" + patientId + " AND " + KEY_FORM_ID + "=" + formId + " AND " + KEY_FORMINSTANCE_STATUS + "='" + status + "'", null, null,
				null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstances(Integer patientId) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_FORM_ID, KEY_FORMINSTANCE_STATUS, KEY_PATH, KEY_FORMINSTANCE_DISPLAY }, KEY_PATIENT_ID + "=" + patientId, null, null, null, null, null);

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
				FORMS_TABLE + ", " + FORMINSTANCES_TABLE,
				// columns
				new String[] { FORMINSTANCES_TABLE + "." + KEY_ID + ", " + FORMINSTANCES_TABLE + "." + KEY_FORM_ID + ", " + FORMINSTANCES_TABLE + "." + KEY_FORMINSTANCE_DISPLAY + ", " + FORMINSTANCES_TABLE + "." + KEY_PATH + ", " + FORMS_TABLE + "." + KEY_FORM_NAME + ", "
						+ FORMINSTANCES_TABLE + "." + KEY_FORMINSTANCE_SUBTEXT },
				// where
				FORMINSTANCES_TABLE + "." + KEY_PATIENT_ID + "=" + patientId + " AND " + FORMS_TABLE + "." + KEY_FORM_ID + "=" + FORMINSTANCES_TABLE + "." + KEY_FORM_ID,
				// group by, having
				null, null,
				// order by Key_ID... not importing the completed date, since
				// these should be uploaded regularly and therefore there is no
				// reason to do it
				FORMINSTANCES_TABLE + "." + KEY_ID + " desc",
				// limit
				null);

		Cursor c = null;
		c = mDb.rawQuery(query, null);
		// .query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID },
		// KEY_PATIENT_ID + "=" + patientId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPriorityFormIdByPatientId(Integer patientId) throws SQLException {
		Cursor c = null;
		String selection = KEY_FIELD_NAME + "=" + KEY_FIELD_FORM_VALUE + " and " + KEY_PATIENT_ID + "=" + patientId;
		c = mDb.query(true, OBSERVATIONS_TABLE, new String[] { KEY_VALUE_INT }, selection, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchCompletedFormIdByPatientId(Integer patientId) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_FORM_ID }, KEY_PATIENT_ID + "=" + patientId, null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstance(long id) throws SQLException {
		Cursor c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_FORM_ID, KEY_FORMINSTANCE_STATUS, KEY_PATH, KEY_FORMINSTANCE_DISPLAY }, KEY_ID + "= ?", new String[] { Long.toString(id) }, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstancesByStatus(String status) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_PATH, KEY_FORMINSTANCE_DISPLAY, KEY_FORMINSTANCE_STATUS }, KEY_FORMINSTANCE_STATUS + "='" + status + "'", null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchFormInstancesByPath(String path) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, FORMINSTANCES_TABLE, new String[] { KEY_ID, KEY_FORM_ID, KEY_FORMINSTANCE_STATUS, KEY_PATH, KEY_FORMINSTANCE_DISPLAY }, KEY_PATH + "='" + path + "'", null, null, null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public void createDownloadLog() {
		Long downloadTime = Long.valueOf(System.currentTimeMillis());
		ContentValues cv = new ContentValues();

		cv.put(DOWNLOAD_TIME, downloadTime);

		try {
			mDb.insert(DOWNLOAD_LOG_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

	}

	public long fetchMostRecentDownload() {
		Cursor c = null;
		long datetime = 0;
		c = mDb.query(DOWNLOAD_LOG_TABLE, new String[] { "MAX(" + DOWNLOAD_TIME + ") AS " + DOWNLOAD_TIME }, null, null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				do {
					datetime = c.getLong(c.getColumnIndex(DOWNLOAD_TIME));
				} while (c.moveToNext());
			}
			c.close();
		}

		return datetime;
	}

	// PRIORITY FORMS SECTION //
	public void updatePriorityFormList() throws SQLException {
		Cursor c = null;
		// SQLQuery:
		// SELECT observations.patient_id as patientid, group_concat(forms.name,
		// \", \") as name FROM observations INNER JOIN forms
		// WHERE (field_name = \"odkconnector.property.form\" AND value_int =
		// form_id) GROUP BY patient_id;
		String subquery = SQLiteQueryBuilder.buildQueryString(
		// include distinct
				true,
				// FROM tables
				FORMS_TABLE + "," + OBSERVATIONS_TABLE,
				// two columns (one of which is a group_concat()
				new String[] { OBSERVATIONS_TABLE + "." + KEY_PATIENT_ID + ", group_concat(" + FORMS_TABLE + "." + KEY_FORM_NAME + ",\", \") AS " + KEY_PRIORITY_FORM_NAMES },
				// where
				OBSERVATIONS_TABLE + "." + KEY_FIELD_NAME + "=" + KEY_FIELD_FORM_VALUE + " AND " + FORMS_TABLE + "." + KEY_FORM_ID + "=" + OBSERVATIONS_TABLE + "." + KEY_VALUE_INT,
				// group by
				OBSERVATIONS_TABLE + "." + KEY_PATIENT_ID, null, null, null);

		c = mDb.rawQuery(subquery, null);

		if (c.moveToFirst()) {
			do {
				String patientId = c.getString(c.getColumnIndex(KEY_PATIENT_ID));
				String formName = c.getString(c.getColumnIndex(KEY_PRIORITY_FORM_NAMES));
				ContentValues cv = new ContentValues();
				cv.put(KEY_PRIORITY_FORM_NAMES, formName);

				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);
			} while (c.moveToNext());
		}
		c.close();
	}

	public void updatePriorityFormNumbers() throws SQLException {

		Cursor c = null;
		c = mDb.query(OBSERVATIONS_TABLE, new String[] { KEY_PATIENT_ID, "count(*) as " + KEY_PRIORITY_FORM_NUMBER }, KEY_FIELD_NAME + "=" + KEY_FIELD_FORM_VALUE, null, KEY_PATIENT_ID, null, null);

		if (c.moveToFirst()) {
			do {
				String patientId = c.getString(c.getColumnIndex(KEY_PATIENT_ID));
				int formNumber = c.getInt(c.getColumnIndex(KEY_PRIORITY_FORM_NUMBER));
				ContentValues cv = new ContentValues();
				cv.put(KEY_PRIORITY_FORM_NUMBER, formNumber);

				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);

			} while (c.moveToNext());
		}
		c.close();
	}

	public void updatePriorityFormsByPatientId(String updatePatientId, String formId) throws SQLException {
		// int deleted = mDb.delete(OBSERVATIONS_TABLE, KEY_PATIENT_ID +
		// "=? AND " + KEY_FIELD_NAME + "=? AND " + KEY_VALUE_INT + "=?", new
		// String[] { updatePatientId, KEY_FIELD_FORM_VALUE, formId });
		int deleted = mDb.delete(OBSERVATIONS_TABLE, KEY_PATIENT_ID + "=" + updatePatientId + " AND " + KEY_FIELD_NAME + "=" + KEY_FIELD_FORM_VALUE + " AND " + KEY_VALUE_INT + "=" + formId, null);
		if (deleted > 0) {

			Cursor c = null;
			// 1. Update the PriorityFormNumbers
			c = mDb.query(OBSERVATIONS_TABLE, new String[] { KEY_PATIENT_ID, "count(*) as " + KEY_PRIORITY_FORM_NUMBER }, KEY_FIELD_NAME + "=? AND " + KEY_PATIENT_ID + "=?", new String[] { KEY_FIELD_FORM_VALUE, updatePatientId }, KEY_PATIENT_ID, null, null);
			ContentValues cv = new ContentValues();
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(KEY_PATIENT_ID));
					int formNumber = c.getInt(c.getColumnIndex(KEY_PRIORITY_FORM_NUMBER));

					cv.put(KEY_PRIORITY_FORM_NUMBER, formNumber);
					mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);

				} while (c.moveToNext());
			} else {
				// } else if
				// (c.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
				cv.putNull(KEY_PRIORITY_FORM_NUMBER);
				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + updatePatientId, null);

			}

			c.close();

			// 2. Update the PriorityFormNames
			Cursor cname = null;
			String subquery = SQLiteQueryBuilder.buildQueryString(
			// include distinct
					true,
					// FROM tables
					FORMS_TABLE + "," + OBSERVATIONS_TABLE,
					// two columns (one of which is a group_concat()
					new String[] { OBSERVATIONS_TABLE + "." + KEY_PATIENT_ID + ", group_concat(" + FORMS_TABLE + "." + KEY_FORM_NAME + ",\", \") AS " + KEY_PRIORITY_FORM_NAMES },
					// where
					OBSERVATIONS_TABLE + "." + KEY_FIELD_NAME + "=" + KEY_FIELD_FORM_VALUE + " AND " + FORMS_TABLE + "." + KEY_FORM_ID + "=" + OBSERVATIONS_TABLE + "." + KEY_VALUE_INT + " AND " + OBSERVATIONS_TABLE + "." + KEY_PATIENT_ID + "=" + updatePatientId,
					// group by
					OBSERVATIONS_TABLE + "." + KEY_PATIENT_ID, null, null, null);

			cname = mDb.rawQuery(subquery, null);
			ContentValues cvname = new ContentValues();
			if (cname.moveToFirst()) {
				do {
					String patientId = cname.getString(c.getColumnIndex(KEY_PATIENT_ID));
					String formName = cname.getString(c.getColumnIndex(KEY_PRIORITY_FORM_NAMES));

					cvname.put(KEY_PRIORITY_FORM_NAMES, formName);
					mDb.update(PATIENTS_TABLE, cvname, KEY_PATIENT_ID + "=" + patientId, null);
				} while (cname.moveToNext());
			} else {
				// } else if
				// (cname.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
				cvname.putNull(KEY_PRIORITY_FORM_NAMES);
				mDb.update(PATIENTS_TABLE, cvname, KEY_PATIENT_ID + "=" + updatePatientId, null);

			}

			cname.close();
		}
	}

	// SAVED FORMS SECTION //
	public void updateSavedFormsList() throws SQLException {
		// SELECT observations.patient_id as patientid, group_concat(forms.name,
		// \", \") as name FROM observations INNER JOIN forms
		// WHERE (field_name = \"odkconnector.property.form\" AND value_int =
		// form_id) GROUP BY patient_id;

		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + " IS NOT NULL";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "group_concat( " + InstanceColumns.FORM_NAME + ",\", \") AS " + KEY_SAVED_FORM_NAMES }, selection, selectionArgs, null);
		ContentValues cv = new ContentValues();
		if (c.moveToFirst()) {
			do {
				String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
				String formName = c.getString(c.getColumnIndex(KEY_SAVED_FORM_NAMES));
				cv.put(KEY_SAVED_FORM_NAMES, formName);

				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);
			} while (c.moveToNext());
		}

		c.close();

	}

	// saved forms are kept in the collect database...
	public void updateSavedFormNumbers() throws SQLException {
		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + " IS NOT NULL";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "count(*) as " + InstanceColumns.STATUS }, selection, selectionArgs, null);

		if (c.moveToFirst()) {
			do {
				String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
				int formNumber = c.getInt(c.getColumnIndex(InstanceColumns.STATUS));
				ContentValues cv = new ContentValues();
				cv.put(KEY_SAVED_FORM_NUMBER, formNumber);

				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);
			} while (c.moveToNext());
		}

		c.close();

	}

	public void updateSavedFormsListByPatientId(String updatePatientId) throws SQLException {

		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, updatePatientId };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "group_concat( " + InstanceColumns.FORM_NAME + ",\", \") AS " + KEY_SAVED_FORM_NAMES }, selection, selectionArgs, null);

		ContentValues cv = new ContentValues();
		if (c.moveToFirst()) {
			do {
				String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
				String formName = c.getString(c.getColumnIndex(KEY_SAVED_FORM_NAMES));

				cv.put(KEY_SAVED_FORM_NAMES, formName);

				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);
			} while (c.moveToNext());
		} else {
			// } else if
			// (c.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
			cv.putNull(KEY_SAVED_FORM_NAMES);
			mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + updatePatientId, null);

		}

		c.close();

	}

	// saved forms are kept in the collect database...
	public void updateSavedFormNumbersByPatientId(String updatePatientId) throws SQLException {
		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, updatePatientId };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "count(*) as " + InstanceColumns.STATUS }, selection, selectionArgs, null);
		ContentValues cv = new ContentValues();
		if (c.moveToFirst()) {
			do {
				String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
				int formNumber = c.getInt(c.getColumnIndex(InstanceColumns.STATUS));
				cv.put(KEY_SAVED_FORM_NUMBER, formNumber);

				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);
			} while (c.moveToNext());
		} else {
			cv.putNull(KEY_SAVED_FORM_NUMBER);
			mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + updatePatientId, null);

		}

		c.close();

	}

	// COUNTING SECTION....
	public int countAllPriorityFormNumbers() throws SQLException {
		int count = 0;
		Cursor c = null;
		// counting total priority form number:
		// c = mDb.query(PATIENTS_TABLE, new String[] { "total(" +
		// KEY_PRIORITY_FORM_NUMBER + ") AS " + KEY_PRIORITY_FORM_NUMBER },
		// KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL", null, null, null, null);

		// counting total patients with a priority form:
		c = mDb.query(PATIENTS_TABLE, new String[] { "count(*) AS " + KEY_PRIORITY_FORM_NUMBER }, KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL", null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				count = c.getInt(c.getColumnIndex(KEY_PRIORITY_FORM_NUMBER));
			}
			c.close();
		}
		return count;
	}

	public int countAllSavedFormNumbers() throws SQLException {
		int count = 0;
		Cursor c = null;

		// Count Total Saved Forms:
		// All saved numbers come from the Collect.Instances.Db, otherwise, you
		// would miss the saved instances where there are no identifiers.
		// String selection = InstanceColumns.STATUS + "=?";
		// String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE };
		// c =
		// App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI,
		// new String[] { "count(*) as " + KEY_SAVED_FORM_NUMBER }, selection,
		// selectionArgs, null);

		// Count Total Patients with Saved Forms:
		c = mDb.query(PATIENTS_TABLE, new String[] { "count(*) AS " + KEY_SAVED_FORM_NUMBER }, KEY_SAVED_FORM_NUMBER + " IS NOT NULL", null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				count = c.getInt(c.getColumnIndex(KEY_SAVED_FORM_NUMBER));
			}
			c.close();
		}

		return count;
	}

	public int findLastClientCreatedId() throws SQLException {
		int lowestNegativeId = 0;
		Cursor c = null;
		c = mDb.query(PATIENTS_TABLE, new String[] { "min(" + KEY_PATIENT_ID + ") AS " + KEY_PATIENT_ID }, null, null, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				lowestNegativeId = c.getInt(c.getColumnIndex(KEY_PATIENT_ID));
			}
			c.close();
		}

		return lowestNegativeId;

	}

	// Count Completed Forms/Clients
	public int countAllCompletedUnsentForms() throws SQLException {
		int count = 0;

		// Count all Completed forms as entries in Db: All Unsent Completed
		// Forms should all be in the clinic.sqlite
		// FORMINSTANCES table, so just count:
		// count = DatabaseUtils.queryNumEntries(mDb, FORMINSTANCES_TABLE);
		// int intcount = safeLongToInt(count);
		// Log.e("louis.fazen",
		// "countAllCompletedUnsentForms is not null with count of " +
		// intcount);
		// return intcount;

		// Count all patients with completed forms (i.e. group by patient id)
		Cursor c = null;
		c = mDb.query("(SELECT DISTINCT " + KEY_PATIENT_ID + " FROM " + FORMINSTANCES_TABLE + ")", new String[] { "count(" + KEY_PATIENT_ID + ") AS " + KEY_PATIENT_ID }, null, null, null, null, null);

		if (c != null) {
			if (c.moveToFirst()) {
				count = c.getInt(c.getColumnIndex(KEY_PATIENT_ID));
			}
			c.close();
		}

		return count;

	}

	public int countAllPatients() throws SQLException {
		long count = 0;
		count = DatabaseUtils.queryNumEntries(mDb, PATIENTS_TABLE);
		int intcount = safeLongToInt(count);
		return intcount;
	}

	public int countAllForms() throws SQLException {
		long count = 0;
		count = DatabaseUtils.queryNumEntries(mDb, FORMS_TABLE);
		int intcount = safeLongToInt(count);
		return intcount;
	}

	public static int safeLongToInt(long l) {
		if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(l + "is outside the bounds of int");
		}
		return (int) l;
	}

	public boolean updateFormInstance(String path, String status) {

		ContentValues cv = new ContentValues();

		cv.put(KEY_FORMINSTANCE_STATUS, status);

		return mDb.update(FORMINSTANCES_TABLE, cv, KEY_PATH + "='" + path + "'", null) > 0;
	}

	
	public boolean updateInstancePath(Integer id, String newPath) {

		ContentValues cv = new ContentValues();

		cv.put(KEY_PATH, newPath);

		return mDb.update(FORMINSTANCES_TABLE, cv, KEY_ID + "=?", new String[] {String.valueOf(id)}) > 0;
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

		cv.put(KEY_PATIENT_ID, patient.getPatientId());
		cv.put(KEY_IDENTIFIER, patient.getIdentifier());

		cv.put(KEY_GIVEN_NAME, patient.getGivenName());
		cv.put(KEY_FAMILY_NAME, patient.getFamilyName());
		cv.put(KEY_MIDDLE_NAME, patient.getMiddleName());

		cv.put(KEY_BIRTH_DATE, patient.getBirthdate());
		cv.put(KEY_GENDER, patient.getGender());

		return mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "='" + patient.getPatientId() + "'", null) > 0;
	}

	public boolean updateFormPath(Integer formId, String path) {
		ContentValues cv = new ContentValues();

		cv.put(KEY_FORM_ID, formId);
		cv.put(KEY_PATH, path);

		return mDb.update(FORMS_TABLE, cv, KEY_FORM_ID + "='" + formId.toString() + "'", null) > 0;
	}

	public static boolean createStorage() {
		return FileUtils.createFolder(FileUtils.DATABASE_PATH);
	}

	public Cursor fetchPatientObservations(Integer patientId) throws SQLException {
		Cursor c = null;
		// TODO removing an extra KEY_VALUE_TEXT doesn't screw things up?
		c = mDb.query(OBSERVATIONS_TABLE, new String[] { KEY_VALUE_TEXT, KEY_DATA_TYPE, KEY_VALUE_NUMERIC, KEY_VALUE_DATE, KEY_VALUE_INT, KEY_FIELD_NAME, KEY_ENCOUNTER_DATE }, KEY_PATIENT_ID + "=" + patientId, null, null, null, KEY_FIELD_NAME + "," + KEY_ENCOUNTER_DATE + " desc");

		if (c != null)
			c.moveToFirst();

		return c;
	}

	public Cursor fetchPatientObservation(Integer patientId, String fieldName) throws SQLException {
		Cursor c = null;

		c = mDb.query(OBSERVATIONS_TABLE, new String[] { KEY_VALUE_TEXT, KEY_DATA_TYPE, KEY_VALUE_NUMERIC, KEY_VALUE_DATE, KEY_VALUE_INT, KEY_ENCOUNTER_DATE }, KEY_PATIENT_ID + "=" + patientId + " AND " + KEY_FIELD_NAME + "='" + fieldName + "'", null, null, null,
				KEY_ENCOUNTER_DATE + " desc");

		if (c != null)
			c.moveToFirst();

		return c;
	}

	public void removeOrphanInstances(Context ctx) {
		if (FileUtils.storageReady()) {
			Cursor c = fetchFormInstancesByStatus(STATUS_UNSUBMITTED);
			if (c != null)
				c.moveToFirst();
			while (!c.isAfterLast()) {
				String instancePath = c.getString(c.getColumnIndex(KEY_PATH));
				File f = new File(instancePath);
				if (!f.exists()) {
					if (deleteFormInstance(c.getInt(c.getColumnIndex(KEY_ID)))) {
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

	// NB: louis.fazen is following Yaw's logic here, but in actuality Patient Forms
	// and Observations are both going into obs, and therefore, the code is
	// identical...
	
	
	// (louis.fazen NOTE: Do NOT use this with Cursor indices in other code
	// Cursor has different indices than IH b/c it does not include _id
	// i.e. Cursor.getColumnIndex = (IH.getColumnIndex -1)
	// ih is 71% faster than Cursor (even w/ cursor limit=1)
	public void makeIndices() {
		// Patient Table
		InsertHelper patientIh = new InsertHelper(mDb, PATIENTS_TABLE);
		ptIdentifierIndex = patientIh.getColumnIndex(KEY_IDENTIFIER);
		ptGivenIndex = patientIh.getColumnIndex(KEY_GIVEN_NAME);
		ptFamilyIndex = patientIh.getColumnIndex(KEY_FAMILY_NAME);
		ptMiddleIndex = patientIh.getColumnIndex(KEY_MIDDLE_NAME);
		ptBirthIndex = patientIh.getColumnIndex(KEY_BIRTH_DATE);
		ptGenderIndex = patientIh.getColumnIndex(KEY_GENDER);
		ptFormIndex = patientIh.getColumnIndex(KEY_PRIORITY_FORM_NAMES);
		ptFormNumberIndex = patientIh.getColumnIndex(KEY_PRIORITY_FORM_NUMBER);
		patientIh.close();

		// Obs Table
		// NB KEY_PATIENT_ID used as Index in all tables (happens to be same)
		// seems problematic, but how Yaw has coded it... so i am keeping
		InsertHelper obsIh = new InsertHelper(mDb, OBSERVATIONS_TABLE);
		ptIdIndex = obsIh.getColumnIndex(KEY_PATIENT_ID);
		obsTextIndex = obsIh.getColumnIndex(KEY_VALUE_TEXT);
		obsNumIndex = obsIh.getColumnIndex(KEY_VALUE_NUMERIC);
		obsDateIndex = obsIh.getColumnIndex(KEY_VALUE_DATE);
		obsIntIndex = obsIh.getColumnIndex(KEY_VALUE_INT);
		obsFieldIndex = obsIh.getColumnIndex(KEY_FIELD_NAME);
		obsTypeIndex = obsIh.getColumnIndex(KEY_DATA_TYPE);
		obsEncDateIndex = obsIh.getColumnIndex(KEY_ENCOUNTER_DATE);
		obsIh.close();

		updateIndices = false;
	}
	
	public void insertPatientForms(final DataInputStream zdis) throws Exception {
		if (updateIndices)
			makeIndices();
		InsertHelper ih = new InsertHelper(mDb, OBSERVATIONS_TABLE);
		mDb.beginTransaction();
		try {
			int icount = zdis.readInt();
			Log.e(t, "insertPatients icount: " + icount);
			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();

				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == Constants.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == Constants.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == Constants.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == Constants.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(zdis.readUTF()));
				}
				
				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(zdis.readUTF()));

				// Insert the row into the database.
				ih.execute();

				// publishProgress("Processing Forms",
				// Integer.valueOf(i).toString(),
				// Integer.valueOf(icount).toString());
			}
			mDb.setTransactionSuccessful();
		} finally {
			ih.close();
			mDb.endTransaction();
		}

	}

	public void insertPatients(DataInputStream zdis) throws Exception {
		InsertHelper ih = new InsertHelper(mDb, PATIENTS_TABLE);
		if (updateIndices)
			makeIndices();
		mDb.beginTransaction();

		try {
			int icount = zdis.readInt();
			Log.e(t, "insertObservations icount: " + icount);
			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();

				// Add the data for each column
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(ptFamilyIndex, zdis.readUTF());
				ih.bind(ptMiddleIndex, zdis.readUTF());
				ih.bind(ptGivenIndex, zdis.readUTF());
				ih.bind(ptGenderIndex, zdis.readUTF());
				ih.bind(ptBirthIndex, parseDate(zdis.readUTF()));
				ih.bind(ptIdentifierIndex, zdis.readUTF());

				// ih.bind(ptFormIndex, "");
				// ih.bind(ptFormNumberIndex, "");

				// Log.e(t,
				// " ptIdIndex2: " + String.valueOf(ptIdIndex) +
				// " ptFamilyIndex:5 " + String.valueOf(ptFamilyIndex) +
				// " ptMiddleIndex:6 " + String.valueOf(ptMiddleIndex) +
				// " ptGivenIndex:4 " + String.valueOf(ptGivenIndex) +
				// " ptGenderIndex:8 "
				// + String.valueOf(ptGenderIndex) + " ptBirthIndex:7 " +
				// String.valueOf(ptBirthIndex) + " ptIdentifierIndex:3 " +
				// String.valueOf(ptIdentifierIndex));

				// Insert the row into the database.
				ih.execute();

			}
			mDb.setTransactionSuccessful();
		} finally {
			ih.close();
			mDb.endTransaction();
		}
		
	}

	public void insertObservations(DataInputStream zdis) throws Exception {

		InsertHelper ih = new InsertHelper(mDb, OBSERVATIONS_TABLE);
		if (updateIndices)
			makeIndices();
		mDb.beginTransaction();

		try {
			int icount = zdis.readInt();
			Log.e(t, "insertObservations icount: " + icount);
			for (int i = 1; i < icount + 1; i++) {

				ih.prepareForInsert();

				// Add the data for each column
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == Constants.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == Constants.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == Constants.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == Constants.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(zdis.readUTF()));
				}

				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(zdis.readUTF()));

				// Insert the row into the database.
				ih.execute();

			}
			mDb.setTransactionSuccessful();
		} finally {
			ih.close();
			mDb.endTransaction();
		}

	}

	private static String parseDate(String s) {
		String date = s.split("T")[0];
		SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
		try {
			return outputFormat.format(inputFormat.parse(date));
		} catch (ParseException e) {
			return "Unknown date";
		}
	}
}