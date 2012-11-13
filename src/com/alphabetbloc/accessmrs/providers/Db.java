package com.alphabetbloc.accessmrs.providers;

import java.util.ArrayList;
import java.util.Arrays;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteQueryBuilder;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI;
import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.data.ActivityLog;
import com.alphabetbloc.accessmrs.data.Form;
import com.alphabetbloc.accessmrs.data.FormInstance;
import com.alphabetbloc.accessmrs.data.Observation;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.ui.user.DashboardActivity;
import com.alphabetbloc.accessmrs.utilities.App;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 */
public class Db {

	private final static String TAG = Db.class.getSimpleName();
	private static Db instance;

	public static synchronized Db open() {
		if (instance == null) {
			instance = new Db();
		}
		return instance;
	}

	// INSERT SECTION
	public long createPatient(Patient patient) {
		ContentValues cv = new ContentValues();
		cv.put(DataModel.KEY_PATIENT_ID, patient.getPatientId());
		cv.put(DataModel.KEY_IDENTIFIER, patient.getIdentifier());
		cv.put(DataModel.KEY_GIVEN_NAME, patient.getGivenName());
		cv.put(DataModel.KEY_FAMILY_NAME, patient.getFamilyName());
		cv.put(DataModel.KEY_MIDDLE_NAME, patient.getMiddleName());
		cv.put(DataModel.KEY_BIRTH_DATE, patient.getBirthdate());
		cv.put(DataModel.KEY_GENDER, patient.getGender());
		cv.put(DataModel.KEY_CLIENT_CREATED, patient.getCreateCode());
		cv.put(DataModel.KEY_UUID, patient.getUuid());
		return DbProvider.openDb().insert(DataModel.PATIENTS_TABLE, cv);
	}

	public long createObservation(Observation obs) {
		ContentValues cv = new ContentValues();
		cv.put(DataModel.KEY_PATIENT_ID, obs.getPatientId());
		cv.put(DataModel.KEY_VALUE_TEXT, obs.getValueText());
		cv.put(DataModel.KEY_VALUE_NUMERIC, obs.getValueNumeric());
		cv.put(DataModel.KEY_VALUE_DATE, obs.getValueDate());
		cv.put(DataModel.KEY_VALUE_INT, obs.getValueInt());
		cv.put(DataModel.KEY_FIELD_NAME, obs.getFieldName());
		cv.put(DataModel.KEY_DATA_TYPE, obs.getDataType());
		cv.put(DataModel.KEY_ENCOUNTER_DATE, obs.getEncounterDate());
		return DbProvider.openDb().insert(DataModel.OBSERVATIONS_TABLE, cv);
	}

	public long createForm(Form form) {
		ContentValues cv = new ContentValues();
		cv.put(DataModel.KEY_FORM_ID, form.getFormId());
		cv.put(DataModel.KEY_NAME, form.getName());
		cv.put(DataModel.KEY_PATH, form.getPath());
		return DbProvider.openDb().insert(DataModel.FORMS_TABLE, cv);
	}

	public long createFormInstance(FormInstance instance, String title) {
		ContentValues cv = new ContentValues();
		cv.put(DataModel.KEY_PATIENT_ID, instance.getPatientId());
		cv.put(DataModel.KEY_FORM_ID, instance.getFormId());
		cv.put(DataModel.KEY_FORMINSTANCE_STATUS, instance.getStatus());
		cv.put(DataModel.KEY_PATH, instance.getPath());
		cv.put(DataModel.KEY_FORMINSTANCE_DISPLAY, title);
		cv.put(DataModel.KEY_FORMINSTANCE_SUBTEXT, instance.getCompletionSubtext());
		return DbProvider.openDb().insert(DataModel.FORMINSTANCES_TABLE, cv);
	}

	public long createDownloadLog() {
		Long downloadTime = Long.valueOf(System.currentTimeMillis());
		ContentValues cv = new ContentValues();
		cv.put(DataModel.DOWNLOAD_TIME, downloadTime);
		return DbProvider.openDb().insert(DataModel.DOWNLOAD_LOG_TABLE, cv);
	}

	public long createActivityLog(ActivityLog activitylog) {
		ContentValues cv = new ContentValues();
		cv.put(DataModel.PROVIDER_ID, activitylog.getProviderId());
		cv.put(DataModel.PATIENT_ID, activitylog.getPatientId());
		cv.put(DataModel.FORM_NAME, activitylog.getFormId());
		cv.put(DataModel.FORM_PRIORITY_BOOLEAN, activitylog.getFormPriority());
		cv.put(DataModel.FORM_START_TIME, activitylog.getActivityStartTime());
		cv.put(DataModel.FORM_STOP_TIME, activitylog.getActivityStopTime());
		cv.put(DataModel.FORM_LAUNCH_TYPE, activitylog.getFormType());
		return DbProvider.openDb().insert(DataModel.FORM_LOG_TABLE, cv);
	}

	public boolean clearSubmittedForms() throws SQLException {
		String selection = InstanceColumns.STATUS + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_SUBMITTED };
		return DbProvider.openDb().delete(DataModel.FORMINSTANCES_TABLE, selection, selectionArgs);
	}

	// QUERY SECTION
	// Patients
	public Cursor fetchPatient(Integer patientId) throws SQLException {
		String selection = DataModel.KEY_PATIENT_ID + "=" + patientId;
		String sortOrder = DataModel.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
		return DbProvider.openDb().queryDistinct(DataModel.PATIENTS_TABLE, null, selection, null, sortOrder);
	}

	public Cursor fetchPatient(String uuidString) throws SQLException {
		String selection = DataModel.KEY_UUID + "=" + uuidString;
		String sortOrder = DataModel.KEY_PRIORITY_FORM_NUMBER + " DESC, " + DataModel.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
		return DbProvider.openDb().queryDistinct(DataModel.PATIENTS_TABLE, null, selection, null, sortOrder);
	}

	// Forms
	public Cursor fetchAllForms() throws SQLException {
		String[] projection = new String[] { DataModel.KEY_ID, DataModel.KEY_FORM_ID, DataModel.KEY_NAME, DataModel.KEY_PATH };
		return DbProvider.openDb().queryDistinct(DataModel.FORMS_TABLE, projection, null, null, null);
	}

	public Cursor fetchForm(Integer formId) throws SQLException {
		String[] projection = new String[] { DataModel.KEY_ID, DataModel.KEY_NAME, DataModel.KEY_PATH };
		String selection = DataModel.KEY_FORM_ID + "=" + formId;
		return DbProvider.openDb().queryDistinct(DataModel.FORMS_TABLE, projection, selection, null, null);
	}

	// Instances
	public Cursor fetchAllFormInstances() throws SQLException {
		String[] projection = new String[] { DataModel.KEY_ID, DataModel.KEY_PATIENT_ID, DataModel.KEY_FORM_ID };
		return DbProvider.openDb().queryDistinct(DataModel.FORMINSTANCES_TABLE, projection, null, null, null);
	}

	public Cursor fetchCompletedFormIdByPatientId(Integer patientId) throws SQLException {
		String[] projection = new String[] { DataModel.KEY_FORM_ID };
		String selection = DataModel.KEY_PATIENT_ID + "=" + patientId;
		return DbProvider.openDb().queryDistinct(DataModel.FORMINSTANCES_TABLE, projection, selection, null, null);
	}

	public Cursor fetchFormInstance(long id) throws SQLException {
		String selection = DataModel.KEY_ID + "=" + Long.toString(id);
		return fetchFormInstanceSelection(selection);
	}

	public Cursor fetchFormInstances(Integer patientId) throws SQLException {
		String selection = DataModel.KEY_PATIENT_ID + "=" + patientId;
		return fetchFormInstanceSelection(selection);
	}

	public Cursor fetchFormInstancesByPath(String path) throws SQLException {
		String selection = DataModel.KEY_PATH + "='" + path + "'";
		return fetchFormInstanceSelection(selection);
	}

	public Cursor fetchFormInstancesByStatus(String status) throws SQLException {
		String selection = DataModel.KEY_FORMINSTANCE_STATUS + "='" + status + "'";
		return fetchFormInstanceSelection(selection);
	}

	public Cursor fetchFormInstance(Integer patientId, Integer formId) throws SQLException {
		String selection = DataModel.KEY_PATIENT_ID + "=" + patientId + " AND " + DataModel.KEY_FORM_ID + "=" + formId;
		return fetchFormInstanceSelection(selection);
	}

	public Cursor fetchPatientFormInstancesByStatus(Integer patientId, Integer formId, String status) throws SQLException {
		String selection = DataModel.KEY_PATIENT_ID + "=" + patientId + " AND " + DataModel.KEY_FORM_ID + "=" + formId + " AND " + DataModel.KEY_FORMINSTANCE_STATUS + "='" + status + "'";
		return fetchFormInstanceSelection(selection);
	}

	public Cursor fetchFormInstanceSelection(String selection) throws SQLException {
		String[] projection = new String[] { DataModel.KEY_ID, DataModel.KEY_FORM_ID, DataModel.KEY_PATIENT_ID, DataModel.KEY_FORMINSTANCE_STATUS, DataModel.KEY_PATH, DataModel.KEY_FORMINSTANCE_DISPLAY };
		return DbProvider.openDb().queryDistinct(DataModel.FORMINSTANCES_TABLE, projection, selection, null, null);
	}

	// Observations
	public Cursor fetchPriorityFormIdByPatientId(Integer patientId) throws SQLException {
		String[] projection = new String[] { DataModel.KEY_VALUE_INT };
		String selection = DataModel.KEY_FIELD_NAME + "=" + DataModel.KEY_FIELD_FORM_VALUE + " and " + DataModel.KEY_PATIENT_ID + "=" + patientId;
		return DbProvider.openDb().queryDistinct(DataModel.OBSERVATIONS_TABLE, projection, selection, null, null);
	}

	public Cursor fetchPatientObservations(Integer patientId) throws SQLException {
		String selection = DataModel.KEY_PATIENT_ID + "=? AND " + DataModel.KEY_FIELD_NAME + " != " + DataModel.KEY_FIELD_FORM_VALUE;
		String[] selectionArgs = new String[] { String.valueOf(patientId) };
		String sortOrder = DataModel.KEY_FIELD_NAME + "," + DataModel.KEY_ENCOUNTER_DATE + " desc";
		return DbProvider.openDb().query(DataModel.OBSERVATIONS_TABLE, null, selection, selectionArgs, sortOrder);
	}

	public Cursor fetchPatientObservation(Integer patientId, String fieldName) throws SQLException {
		String[] projection = new String[] { DataModel.KEY_VALUE_TEXT, DataModel.KEY_DATA_TYPE, DataModel.KEY_VALUE_NUMERIC, DataModel.KEY_VALUE_DATE, DataModel.KEY_VALUE_INT, DataModel.KEY_ENCOUNTER_DATE };
		String selection = DataModel.KEY_PATIENT_ID + "=" + patientId + " AND " + DataModel.KEY_FIELD_NAME + "='" + fieldName + "'";
		String sortOrder = DataModel.KEY_ENCOUNTER_DATE + " desc";
		return DbProvider.openDb().query(DataModel.OBSERVATIONS_TABLE, projection, selection, null, sortOrder);
	}

	public Cursor fetchAllPatients(int listType) throws SQLException {
		return fetchPatients(null, null, listType, null);
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
	public Cursor searchPatients(String name, String identifier, int listType) throws SQLException {
		String listOrder = DataModel.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
		return fetchPatients(name, identifier, listType, listOrder);
	}

	public Cursor fetchPatients(String name, String identifier, int listType, String listOrder) throws SQLException {

		if (listOrder == null)
			listOrder = getDefaultListOrder(listType);
		String listSelection = null;
		String table = DataModel.PATIENTS_TABLE;
		String[] projection = null;
		String groupBy = null;
		switch (listType) {
		case DashboardActivity.LIST_SUGGESTED:
			listSelection = DataModel.KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL";
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listSelection = DataModel.KEY_SAVED_FORM_NUMBER + " IS NOT NULL";
			break;
		case DashboardActivity.LIST_COMPLETE:
			table = DataModel.PATIENTS_TABLE + ", " + DataModel.FORMINSTANCES_TABLE;
			projection = new String[] { DataModel.PATIENTS_TABLE + "." + DataModel.KEY_ID, DataModel.PATIENTS_TABLE + "." + DataModel.KEY_PATIENT_ID, DataModel.KEY_IDENTIFIER, DataModel.KEY_GIVEN_NAME, DataModel.KEY_FAMILY_NAME, DataModel.KEY_MIDDLE_NAME,
					DataModel.KEY_BIRTH_DATE, DataModel.KEY_GENDER, DataModel.KEY_PRIORITY_FORM_NAMES, DataModel.KEY_PRIORITY_FORM_NUMBER, DataModel.KEY_SAVED_FORM_NUMBER, DataModel.KEY_SAVED_FORM_NAMES, DataModel.KEY_FORMINSTANCE_SUBTEXT, DataModel.KEY_CLIENT_CREATED,
					DataModel.KEY_UUID };
			groupBy = DataModel.PATIENTS_TABLE + "." + DataModel.KEY_PATIENT_ID;
			listSelection = DataModel.PATIENTS_TABLE + "." + DataModel.KEY_PATIENT_ID + "=" + DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_PATIENT_ID;
			break;
		case DashboardActivity.LIST_SIMILAR_CLIENTS:
		case DashboardActivity.LIST_ALL:
			listSelection = null;
			break;
		}

		StringBuilder selection = new StringBuilder();
		if (name != null) {
			// search using name
			selection = createNameSelection(name, listType);
			if (listSelection != null)
				selection.append(" AND ").append(listSelection);
			listSelection = selection.toString();

		} else if (identifier != null) {
			// escape all wildcard characters
			identifier = identifier.replaceAll("\\*", "^*");
			identifier = identifier.replaceAll("%", "^%");
			identifier = identifier.replaceAll("_", "^_");
			selection.append(DataModel.KEY_IDENTIFIER).append(" LIKE '").append(identifier).append("%' ESCAPE '^'");
			if (listSelection != null)
				selection.append(" AND ").append(listSelection);
			listSelection = selection.toString();
		}
		if (App.DEBUG)
			Log.v(TAG, "SELECT " + projection + " FROM " + table + " WHERE " + listSelection + " GROUP BY " + groupBy + " ORDER BY " + listOrder);
		return DbProvider.openDb().query(true, table, projection, listSelection, null, groupBy, null, listOrder, null);
	}

	private String getDefaultListOrder(int listtype) {
		String listOrder = null;

		switch (listtype) {
		case DashboardActivity.LIST_SUGGESTED:
			listOrder = DataModel.KEY_PRIORITY_FORM_NUMBER + " DESC, " + DataModel.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listOrder = DataModel.KEY_SAVED_FORM_NUMBER + " DESC, " + DataModel.KEY_PRIORITY_FORM_NUMBER + " DESC, " + DataModel.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			break;
		case DashboardActivity.LIST_COMPLETE:
		case DashboardActivity.LIST_ALL:
		default:
			listOrder = DataModel.KEY_FAMILY_NAME + " COLLATE NOCASE ASC";
			break;
		}

		return listOrder;
	}

	private StringBuilder createNameSelection(String name, int listtype) {

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
						expr.append(DataModel.KEY_GIVEN_NAME).append(" LIKE '").append(n).append("%'");
					} else if (i == 1) {
						expr.append(" AND ");
						expr.append(DataModel.KEY_FAMILY_NAME).append(" LIKE '").append(n).append("%'");
					}

				} else {
					expr.append(DataModel.KEY_GIVEN_NAME).append(" LIKE '").append(n).append("%'");
					expr.append(" OR ");
					expr.append(DataModel.KEY_FAMILY_NAME).append(" LIKE '").append(n).append("%'");
					expr.append(" OR ");
					expr.append(DataModel.KEY_MIDDLE_NAME).append(" LIKE '").append(n).append("%'");
					if (i < names.length - 1)
						expr.append(" OR ");
				}
			}
		}
		expr.append(")");

		return expr;
	}

	// moved this here, as wanted it in several places...!
	public ArrayList<Observation> fetchPatientObservationList(Integer patientId) {

		ArrayList<Observation> mObservations = new ArrayList<Observation>();
		mObservations.clear();

		Cursor c = fetchPatientObservations(patientId);

		if (c != null) {
			if (c.moveToFirst()) {
				int valueTextIndex = c.getColumnIndex(DataModel.KEY_VALUE_TEXT);
				int valueIntIndex = c.getColumnIndex(DataModel.KEY_VALUE_INT);
				int valueDateIndex = c.getColumnIndex(DataModel.KEY_VALUE_DATE);
				int valueNumericIndex = c.getColumnIndex(DataModel.KEY_VALUE_NUMERIC);
				int fieldNameIndex = c.getColumnIndex(DataModel.KEY_FIELD_NAME);
				int encounterDateIndex = c.getColumnIndex(DataModel.KEY_ENCOUNTER_DATE);
				int dataTypeIndex = c.getColumnIndex(DataModel.KEY_DATA_TYPE);

				Observation obs;
				String prevFieldName = null;

				do {
					String fieldName = c.getString(fieldNameIndex);

					// Only get first (most recent) obs
					if (!fieldName.equals(prevFieldName)) {

						obs = new Observation();
						obs.setFieldName(fieldName);
						obs.setEncounterDate(c.getString(encounterDateIndex));
						int dataType = c.getInt(dataTypeIndex);
						obs.setDataType((byte) dataType);
						switch (dataType) {
						case DataModel.TYPE_INT:
							obs.setValueInt(c.getInt(valueIntIndex));
							break;
						case DataModel.TYPE_DOUBLE:
							obs.setValueNumeric(c.getDouble(valueNumericIndex));
							break;
						case DataModel.TYPE_DATE:
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

			c.close();
		}

		return mObservations;
	}

	public Cursor fetchCompletedByPatientId(Integer patientId) throws SQLException {

		String query = SQLiteQueryBuilder.buildQueryString(
		// distinct
				true,
				// tables
				DataModel.FORMS_TABLE + ", " + DataModel.FORMINSTANCES_TABLE,
				// columns
				new String[] { DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_ID + ", " + DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_FORM_ID + ", " + DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_FORMINSTANCE_DISPLAY + ", " + DataModel.FORMINSTANCES_TABLE
						+ "." + DataModel.KEY_PATH + ", " + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_NAME + ", " + DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_FORMINSTANCE_SUBTEXT },
				// where
				DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_PATIENT_ID + "=" + patientId + " AND " + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_ID + "=" + DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_FORM_ID,
				// group by, having
				null, null,
				// order by
				DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_ID + " desc",
				// limit
				null);

		return DbProvider.openDb().rawQuery(query, null);
	}

	// Find
	public int findLastClientCreatedId() throws SQLException {
		Cursor c = null;
		String[] projection = new String[] { "min(" + DataModel.KEY_PATIENT_ID + ") AS " + DataModel.KEY_PATIENT_ID };
		c = DbProvider.openDb().query(DataModel.PATIENTS_TABLE, projection, null, null, null);

		int lowestNegativeId = 0;
		if (c != null) {
			if (c.moveToFirst())
				lowestNegativeId = c.getInt(c.getColumnIndex(DataModel.KEY_PATIENT_ID));
			c.close();
		}
		return lowestNegativeId;
	}

	// TODO! Verify this!
	public long fetchMostRecentDownload() {
		Cursor c = null;
		long datetime = 0;
		String[] projection = new String[] { "MAX(" + DataModel.DOWNLOAD_TIME + ") AS " + DataModel.DOWNLOAD_TIME };
		c = DbProvider.openDb().query(DataModel.DOWNLOAD_LOG_TABLE, projection, null, null, null);

		if (c != null) {
			if (c.moveToFirst())
				datetime = c.getLong(c.getColumnIndex(DataModel.DOWNLOAD_TIME));
			c.close();
		}
		return datetime;
	}

	// COUNT SECTION
	public int countAllPriorityFormNumbers() throws SQLException {
		int count = 0;
		Cursor c = null;
		String[] projection = new String[] { "count(*) AS " + DataModel.KEY_PRIORITY_FORM_NUMBER };
		String selection = DataModel.KEY_PRIORITY_FORM_NUMBER + " IS NOT NULL";
		c = DbProvider.openDb().query(DataModel.PATIENTS_TABLE, projection, selection, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				count = c.getInt(c.getColumnIndex(DataModel.KEY_PRIORITY_FORM_NUMBER));
			}
			c.close();
		}
		return count;
	}

	public int countAllSavedFormNumbers() throws SQLException {
		int count = 0;
		Cursor c = null;
		String[] projection = new String[] { "count(*) AS " + DataModel.KEY_SAVED_FORM_NUMBER };
		String selection = DataModel.KEY_SAVED_FORM_NUMBER + " IS NOT NULL";
		c = DbProvider.openDb().query(DataModel.PATIENTS_TABLE, projection, selection, null, null);

		if (c != null) {
			if (c.moveToFirst())
				count = c.getInt(c.getColumnIndex(DataModel.KEY_SAVED_FORM_NUMBER));
			c.close();
		}
		return count;
	}

	// Count all patients with completed forms (i.e. group by patient id)
	public int countAllCompletedUnsentForms() throws SQLException {
		int count = 0;
		Cursor c = null;
		String table = "(SELECT DISTINCT " + DataModel.KEY_PATIENT_ID + " FROM " + DataModel.FORMINSTANCES_TABLE + ")";
		String[] projection = new String[] { "count(" + DataModel.KEY_PATIENT_ID + ") AS " + DataModel.KEY_PATIENT_ID };
		c = DbProvider.openDb().query(table, projection, null, null, null);

		if (c != null) {
			if (c.moveToFirst())
				count = c.getInt(c.getColumnIndex(DataModel.KEY_PATIENT_ID));
			c.close();
		}
		return count;
	}

	public int countAllPatients() throws SQLException {
		return DbProvider.openDb().countRows(DataModel.PATIENTS_TABLE);
	}

	public int countAllForms() throws SQLException {
		return DbProvider.openDb().countRows(DataModel.FORMS_TABLE);
	}

	// UPDATE SECTION
	public void updatePriorityFormList() throws SQLException {
		DbProvider.openDb();
		Cursor c = null;
		String subquery = SQLiteQueryBuilder.buildQueryString(
		// include distinct
				true,
				// FROM tables
				DataModel.FORMS_TABLE + "," + DataModel.OBSERVATIONS_TABLE,
				// two columns (one of which is a group_concat()
				new String[] { DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_PATIENT_ID + ", group_concat(" + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_NAME + ",\", \") AS " + DataModel.KEY_PRIORITY_FORM_NAMES },
				// where
				DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_FIELD_NAME + "=" + DataModel.KEY_FIELD_FORM_VALUE + " AND " + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_ID + "=" + DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_VALUE_INT,
				// group by
				DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_PATIENT_ID, null, null, null);

		c = DbProvider.openDb().rawQuery(subquery, null);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(DataModel.KEY_PATIENT_ID));
					String formName = c.getString(c.getColumnIndex(DataModel.KEY_PRIORITY_FORM_NAMES));
					ContentValues cv = new ContentValues();
					cv.put(DataModel.KEY_PRIORITY_FORM_NAMES, formName);

					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			}
			c.close();
		}

	}

	public void deleteTemporaryPatients() throws SQLException {

		Cursor c = null;
		String query = "SELECT " + DataModel.PATIENTS_TABLE + "." + DataModel.KEY_PATIENT_ID + " AS " + DataModel.KEY_PATIENT_ID + " FROM " + DataModel.PATIENTS_TABLE + " LEFT OUTER JOIN " + DataModel.FORMINSTANCES_TABLE + " ON " + DataModel.PATIENTS_TABLE + "."
				+ DataModel.KEY_PATIENT_ID + " = " + DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_PATIENT_ID + " WHERE " + DataModel.KEY_CLIENT_CREATED + "= 2" + " AND " + DataModel.FORMINSTANCES_TABLE + "." + DataModel.KEY_PATIENT_ID + " IS NULL";

		c = DbProvider.openDb().rawQuery(query, null);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(DataModel.KEY_PATIENT_ID));
					DbProvider.openDb().delete(DataModel.PATIENTS_TABLE, DataModel.KEY_PATIENT_ID + "=?", new String[] { patientId });

				} while (c.moveToNext());
			}
			c.close();
		}

	}

	public void updatePriorityFormNumbers2(){
		DbProvider.openDb();
		Cursor c = null;
		String subquery = SQLiteQueryBuilder.buildQueryString(
		// include distinct
				true,
				// FROM tables
				DataModel.FORMS_TABLE + "," + DataModel.OBSERVATIONS_TABLE,
				// two columns (one of which is a group_concat()
				new String[] { DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_PATIENT_ID + ", group_concat(" + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_NAME + ",\", \") AS " + DataModel.KEY_PRIORITY_FORM_NAMES },
				// where
				DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_FIELD_NAME + "=" + DataModel.KEY_FIELD_FORM_VALUE + " AND " + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_ID + "=" + DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_VALUE_INT,
				// group by
				DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_PATIENT_ID, null, null, null);

		c = DbProvider.openDb().rawQuery(subquery, null);
	}
	
	public void updatePriorityFormNumbers() throws SQLException {

		Cursor c = null;
		String[] projection = new String[] { DataModel.KEY_PATIENT_ID, "count(*) as " + DataModel.KEY_PRIORITY_FORM_NUMBER };
		String selection = DataModel.KEY_FIELD_NAME + "=" + DataModel.KEY_FIELD_FORM_VALUE;
		String groupBy = DataModel.KEY_PATIENT_ID;
		c = DbProvider.openDb().query(DataModel.OBSERVATIONS_TABLE, projection, selection, null, groupBy, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(DataModel.KEY_PATIENT_ID));
					int formNumber = c.getInt(c.getColumnIndex(DataModel.KEY_PRIORITY_FORM_NUMBER));
					ContentValues cv = new ContentValues();
					cv.put(DataModel.KEY_PRIORITY_FORM_NUMBER, formNumber);

					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + patientId, null);

				} while (c.moveToNext());
			}
			c.close();
		}

	}

	public void updatePriorityFormsByPatientId(String updatePatientId, String formId) throws SQLException {

		boolean deleted = DbProvider.openDb().delete(DataModel.OBSERVATIONS_TABLE, DataModel.KEY_PATIENT_ID + "=" + updatePatientId + " AND " + DataModel.KEY_FIELD_NAME + "=" + DataModel.KEY_FIELD_FORM_VALUE + " AND " + DataModel.KEY_VALUE_INT + "=" + formId, null);
		if (deleted) {

			Cursor c = null;
			// 1. Update the PriorityFormNumbers
			String[] projection = new String[] { DataModel.KEY_PATIENT_ID, "count(*) as " + DataModel.KEY_PRIORITY_FORM_NUMBER };
			String selection = DataModel.KEY_FIELD_NAME + "=? AND " + DataModel.KEY_PATIENT_ID + "=?";
			String[] selectionArgs = new String[] { DataModel.KEY_FIELD_FORM_VALUE, updatePatientId };
			String groupBy = DataModel.KEY_PATIENT_ID;
			c = DbProvider.openDb().query(DataModel.OBSERVATIONS_TABLE, projection, selection, selectionArgs, groupBy, null, null);
			ContentValues cv = new ContentValues();
			if (c != null) {
				if (c.moveToFirst()) {
					do {
						String patientId = c.getString(c.getColumnIndex(DataModel.KEY_PATIENT_ID));
						int formNumber = c.getInt(c.getColumnIndex(DataModel.KEY_PRIORITY_FORM_NUMBER));

						cv.put(DataModel.KEY_PRIORITY_FORM_NUMBER, formNumber);
						DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + patientId, null);

					} while (c.moveToNext());
				} else {
					// } else if
					// (c.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
					cv.putNull(DataModel.KEY_PRIORITY_FORM_NUMBER);
					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + updatePatientId, null);

				}

				c.close();
			}

			// 2. Update the PriorityFormNames
			Cursor cname = null;
			String subquery = SQLiteQueryBuilder.buildQueryString(
			// include distinct
					true,
					// FROM tables
					DataModel.FORMS_TABLE + "," + DataModel.OBSERVATIONS_TABLE,
					// two columns (one of which is a group_concat()
					new String[] { DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_PATIENT_ID + ", group_concat(" + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_NAME + ",\", \") AS " + DataModel.KEY_PRIORITY_FORM_NAMES },
					// where
					DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_FIELD_NAME + "=" + DataModel.KEY_FIELD_FORM_VALUE + " AND " + DataModel.FORMS_TABLE + "." + DataModel.KEY_FORM_ID + "=" + DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_VALUE_INT + " AND "
							+ DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_PATIENT_ID + "=" + updatePatientId,
					// group by
					DataModel.OBSERVATIONS_TABLE + "." + DataModel.KEY_PATIENT_ID, null, null, null);

			cname = DbProvider.openDb().rawQuery(subquery, null);
			ContentValues cvname = new ContentValues();
			if (cname != null) {
				if (cname.moveToFirst()) {
					do {
						String patientId = cname.getString(c.getColumnIndex(DataModel.KEY_PATIENT_ID));
						String formName = cname.getString(c.getColumnIndex(DataModel.KEY_PRIORITY_FORM_NAMES));

						cvname.put(DataModel.KEY_PRIORITY_FORM_NAMES, formName);
						DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cvname, DataModel.KEY_PATIENT_ID + "=" + patientId, null);
					} while (cname.moveToNext());
				} else {
					// } else if
					// (cname.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
					cvname.putNull(DataModel.KEY_PRIORITY_FORM_NAMES);
					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cvname, DataModel.KEY_PATIENT_ID + "=" + updatePatientId, null);

				}

				cname.close();
			}
		}
	}

	public void updateSavedFormsList() throws SQLException {
		DbProvider.openDb();
		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + " IS NOT NULL";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE };
		c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "group_concat( " + InstanceColumns.FORM_NAME + ") AS " + DataModel.KEY_SAVED_FORM_NAMES }, selection, selectionArgs, null);
		ContentValues cv = new ContentValues();
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
					String formName = c.getString(c.getColumnIndex(DataModel.KEY_SAVED_FORM_NAMES));
					cv.put(DataModel.KEY_SAVED_FORM_NAMES, formName);

					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			}
			c.close();
		}

	}

	// saved forms are kept in the AccessForms (i.e. ODK Collect) database...
	public void updateSavedFormNumbers() throws SQLException {
		DbProvider.openDb();
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
					cv.put(DataModel.KEY_SAVED_FORM_NUMBER, formNumber);

					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			}
			c.close();
		}
	}

	public void updateSavedFormsListByPatientId(String updatePatientId) throws SQLException {
		DbProvider.openDb();
		Cursor c = null;
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, updatePatientId };
		c = App.getApp().getContentResolver()
				.query(Uri.parse(InstanceColumns.CONTENT_URI + "/groupbypatientid"), new String[] { InstanceColumns.PATIENT_ID, "group_concat( " + InstanceColumns.FORM_NAME + ",\", \") AS " + DataModel.KEY_SAVED_FORM_NAMES }, selection, selectionArgs, null);

		ContentValues cv = new ContentValues();
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String patientId = c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID));
					String formName = c.getString(c.getColumnIndex(DataModel.KEY_SAVED_FORM_NAMES));

					cv.put(DataModel.KEY_SAVED_FORM_NAMES, formName);

					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());
			} else {
				// } else if
				// (c.isNull(c.getColumnIndex(InstanceColumns.PATIENT_ID))){
				cv.putNull(DataModel.KEY_SAVED_FORM_NAMES);
				DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + updatePatientId, null);

			}

			c.close();
		}

	}

	// TODO! Verify that this is ever called (if moveToFirst is null?) I think
	// the logic is wrong here!
	// saved forms are kept in the AccessForms (i.e. ODK Collect) database...
	public void updateSavedFormNumbersByPatientId(String updatePatientId) throws SQLException {
		DbProvider.openDb();
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
					cv.put(DataModel.KEY_SAVED_FORM_NUMBER, formNumber);

					DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + patientId, null);
				} while (c.moveToNext());

			} else {
				cv.putNull(DataModel.KEY_SAVED_FORM_NUMBER);
				DbProvider.openDb().update(DataModel.PATIENTS_TABLE, cv, DataModel.KEY_PATIENT_ID + "=" + updatePatientId, null);

			}

			c.close();
		}

	}

	public boolean updateFormInstance(String path, String status) {
		DbProvider.openDb();
		ContentValues cv = new ContentValues();
		cv.put(DataModel.KEY_FORMINSTANCE_STATUS, status);
		return DbProvider.openDb().update(DataModel.FORMINSTANCES_TABLE, cv, DataModel.KEY_PATH + "='" + path + "'", null);
	}

	public boolean updateFormPath(Integer formId, String path) {
		DbProvider.openDb();
		ContentValues cv = new ContentValues();
		cv.put(DataModel.KEY_FORM_ID, formId);
		cv.put(DataModel.KEY_PATH, path);
		return DbProvider.openDb().update(DataModel.FORMS_TABLE, cv, DataModel.KEY_FORM_ID + "='" + formId.toString() + "'", null);
	}

}