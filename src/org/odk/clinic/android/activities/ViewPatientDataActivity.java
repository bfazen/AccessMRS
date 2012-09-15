package org.odk.clinic.android.activities;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.utilities.FileUtils;

import com.alphabetbloc.clinic.services.RefreshDataService;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays ArrayList<Form> in a Time-Separated List with patient header
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewPatientDataActivity extends ListActivity {

	private static final int SWIPE_MIN_DISTANCE = 120;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	protected OnTouchListener mSwipeListener;
	protected GestureDetector mSwipeDetector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			finish();
		}

		mSwipeDetector = new GestureDetector(new myGestureListener());
		mSwipeListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mSwipeDetector.onTouchEvent(event);
			}
		};

	}
		
	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);

	}
	
	protected void createPatientHeader(Integer patientId) {
		
		Patient focusPt = getPatient(patientId);

		View v = (View) findViewById(R.id.client_header);
		if (v != null) {
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

	protected View buildSectionLabel(String section, boolean icon) {
		View v;
		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		v.setBackgroundResource(R.color.medium_gray);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);
		if (icon)
			sectionImage.setVisibility(View.VISIBLE);
		else
			sectionImage.setVisibility(View.GONE);
		textView.setText(section);
		v.setOnTouchListener(mSwipeListener);
		return (v);
	}

	// TODO better resource management if Patient were Parceable object?
	protected Patient getPatient(Integer patientId) {

		Patient p = null;

		Cursor c = DbAdapter.openDb().fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {
			int patientIdIndex = c.getColumnIndex(DbAdapter.KEY_PATIENT_ID);
			int identifierIndex = c.getColumnIndex(DbAdapter.KEY_IDENTIFIER);
			int givenNameIndex = c.getColumnIndex(DbAdapter.KEY_GIVEN_NAME);
			int familyNameIndex = c.getColumnIndex(DbAdapter.KEY_FAMILY_NAME);
			int middleNameIndex = c.getColumnIndex(DbAdapter.KEY_MIDDLE_NAME);
			int birthDateIndex = c.getColumnIndex(DbAdapter.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(DbAdapter.KEY_GENDER);
			int priorityIndex = c.getColumnIndexOrThrow(DbAdapter.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(DbAdapter.KEY_PRIORITY_FORM_NAMES);

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

		return p;
	}

	protected class myGestureListener extends SimpleOnGestureListener {

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

	protected void showCustomToast(String message) {
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