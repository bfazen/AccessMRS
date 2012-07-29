package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.ViewCompletedForms.onFormClick;
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnTouchListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.services.RefreshDataService;

/**
 * 
 * Louis Fazen (louis.fazen@gmail.com) has simplified this ListActivity and
 * moved most of the code to ViewAllForms
 * 
 * @author Louis Fazen
 * @author Yaw Anokwa
 * 
 */
public class ViewPatientActivity extends ListActivity {
	private static Patient mPatient;
	private static View mFormView;
	private ArrayList<Integer> mSelectedForms = new ArrayList<Integer>();
	private ArrayAdapter<Observation> mObservationAdapter;
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();
	
	private static final int SWIPE_MIN_DISTANCE = 120;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;

	private String patientIdStr;
	private Context mContext;
	private Resources res;
	private MergeAdapter adapter;
	private GestureDetector mFormDetector;
	private OnTouchListener mFormListener;
	private GestureDetector mFormHistoryDetector;
	private OnTouchListener mFormHistoryListener;
	private GestureDetector mSwipeDetector;
	private OnTouchListener mSwipeListener;
	private ListView mClientListView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.view_patient);
		mContext = this;
		res = this.getResources();

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			finish();
		}
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.view_patient));

		// TODO Check for invalid patient IDs
		patientIdStr = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);
		mPatient.setTotalCompletedForms(findPreviousEncounters());
		
		mSwipeDetector = new GestureDetector(new onHeadingClick());
		mSwipeListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mSwipeDetector.onTouchEvent(event);
			}
		};
		
		mFormDetector = new GestureDetector(new onFormClick());
		mFormListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormDetector.onTouchEvent(event);
			}
		};
		
		mFormHistoryDetector = new GestureDetector(new onFormHistoryClick());
		mFormHistoryListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormHistoryDetector.onTouchEvent(event);
			}
		};

	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);
		if (mPatient != null) {

			// TODO Create more efficient SQL query to get only latest obs
			// values
			createPatientHeader(mPatient.getPatientId());
			getAllObservations(mPatient.getPatientId());
			getPatientForms(mPatient.getPatientId());
			refreshView();

		}
	}

	private void createPatientHeader(Integer patientId) {

		Patient focusPt = getPatient(patientId);
		
		View v = (View) findViewById(R.id.client_header);
		if (v != null){
			v.setOnTouchListener(mSwipeListener);
		} 
		
		TextView textView = (TextView) findViewById(R.id.identifier_text);
		if (textView != null) {
			textView.setText(focusPt.getIdentifier());
		}

		textView = (TextView) findViewById(R.id.name_text);
		if (textView != null) {
			StringBuilder nameBuilder = new StringBuilder();
			nameBuilder.append(focusPt.getGivenName());
			nameBuilder.append(' ');
			nameBuilder.append(focusPt.getMiddleName());
			nameBuilder.append(' ');
			nameBuilder.append(focusPt.getFamilyName());
			textView.setText(nameBuilder.toString());
		}

		textView = (TextView) findViewById(R.id.birthdate_text);
		if (textView != null) {
			textView.setText(focusPt.getBirthdate());

		}

		ImageView imageView = (ImageView) findViewById(R.id.gender_image);
		if (imageView != null) {
			if (focusPt.getGender().equals("M")) {
				imageView.setImageResource(R.drawable.male_gray);
			} else if (focusPt.getGender().equals("F")) {
				imageView.setImageResource(R.drawable.female_gray);
			}
		}
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

			p = new Patient();
			p.setPatientId(c.getInt(patientIdIndex));
			p.setIdentifier(c.getString(identifierIndex));
			p.setGivenName(c.getString(givenNameIndex));
			p.setFamilyName(c.getString(familyNameIndex));
			p.setMiddleName(c.getString(middleNameIndex));
			p.setBirthDate(c.getString(birthDateIndex));
			p.setGender(c.getString(genderIndex));

		}

		if (c != null) {
			c.close();
		}
		ca.close();

		return p;
	}

	private void getPatientForms(Integer patientId) {

		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		Cursor c = ca.fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {

			int priorityIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NAMES);
			int savedNumberIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_SAVED_FORM_NUMBER);
			int savedFormIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_SAVED_FORM_NAMES);

			mPatient.setPriorityNumber(c.getInt(priorityIndex));
			mPatient.setPriorityForms(c.getString(priorityFormIndex));
			mPatient.setSavedNumber(c.getInt(savedNumberIndex));
			mPatient.setSavedForms(c.getString(savedFormIndex));

			if (c.getInt(priorityIndex) > 0) {
				mPatient.setPriority(true);
			} else {
				mPatient.setPriority(false);
			}

			if (c.getInt(savedNumberIndex) > 0) {
				mPatient.setSaved(true);
			} else {
				mPatient.setSaved(false);
			}

		}

		if (c != null) {
			c.close();
		}
		ca.close();

	}

	private void getAllObservations(Integer patientId) {
		mObservations.clear();

		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		Cursor c = ca.fetchPatientObservations(patientId);

		if (c != null && c.getCount() > 0) {

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

		adapter = new MergeAdapter();

		mObservationAdapter = new ObservationAdapter(this, R.layout.observation_list_item, mObservations);
		mFormView = formView();

		adapter.addView(buildSectionLabel(getString(R.string.clinical_form_section)));
		adapter.addView(mFormView);
		mFormView.setOnTouchListener(mFormListener);
		
		if (!mObservations.isEmpty()) {
			adapter.addView(buildSectionLabel(getString(R.string.clinical_data_section)));
			adapter.addAdapter(mObservationAdapter);
		}

		if (mPatient.getTotalCompletedForms() > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.form_history_section)));
			View v = formHistoryView();
			adapter.addView(v);
			v.setOnTouchListener(mFormHistoryListener);
		}

		ListView lv = getListView();
		lv.setAdapter(adapter);
		lv.setOnTouchListener(mSwipeListener);
	}

	private View buildSectionLabel(String section) {
		View v;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		v.setBackgroundResource(R.color.medium_gray);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		textView.setText(section);
		v.setOnTouchListener(mSwipeListener);
		return (v);
	}

	private View formView() {
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View formSummaryGroup = vi.inflate(R.layout.form_summary_group, null);
		ViewGroup parent = (ViewGroup) formSummaryGroup.findViewById(R.id.vertical_container);
		formSummaryGroup.setClickable(true);

//		formSummaryGroup.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				if (checkForForms()) {
//					Intent i = new Intent(getApplicationContext(), ViewAllForms.class);
//					i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
//					startActivity(i);
//				} else {
//					showCustomToast(getString(R.string.no_forms));
//				}
//			}
//		});

		ImageView formArrow = (ImageView) formSummaryGroup.findViewById(R.id.arrow_image);

		if (formArrow != null) {
			if (mPatient.getPriority()) {
				formArrow.setImageResource(R.drawable.arrow_red);
				View priorityCounter = vi.inflate(R.layout.form_summary, null);
				ImageView priorityImage = (ImageView) priorityCounter.findViewById(R.id.counter_image);
				priorityImage.setImageDrawable(res.getDrawable(R.drawable.priority));
				TextView priorityNumber = (TextView) priorityCounter.findViewById(R.id.counter_number);
				TextView priorityText = (TextView) priorityCounter.findViewById(R.id.counter_text);
				if (priorityNumber != null && priorityText != null) {
					priorityNumber.setText(mPatient.getPriorityNumber().toString());
					priorityText.setText(R.string.to_do_forms);
					priorityText.append(" ");
				}
				parent.addView(priorityCounter);

			} else {
				formArrow.setImageResource(R.drawable.arrow_gray);

			}
		}

		if (mPatient.getSaved()) {
			View savedCounter = vi.inflate(R.layout.form_summary, null);
			ImageView savedImage = (ImageView) savedCounter.findViewById(R.id.counter_image);
			savedImage.setImageDrawable(res.getDrawable(R.drawable.incomplete));
			TextView savedNumber = (TextView) savedCounter.findViewById(R.id.counter_number);
			TextView savedText = (TextView) savedCounter.findViewById(R.id.counter_text);
			if (savedNumber != null && savedText != null) {
				savedNumber.setText(mPatient.getSavedNumber().toString());
				savedText.setText(R.string.incomplete_forms);
				savedText.append(" ");
			}
			parent.addView(savedCounter);
		}

		return (formSummaryGroup);
	}

	private Integer findPreviousEncounters() {
		Integer completedForms = 0;
		String patientIdStr = String.valueOf(mPatient.getPatientId());
		String selection = "(" + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? ) and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_COMPLETE, InstanceProviderAPI.STATUS_SUBMITTED, patientIdStr };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, new String[] { InstanceColumns.PATIENT_ID, "count(*) as count" }, selection, selectionArgs, null);

		if (c.moveToFirst()) {

			// if (patientIdStr ==
			// c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID))) {
			completedForms = c.getInt(c.getColumnIndex("count"));
			// }
		}

		c.close();
		return completedForms;
	}

	private View formHistoryView() {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

//		formsSummary.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				Intent i = new Intent(getApplicationContext(), ViewCompletedForms.class);
//				i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
//				startActivity(i);
//
//			}
//		});

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

			allFormTitle.setText("View All Visits");

			if (priorityNumber != null && priorityImage != null) {
				priorityNumber.setText(mPatient.getTotalCompletedForms().toString());
				priorityImage.setVisibility(View.VISIBLE);
			}
		}

		return (formsSummary);
	}

	
	
	class onFormClick extends SimpleOnGestureListener {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			try {
				if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
					return false;
				if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					finish();
				}
			} catch (Exception e) {
				// nothing
			}
			return false;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			if (checkForForms()) {
				Intent i = new Intent(getApplicationContext(), ViewAllForms.class);
				i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
				startActivity(i);
			} else {
				showCustomToast(getString(R.string.no_forms));
			}
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

	}
	
	class onFormHistoryClick extends SimpleOnGestureListener {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			try {
				if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
					return false;
				if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					finish();
				}
			} catch (Exception e) {
				// nothing
			}
			return false;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			Intent i = new Intent(getApplicationContext(), ViewCompletedForms.class);
			i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
			startActivity(i);
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

	}
	

	class onHeadingClick extends SimpleOnGestureListener {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			try {
				if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
					return false;
				if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					finish();
				}
			} catch (Exception e) {
				// nothing
			}
			return false;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

	}
	
	
	
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	private BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {

			Intent intent = new Intent(mContext, RefreshDataActivity.class);
			intent.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.ASK_TO_DOWNLOAD);
			startActivity(intent);

		}
	};

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
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
