package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.tasks.ActivityLogTask;
import org.odk.clinic.android.utilities.App;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.alphabetbloc.clinic.services.RefreshDataService;

/**
 * Displays all the Completed/Finalized forms from Collect instances.db If
 * called from ViewPatient, then it has a patient, and must have Patient heading
 * If called from AllFormList, then is has patientId = -1, and has different
 * heading
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewCompletedForms extends ViewFormsActivity {

	public static final String EDIT_FORM = "edit_form";
	public static final int VIEW_FORM_ONLY = 4;

	private static Integer mPatientId;
	private ActivityLog mActivityLog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.app_name) + " > " + "Previous Encounters");

		String patientIdString = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
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

		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);

		if (mPatientId != null) {
			createPatientHeader(mPatientId);
			refreshView();

		}
	}

	private void refreshView() {

		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_COMPLETE, mPatientId.toString() };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, InstanceColumns.LAST_STATUS_CHANGE_DATE + " desc");

		ArrayList<Form> selectedForms = new ArrayList<Form>();

		if (c != null) {
			c.moveToFirst();
		}

		if (c != null && c.getCount() >= 0) {
			int formPathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
			int formIdIndex = c.getColumnIndex(InstanceColumns.JR_FORM_ID);
			int displayNameIndex = c.getColumnIndex(InstanceColumns.DISPLAY_NAME);
			int formNameIndex = c.getColumnIndex(InstanceColumns.FORM_NAME);
			int displaySubtextIndex = c.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT);
			int idIndex = c.getColumnIndex(InstanceColumns._ID);
			int dateIndex = c.getColumnIndex(InstanceColumns.LAST_STATUS_CHANGE_DATE);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(idIndex)) {
						form = new Form();
						form.setInstanceId(c.getInt(idIndex));
						form.setFormId(c.getInt(formIdIndex));
						form.setName(c.getString(formNameIndex));
						form.setDisplayName(c.getString(displayNameIndex));
						form.setPath(c.getString(formPathIndex));
						form.setDisplaySubtext(c.getString(displaySubtextIndex));
						form.setDate(c.getLong(dateIndex));

						selectedForms.add(form);
					}
				} while (c.moveToNext());
			}
		}

		if (c != null)
			c.close();

		createFormHistoryList(selectedForms);
	}

	class onFormClick extends formGestureDetector {

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
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString());
		}

		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + f.getInstanceId()));
		startActivityForResult(intent, VIEW_FORM_ONLY);
	}

	private void startActivityLog(String formId) {

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String providerId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(providerId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId(mPatientId.toString());
		mActivityLog.setActivityStartTime();
		mActivityLog.setFormPriority("not applicable");
		mActivityLog.setFormType("Previous Encounter");

	}

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		mActivityLog.setActivityStopTime();
		new ActivityLogTask(mActivityLog).execute();
	}

}
