/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author Yaw Anokwa, Carl Hartung (other authors? taken from ODK Clinic/Collect, originally from NotePadProvider Tutorial)
 *
 */
public final class InstanceProviderAPI {
    public static final String AUTHORITY = "org.odk.collect.android.provider.odk.instances";

    // This class cannot be instantiated
    private InstanceProviderAPI() {}
    public static final String DATABASE_NAME = "instances.db";
    
    // status for instances
    public static final String STATUS_INCOMPLETE = "incomplete";
    public static final String STATUS_COMPLETE = "complete";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_SUBMISSION_FAILED = "submissionFailed";
    public static final String STATUS_INITIALIZED = "initialized";
    public static final String STATUS_ENCRYPTED = "encrypted";
    
    /**
     * Notes table
     */
    public static final class InstanceColumns implements BaseColumns {
        // This class cannot be instantiated
        private InstanceColumns() {}
        
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/instances");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.odk.instance";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.odk.instance";

        // These are the only things needed for an insert
        public static final String DISPLAY_NAME = "displayName";
        public static final String SUBMISSION_URI = "submissionUri";
        public static final String INSTANCE_FILE_PATH = "instanceFilePath";
        public static final String JR_FORM_ID = "jrFormId";
        
        
//        louis.fazen is adding these two columns because they were not provided originally...
        public static final String PATIENT_ID = "patientId";
        public static final String FORM_NAME = "formName";
        
        // these are generated for you (but you can insert something else if you want)
        public static final String STATUS = "status";
        public static final String LAST_STATUS_CHANGE_DATE = "date";
        public static final String DISPLAY_SUBTEXT = "displaySubtext";
        public static final String SUBMISSION_KEY = "key";
        public static final String DECRYPTION_TIME = "decryptionTime";
        
        //public static final String DISPLAY_SUB_SUBTEXT = "displaySubSubtext";


//        public static final String DEFAULT_SORT_ORDER = "modified DESC";
//        public static final String TITLE = "title";
//        public static final String NOTE = "note";
//        public static final String CREATED_DATE = "created";
//        public static final String MODIFIED_DATE = "modified";
    }
}
