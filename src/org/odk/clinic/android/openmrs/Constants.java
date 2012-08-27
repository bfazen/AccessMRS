/*
 * Copyright (C) 2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.clinic.android.openmrs;

import android.app.AlarmManager;


/**
 * The constants used in multiple classes in this application.
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * 
 */
public class Constants {

	/** Value representing a not yet set status. */
	public static final int STATUS_NULL = -1;

	/** Value representing success of an action. */
	public static final int STATUS_SUCCESS = 1;

	/** Value representing failure of an action. */
	public static final int STATUS_FAILURE = 0;
	
	/** Value representing failure of an action. */
	public static final int STATUS_ACCESS_DENIED = 2;

	/** Action to get a list of form definitions. */
	public static final int ACTION_DOWNLOAD_FORMS = 3;

	/** Action to save a list of form data. */
	public static final int ACTION_UPLOAD_FORMS = 5;

	/** Action to download a list of patients from the server. */
	public static final int ACTION_DOWNLOAD_PATIENTS = 6;

	/** Action to download a list of patients from the server. */
	public static final int ACTION_DOWNLOAD_USERS = 7;

	/** Action to download a list of users and forms from the server. */
	public static final int ACTION_DOWNLOAD_COHORTS = 8;
	
	public static final int ACTION_DOWNLOAD_SAVED_SEARCHES = 9;
	
	/** Action to download a list of patients from the server. */
	public static final int ACTION_DOWNLOAD_SS_PATIENTS = 10;

	/** Action to download a list of users and forms from the server. */
	public static final int ACTION_DOWNLOAD_USERS_AND_FORMS = 11;
	
	/** Action to download a list of patients filtered by name and identifier. */
	public static final int ACTION_DOWNLOAD_FILTERED_PATIENTS = 15;
	
	/** The default value for the user serializer class.*/
	public static final String GLOBAL_PROP_KEY_USER_SERIALIZER= "xforms.userSerializer";
	
	/** The default value for the patient serializer class.*/
	public static final String GLOBAL_PROP_KEY_PATIENT_SERIALIZER  = "xforms.patientSerializer";
   
	/** The default value for the cohort serializer class.*/
    public static final String GLOBAL_PROP_KEY_COHORT_SERIALIZER = "xforms.cohortSerializer";
    
    public static final String GLOBAL_PROP_KEY_SAVED_SEARCH_SERIALIZER = "xforms.savedSearchSerializer";

	/** The default value for the xform serializer class.*/
	public static final String GLOBAL_PROP_KEY_XFORM_SERIALIZER = "xforms.xformSerializer";
	
	/** The global property key for the patient download cohort.*/
	public static final String GLOBAL_PROP_KEY_PATIENT_DOWNLOAD_COHORT = "xforms.patientDownloadCohort";
	
	public static final String GLOBAL_PROP_KEY_USE_PATIENT_XFORM = "xforms.usePatientXform";

	public static final String GLOBAL_PROP_KEY_USE_ENCOUNTER_XFORM = "xforms.useEncounterXform";
	
	public static final String USER_DOWNLOAD_URL = "/moduleServlet/xforms/userDownload";

	public static final String COHORT_DOWNLOAD_URL = "/module/odkconnector/download/cohort.form";

	public static final String PATIENT_DOWNLOAD_URL = "/module/odkconnector/download/patients.form";
	
	public static final String FORMLIST_DOWNLOAD_URL = "/moduleServlet/xformshelper/xfhFormList?type=odk_clinic";
	
	public static final String FORM_DOWNLOAD_URL = "/moduleServlet/xformshelper/xfhFormDownload?type=odk_clinic";
	
	public static final String INSTANCE_UPLOAD_URL = "/moduleServlet/xformshelper/xfhFormUpload";

	public static final int TYPE_STRING = 1;
	public static final int TYPE_INT = 2;
	public static final int TYPE_DOUBLE = 3;
	public static final int TYPE_DATE = 4;
	
	
	public static final String KEY_PATIENT_ID = "PATIENT_ID";
	public static final String KEY_UUID = "UUID";
	public static final String KEY_PATIENT_NAME = "PATIENT_NAME";
	public static final String KEY_PATIENT_IDENTIFIER = "PATIENT_IDENTIFIER";
	public static final String KEY_PATIENT_GENDER = "PATIENT_GENDER";
	public static final String KEY_PATIENT_DOB = "PATIENT_DOB";
	
	public static final long MINIMUM_REFRESH_TIME = AlarmManager.INTERVAL_FIFTEEN_MINUTES/15;
	public static final long MAXIMUM_REFRESH_TIME = AlarmManager.INTERVAL_HALF_HOUR/15;
	
	public static final String KEY_OBSERVATION_FIELD_NAME = "KEY_OBSERVATION_FIELD_NAME";
	
}
