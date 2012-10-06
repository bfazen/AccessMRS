package com.alphabetbloc.clinic.ui.user;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.adapters.PatientAdapter;
import com.alphabetbloc.clinic.data.Patient;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.ui.user.BaseListActivity.myGestureListener;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) (All Methods except where noted
 *         otherwise)
 * 
 * @Author Yaw Anokwa (the getPatients() method)
 */
public class ListPatientActivity extends BaseListActivity implements SyncStatusObserver {

	// Menu ID's	
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;

	public static int mListType;
	private EditText mSearchText;
	private TextWatcher mFilterTextWatcher;
	private ArrayAdapter<Patient> mPatientAdapter;
	private ArrayList<Patient> mPatients = new ArrayList<Patient>();
	private Context mContext;
	private String mSearchPatientStr = null;
	private String mSearchPatientId = null;
	private RelativeLayout mSearchBar;
	private ImageButton mAddClientButton;
	private Button mSimilarClientButton;
	private Button mCancelClientButton;
	private GestureDetector mClientDetector;
	private OnTouchListener mClientListener;
	private ListView mClientListView;
	private int mIndex = 0;
	private int mTop = 0;
	private OnTouchListener mSwipeListener;
	private GestureDetector mSwipeDetector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		setContentView(R.layout.find_patient);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.find_patient));
		// get intents
		mListType = getIntent().getIntExtra(DashboardActivity.LIST_TYPE, 1);
		if (mListType == DashboardActivity.LIST_SIMILAR_CLIENTS) {
			mSearchPatientStr = getIntent().getStringExtra("search_name_string");
			mSearchPatientId = getIntent().getStringExtra("search_id_string");
		}

		mFilterTextWatcher = new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (mPatientAdapter != null) {
					mPatientAdapter.getFilter().filter(s);
				}
			}

			@Override
			public void afterTextChanged(Editable s) {

			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}
		};

		mSearchText = (EditText) findViewById(R.id.search_text);
		mSearchText.addTextChangedListener(mFilterTextWatcher);

		/*
		 * mBarcodeButton = (ImageButton) findViewById(R.id.barcode_button);
		 * mBarcodeButton.setOnClickListener(new OnClickListener() { public void
		 * onClick(View v) { Intent i = new
		 * Intent("com.google.zxing.client.android.SCAN"); try {
		 * startActivityForResult(i, BARCODE_CAPTURE); } catch
		 * (ActivityNotFoundException e) { Toast t =
		 * Toast.makeText(getApplicationContext(), getString(R.string.error,
		 * getString(R.string.barcode_error)), Toast.LENGTH_SHORT);
		 * t.setGravity(Gravity.CENTER_VERTICAL, 0, 0); t.show(); } } });
		 */

		mAddClientButton = (ImageButton) findViewById(R.id.add_client);
		mAddClientButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				Intent i = new Intent(mContext, CreatePatientActivity.class);
				startActivity(i);

			}
		});

		// Verify Similar Clients on Add New Client
		// mSearchBar = (RelativeLayout) findViewById(R.id.searchholder);
		mSearchBar = (RelativeLayout) findViewById(R.id.search_holder);
		mSimilarClientButton = (Button) findViewById(R.id.similar_client_button);
		mSimilarClientButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				setResult(RESULT_OK);
				finish();
			}
		});
		mSimilarClientButton.setVisibility(View.GONE);

		mCancelClientButton = (Button) findViewById(R.id.cancel_client_button);
		mCancelClientButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
		mCancelClientButton.setVisibility(View.GONE);

		mClientDetector = new GestureDetector(new onClientClick());
		mClientListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mClientDetector.onTouchEvent(event);
			}
		};

		mSwipeDetector = new GestureDetector(new myGestureListener());
		mSwipeListener = new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return mSwipeDetector.onTouchEvent(event);
			}
		};
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mSearchText.setText(mSearchText.getText().toString());
		// NB: get immediate view position
		if (mClientListView != null)
			mClientListView.setSelectionFromTop(mIndex, mTop);

		// then refresh the view
		findPatients();
	}
	

	// TODO!: consider changing this whole thing to a viewpager... may be much
	// simpler, and also add animation
	class onClientClick extends SimpleOnGestureListener {

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
			int pos = mClientListView.pointToPosition((int) e.getX(), (int) e.getY());
			if (pos != -1) {
				savePosition();
				Patient p = (Patient) mPatientAdapter.getItem(pos);
				String patientIdStr = p.getPatientId().toString();
				Intent ip = new Intent(getApplicationContext(), ViewPatientActivity.class);
				ip.putExtra(BasePatientActivity.KEY_PATIENT_ID, patientIdStr);
				startActivity(ip);
			}
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

	}

	@Override
	protected void savePosition() {
		mIndex = mClientListView.getFirstVisiblePosition();
		View v = mClientListView.getChildAt(0);
		mTop = (v == null) ? 0 : v.getTop();
	}

	// VIEW:
	private void findPatients() {

		if (mSearchPatientStr == null && mSearchPatientId == null) {
			mPatients.clear();
			getPatients(null, null);
		} else {
			mPatients.clear();
			if (mSearchPatientStr != null) {

				getPatients(mSearchPatientStr, null);
			}
			if (mSearchPatientId != null && mSearchPatientId.length() > 3) {

				getPatients(null, mSearchPatientId);
			}
		}

		refreshView();
	}

	private void getPatients(String searchString, String patientId) {

		DbProvider ca = DbProvider.openDb();

		Cursor c = null;
		if (mSearchPatientStr != null || mSearchPatientId != null) {

			c = ca.fetchPatients(searchString, patientId, mListType);
		} else {
			c = ca.fetchAllPatients(mListType);
		}

		if (c != null && c.getCount() >= 0) {

			int patientIdIndex = c.getColumnIndex(DbProvider.KEY_PATIENT_ID);
			int identifierIndex = c.getColumnIndex(DbProvider.KEY_IDENTIFIER);
			int givenNameIndex = c.getColumnIndex(DbProvider.KEY_GIVEN_NAME);
			int familyNameIndex = c.getColumnIndex(DbProvider.KEY_FAMILY_NAME);
			int middleNameIndex = c.getColumnIndex(DbProvider.KEY_MIDDLE_NAME);
			int birthDateIndex = c.getColumnIndex(DbProvider.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(DbProvider.KEY_GENDER);
			int priorityIndex = c.getColumnIndexOrThrow(DbProvider.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(DbProvider.KEY_PRIORITY_FORM_NAMES);
			int savedIndex = c.getColumnIndexOrThrow(DbProvider.KEY_SAVED_FORM_NUMBER);
			int savedFormIndex = c.getColumnIndexOrThrow(DbProvider.KEY_SAVED_FORM_NAMES);

			if (c.getCount() > 0) {

				Patient p;
				do {
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
					p.setSavedNumber(c.getInt(savedIndex));
					p.setSavedForms(c.getString(savedFormIndex));
					p.setUuid(c.getString(savedFormIndex));

					if (c.getInt(priorityIndex) > 0) {
						p.setPriority(true);
					} else {
						p.setPriority(false);
					}

					if (c.getInt(savedIndex) > 0) {
						p.setSaved(true);
					} else {
						p.setSaved(false);
					}

					mPatients.add(p);

				} while (c.moveToNext());
			}

		}

		if (c != null) {
			c.close();
		}
	}

	private void refreshView() {

		RelativeLayout listLayout = (RelativeLayout) findViewById(R.id.list_type);
		TextView listText = (TextView) findViewById(R.id.name_text);
		ImageView listIcon = (ImageView) findViewById(R.id.section_image);

		switch (mListType) {
		case DashboardActivity.LIST_SUGGESTED:
			listLayout.setBackgroundResource(R.color.priority);
			listIcon.setBackgroundResource(R.drawable.ic_priority);
			listText.setText(R.string.suggested_clients_section);
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listLayout.setBackgroundResource(R.color.saved);
			listIcon.setBackgroundResource(R.drawable.ic_saved);
			listText.setText(R.string.incomplete_clients_section);
			break;
		case DashboardActivity.LIST_COMPLETE:
			listLayout.setBackgroundResource(R.color.completed);
			listIcon.setBackgroundResource(R.drawable.ic_completed);
			listText.setText(R.string.completed_clients_section);
			break;
		case DashboardActivity.LIST_SIMILAR_CLIENTS:
			listLayout.setBackgroundResource(R.color.priority);
			listIcon.setBackgroundResource(R.drawable.ic_priority);
			String similarClient;
			if (mPatients.size() < 2) {
				similarClient = getString(R.string.similar_client_section);
			} else {
				similarClient = getString(R.string.similar_clients_section);
			}
			listText.setText(String.valueOf(mPatients.size()) + " " + similarClient);
			mSearchText.setVisibility(View.GONE);
			mAddClientButton.setVisibility(View.GONE);
			mSearchBar.setVisibility(View.GONE);
			mSimilarClientButton.setVisibility(View.VISIBLE);
			mCancelClientButton.setVisibility(View.VISIBLE);

			break;
		case DashboardActivity.LIST_ALL:
			listLayout.setBackgroundResource(R.color.dark_gray);
			listIcon.setBackgroundResource(R.drawable.ic_additional);
			listText.setText(R.string.all_clients_section);
			break;
		}

		mPatientAdapter = new PatientAdapter(this, R.layout.patient_list_item, mPatients);
		// setListAdapter(mPatientAdapter);

		mClientListView = getListView();
		mClientListView.setAdapter(mPatientAdapter);

		mClientListView.setOnTouchListener(mClientListener);
		listLayout.setOnTouchListener(mSwipeListener);

		// get the same item position as before (but now should have updated
		// numbers)
		if (mIndex > 0 || mTop > 0) {
			mClientListView.setSelectionFromTop(mIndex, mTop);
			mIndex = 0;
			mTop = 0;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mSearchText.removeTextChangedListener(mFilterTextWatcher);

	}

}
