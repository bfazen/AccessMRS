package com.alphabetbloc.clinic.ui.user;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import com.alphabetbloc.clinic.R;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import com.alphabetbloc.clinic.adapters.FormAdapter;
import com.alphabetbloc.clinic.adapters.MergeAdapter;
import com.alphabetbloc.clinic.data.ActivityLog;
import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.data.FormInstance;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.tasks.ActivityLogTask;
import com.alphabetbloc.clinic.utilities.App;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * Displays ArrayList<Form> in a Time-Separated List with patient header
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewFormsActivity extends ViewDataActivity {

	public static final String EDIT_FORM = "edit_form";
	public static final int FILL_FORM = 3;
	public static final int VIEW_FORM_ONLY = 4;
	public static final int FILL_PRIORITY_FORM = 5;
	protected ActivityLog mActivityLog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	protected ArrayList<Form> getCompletedForms(String patientId) {
		String selection = "(" + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? ) and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_ENCRYPTED, InstanceProviderAPI.STATUS_COMPLETE, InstanceProviderAPI.STATUS_SUBMITTED, patientId };
		return queryInstances(selection, selectionArgs);
	}

	protected ArrayList<Form> getSavedForms(String patientId) {
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, patientId };
		return queryInstances(selection, selectionArgs);
	}

	protected FormAdapter buildInstancesList(Form[] forms) {
		ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(forms));
		FormAdapter formAdapter = new FormAdapter(this, R.layout.saved_instances, formList, false);
		return (formAdapter);
	}

	protected ArrayList<Integer> getPriorityForms(Integer patientId) {
		ArrayList<Integer> selectedFormIds = new ArrayList<Integer>();

		Cursor c = DbProvider.openDb().fetchPriorityFormIdByPatientId(patientId);

		if (c != null && c.getCount() > 0) {
			int valueIntIndex = c.getColumnIndex(DbProvider.KEY_VALUE_INT);
			do {
				selectedFormIds.add(c.getInt(valueIntIndex));
			} while (c.moveToNext());
		}

		if (c != null) {
			c.close();
		}

		return selectedFormIds;
	}

	protected ArrayList<Form> queryInstances(String selection, String[] selectionArgs) {

		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, InstanceColumns.LAST_STATUS_CHANGE_DATE + " DESC");

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

		return selectedForms;
	}

	/**
	 * Create an activity log of launching this form
	 * 
	 * @param patientId
	 * @param formId
	 * @param formtype
	 * @param priority
	 */
	protected void startActivityLog(String patientId, String formId, String formtype, boolean priority) {
		SharedPreferences chwsettings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (chwsettings.getBoolean("IsLoggingEnabled", true)) {

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			String providerId = settings.getString(getString(R.string.key_provider), "0");
			mActivityLog = new ActivityLog();
			mActivityLog.setProviderId(providerId);
			mActivityLog.setFormId(formId);
			mActivityLog.setPatientId(patientId);
			mActivityLog.setActivityStartTime();
			mActivityLog.setFormType(formtype);
			if (priority)
				mActivityLog.setFormPriority("true");
			else
				mActivityLog.setFormPriority("false");
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			mActivityLog.setActivityStopTime();
			new ActivityLogTask(mActivityLog).execute();
		}
	}

	protected MergeAdapter createFormHistoryList(MergeAdapter mMergeAdapter, ArrayList<Form> formInstances) {

		if (formInstances != null && formInstances.size() > 0) {
			ArrayList<Form> dayGroup = new ArrayList<Form>();
			ArrayList<Form> weekGroup = new ArrayList<Form>();
			ArrayList<Form> monthGroup = new ArrayList<Form>();
			ArrayList<Form> sixMonthGroup = new ArrayList<Form>();
			ArrayList<Form> oneYearGroup = new ArrayList<Form>();
			// ArrayList<Form> gtOneYearGroup = new ArrayList<Form>();
			ArrayList<Form> allOtherGroup = new ArrayList<Form>();

			for (Form form : formInstances) {
				Long age = (Long.valueOf(System.currentTimeMillis()) - (form.getDate()));
				int day = 1000 * 60 * 60 * 24;
				if (age != null && age >= 0) {
					if (age < day)
						dayGroup.add(form);
					else if (age < (day * 7))
						weekGroup.add(form);
					else if (age < (day * 7 * 30))
						monthGroup.add(form);
					else if (age < (day * 7 * 30 * 6))
						sixMonthGroup.add(form);
					else if (age < (day * 7 * 30 * 12))
						oneYearGroup.add(form);
					// else if (age > (day*7*30*12)) gtOneYearGroup.add(form);
					else
						allOtherGroup.add(form);
				}

			}
			if (!dayGroup.isEmpty()) {
				Form[] day = dayGroup.toArray(new Form[dayGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_day), false));
				mMergeAdapter.addAdapter(buildInstancesList(day));
			}
			if (!weekGroup.isEmpty()) {
				Form[] week = weekGroup.toArray(new Form[weekGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_week), false));
				mMergeAdapter.addAdapter(buildInstancesList(week));
			}
			if (!monthGroup.isEmpty()) {
				Form[] month = monthGroup.toArray(new Form[monthGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_month), false));
				mMergeAdapter.addAdapter(buildInstancesList(month));
			}
			if (!sixMonthGroup.isEmpty()) {
				Form[] sixmonth = monthGroup.toArray(new Form[monthGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_six_months), false));
				mMergeAdapter.addAdapter(buildInstancesList(sixmonth));
			}
			if (!oneYearGroup.isEmpty()) {
				Form[] year = oneYearGroup.toArray(new Form[oneYearGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_year), false));
				mMergeAdapter.addAdapter(buildInstancesList(year));
			}
			if (!allOtherGroup.isEmpty()) {
				Form[] allother = allOtherGroup.toArray(new Form[allOtherGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_unknown_gt_year), false));
				mMergeAdapter.addAdapter(buildInstancesList(allother));
			}

		}

		return mMergeAdapter;
	}

	protected void updateDatabases(int requestCode, Intent intent, Integer patientId) {
		// 1. GET instance from Collect
		Uri u = intent.getData();
		String dbjrFormId = null;
		String displayName = null;
		String fileDbPath = null;
		String status = null;

		Cursor mCursor = App.getApp().getContentResolver().query(u, null, null, null, null);
		mCursor.moveToPosition(-1);
		while (mCursor.moveToNext()) {
			status = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.STATUS));
			dbjrFormId = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.JR_FORM_ID));
			displayName = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.DISPLAY_NAME));
			fileDbPath = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
		}
		if (mCursor != null) {
			mCursor.close();
		}

		// 2. updateSavedForm Numbers by Patient ID--Not necessary here,
		// as we have no ID
		// Allows for faster PatientList Queries using ONLY patient table
		// otherwise, for patient list, need: patients, instances, and obs
		// tables
		DbProvider ca = DbProvider.openDb();
		if (patientId > 0) {
			ca.updateSavedFormNumbersByPatientId(patientId.toString());
			ca.updateSavedFormsListByPatientId(patientId.toString());
		}

		// 3. Add to Clinic Db if complete, even without ID
		if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {

			FormInstance fi = new FormInstance();
			fi.setPatientId(patientId);
			fi.setFormId(Integer.parseInt(dbjrFormId));
			fi.setPath(fileDbPath);
			fi.setStatus(DbProvider.STATUS_UNSUBMITTED);
			Date date = new Date();
			date.setTime(System.currentTimeMillis());
			String dateString = "Completed: " + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
			fi.setCompletionSubtext(dateString);
			ca.createFormInstance(fi, displayName);

			if (requestCode == FILL_PRIORITY_FORM) {
				ca.updatePriorityFormsByPatientId(patientId.toString(), dbjrFormId);
			}
		}

	}
}