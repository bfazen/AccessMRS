package org.odk.clinic.android.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.ViewCompletedForms.onFormClick;
import org.odk.clinic.android.activities.ViewFormsActivity.formGestureDetector;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
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
import android.widget.ListView;

import com.alphabetbloc.clinic.services.RefreshDataService;

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

	public static final String EDIT_FORM = "edit_form";
	public static final int FILL_FORM = 4;
	public static final int FILL_PRIORITY_FORM = 5;

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
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, mPatientId.toString() };
		Cursor c = App
				.getApp()
				.getContentResolver()
				.query(InstanceColumns.CONTENT_URI,
						new String[] { InstanceColumns._ID, InstanceColumns.JR_FORM_ID, InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_SUBTEXT, InstanceColumns.FORM_NAME, InstanceColumns.INSTANCE_FILE_PATH, InstanceColumns.LAST_STATUS_CHANGE_DATE }, selection,
						selectionArgs, InstanceColumns.LAST_STATUS_CHANGE_DATE + " DESC");

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

		int priority = FILL_FORM;
		if (mPatientId > 0) {
			ArrayList<Integer> selectedFormIds = getPriorityForms(mPatientId);
			if (selectedFormIds.contains(f.getFormId())) {
				priority = FILL_PRIORITY_FORM;
			}
		}

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString(), priority);
		}

		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + f.getInstanceId()));
		startActivityForResult(intent, priority);

	}

	private void startActivityLog(String formId, int priority) {

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String providerId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(providerId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId(mPatientId.toString());
		mActivityLog.setActivityStartTime();
		mActivityLog.setFormType("Saved");
		if (priority == FILL_PRIORITY_FORM)
			mActivityLog.setFormPriority("true");
		else
			mActivityLog.setFormPriority("false");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			mActivityLog.setActivityStopTime();
			new ActivityLogTask(mActivityLog).execute();
		}

		if (resultCode == RESULT_CANCELED) {
			return;
		}
		// NB: RESULT_OK based on:
		// Collect.FormEntryActivity.finishReturnInstance() line1654
		// Uri instance = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id);
		// setResult(RESULT_OK, new Intent().setData(instance));
		// BUT Original ListPatientActivity does not include it

		if (resultCode == RESULT_OK) {

			if ((requestCode == FILL_FORM || requestCode == FILL_PRIORITY_FORM) && intent != null) {

				// 1. GET instance from Collect
				Uri u = intent.getData();
				String dbjrFormId = null;
				String displayName = null;
				String filePath = null;
				String status = null;

				Cursor mCursor = App.getApp().getContentResolver().query(u, null, null, null, null);
				mCursor.moveToPosition(-1);
				while (mCursor.moveToNext()) {
					status = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.STATUS));
					dbjrFormId = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.JR_FORM_ID));
					displayName = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.DISPLAY_NAME));
					filePath = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
				}
				if (mCursor != null) {
					mCursor.close();
				}

				// 2. updateSavedForm Numbers by Patient ID--Not necessary here,
				// as we have no ID
				ClinicAdapter ca = new ClinicAdapter();
				ca.open();
				if (mPatientId > 0) {
					ca.updateSavedFormNumbersByPatientId(mPatientId.toString());
					ca.updateSavedFormsListByPatientId(mPatientId.toString());
				}

				// 3. Add to Clinic Db if complete, even without ID
				if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {

					FormInstance fi = new FormInstance();
					fi.setPatientId(mPatientId);
					fi.setFormId(Integer.parseInt(dbjrFormId));
					fi.setPath(filePath);
					fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);
					Date date = new Date();
					date.setTime(System.currentTimeMillis());
					String dateString = "Completed: " + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
					fi.setCompletionSubtext(dateString);
					ca.createFormInstance(fi, displayName);

					if (requestCode == FILL_PRIORITY_FORM) {
						ca.updatePriorityFormsByPatientId(mPatientId.toString(), dbjrFormId);
					}
				}

				ca.close();

			}

		}
		super.onActivityResult(requestCode, resultCode, intent);
		finish();
	}

}