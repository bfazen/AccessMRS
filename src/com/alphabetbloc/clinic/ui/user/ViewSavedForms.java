package com.alphabetbloc.clinic.ui.user;

import java.util.ArrayList;

import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ListView;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.adapters.MergeAdapter;
import com.alphabetbloc.clinic.data.Form;

/**
 * Displays all the Saved forms from Collect instances.db. * This View is only
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
		setContentView(R.layout.example_cw_main);

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

	private void refreshView() {
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
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + f.getInstanceId()));
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