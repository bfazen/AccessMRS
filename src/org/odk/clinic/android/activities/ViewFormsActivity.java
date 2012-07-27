package org.odk.clinic.android.activities;

import java.util.ArrayList;
import java.util.Arrays;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.FormAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.Patient;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Displays ArrayList<Form> in a Time-Separated List with patient header
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewFormsActivity extends ListActivity {

	private static final int SWIPE_MIN_DISTANCE = 120;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	protected MergeAdapter mMergeAdapter;
	protected ListView mListView;

	protected GestureDetector mFormDetector;
	protected OnTouchListener mFormListener;

	protected GestureDetector mSwipeDetector;
	protected OnTouchListener mSwipeListener;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	
	}

	protected class formGestureDetector extends SimpleOnGestureListener {
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
			return true;
		}

	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	protected BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {

			Intent intent = new Intent(getApplicationContext(), RefreshDataActivity.class);
			intent.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.ASK_TO_DOWNLOAD);
			startActivity(intent);

		}
	};

	protected void createPatientHeader(Integer patientId) {
		mSwipeDetector = new GestureDetector(new formGestureDetector());
		mSwipeListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mSwipeDetector.onTouchEvent(event);
			}
		};
		
		setContentView(R.layout.example_cw_main);

		View v = (View) findViewById(R.id.client_header);
		if (v != null){
			v.setOnTouchListener(mSwipeListener);
		} 
		
		Patient focusPt = getPatient(patientId);
		
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

	protected void createFormHistoryList(ArrayList<Form> formInstances) {
		mMergeAdapter = new MergeAdapter();

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
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_day)));
				mMergeAdapter.addAdapter(buildInstancesList(day));
			}
			if (!weekGroup.isEmpty()) {
				Form[] week = weekGroup.toArray(new Form[weekGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_week)));
				mMergeAdapter.addAdapter(buildInstancesList(week));
			}
			if (!monthGroup.isEmpty()) {
				Form[] month = monthGroup.toArray(new Form[monthGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_month)));
				mMergeAdapter.addAdapter(buildInstancesList(month));
			}
			if (!sixMonthGroup.isEmpty()) {
				Form[] sixmonth = monthGroup.toArray(new Form[monthGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_six_months)));
				mMergeAdapter.addAdapter(buildInstancesList(sixmonth));
			}
			if (!oneYearGroup.isEmpty()) {
				Form[] year = oneYearGroup.toArray(new Form[oneYearGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_last_year)));
				mMergeAdapter.addAdapter(buildInstancesList(year));
			}
			if (!allOtherGroup.isEmpty()) {
				Form[] allother = allOtherGroup.toArray(new Form[allOtherGroup.size()]);
				mMergeAdapter.addView(buildSectionLabel(getString(R.string.instances_unknown_gt_year)));
				mMergeAdapter.addAdapter(buildInstancesList(allother));
			}

		}

		mListView = getListView();
		mListView.setAdapter(mMergeAdapter);
		mListView.setOnTouchListener(mFormListener);
	}

	protected View buildSectionLabel(String timeperiod) {
		View v;
		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);
		sectionImage.setVisibility(View.GONE);
		v.setBackgroundResource(R.color.dark_gray);
		v.setOnTouchListener(mSwipeListener);
		textView.setText(timeperiod);
		return (v);
	}

	protected FormAdapter buildInstancesList(Form[] forms) {
		ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(forms));
		FormAdapter formAdapter = new FormAdapter(this, R.layout.saved_instances, formList, false);
		return (formAdapter);
	}

	protected ArrayList<Integer> getPriorityForms(Integer patientId) {
		ArrayList<Integer> selectedFormIds = new ArrayList<Integer>();
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPriorityFormIdByPatientId(patientId);

		if (c != null && c.getCount() > 0) {
			int valueIntIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_INT);
			do {
				selectedFormIds.add(c.getInt(valueIntIndex));
			} while (c.moveToNext());
		}

		if (c != null) {
			c.close();
		}
		ca.close();

		return selectedFormIds;
	}

	// TODO: better resource management if Patient were Parceable object?
	protected Patient getPatient(Integer patientId) {

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

	/**
	 * For Consistency, using Collect's same UI math for onFling
	 * 
	 */


	// if ((Math.abs(e1.getX() - e2.getX()) > xPixelLimit && Math.abs(e1.getY()
	// - e2.getY()) < yPixelLimit) || Math.abs(e1.getX() - e2.getX()) >
	// xPixelLimit * 2) {
	// if (velocityX > 0) {



}