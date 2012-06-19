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
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
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

	private Button mDownloadButton;
	private Button mCreateButton;

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
		setContentView(R.layout.find_patient);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.find_patient));

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			finish();
		}

		mDownloadButton = (Button) findViewById(R.id.download_patients);
		mDownloadButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				uploadAllForms();
				updateAllPatients();
				downloadNewForms();

			}
		});

		mCreateButton = (Button) findViewById(R.id.create_patient);
		mCreateButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {

				// branch of id of form to fill...
				if (mForms.size() > 0) {
					Intent i = new Intent(mContext, AllFormList.class);
					startActivity(i);
					
				} else {
					showCustomToast(getString(R.string.no_forms));
				}
			}

		});
	}

	@Override
	protected void onResume() {
		if (mUploadFormTask != null) {
			mUploadFormTask.setUploadListener(this);
		}
		super.onResume();
//		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
//		boolean firstRun = settings.getBoolean(PreferencesActivity.KEY_FIRST_RUN, true);
//		if (firstRun) {
//			// Save first run status
//			SharedPreferences.Editor editor = settings.edit();
//			editor.putBoolean(PreferencesActivity.KEY_FIRST_RUN, false);
//			editor.commit();
//
//			// Start preferences activity
//			Intent ip = new Intent(getApplicationContext(), PreferencesActivity.class);
//			startActivity(ip);
//
//		} else {
		
			updateNumbers();
			refreshView();

//		}
	}

	private void updateNumbers() {
		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
		}
		patients = mCla.countAllPatients();
		forms = mCla.countAllForms();
		completedForms = mCla.countAllCompletedUnsentForms();
		priorityToDoForms = mCla.countAllPriorityFormNumbers();
		incompleteForms = mCla.countAllSavedFormNumbers();

		mCla.close();
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
