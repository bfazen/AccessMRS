package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.tasks.UploadInstanceTask;
import org.odk.clinic.android.utilities.FileUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DashboardActivity extends Activity implements UploadFormListener {

	// Menu ID's
	private static final int MENU_PREFERENCES = Menu.FIRST;

	// Request codes
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;

	private static final String DOWNLOAD_PATIENT_CANCELED_KEY = "downloadPatientCanceled";

	private RelativeLayout mDownloadButton;
	private RelativeLayout mCreateButton;
	private RelativeLayout mViewPatientsButton;

	private ClinicAdapter mCla;

	private ArrayList<String> mForms = new ArrayList<String>();

	private boolean mDownloadPatientCanceled = false;

	private UploadInstanceTask mUploadFormTask;

	private int patients = 0;
	private int forms = 0;
	private int priorityToDoForms = 0;
	private int completedForms = 0;
	private int incompleteForms = 0;
	private Context mContext;
	private static String mProviderId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;

		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey(DOWNLOAD_PATIENT_CANCELED_KEY)) {
				mDownloadPatientCanceled = savedInstanceState.getBoolean(DOWNLOAD_PATIENT_CANCELED_KEY);
			}
		}
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.find_patient));
		TextView providerNumber = (TextView) findViewById(R.id.provider_number);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
		providerNumber.setText(mProviderId);

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			finish();
		}

		mDownloadButton = (RelativeLayout) findViewById(R.id.refresh);
		mDownloadButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				uploadAllForms();
				updateAllPatients();
				downloadNewForms();

			}
		});

		mCreateButton = (RelativeLayout) findViewById(R.id.load_blank_forms);
		mCreateButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {

				// branch of id of form to fill...
				if (forms > 0) {
					Intent i = new Intent(mContext, AllFormList.class);
					startActivity(i);

				} else {
					showCustomToast(getString(R.string.no_forms));
				}
			}

		});

		mViewPatientsButton = (RelativeLayout) findViewById(R.id.load_patients);
		mViewPatientsButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {

				Intent i = new Intent(mContext, ListPatientActivity.class);
				startActivity(i);

			}

		});

	}

	@Override
	protected void onResume() {
		if (mUploadFormTask != null) {
			mUploadFormTask.setUploadListener(this);
		}
		super.onResume();
		// SharedPreferences settings =
		// PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		// boolean firstRun =
		// settings.getBoolean(PreferencesActivity.KEY_FIRST_RUN, true);
		// if (firstRun) {
		// // Save first run status
		// SharedPreferences.Editor editor = settings.edit();
		// editor.putBoolean(PreferencesActivity.KEY_FIRST_RUN, false);
		// editor.commit();
		//
		// // Start preferences activity
		// Intent ip = new Intent(getApplicationContext(),
		// PreferencesActivity.class);
		// startActivity(ip);
		//
		// } else {

		updateNumbers();
		refreshView();

		// }
	}

	private void updateNumbers() {
		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
			mCla.open();
		}

		patients = mCla.countAllPatients();
		incompleteForms = mCla.countAllSavedFormNumbers();
		completedForms = mCla.countAllCompletedUnsentForms();
		priorityToDoForms = mCla.countAllPriorityFormNumbers();
		forms = mCla.countAllForms();
		String refreshtime = mCla.fetchMostRecentDownload();
		mCla.close();
		

		//Download Section
//		TextView refreshTitle = (TextView) findViewById(R.id.refresh_subtext_title);
//		 refreshTitle.setText("Update:");
		TextView refreshSubtext = (TextView) findViewById(R.id.refresh_subtext);
		 refreshSubtext.setText(refreshtime);
		 
		// Patient Section
		RelativeLayout patientRL = (RelativeLayout) findViewById(R.id.patients_number_block);
		TextView patientNumber = (TextView) findViewById(R.id.patients_number);
		TextView patientSubtext = (TextView) findViewById(R.id.patients_subtext);

		if (patients > 0) {
			patientRL.setBackgroundResource(R.drawable.gray);
			patientNumber.setText(String.valueOf(patients));
			if (patients > 1) {
				patientSubtext.setText(R.string.current_patients);
			} else {
				patientSubtext.setText(R.string.current_patient);
			}

		} else {
			patientRL.setVisibility(View.GONE);
			patientNumber.setVisibility(View.GONE);
			patientSubtext.setVisibility(View.GONE);
		}

		// Form Section
		RelativeLayout formRL = (RelativeLayout) findViewById(R.id.form_number_block);
		TextView formNumber = (TextView) findViewById(R.id.form_number);
		TextView formSubtext = (TextView) findViewById(R.id.form_subtext);

		if (forms > 0) {

			formRL.setBackgroundResource(R.drawable.gray);
			formNumber.setText(String.valueOf(forms));
			if (forms > 1) {
				formSubtext.setText(R.string.downloaded_forms);
			} else {
				formSubtext.setText(R.string.downloaded_form);
			}

		} else {
			formRL.setVisibility(View.GONE);
			formNumber.setVisibility(View.GONE);
			formSubtext.setVisibility(View.GONE);
		}

		// //Priority Form Section
		RelativeLayout todoRL = (RelativeLayout) findViewById(R.id.todo_block);
		TextView todoNumber = (TextView) findViewById(R.id.todo_number);
		TextView todoText = (TextView) findViewById(R.id.todo_forms);

		if (priorityToDoForms > 0) {

			int bottom = todoRL.getPaddingBottom();
			int top = todoRL.getPaddingTop();
			int right = todoRL.getPaddingRight();
			int left = todoRL.getPaddingLeft();
			Log.e("louis.fazen", "padding is:" + top + bottom + left + right);
			todoRL.setBackgroundResource(R.drawable.priority);

			todoNumber.setText(String.valueOf(priorityToDoForms));
			if (priorityToDoForms > 1) {
				todoText.setText(R.string.to_do_forms);
			} else {
				todoText.setText(R.string.to_do_form);
			}

		} else {
			todoRL.setVisibility(View.GONE);
			todoNumber.setVisibility(View.GONE);
			todoText.setVisibility(View.GONE);
		}

		// Incomplete/Saved Form Section
		RelativeLayout incompletedRL = (RelativeLayout) findViewById(R.id.saved_block);
		TextView incompletedNumber = (TextView) findViewById(R.id.saved_number);
		TextView incompletedText = (TextView) findViewById(R.id.saved_forms);

		if (incompleteForms > 0) {
			Log.e("louis.fazen", "incompleteForms is not null with count " + incompleteForms);
			incompletedRL.setBackgroundResource(R.drawable.incomplete);
			incompletedNumber.setText(String.valueOf(incompleteForms));
			if (incompleteForms > 1) {
				incompletedText.setText(R.string.incomplete_forms);
			} else {
				incompletedText.setText(R.string.incomplete_form);
			}

		} else {
			incompletedRL.setVisibility(View.GONE);
			incompletedNumber.setVisibility(View.GONE);
			incompletedText.setVisibility(View.GONE);
		}
		
		// Completed Form Section
				RelativeLayout completedRL = (RelativeLayout) findViewById(R.id.completed_block);
				TextView completedNumber = (TextView) findViewById(R.id.completed_number);
				TextView completedText = (TextView) findViewById(R.id.completed_forms);

				if (completedForms > 0) {
					Log.e("louis.fazen", "completedForms is not null with count " + completedForms);
					completedRL.setBackgroundResource(R.drawable.completed);
					completedNumber.setText(String.valueOf(completedForms));
					if (completedForms > 1) {
						completedText.setText(R.string.completed_forms);
					} else {
						completedText.setText(R.string.completed_form);
					}

				} else {
					Log.e("louis.fazen", "completedForms is now invisible");
					completedRL.setVisibility(View.GONE);
					completedNumber.setVisibility(View.GONE);
					completedText.setVisibility(View.GONE);
				}

	}

	private void refreshView() {

	}

	// BUTTONS....
	private void downloadNewForms() {
		Intent id = new Intent(getApplicationContext(), DownloadFormActivity.class);
		startActivity(id);
	}

	private void updateAllPatients() {
		Intent id = new Intent(getApplicationContext(), DownloadPatientActivity.class);
		startActivity(id);
	}

	private void uploadAllForms() {

		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
		}
		Cursor c = mCla.fetchFormInstancesByStatus(ClinicAdapter.STATUS_UNSUBMITTED);
		startManagingCursor(c);
		ArrayList<String> selectedInstances = new ArrayList<String>();

		if (c != null && c.getCount() > 0) {
			String s = c.getString(c.getColumnIndex(ClinicAdapter.KEY_PATH));
			selectedInstances.add(s);

			// bundle intent with upload files
			Intent i = new Intent(this, InstanceUploaderActivity.class);
			// i.putExtra(FormEntryActivity.KEY_INSTANCES, selectedInstances);
			i.putExtra(ClinicAdapter.KEY_INSTANCES, selectedInstances);
			startActivity(i);
		}

		if (c != null)
			c.close();

		mCla.close();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		Log.e("Dashboard", "OnCreateOptionsMenu called with IsMenuEnabled= " + settings.getBoolean("IsMenuEnabled", true));
		if (settings.getBoolean("IsMenuEnabled", true) == false) {
			return false;
		} else {
			menu.add(0, MENU_PREFERENCES, 0, getString(R.string.server_preferences)).setIcon(android.R.drawable.ic_menu_preferences);
			return true;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_PREFERENCES:
			Intent ip = new Intent(getApplicationContext(), PreferencesActivity.class);
			startActivity(ip);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// LIFECYCLE
	@Override
	protected void onDestroy() {
		if (mUploadFormTask != null) {
			mUploadFormTask.setUploadListener(null);
			if (mUploadFormTask.getStatus() == AsyncTask.Status.FINISHED) {
				mUploadFormTask.cancel(true);
			}
		}
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putBoolean(DOWNLOAD_PATIENT_CANCELED_KEY, mDownloadPatientCanceled);
	}

	@Override
	public void uploadComplete(ArrayList<String> result) {
		// Auto-generated method stub

	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// Auto-generated method stub

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
