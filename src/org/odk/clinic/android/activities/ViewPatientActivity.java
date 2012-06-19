package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.adapters.ObservationAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

// TODO if no obs, don't crash when viewing patients

/**
 * 
 * Louis Fazen (louis.fazen@gmail.com) has simplified this ListActivity and
 * moved most of the code to FormPriorityList
 * 
 */
public class ViewPatientActivity extends ListActivity {

	private Button mActionButton;

	private static Patient mPatient;

	private static View mFormView;
	private static View mFormHistoryView;

	private ArrayList<Integer> mSelectedForms = new ArrayList<Integer>();

	private ArrayAdapter<Observation> mObservationAdapter;
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();

	private String patientIdStr;

	private String activityLogEnd = "";

	private Context mContext;

	private Resources res;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.view_patient);
		mContext = this;
		res = this.getResources();

		Log.e("louis.fazen", "mContext Constructor= " + mContext);

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			finish();
		}

		// TODO Check for invalid patient IDs
		patientIdStr = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);
		mPatient.setTotalCompletedForms(findPreviousEncounters());
		

		setTitle(getString(R.string.app_name) + " > " + getString(R.string.view_patient));

		TextView textView = (TextView) findViewById(R.id.identifier_text);
		Log.e("louis.fazen", "textvView Constructor= " + textView);
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

		// mActionButton = (Button) findViewById(R.id.fill_forms);
		// mActionButton.setOnClickListener(new OnClickListener() {
		// @Override
		// public void onClick(View arg0) {
		// if (checkForForms()) {
		// Intent i = new Intent(getApplicationContext(),
		// FormPriorityList.class);
		// i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
		// startActivity(i);
		// } else {
		// showCustomToast(getString(R.string.no_forms));
		// }
		// }
		//
		// });

		activityLogEnd = "oncreate";
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);
	}

	private boolean checkForForms() {
		boolean checkForms = false;

		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		Cursor c = ca.fetchAllForms();
		if (c != null && c.getCount() >= 0) {
			checkForms = true;
		}
		if (c != null)
			c.close();
		ca.close();
		return checkForms;
	}

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
			int savedNumberIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_SAVED_FORM_NUMBER);
			int savedFormIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_SAVED_FORM_NAMES);

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
			p.setSavedNumber(c.getInt(savedNumberIndex));
			p.setSavedForms(c.getString(savedFormIndex));

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

	private void getAllObservations(Integer patientId) {

		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPatientObservations(patientId);

		if (c != null && c.getCount() > 0) {
			mObservations.clear();
			int valueTextIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_TEXT);
			int valueIntIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_INT);
			int valueDateIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_DATE);
			int valueNumericIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_NUMERIC);
			int fieldNameIndex = c.getColumnIndex(ClinicAdapter.KEY_FIELD_NAME);
			int encounterDateIndex = c.getColumnIndex(ClinicAdapter.KEY_ENCOUNTER_DATE);
			int dataTypeIndex = c.getColumnIndex(ClinicAdapter.KEY_DATA_TYPE);

			Observation obs;
			String prevFieldName = null;
			do {
				String fieldName = c.getString(fieldNameIndex);

				if (fieldName.equalsIgnoreCase("odkconnector.property.form"))
					mSelectedForms.add(c.getInt(valueIntIndex));
				else {
					// We only want most recent observation, so only get first
					// observation
					if (!fieldName.equals(prevFieldName)) {

						obs = new Observation();
						obs.setFieldName(fieldName);
						obs.setEncounterDate(c.getString(encounterDateIndex));

						int dataType = c.getInt(dataTypeIndex);
						obs.setDataType((byte) dataType);
						switch (dataType) {
						case Constants.TYPE_INT:
							obs.setValueInt(c.getInt(valueIntIndex));
							break;
						case Constants.TYPE_DOUBLE:
							obs.setValueNumeric(c.getDouble(valueNumericIndex));
							break;
						case Constants.TYPE_DATE:
							obs.setValueDate(c.getString(valueDateIndex));

							break;
						default:
							obs.setValueText(c.getString(valueTextIndex));
						}

						mObservations.add(obs);

						prevFieldName = fieldName;
					}
				}

			} while (c.moveToNext());
		}

		refreshView();

		if (c != null) {
			c.close();
		}
		ca.close();
	}

	/*
	 * // TODO on long press, graph // TODO if you have only one value, don't
	 * display next level
	 * 
	 * @Override protected void onListItemClick(ListView listView, View view,
	 * int position, long id) {
	 * 
	 * if (mPatient != null) { // Get selected observation Observation obs =
	 * (Observation) getListAdapter().getItem(position);
	 * 
	 * Intent ip; int dataType = obs.getDataType(); if (dataType ==
	 * Constants.TYPE_INT || dataType == Constants.TYPE_DOUBLE) { ip = new
	 * Intent(getApplicationContext(), ObservationChartActivity.class);
	 * ip.putExtra(Constants.KEY_PATIENT_ID, mPatient.getPatientId()
	 * .toString()); ip.putExtra(Constants.KEY_OBSERVATION_FIELD_NAME,
	 * obs.getFieldName()); startActivity(ip); } else { ip = new
	 * Intent(getApplicationContext(), ObservationTimelineActivity.class);
	 * ip.putExtra(Constants.KEY_PATIENT_ID, mPatient.getPatientId()
	 * .toString()); ip.putExtra(Constants.KEY_OBSERVATION_FIELD_NAME,
	 * obs.getFieldName()); startActivity(ip); } } }
	 */

	private void refreshView() {

		MergeAdapter adapter = new MergeAdapter();

		mObservationAdapter = new ObservationAdapter(this, R.layout.observation_list_item, mObservations);
		mFormView = formView();

		adapter.addView(buildSectionLabel(getString(R.string.clinical_form_section)));
		adapter.addView(mFormView);

		adapter.addView(buildSectionLabel(getString(R.string.clinical_data_section)));
		adapter.addAdapter(mObservationAdapter);

		Log.e("louis.fazen", "totalCompletedForms=" + mPatient.getTotalCompletedForms());
		if (mPatient.getTotalCompletedForms() > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.form_history_section)));
			adapter.addView(formHistoryView());
		}

		setListAdapter(adapter);
	}

	private View buildSectionLabel(String section) {
		View v;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);

		if ((section == getString(R.string.clinical_form_section)) && mPatient.getPriority()) {
			v.setBackgroundResource(R.color.priority);
		} else {

			v.setBackgroundResource(R.color.medium_gray);
		}

		TextView textView = (TextView) v.findViewById(R.id.name_text);
		textView.setText(section);

		return (v);
	}

	private View formView() {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

		formsSummary.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (checkForForms()) {
					Intent i = new Intent(getApplicationContext(), FormPriorityList.class);
					i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
					startActivity(i);
				} else {
					showCustomToast(getString(R.string.no_forms));
				}
			}
		});

		ImageView priorityArrow = (ImageView) formsSummary.findViewById(R.id.arrow_image);
		ImageView priorityImage = (ImageView) formsSummary.findViewById(R.id.priority_image);
		TextView priorityNumber = (TextView) formsSummary.findViewById(R.id.priority_number);
		TextView allFormTitle = (TextView) formsSummary.findViewById(R.id.all_form_title);
		TextView formNames = (TextView) formsSummary.findViewById(R.id.form_names);

		// ImageView savedImage = (ImageView)
		// formsSummary.findViewById(R.id.saved_image);
		// TextView savedNumber = (TextView)
		// formsSummary.findViewById(R.id.saved_number);
		// // TextView allFormTitle = (TextView)
		// formsSummary.findViewById(R.id.all_form_title);
		// TextView savedFormNames = (TextView)
		// formsSummary.findViewById(R.id.saved_form_names);

		priorityImage.setImageDrawable(res.getDrawable(R.drawable.priority_icon_blank));
		formNames.setTextColor(res.getColor(R.color.dark_gray));

		if (priorityArrow != null && formNames != null && allFormTitle != null) {

			if (mPatient.getPriority()) {
				priorityArrow.setImageResource(R.drawable.arrow_red);
				formNames.setText(mPatient.getPriorityForms());
				formNames.setTextColor(R.color.priority);
				// formTitle.setText("Suggested Forms:");
				allFormTitle.setVisibility(View.GONE);
				if (priorityNumber != null && priorityImage != null) {
					priorityNumber.setText(mPatient.getPriorityNumber().toString());
					priorityImage.setVisibility(View.VISIBLE);
				}

			} else {
				priorityArrow.setImageResource(R.drawable.arrow_gray);
				formNames.setVisibility(View.GONE);
				// formNames.setText("No Outstanding Forms For " +
				// mPatient.getGivenName() + " " + mPatient.getFamilyName());
				allFormTitle.setText("View All Forms");

				if (priorityNumber != null && priorityImage != null) {
					priorityNumber.setText(null);
					priorityNumber.setVisibility(View.GONE);
					priorityImage.setVisibility(View.GONE);
				}

			}
		}

		return (formsSummary);
	}

	private Integer findPreviousEncounters() {
		Integer completedForms = 0;
		String patientIdStr = String.valueOf(mPatient.getPatientId());
		String selection = "(" + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? ) and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_COMPLETE, InstanceProviderAPI.STATUS_SUBMITTED, patientIdStr };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, new String[] { InstanceColumns.PATIENT_ID, "count(*) as count" }, selection, selectionArgs, null);

		if (c.moveToFirst()) {

//			if (patientIdStr == c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID))) {
				completedForms = c.getInt(c.getColumnIndex("count"));
//			}
		}

		c.close();
		return completedForms;
	}

	private View formHistoryView() {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

		formsSummary.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (checkForForms()) {
					Intent i = new Intent(getApplicationContext(), ViewCompletedForms.class);
					i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
					startActivity(i);
				} else {
					showCustomToast(getString(R.string.no_forms));
				}
			}
		});

		ImageView priorityArrow = (ImageView) formsSummary.findViewById(R.id.arrow_image);
		ImageView priorityImage = (ImageView) formsSummary.findViewById(R.id.priority_image);
		RelativeLayout priorityBlock = (RelativeLayout) formsSummary.findViewById(R.id.priority_block);
		TextView priorityNumber = (TextView) formsSummary.findViewById(R.id.priority_number);
		TextView allFormTitle = (TextView) formsSummary.findViewById(R.id.all_form_title);
		TextView formNames = (TextView) formsSummary.findViewById(R.id.form_names);

		if (priorityArrow != null && formNames != null && allFormTitle != null) {
			priorityImage.setImageDrawable(res.getDrawable(R.drawable.ic_gray_block));
			priorityBlock.setPadding(0, 0, 0, 0);
			
			priorityArrow.setImageResource(R.drawable.arrow_gray);
			formNames.setVisibility(View.GONE);
			allFormTitle.setTextColor(res.getColor(R.color.dark_gray));

			allFormTitle.setText("View All Forms");

			if (priorityNumber != null && priorityImage != null) {
				priorityNumber.setText(mPatient.getTotalCompletedForms().toString());
				priorityImage.setVisibility(View.VISIBLE);
			}
		}

		return (formsSummary);
	}

	@Override
	protected void onDestroy() {
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);
		super.onDestroy();
		activityLogEnd = "onDestroy";
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);
	}

	@Override
	protected void onResume() {
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);
		super.onResume();
		activityLogEnd = "onresume";
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);
		if (mPatient != null) {
			// TODO Create more efficient SQL query to get only the latest
			// observation values
			getAllObservations(mPatient.getPatientId());
		}
	}

	@Override
	protected void onPause() {
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);
		super.onPause();
		activityLogEnd = "onpause";
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		activityLogEnd = "onsaveInstanceState";
		Log.e("ViewPatientActivity", "ACTIVITY_LOG_END: " + activityLogEnd);

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

}

// louis.fazen extras.....
// View patientView = (View) findViewById(R.id.patient_info);
// patientView.setBackgroundResource(R.drawable.search_gradient);
// patientView.setBackgroundColor(res.getColor(R.color.light_gray));

// LayoutInflater vi = (LayoutInflater)
// mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
// formsSummary = (Button)
// getLayoutInflater().inflate(R.layout.priority_form_summary, null);
// formsSummary = (Button)
// getLayoutInflater().inflate(R.layout.priority_form_summary, null);

// button = (Button)
// getLayoutInflater().inflate(R.layout.priority_form_summary, null);

// vi.inflate(R.layout.priority_form_summary, null);

// nameView.setTextColor(res.getColor(R.color.priority));
// nameView.setTextColor(res.getColor(R.color.dark_gray));

// Button formsSummary = new Button(this);

// LayoutInflater inflater =
// (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
// ImageButton b =
// (Button)inflater.inflate(R.layout.priority_form_summary,
// null);

// }
// Button b = (Button)inflater.inflate(R.layout.priority_form_summary,
// null);

// formsSummary.setBackgroundResource(R.drawable.priority_form_summary);
// formsSummary = (Button)
// getLayoutInflater().inflate(R.layout.priority_form_summary, );

// formsSummary.setText("Add Capitalized Words");

// TODO: louis.fazen turn this into a list item
// ImageView priorityArrow = (ImageView)
// v.findViewById(R.id.arrow_image);

