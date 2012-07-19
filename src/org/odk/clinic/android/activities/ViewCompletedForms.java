package org.odk.clinic.android.activities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.FormAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays all the Completed/Finalized forms from Collect instances.db
 * If called from ViewPatient, then it has a patient, and must have Patient heading
 * If called from AllFormList, then is has patientId = -1, and has different heading
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewCompletedForms extends ListActivity {

	public static final String EDIT_FORM = "edit_form";
	public static final int VIEW_FORM_ONLY = 4;

	private ArrayList<Form> mTotalForms = new ArrayList<Form>();
	// private Patient mPatient;
	private String mPatientIdString;
	private Context mContext;
	private static String mProviderId;
	private MergeAdapter mAdapter;
	private static Patient mPatient;
	private ActivityLog mActivityLog;
	private Resources res;

	@Override
	public void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.app_name) + " > " + "Previous Encounters");
		mContext = this;
		res = this.getResources();

		mPatientIdString = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(mPatientIdString);
		
//		if (patientId > 0) {
			setContentView(R.layout.example_cw_main);
			mPatient = getPatient(patientId);

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
//		} else {
//			
//			setContentView(R.layout.all_form_list);
//			TextView textView = (TextView) findViewById(R.id.name_text);
//			ImageView sectionImage = (ImageView) findViewById(R.id.section_image);
//			View viewTitle = (View) findViewById(R.id.title);
//			viewTitle.setBackgroundResource(R.color.dark_gray);
//
//			textView.setText("Forms For New Clients");
////			TODO: get rid of the need for res to be included here
//			sectionImage.setImageDrawable(res.getDrawable(R.drawable.icon));
//
//		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mPatientIdString != null) {
			refreshView();
		}
	}

	@Override
	protected void onListItemClick(ListView listView, View view, int position, long id) {
		Form f = (Form) getListAdapter().getItem(position);
		launchFormViewOnly(f.getInstanceId());
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString(), "Previous Encounter");
		}
	}

	private void refreshView() {
		mAdapter = new MergeAdapter();
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_COMPLETE, mPatientIdString };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, InstanceColumns.LAST_STATUS_CHANGE_DATE + " desc");

		if (c != null) {
			c.moveToFirst();
		}

		if (c != null && c.getCount() >= 0) {
			mTotalForms.clear();
			int formPathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
			int formIdIndex = c.getColumnIndex(InstanceColumns.JR_FORM_ID);
			int displayNameIndex = c.getColumnIndex(InstanceColumns.DISPLAY_NAME);
			int formNameIndex = c.getColumnIndex(InstanceColumns.FORM_NAME);
			int displaySubtextIndex = c.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT);
			int idIndex = c.getColumnIndex(InstanceColumns._ID);
			// int patientIdIndex =
			// c.getColumnIndex(InstanceColumns.PATIENT_ID);
			// int displayStatusIndex =
//			c.getColumnIndex(InstanceColumns.STATUS);
			int dateIndex = c.getColumnIndex(InstanceColumns.LAST_STATUS_CHANGE_DATE);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(idIndex)) {
						form = new Form();
						form.setInstanceId(c.getInt(idIndex));
						form.setFormId(c.getInt(formIdIndex));
						// Official form name
						form.setName(c.getString(formNameIndex));
						// Final Form EditText (defaults to person or form name)
						form.setDisplayName(c.getString(displayNameIndex));
						
						form.setPath(c.getString(formPathIndex));
						//Submitted on vs. Saved on ... etc.
						form.setDisplaySubtext(c.getString(displaySubtextIndex));
						//used for splitting into date menus
						form.setDate(c.getLong(dateIndex));
						mTotalForms.add(form);
					}
				} while (c.moveToNext());

			}
		}

		if (c != null)
			c.close();

		Collections.shuffle(mTotalForms);
		// FormAdapter formAdapter = new FormAdapter(this,
		// R.layout.saved_instances, mTotalForms, false);
		// adapter.addAdapter(formAdapter);
		buildDateSections(mTotalForms);
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

			for (Form form : mTotalForms) {
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

	private void launchFormViewOnly(int instanceId) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));

		// TODO: The proper way to do this would be via using ACTION_VIEW
		// as it allows for external apps to connect to Collect as ViewOnly
		// Functionality, not just my version of Collect
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
		startActivityForResult(intent, VIEW_FORM_ONLY);

	}

	// Original Version:

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		mActivityLog.setActivityStopTime();
		// if (requestCode != patientAndFormCode) {
		// mActivityLog.setFormId("Error: StartCode=" + patientAndFormCode +
		// " EndCode=" + requestCode);
		// }
		new ActivityLogTask(mActivityLog).execute();
	}

	private void startActivityLog(String formId, String formType) {

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
		String patientId = String.valueOf(mPatient.getPatientId());
		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(mProviderId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId(patientId);
		mActivityLog.setActivityStartTime();
		mActivityLog.setFormPriority("not applicable");
		mActivityLog.setFormType(formType);

	}

	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_SHORT);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
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

/*
 * // Method 2: find the total in the array, then create the array, then add the
 * elements // about 4-8 times less overhead than an arraylist, but also means
 * you have to cycle through list twice // find the array total
 * 
 * //create the arrays ArrayList<Form> dayGroup = new ArrayList<Form>(); Form[]
 * weekGroup = new Form[10]; Form[] weekGroup = new Form[10]; Form[] monthGroup
 * = new Form[10]; Form[] sixMonthGroup = new Form[10]; Form[] oneYearGroup =
 * new Form[10]; Form[] gtOneYearGroup = new Form[10];
 * 
 * if (formInstances != null && formInstances.size() > 0) { Form[] formGroup =
 * new Form[10]; int c = 1; for (Form form : mTotalForms) {
 * 
 * while (c < 11) { formGroup[c++] = form; } c = 0;
 * 
 * Long interval = formGroup[1].getDate() - formGroup[10].getDate(); String
 * intervalString = null; adapter.addView(buildSectionLabel(intervalString));
 * adapter.addAdapter(buildInstancesList(formGroup)); } }
 */
