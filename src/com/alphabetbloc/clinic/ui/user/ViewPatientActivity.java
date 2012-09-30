package com.alphabetbloc.clinic.ui.user;

import java.util.ArrayList;

import com.alphabetbloc.clinic.R;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.ObservationAdapter;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alphabetbloc.clinic.adapters.MergeAdapter;
import com.alphabetbloc.clinic.data.DbAdapter;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.utilities.App;

/**
 * 
 * Louis Fazen (louis.fazen@gmail.com) has simplified this ListActivity and
 * moved most of the code to ViewAllForms
 * 
 * @author Louis Fazen
 * @author Yaw Anokwa
 * 
 */
public class ViewPatientActivity extends ViewDataActivity {

	private static Patient mPatient;
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();
	private String patientIdStr;
	private Context mContext;
	private Resources res;
	private OnTouchListener mFormListener;
	private OnTouchListener mFormHistoryListener;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.view_patient);
		mContext = this;
		res = this.getResources();

		patientIdStr = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);
		if (mPatient == null) {
			showCustomToast(getString(R.string.error, R.string.no_patient));
			finish();
		}

		final GestureDetector mFormDetector = new GestureDetector(new onFormClick());
		mFormListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormDetector.onTouchEvent(event);
			}
		};

		final GestureDetector mFormHistoryDetector = new GestureDetector(new onFormHistoryClick());
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
			mPatient.setTotalCompletedForms(findPreviousEncounters());
			createPatientHeader(mPatient.getPatientId());
			getAllObservations(mPatient.getPatientId());
			getPatientForms(mPatient.getPatientId());
			refreshView();

		}
	}

	private View formHistoryView() {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

		// formsSummary.setOnClickListener(new View.OnClickListener() {
		// public void onClick(View v) {
		// Intent i = new Intent(getApplicationContext(),
		// ViewCompletedForms.class);
		// i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
		// startActivity(i);
		//
		// }
		// });
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

	private boolean checkForForms() {
		boolean checkForms = false;

		Cursor c = DbAdapter.openDb().fetchAllForms();
		if (c != null && c.getCount() >= 0) {
			checkForms = true;
		}
		if (c != null)
			c.close();
		return checkForms;
	}

	private void getPatientForms(Integer patientId) {

		Cursor c = DbAdapter.openDb().fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {

			int priorityIndex = c.getColumnIndexOrThrow(DbAdapter.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(DbAdapter.KEY_PRIORITY_FORM_NAMES);
			int savedNumberIndex = c.getColumnIndexOrThrow(DbAdapter.KEY_SAVED_FORM_NUMBER);
			int savedFormIndex = c.getColumnIndexOrThrow(DbAdapter.KEY_SAVED_FORM_NAMES);

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

	}

	private void getAllObservations(Integer patientId) {
		mObservations = DbAdapter.openDb().fetchPatientObservationList(patientId);
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

		View mFormView;
		MergeAdapter adapter = new MergeAdapter();

		ArrayAdapter<Observation> obsAdapter = new ObservationAdapter(this, R.layout.observation_list_item, mObservations);
		mFormView = formView();

		adapter.addView(buildSectionLabel(getString(R.string.clinical_form_section), true));
		adapter.addView(mFormView);
		mFormView.setOnTouchListener(mFormListener);

		if (!mObservations.isEmpty()) {
			adapter.addView(buildSectionLabel(getString(R.string.clinical_data_section), true));
			adapter.addAdapter(obsAdapter);
		}

		if (mPatient.getTotalCompletedForms() > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.form_history_section), true));
			View v = formHistoryView();
			adapter.addView(v);
			v.setOnTouchListener(mFormHistoryListener);
		}

		ListView lv = getListView();
		lv.setAdapter(adapter);
		lv.setOnTouchListener(mSwipeListener);
	}

	private View formView() {
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View formSummaryGroup = vi.inflate(R.layout.form_summary_group, null);
		ViewGroup parent = (ViewGroup) formSummaryGroup.findViewById(R.id.vertical_container);
		formSummaryGroup.setClickable(true);

		// formSummaryGroup.setOnClickListener(new View.OnClickListener() {
		// public void onClick(View v) {
		// if (checkForForms()) {
		// Intent i = new Intent(getApplicationContext(), ViewAllForms.class);
		// i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
		// startActivity(i);
		// } else {
		// showCustomToast(getString(R.string.no_forms));
		// }
		// }
		// });

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
		String selection = "(" + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? ) and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_ENCRYPTED, InstanceProviderAPI.STATUS_COMPLETE, InstanceProviderAPI.STATUS_SUBMITTED, patientIdStr };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, new String[] { InstanceColumns.PATIENT_ID, "count(*) as count" }, selection, selectionArgs, null);

		if (c != null) {
			if (c.moveToFirst()) {
				// if (patientIdStr ==
				// c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID))) {
				completedForms = c.getInt(c.getColumnIndex("count"));
				// }
			}
			c.close();
		}
		return completedForms;
	}

	protected class onFormClick extends myGestureListener {

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
	}

	protected class onFormHistoryClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			Intent i = new Intent(getApplicationContext(), ViewCompletedForms.class);
			i.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
			startActivity(i);
			return false;
		}

	}

}
