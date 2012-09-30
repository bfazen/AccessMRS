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
	
	public static final long MINIMUM_REFRESH_TIME = AlarmManager.INTERVAL_FIFTEEN_MINUTES/120;
	public static final long MAXIMUM_REFRESH_TIME = AlarmManager.INTERVAL_FIFTEEN_MINUTES/60;
	
	public static final String KEY_OBSERVATION_FIELD_NAME = "KEY_OBSERVATION_FIELD_NAME";
	
}
