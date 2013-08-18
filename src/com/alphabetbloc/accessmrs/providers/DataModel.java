package com.alphabetbloc.accessmrs.providers;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class DataModel {

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
//	public static final String KEY_PRIORITY_FORM_NAMES = "priority_forms";
	public static final String KEY_PRIORITY_FORM_NUMBER = "priority_number";
//	public static final String KEY_SAVED_FORM_NAMES = "saved_forms";
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
	public static final String KEY_FIELD_FORM_VALUE = "\'odkconnector.property.form\'";

	// observation types
	public static final int TYPE_STRING = 1;
	public static final int TYPE_INT = 2;
	public static final int TYPE_DOUBLE = 3;
	public static final int TYPE_DATE = 4;

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

	// event columns
	public static final String PROVIDER_ID = "provider";
	public static final String PATIENT_ID = "patient";
	public static final String FORM_PRIORITY_BOOLEAN = "form_priority";
	public static final String FORM_NAME = "form_name";
	public static final String FORM_START_TIME = "start_time";
	public static final String FORM_STOP_TIME = "stop_time";
	public static final String FORM_LAUNCH_TYPE = "launch_type";
	public static final String FORM_LOG_TABLE = "form_log";

	// download table
	public static final String DOWNLOAD_TIME = "download_time";

	//sync table
	public static final String TABLE_NAME = "table_name";
	public static final String SYNC_STATUS = "sync_status";
	public static final String SYNC_RESULT = "sync_result";
	
	//consent table
	public static final String CONSENT_DATE = "date";
	public static final String CONSENT_EXPIRATION_DATE = "expiration_date";
	public static final String CONSENT_SIGNATURE = "signature";
	public static final String PATIENT_PHOTO = "photo";
	public static final String CONSENT_VALUE = "consent";
	public static final String CONSENT_VOIDED = "voided";
	public static final String CONSENT_VOIDED_DATE = "voided_date";
	public static final int CONSENT_OBTAINED = 1;
	public static final int CONSENT_DECLINED = 0;
	public static final int CONSENT_IS_VOIDED = 1;
	public static final int CONSENT_NOT_VOIDED = 0;
	
	// tables
	public static final String PATIENTS_TABLE = "patients";
	public static final String OBSERVATIONS_TABLE = "observations";
	public static final String FORMS_TABLE = "forms";
	public static final String FORMINSTANCES_TABLE = "instances";
	public static final String DOWNLOAD_LOG_TABLE = "download_log";
	public static final String SYNC_TABLE = "sync";
	public static final String CONSENT_TABLE = "consent";
	
	// database
	public static final String DATABASE_NAME = "accessmrs.sqlite3";
	public static final int DATABASE_VERSION = 11;

	// table create statements
	public static final String CREATE_PATIENTS_TABLE = "create table " + 
			PATIENTS_TABLE + " (_id integer primary key autoincrement, " + 
			KEY_PATIENT_ID + " integer not null, " + 
			KEY_IDENTIFIER + " text, " + 
			KEY_GIVEN_NAME + " text, " + 
			KEY_FAMILY_NAME + " text, "	+ 
			KEY_MIDDLE_NAME + " text, " + 
			KEY_BIRTH_DATE + " text, " + 
			KEY_GENDER + " text, " + 
			KEY_CLIENT_CREATED + " integer, " + 
			KEY_UUID + " text, " + 
			KEY_PRIORITY_FORM_NUMBER + " integer, " + 
//			KEY_PRIORITY_FORM_NAMES + " text, " + 
			KEY_SAVED_FORM_NUMBER + " integer);"; 
//			KEY_SAVED_FORM_NAMES + " text);";

	public static final String CREATE_OBSERVATIONS_TABLE = "create table " + 
			OBSERVATIONS_TABLE + " (_id integer primary key autoincrement, " + 
			KEY_PATIENT_ID + " integer not null, " + 
			KEY_DATA_TYPE + " integer not null, " + 
			KEY_VALUE_TEXT + " text, " + 
			KEY_VALUE_NUMERIC + " double, " + 
			KEY_VALUE_DATE + " text, " + 
			KEY_VALUE_INT + " integer, " + 
			KEY_FIELD_NAME + " text not null, " + 
			KEY_ENCOUNTER_DATE + " text not null);";

	public static final String CREATE_FORMS_TABLE = "create table " + 
			FORMS_TABLE + " (_id integer primary key autoincrement, " + 
			KEY_FORM_ID + " integer not null, " + 
			KEY_NAME + " text, " + 
			KEY_PATH + " text);";

	public static final String CREATE_FORMINSTANCES_TABLE = "create table " + 
			FORMINSTANCES_TABLE + " (_id integer primary key autoincrement, " + 
			KEY_PATIENT_ID + " integer not null, " + 
			KEY_FORM_ID + " integer not null, " + 
			KEY_FORMINSTANCE_DISPLAY + " text, " + 
			KEY_FORMINSTANCE_SUBTEXT + " text, " + 
			KEY_FORMINSTANCE_STATUS + " text, " + 
			KEY_PATH + " text);";

	public static final String CREATE_FORM_LOG_TABLE = "create table " + 
			FORM_LOG_TABLE + " (_id integer primary key autoincrement, " + 
			PATIENT_ID + " text, " + 
			PROVIDER_ID + " text, " + 
			FORM_NAME + " text, " + 
			FORM_START_TIME + " integer, " + 
			FORM_STOP_TIME + " integer, " + 
			FORM_LAUNCH_TYPE + " text, " + 
			FORM_PRIORITY_BOOLEAN + " text);";

	public static final String CREATE_DOWNLOAD_LOG_TABLE = "create table " + 
			DOWNLOAD_LOG_TABLE + " (_id integer primary key autoincrement, " + 
			DOWNLOAD_TIME + " integer);";
	
	public static final String CREATE_SYNC_TABLE = "create table " + 
			SYNC_TABLE + " (_id integer primary key autoincrement, " + 
			TABLE_NAME + " text, " + 
			DOWNLOAD_TIME + " integer, " +
			SYNC_STATUS + " integer, " + 
			SYNC_RESULT + " integer);";
	
	public static final String CREATE_CONSENT_TABLE = "create table " + 
			CONSENT_TABLE + " (_id integer primary key autoincrement, " + 
			KEY_PATIENT_ID + " integer not null, " + 
			CONSENT_DATE + " integer, " +
			PATIENT_PHOTO + " blob, " + 
			CONSENT_SIGNATURE + " blob, " + 
			CONSENT_VALUE + " integer, " +
			CONSENT_EXPIRATION_DATE + " integer, " +
			CONSENT_VOIDED + " integer, " +
			CONSENT_VOIDED_DATE + " integer);";
	
}