package com.alphabetbloc.accessmrs.ui.user;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.os.AsyncTask;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.adapters.PatientAdapter;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) (All Methods except where noted
 *         otherwise)
 * 
 */
public class ListPatientActivity extends BaseUserListActivity implements SyncStatusObserver {

	// Menu ID's
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;

	public static int mListType;
	private EditText mSearchText;
	private TextWatcher mFilterTextWatcher;
	public ArrayAdapter<Patient> mPatientAdapter;
	private Context mContext;
	private RelativeLayout mSearchBar;
	private ImageButton mAddClientButton;
	private Button mSimilarClientButton;
	private Button mCancelClientButton;
	private GestureDetector mClientDetector;
	private OnTouchListener mClientListener;
	private ListView mClientListView;
	private RelativeLayout mLoadingWheel;
	private LinearLayout mPatientList;
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

		mPatientList = (LinearLayout) findViewById(R.id.patient_holder);
		mLoadingWheel = (RelativeLayout) findViewById(R.id.patient_loader);
		mPatientList.setVisibility(View.GONE);
		mLoadingWheel.setVisibility(View.VISIBLE);

		ArrayList<Patient> patients = new ArrayList<Patient>();
		mPatientAdapter = new PatientAdapter(mContext, R.layout.patient_list_item, patients);
		mPatientAdapter.setNotifyOnChange(true);
		mClientListView = getListView();
		mClientListView.setAdapter(mPatientAdapter);
		mClientListView.setOnTouchListener(mClientListener);

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
			listText.setText(R.string.all_clients_section);
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

		listLayout.setOnTouchListener(mSwipeListener);
	}

	@Override
	protected void onResume() {
		super.onResume();

		mSearchText.setText(mSearchText.getText().toString());
		// NB: get immediate view position
		if (mClientListView != null)
			mClientListView.setSelectionFromTop(mIndex, mTop);

		refreshView();
	}

	//TODO! Change this to a SimpleCursorAdapter?
	protected void refreshView() {

		new AsyncTask<Void, Void, ArrayList<Patient>>() {

			@Override
			protected ArrayList<Patient> doInBackground(Void... params) {

				ArrayList<Patient> patients = new ArrayList<Patient>();
				String mSearchPatientStr = null;
				String mSearchPatientId = null;
				if (mListType == DashboardActivity.LIST_SIMILAR_CLIENTS) {
					mSearchPatientStr = getIntent().getStringExtra("search_name_string");
					mSearchPatientId = getIntent().getStringExtra("search_id_string");
					if (mSearchPatientId != null && mSearchPatientId.length() < 4)
						mSearchPatientId = null;
				}

				Cursor c = null;
				if (mSearchPatientStr != null || mSearchPatientId != null)
					c = Db.open().searchPatients(mSearchPatientStr, mSearchPatientId, mListType);
				else
					c = Db.open().fetchAllPatients(mListType);

				if (c != null) {

					if (c.moveToFirst()) {
						int patientIdIndex = c.getColumnIndex(DataModel.KEY_PATIENT_ID);
						int identifierIndex = c.getColumnIndex(DataModel.KEY_IDENTIFIER);
						int givenNameIndex = c.getColumnIndex(DataModel.KEY_GIVEN_NAME);
						int familyNameIndex = c.getColumnIndex(DataModel.KEY_FAMILY_NAME);
						int middleNameIndex = c.getColumnIndex(DataModel.KEY_MIDDLE_NAME);
						int birthDateIndex = c.getColumnIndex(DataModel.KEY_BIRTH_DATE);
						int genderIndex = c.getColumnIndex(DataModel.KEY_GENDER);
						int priorityIndex = c.getColumnIndexOrThrow(DataModel.KEY_PRIORITY_FORM_NUMBER);
						int savedIndex = c.getColumnIndexOrThrow(DataModel.KEY_SAVED_FORM_NUMBER);
						int uuidIndex = c.getColumnIndexOrThrow(DataModel.KEY_UUID);
						
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
								p.setSavedNumber(c.getInt(savedIndex));
								p.setUuid(c.getString(uuidIndex));

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

								patients.add(p);

							} while (c.moveToNext());
						}
					}

					c.close();
				}

				return patients;
			}

			@Override
			protected void onPostExecute(ArrayList<Patient> patients) {
				mPatientAdapter.clear();
				for (Patient p : patients) {
					mPatientAdapter.add(p);
				}
				mPatientAdapter.getFilter().filter(mSearchText.getText().toString());
				mPatientAdapter.notifyDataSetChanged();

				mPatientList.setVisibility(View.VISIBLE);
				mLoadingWheel.setVisibility(View.GONE);
				if (mListType == DashboardActivity.LIST_SIMILAR_CLIENTS)
					((TextView) findViewById(R.id.name_text)).setText(mContext.getResources().getQuantityString(R.plurals.errors, patients.size(), patients.size()));

			}
		}.execute();

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mSearchText.removeTextChangedListener(mFilterTextWatcher);

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

}
