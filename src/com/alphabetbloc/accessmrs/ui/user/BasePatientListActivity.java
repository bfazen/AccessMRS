package com.alphabetbloc.accessmrs.ui.user;

import android.content.Context;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.R;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.providers.Db;

/**
 * Displays ArrayList<Form> in a Time-Separated List with patient header
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) 
 * 
 */

public abstract class BasePatientListActivity extends BaseUserListActivity implements SyncStatusObserver {

	// intent extras
	public static final String KEY_PATIENT_ID = "PATIENT_ID";
	public static final String KEY_OBSERVATION_FIELD_NAME = "KEY_OBSERVATION_FIELD_NAME";
	private OnTouchListener mSwipeListener;
	private GestureDetector mSwipeDetector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSwipeDetector = new GestureDetector(new myGestureListener());
		mSwipeListener = new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return mSwipeDetector.onTouchEvent(event);
			}
		};
	}

	protected void createPatientHeader(Integer patientId) {

		Patient focusPt = Db.open().getPatient(patientId);

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

	// BUTTONS: Dont show the menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	// LIFECYCLE
	@Override
	protected void onPause() {
		super.onPause();
	}
}