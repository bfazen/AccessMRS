package org.odk.clinic.android.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.FormAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.tasks.ActivityLogTask;
import org.odk.clinic.android.utilities.App;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays all the Saved forms from Collect instances.db.  
 *  * This View is only called from ViewPatientList and AllFormList when int CompletedForms > 5
 * If called from ViewPatient, then it has a patient, and accounts for Priority Forms
 * If called from AllFormList, then is has patientId = -1, and skips Priority Forms
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewSavedForms extends ListActivity {

	public static final String EDIT_FORM = "edit_form";
	public static final int FILL_FORM = 4;
	public static final int FILL_PRIORITY_FORM = 5;

	private Integer mPatientId;
	private Context mContext;
	private static String mProviderId;
	private MergeAdapter mAdapter;
	private static Patient mPatient;
	private ActivityLog mActivityLog;
	private Resources res;
	private ArrayList<Integer> mSelectedFormIds = new ArrayList<Integer>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.app_name) + " > " + "Previous Encounters");
		mContext = this;
		res = this.getResources();
		mPatientId = getIntent().getIntExtra(Constants.KEY_PATIENT_ID, 0);
		
		if (mPatientId > 0) {
			setContentView(R.layout.example_cw_main);
			mPatient = getPatient(mPatientId);

			TextView textView = (TextView) findViewById(R.id.identifier_text);
			if (textView != null) {
				textView.setText(mPatient.getIdentifier());
			}

			textView = (TextView) findViewById(R.id.name_text);
			if (textView != null) {
				StringBuilder nameBuilder = new StringBuilder();
				nameBuilder.append(mPatient.getGivenName());
				nameBuilder.append(' ');
				nameBuilder.append(mPatient.getMiddleName());
				nameBuilder.append(' ');
				nameBuilder.append(mPatient.getFamilyName());
				textView.setText(nameBuilder.toString());
			}

			textView = (TextView) findViewById(R.id.birthdate_text);
			if (textView != null) {
				textView.setText(mPatient.getBirthdate());

			}

			ImageView imageView = (ImageView) findViewById(R.id.gender_image);
			if (imageView != null) {
				if (mPatient.getGender().equals("M")) {
					imageView.setImageResource(R.drawable.male_gray);
				} else if (mPatient.getGender().equals("F")) {
					imageView.setImageResource(R.drawable.female_gray);
				}
			}
		} else {
			
			setContentView(R.layout.all_form_list);
			TextView textView = (TextView) findViewById(R.id.name_text);
			ImageView sectionImage = (ImageView) findViewById(R.id.section_image);
			View viewTitle = (View) findViewById(R.id.title);
			viewTitle.setBackgroundResource(R.color.dark_gray);

			textView.setText("Forms For New Clients");
//			TODO: get rid of the need for res to be included here
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.icon));

		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mPatientId != null) {
			if(mPatientId > 0){	
				getPriorityForms();
			}
			refreshView();
		}
	}

	@Override
	protected void onListItemClick(ListView listView, View view, int position, long id) {
		Form f = (Form) getListAdapter().getItem(position);
		String type = null;
		int priority = FILL_FORM;
		if(mSelectedFormIds.contains(f.getFormId())){
			priority = FILL_PRIORITY_FORM;
		}
		
		launchSavedFormEntry(f.getInstanceId(), priority);
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString(), "Saved");
		}
	}

	
	private void getPriorityForms() {
		mSelectedFormIds = new ArrayList<Integer>();

		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPriorityFormIdByPatientId(mPatientId);

		if (c != null && c.getCount() > 0) {
			int valueIntIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_INT);
			do {
				mSelectedFormIds.add(c.getInt(valueIntIndex));
			} while (c.moveToNext());
		}

		if (c != null) {
			c.close();
		}
		ca.close();
		refreshView();
	}
	
	
	private void refreshView() {
		
		// 1. SAVED FORMS: gather the saved forms from Collect Instances.Db
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, mPatientId.toString() };

		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		Cursor csave = App.getApp().getContentResolver()
				.query(InstanceColumns.CONTENT_URI, new String[] { InstanceColumns._ID, InstanceColumns.JR_FORM_ID, InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_SUBTEXT, InstanceColumns.FORM_NAME, InstanceColumns.INSTANCE_FILE_PATH, InstanceColumns.LAST_STATUS_CHANGE_DATE }, selection, selectionArgs, null);
		ArrayList<Form> savedForms = new ArrayList<Form>();

		if (csave != null) {
			csave.moveToFirst();
		}

		if (csave != null && csave.getCount() >= 0) {
			int instanceIdIndex = csave.getColumnIndex(InstanceColumns._ID);
			int formIdIndex = csave.getColumnIndex(InstanceColumns.JR_FORM_ID);
			int subtextIndex = csave.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT);
			int displayNameIndex = csave.getColumnIndex(InstanceColumns.DISPLAY_NAME);
			int pathIndex = csave.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
			int nameIndex = csave.getColumnIndex(InstanceColumns.FORM_NAME);
			int dateIndex = csave.getColumnIndex(InstanceColumns.LAST_STATUS_CHANGE_DATE);

			if (csave.getCount() > 0) {
				Form form;
				do {
					if (!csave.isNull(instanceIdIndex)) {
						form = new Form();
						form.setInstanceId(csave.getInt(instanceIdIndex));
						form.setFormId(csave.getInt(formIdIndex));
						form.setName(csave.getString(nameIndex));
						form.setPath(csave.getString(pathIndex));
						form.setDisplaySubtext(csave.getString(subtextIndex));
						form.setDisplayName(csave.getString(displayNameIndex));
						form.setDate((int) csave.getLong(dateIndex));
						savedForms.add(form);
					}
				} while (csave.moveToNext());
			}
		}
		
		if (csave != null)
			csave.close();

		mAdapter = new MergeAdapter();	
		Collections.shuffle(savedForms);
		buildDateSections(savedForms);
		setListAdapter(mAdapter);
	}

	private void buildDateSections(ArrayList<Form> formInstances) {

		if (formInstances != null && formInstances.size() > 0) {
			int c = 1;
			// Method 1: Simple... use Arraylist despite extra overhead...

			ArrayList<Form> dayGroup = new ArrayList<Form>();
			ArrayList<Form> weekGroup = new ArrayList<Form>();

			ArrayList<Form> monthGroup = new ArrayList<Form>();
			ArrayList<Form> sixMonthGroup = new ArrayList<Form>();
			ArrayList<Form> oneYearGroup = new ArrayList<Form>();
			// ArrayList<Form> gtOneYearGroup = new ArrayList<Form>();
			ArrayList<Form> allOtherGroup = new ArrayList<Form>();

			for (Form form : formInstances) {
				Long age = (Long.valueOf(System.currentTimeMillis()) - (form.getDate()));
				Log.e("louis.fazen", "form.getDate():" + form.getDate());
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
				Log.e("louis.fazen", "daygroup is not null");
				Form[] day = dayGroup.toArray(new Form[dayGroup.size()]);
				mAdapter.addView(buildSectionLabel(getString(R.string.instances_last_day)));
				mAdapter.addAdapter(buildInstancesList(day));
			}
			if (!weekGroup.isEmpty()) {
				Log.e("louis.fazen", "weekgroup is not null");
				Form[] week = weekGroup.toArray(new Form[weekGroup.size()]);
				mAdapter.addView(buildSectionLabel(getString(R.string.instances_last_week)));
				mAdapter.addAdapter(buildInstancesList(week));
			}
			if (!monthGroup.isEmpty()) {
				Log.e("louis.fazen", "monthgroup is not null");
				Form[] month = monthGroup.toArray(new Form[monthGroup.size()]);
				mAdapter.addView(buildSectionLabel(getString(R.string.instances_last_month)));
				mAdapter.addAdapter(buildInstancesList(month));
			}
			if (!sixMonthGroup.isEmpty()) {
				Log.e("louis.fazen", "sixmonthgroup is not null");
				Form[] sixmonth = monthGroup.toArray(new Form[monthGroup.size()]);
				mAdapter.addView(buildSectionLabel(getString(R.string.instances_last_six_months)));
				mAdapter.addAdapter(buildInstancesList(sixmonth));
			}
			if (!oneYearGroup.isEmpty()) {
				Log.e("louis.fazen", "oneyeargroup is not null");
				Form[] year = oneYearGroup.toArray(new Form[oneYearGroup.size()]);
				mAdapter.addView(buildSectionLabel(getString(R.string.instances_last_year)));
				mAdapter.addAdapter(buildInstancesList(year));
			}
			if (!allOtherGroup.isEmpty()) {
				Log.e("louis.fazen", "allothergroup is not null");
				Form[] allother = allOtherGroup.toArray(new Form[allOtherGroup.size()]);
				mAdapter.addView(buildSectionLabel(getString(R.string.instances_unknown_gt_year)));
				mAdapter.addAdapter(buildInstancesList(allother));
			}

		}
	}

	private View buildSectionLabel(String timeperiod) {
		Log.e("louis.fazen", "buildSectionLabel is called for: " + timeperiod);
		View v;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);
		sectionImage.setVisibility(View.GONE);
		v.setBackgroundResource(R.color.dark_gray);
		textView.setText(timeperiod);
		return (v);
	}

	private FormAdapter buildInstancesList(Form[] forms) {
		ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(forms));
		Collections.shuffle(formList);
		FormAdapter formAdapter = new FormAdapter(this, R.layout.saved_instances, formList, false);
		return (formAdapter);
	}
	
	private void launchSavedFormEntry(int instanceId, int priority) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
		startActivityForResult(intent, priority);
		finish();
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
		// // TODO: louis.fazen added RESULT_OK based on:
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
				if(mPatientId > 0){	
				ca.updateSavedFormNumbersByPatientId(mPatient.getPatientId().toString());
				ca.updateSavedFormsListByPatientId(mPatient.getPatientId().toString());
				}
				
				// 3. Add to Clinic Db if complete, even without ID
				if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {
					Log.e("lef-onActivityResult", "mCursor status:" + status + "=" + InstanceProviderAPI.STATUS_COMPLETE);
					FormInstance fi = new FormInstance();
					fi.setPatientId(mPatientId); // TODO: should change this to look up
											// the patient ID in Collect first,
											// just in case...
					fi.setFormId(Integer.parseInt(dbjrFormId));
					fi.setPath(filePath);
					fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);
					Date date = new Date();
					date.setTime(System.currentTimeMillis());
					String dateString = "Completed on" + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
					fi.setCompletionSubtext(dateString);
					ca.createFormInstance(fi, displayName);
				
					if (requestCode == FILL_PRIORITY_FORM) {
						ca.updatePriorityFormsByPatientId(mPatient.getPatientId().toString(), dbjrFormId);
					}
				}


				ca.close();

			}
			return;
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	private void startActivityLog(String formId, String formType) {

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(mProviderId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId(mPatientId.toString());
		mActivityLog.setActivityStartTime();
		mActivityLog.setFormType(formType);
		
		if (mSelectedFormIds.contains(Integer.valueOf(formId))) {
			mActivityLog.setFormPriority("true");
		} else {
			mActivityLog.setFormPriority("false");
		}

	}

	// TODO: change this to pass a Parceable object from ViewPatientActivity
	// (make Patient parceable)
	private Patient getPatient(Integer patientId) {

		Patient p = null;
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {
			int patientIdIndex = c.getColumnIndex(ClinicAdapter.KEY_PATIENT_ID);
			int identifierIndex = c.getColumnIndex(ClinicAdapter.KEY_IDENTIFIER);
			int givenNameIndex = c.getColumnIndex(ClinicAdapter.KEY_GIVEN_NAME);
			int familyNameIndex = c.getColumnIndex(ClinicAdapter.KEY_FAMILY_NAME);
			int middleNameIndex = c.getColumnIndex(ClinicAdapter.KEY_MIDDLE_NAME);
			int birthDateIndex = c.getColumnIndex(ClinicAdapter.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(ClinicAdapter.KEY_GENDER);

			// TODO: louis.fazen check all the other occurrences of get and
			// setFamilyName and add get and set priority as well...
			int priorityIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NAMES);

			p = new Patient();
			p.setPatientId(c.getInt(patientIdIndex));
			p.setIdentifier(c.getString(identifierIndex));
			p.setGivenName(c.getString(givenNameIndex));
			p.setFamilyName(c.getString(familyNameIndex));
			p.setMiddleName(c.getString(middleNameIndex));
			p.setBirthDate(c.getString(birthDateIndex));
			p.setGender(c.getString(genderIndex));

			// TODO: louis.fazen check all the other occurrences of get
			// and setFamilyName and add get and set priority as well...
			p.setPriorityNumber(c.getInt(priorityIndex));
			p.setPriorityForms(c.getString(priorityFormIndex));

			if (c.getInt(priorityIndex) > 0) {

				p.setPriority(true);

			}

		}

		if (c != null) {
			c.close();
		}
		ca.close();

		return p;
	}

}