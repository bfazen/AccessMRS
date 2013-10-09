/*
 * Copyright (C) 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alphabetbloc.accessmrs.ui.user;

import java.util.ArrayList;


import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ListView;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.adapters.MergeAdapter;
import com.alphabetbloc.accessmrs.data.Form;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.R;

/**
 * Displays all the Saved forms from AccessForms instances.db. * This View is only
 * called from ViewPatientList and AllFormList when int CompletedForms > 5 If
 * called from ViewPatient, then it has a patient, and accounts for Priority
 * Forms If called from AllFormList, then is has patientId = -1, and skips
 * Priority Forms
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewSavedForms extends ViewFormsActivity {

	private static Integer mPatientId;
	private ListView mListView;
	private MergeAdapter mMergeAdapter;
	private GestureDetector mFormDetector;
	private OnTouchListener mFormListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.view_forms);

		String patientIdString = getIntent().getStringExtra(KEY_PATIENT_ID);
		mPatientId = Integer.valueOf(patientIdString);

		mFormDetector = new GestureDetector(new onFormClick());
		mFormListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormDetector.onTouchEvent(event);
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
			createPatientHeader(mPatientId);
			refreshView();
	}

	protected void refreshView() {
		mMergeAdapter = new MergeAdapter();
		mMergeAdapter = createFormHistoryList(mMergeAdapter, getSavedForms(mPatientId.toString()));
		mListView = getListView();
		mListView.setAdapter(mMergeAdapter);
		mListView.setOnTouchListener(mFormListener);
	}

	
	class onFormClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			int pos = mListView.pointToPosition((int) e.getX(), (int) e.getY());
			if (pos != -1) {
				Object o = mMergeAdapter.getItem(pos);
				if (o instanceof Form) {
					launchFormView((Form) o);
				}

			}
			return false;
		}
	}

	protected void launchFormView(Form f) {

		int formType = FILL_FORM;
		boolean priority = false;
		if (mPatientId > 0) {
			ArrayList<Integer> selectedFormIds = getPriorityForms(mPatientId);
			if (selectedFormIds.contains(f.getFormId())) {
				formType = FILL_PRIORITY_FORM;
				priority = true;
			}
		}

		startActivityLog(mPatientId.toString(), f.getFormId().toString(), "Saved", priority);

		Intent intent = new Intent();
		intent.setComponent(new ComponentName("com.alphabetbloc.accessforms", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + f.getInstanceId()));
		
		//KOSIRAI TRIAL ONLY:
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		String kosiraiRct = prefs.getString(getString(R.string.key_kosirai_rct), getString(R.string.default_kosirai_rct));
		intent.putExtra(getString(R.string.key_kosirai_rct), kosiraiRct);
		
		startActivityForResult(intent, formType);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (resultCode == RESULT_CANCELED)
			return;
		else if (resultCode == RESULT_OK && (requestCode == FILL_FORM || requestCode == FILL_PRIORITY_FORM) && intent != null) {
			updateDatabases(requestCode, intent, mPatientId);
			finish();
		}
	
	}

}