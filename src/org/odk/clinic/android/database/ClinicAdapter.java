package org.odk.clinic.android.database;

import java.io.File;
import java.util.ArrayList;

import org.odk.clinic.android.openmrs.Cohort;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.tasks.EventLogger;
import org.odk.clinic.android.utilities.FileUtils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
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
	public static final String KEY_PRIORITY_FORM_NAMES = "priority_forms";
	public static final String KEY_PRIORITY_FORM_NUMBER = "priority_number";

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
	public static final String KEY_FORM_NAME = "form_id";

	// instance columns
	public static final String KEY_FORMINSTANCE_STATUS = "status";
	public static final String KEY_FORMINSTANCE_DISPLAY = "display";

	// status for instances
	public static final String STATUS_UNSUBMITTED = "pending-sync";
	public static final String STATUS_SUBMITTED = "submitted";

	// private DateFormat mDateFormat = new
	// SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	// private String mZeroDate = "0000-00-00 00:00:00";
	
	
	//louis.fazen is putting in new events table...
	private static final String EVENT_NAME = "event_name";
	private static final String EVENT_START = "start_time";
	private static final String EVENT_STOP = "stop_time";
	private static final String EVENT_TABLE = "events";
	
	

	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	private static final String DATABASE_NAME = "clinic.sqlite3";
	private static final String PATIENTS_TABLE = "patients";
	private static final String OBSERVATIONS_TABLE = "observations";
	private static final String COHORTS_TABLE = "cohorts";
	private static final String FORMS_TABLE = "forms";
	private static final String FORMINSTANCES_TABLE = "instances";
	private static final int DATABASE_VERSION = 8;

	// private static final String CREATE_PATIENTS_TABLE = "create table " +
	// PATIENTS_TABLE + " (_id integer primary key autoincrement, " +
	// KEY_PATIENT_ID + " integer not null, " + KEY_IDENTIFIER + " text, " +
	// KEY_GIVEN_NAME + " text, " + KEY_FAMILY_NAME + " text, "
	// + KEY_MIDDLE_NAME + " text, " + KEY_BIRTH_DATE + " text, " + KEY_GENDER +
	// " text);";

	private static final String CREATE_PATIENTS_TABLE = "create table " + PATIENTS_TABLE + " (_id integer primary key autoincrement, " + KEY_PATIENT_ID + " integer not null, " + KEY_IDENTIFIER + " text, " + KEY_GIVEN_NAME + " text, " + KEY_FAMILY_NAME + " text, "
			+ KEY_MIDDLE_NAME + " text, " + KEY_BIRTH_DATE + " text, " + KEY_GENDER + " text, " + KEY_PRIORITY_FORM_NUMBER + " integer, " + KEY_PRIORITY_FORM_NAMES + " text);";

	private static final String CREATE_OBSERVATIONS_TABLE = "create table " + OBSERVATIONS_TABLE + " (_id integer primary key autoincrement, " + KEY_PATIENT_ID + " integer not null, " + KEY_DATA_TYPE + " integer not null, " + KEY_VALUE_TEXT + " text, " + KEY_VALUE_NUMERIC
			+ " double, " + KEY_VALUE_DATE + " text, " + KEY_VALUE_INT + " integer, " + KEY_FIELD_NAME + " text not null, " + KEY_ENCOUNTER_DATE + " text not null);";

	private static final String CREATE_COHORTS_TABLE = "create table " + COHORTS_TABLE + " (_id integer primary key autoincrement, " + KEY_COHORT_ID + " integer not null, " + KEY_NAME + " text);";

	private static final String CREATE_FORMS_TABLE = "create table " + FORMS_TABLE + " (_id integer primary key autoincrement, " + KEY_FORM_ID + " integer not null, " + KEY_NAME + " text, " + KEY_PATH + " text);";

	private static final String CREATE_FORMINSTANCES_TABLE = "create table " + FORMINSTANCES_TABLE + " (_id integer primary key autoincrement, " + KEY_PATIENT_ID + " integer not null, " + KEY_FORM_ID + " integer not null, " + KEY_FORMINSTANCE_DISPLAY + " text, "
			+ KEY_FORMINSTANCE_STATUS + " text, " + KEY_PATH + " text);";

	private static final String CREATE_EVENT_TABLE = "create table " + EVENT_TABLE + " (_id integer primary key autoincrement, " + EVENT_NAME + " text, " + 
			 EVENT_START + " integer, " + EVENT_STOP + " integer);";

	
	private static class DatabaseHelper extends ODKSQLiteOpenHelper {

		DatabaseHelper() {
			super(FileUtils.DATABASE_PATH, DATABASE_NAME, null, DATABASE_VERSION);
			createStorage();
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_PATIENTS_TABLE);
			db.execSQL(CREATE_OBSERVATIONS_TABLE);
			db.execSQL(CREATE_COHORTS_TABLE);
			db.execSQL(CREATE_FORMS_TABLE);
			db.execSQL(CREATE_FORMINSTANCES_TABLE);
//			db.execSQL(CREATE_EVENT_TABLE);
		}

		@Override
		// upgrading will destroy all old data
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + PATIENTS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + OBSERVATIONS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + COHORTS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + FORMS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + FORMINSTANCES_TABLE);
			
//			db.execSQL("DROP TABLE IF EXISTS " + EVENT_TABLE);
			onCreate(db);
		}
	}

	public ClinicAdapter open() throws SQLException {
		mDbHelper = new DatabaseHelper();
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		if (mDbHelper != null) {
			mDbHelper.close();
		}
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
	 * Insert patient into the database.
	 * 
	 * @param patient
	 *            Patient object containing patient info
	 * @return database id of the new patient
	 */
	public long createPatient(Patient patient) {
		ContentValues cv = new ContentValues();

		cv.put(KEY_PATIENT_ID, patient.getPatientId());
		cv.put(KEY_IDENTIFIER, patient.getIdentifier());

		cv.put(KEY_GIVEN_NAME, patient.getGivenName());
		cv.put(KEY_FAMILY_NAME, patient.getFamilyName());
		cv.put(KEY_MIDDLE_NAME, patient.getMiddleName());

		cv.put(KEY_BIRTH_DATE, patient.getBirthdate());
		cv.put(KEY_GENDER, patient.getGender());

		long id = -1;
		try {
			id = mDb.insert(PATIENTS_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public long createObservation(Observation obs) {
		ContentValues cv = new ContentValues();

		cv.put(KEY_PATIENT_ID, obs.getPatientId());
		cv.put(KEY_VALUE_TEXT, obs.getValueText());

		cv.put(KEY_VALUE_NUMERIC, obs.getValueNumeric());
		cv.put(KEY_VALUE_DATE, obs.getValueDate());
		cv.put(KEY_VALUE_INT, obs.getValueInt());

		cv.put(KEY_FIELD_NAME, obs.getFieldName());

		cv.put(KEY_DATA_TYPE, obs.getDataType());

		cv.put(KEY_ENCOUNTER_DATE, obs.getEncounterDate());

		long id = -1;
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
		ContentValues cv = new ContentValues();

		cv.put(KEY_FORM_ID, form.getFormId());
		cv.put(KEY_NAME, form.getName());
		cv.put(KEY_PATH, form.getPath());

		long id = -1;
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
		long id = -1;
		try {
			id = mDb.insert(FORMINSTANCES_TABLE, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(t, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	public void createEvent(EventLogger eventlog) {
		try {
			mDb.beginTransaction();

			ContentValues cv = new ContentValues();
			cv.put(EVENT_START, eventlog.getEventStart());
			cv.put(EVENT_STOP, eventlog.getEventStop());

			if (eventlog.sameEvent()) {
				cv.put(EVENT_NAME, eventlog.getEventName());

			} else {
				cv.put(EVENT_NAME, "Error: " + eventlog.getEventName());
			}

			mDb.insert(EVENT_TABLE, null, cv);
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

	/**
	 * Remove all patients from the database.
	 * 
	 * @return number of affected rows
	 */
	public boolean deleteAllPatients() {
		return mDb.delete(PATIENTS_TABLE, null, null) > 0;
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
	public Cursor fetchPatients(String name, String identifier) throws SQLException {
		Cursor c = null;
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

			for (int i = 0; i < names.length; i++) {
				String n = names[i];
				if (n != null && n.length() > 0) {
					expr.append(KEY_GIVEN_NAME + " LIKE '" + n + "%'");
					expr.append(" OR ");
					expr.append(KEY_FAMILY_NAME + " LIKE '" + n + "%'");
					expr.append(" OR ");
					expr.append(KEY_MIDDLE_NAME + " LIKE '" + n + "%'");
					if (i < names.length - 1)
						expr.append(" OR ");
				}
			}

			c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER }, expr.toString(), null, null, null, null, null);
		} else if (identifier != null) {
			// search using identifier

			// escape all wildcard characters
			identifier = identifier.replaceAll("\\*", "^*");
			identifier = identifier.replaceAll("%", "^%");
			identifier = identifier.replaceAll("_", "^_");

			c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER }, KEY_IDENTIFIER + " LIKE '" + identifier
					+ "%' ESCAPE '^'", null, null, null, null, null);
		}

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchPatient(Integer patientId) throws SQLException {
		Cursor c = null;
		c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER }, KEY_PATIENT_ID + "=" + patientId, null, null,
				null, null, null);

		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

	public Cursor fetchAllPatients() throws SQLException {
		Cursor c = null;
		c = mDb.query(true, PATIENTS_TABLE, new String[] { KEY_ID, KEY_PATIENT_ID, KEY_IDENTIFIER, KEY_GIVEN_NAME, KEY_FAMILY_NAME, KEY_MIDDLE_NAME, KEY_BIRTH_DATE, KEY_GENDER, KEY_PRIORITY_FORM_NAMES, KEY_PRIORITY_FORM_NUMBER }, null, null, null, null, null, null);

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

	// TODO: change the following to be more generic rather than just a long
	// string... see code from 6/6 on desktop CLinicAdapter
	// louis.fazen is adding the following cursor to identify the correct

	public void updatePatientFormList() throws SQLException {
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
				new String[] { OBSERVATIONS_TABLE + "." + KEY_PATIENT_ID + " group_concat(" + FORMS_TABLE + "." + KEY_FORM_NAME + ",\", \") AS " + KEY_PRIORITY_FORM_NAMES },
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

				// DatabaseUtils.cursorRowToContentValues(c, form_row);
				// ptIdFormValues.add(map);
				Log.e("UpdatePatientFormIDs", "form_row content values: " + cv + " and formNames: " + formName);
				// mDb.update(PATIENTS_TABLE, cv, "patient_id=" + patientId,
				// null);
				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);
			} while (c.moveToNext());
		}
		c.close();

	}

	public void updatePatientFormNumbers() throws SQLException {

		Cursor c = null;
		c = mDb.query(OBSERVATIONS_TABLE, new String[] { KEY_PATIENT_ID, "count(*) as " + KEY_PRIORITY_FORM_NUMBER }, KEY_FIELD_NAME + "=" + KEY_FIELD_FORM_VALUE, null, KEY_PATIENT_ID, null, null);

		if (c.moveToFirst()) {
			do {
				String patientId = c.getString(c.getColumnIndex(KEY_PATIENT_ID));
				int formNumber = c.getInt(c.getColumnIndex(KEY_PRIORITY_FORM_NUMBER));
				ContentValues cv = new ContentValues();
				cv.put(KEY_PRIORITY_FORM_NUMBER, formNumber);

				// DatabaseUtils.cursorRowToContentValues(c, form_row);
				// ptIdFormValues.add(map);
				Log.e("UpdatePatientFormIDs", "priority_number content values: " + cv + " and formNumber: " + formNumber);
				mDb.update(PATIENTS_TABLE, cv, KEY_PATIENT_ID + "=" + patientId, null);

			} while (c.moveToNext());
		}
		c.close();

	}

	public boolean updateFormInstance(String path, String status) {

		ContentValues cv = new ContentValues();

		cv.put(KEY_FORMINSTANCE_STATUS, status);

		return mDb.update(FORMINSTANCES_TABLE, cv, KEY_PATH + "='" + path + "'", null) > 0;
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
}