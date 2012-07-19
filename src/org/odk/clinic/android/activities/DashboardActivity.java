package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.Constants;
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
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
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

	// Intent Extras
	public static final String LIST_TYPE = "list_type";
	public static final int LIST_ALL = 1;
	public static final int LIST_SUGGESTED = 2;
	public static final int LIST_INCOMPLETE = 3;
	public static final int LIST_COMPLETE = 4;

	private static final String DOWNLOAD_PATIENT_CANCELED_KEY = "downloadPatientCanceled";

	private RelativeLayout mDownloadButton;
	private RelativeLayout mCreateButton;
	private RelativeLayout mViewPatientsButton;
	private RelativeLayout mIncompletePatientsButton;
	private RelativeLayout mCompletePatientsButton;
	private RelativeLayout mSuggestedPatientsButton;

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
					Intent i = new Intent(mContext, CreatePatientActivity.class);
//					Intent i = new Intent(mContext, AllFormList.class);
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
		// } else {

		refreshView();

	}

	private void refreshView() {
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
		String refreshtime = mCla.fetchMostRecentDownload() + " ";
		mCla.close();

		// Download Section
		// TextView refreshTitle = (TextView)
		// findViewById(R.id.refresh_subtext_title);
		// refreshTitle.setText("Update:");
		TextView refreshSubtext = (TextView) findViewById(R.id.refresh_subtext);
		refreshSubtext.setText(refreshtime);
		
		//Buttons Section
		ViewGroup clientButtonGroup = (ViewGroup) findViewById(R.id.vertical_container);
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		clientButtonGroup.removeAllViews();

		// //Priority Form Section
		if (priorityToDoForms > 0) {
			View priorityButton = vi.inflate(R.layout.dashboard_patients, null);
			priorityButton.setClickable(true);
			priorityButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_SUGGESTED);
					startActivity(i);
				}
			});		
			
			RelativeLayout priorityRL = (RelativeLayout)  priorityButton.findViewById(R.id.number_block);
			TextView priorityNumber = (TextView)  priorityButton.findViewById(R.id.number);
			TextView priorityText = (TextView)  priorityButton.findViewById(R.id.text);
			ImageView priorityArrow = (ImageView) priorityButton.findViewById(R.id.arrow_image);
			
			priorityRL.setBackgroundResource(R.drawable.priority);
			priorityNumber.setText(String.valueOf(priorityToDoForms));
			priorityArrow.setBackgroundResource(R.drawable.arrow_red);
			if (priorityToDoForms > 1) {
				priorityText.setText(R.string.to_do_clients);
			} else {
				priorityText.setText(R.string.to_do_client);
			}
			priorityText.append(" ");
//			priorityText.setTextColor(R.color.priority);
		
			clientButtonGroup.addView(priorityButton);
		}

		// Incomplete/Saved Form Section
		if (incompleteForms > 0) {
			View incompleteButton = vi.inflate(R.layout.dashboard_patients, null);
			incompleteButton.setClickable(true);
			incompleteButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_INCOMPLETE);
					startActivity(i);
				}
			});		
			
			RelativeLayout incompleteRL = (RelativeLayout)  incompleteButton.findViewById(R.id.number_block);
			TextView incompleteNumber = (TextView)  incompleteButton.findViewById(R.id.number);
			TextView incompleteText = (TextView)  incompleteButton.findViewById(R.id.text);
			ImageView incompleteArrow = (ImageView) incompleteButton.findViewById(R.id.arrow_image);
			
			incompleteRL.setBackgroundResource(R.drawable.incomplete);
			incompleteNumber.setText(String.valueOf(incompleteForms));
			incompleteArrow.setBackgroundResource(R.drawable.arrow_gray);
			if (incompleteForms > 1) {
				incompleteText.setText(R.string.incomplete_clients);
			} else {
				incompleteText.setText(R.string.incomplete_client);
			}
			incompleteText.append(" ");
		
			clientButtonGroup.addView(incompleteButton);
		}
		
		// Completed Form Section	
		if (completedForms > 0) {
			View completedButton = vi.inflate(R.layout.dashboard_patients, null);
			completedButton.setClickable(true);
			completedButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_COMPLETE);
					startActivity(i);
				}
			});		
			
			RelativeLayout completedRL = (RelativeLayout)  completedButton.findViewById(R.id.number_block);
			TextView completedNumber = (TextView)  completedButton.findViewById(R.id.number);
			TextView completedText = (TextView)  completedButton.findViewById(R.id.text);
			ImageView completedArrow = (ImageView) completedButton.findViewById(R.id.arrow_image);
			
			completedRL.setBackgroundResource(R.drawable.completed);
			completedNumber.setText(String.valueOf(completedForms));
			completedArrow.setBackgroundResource(R.drawable.arrow_gray);
			if (completedForms > 1) {
				completedText.setText(R.string.completed_clients);
			} else {
				completedText.setText(R.string.completed_client);
			}
			completedText.append(" ");
		
			clientButtonGroup.addView(completedButton);
		}
		
		// Patient Section
		if (patients > 0) {
			View patientsButton = vi.inflate(R.layout.dashboard_patients, null);
			patientsButton.setClickable(true);
			patientsButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_ALL);
					startActivity(i);
				}
			});		
			
			RelativeLayout patientsRL = (RelativeLayout)  patientsButton.findViewById(R.id.number_block);
			TextView patientsNumber = (TextView)  patientsButton.findViewById(R.id.number);
			TextView patientsText = (TextView)  patientsButton.findViewById(R.id.text);
			ImageView patientsArrow = (ImageView) patientsButton.findViewById(R.id.arrow_image);
			
			patientsRL.setBackgroundResource(R.drawable.gray);
			patientsNumber.setText(String.valueOf(patients));
			patientsArrow.setBackgroundResource(R.drawable.arrow_gray);
			if (patients > 1) {
				patientsText.setText(R.string.current_clients);
			} else {
				patientsText.setText(R.string.current_client);
			}
			patientsText.append(" ");
		
			clientButtonGroup.addView(patientsButton);
		}
		
		// Form Section
		RelativeLayout formRL = (RelativeLayout) findViewById(R.id.form_number_block);
		TextView formNumber = (TextView) findViewById(R.id.form_number);
		TextView formSubtext = (TextView) findViewById(R.id.form_subtext);

		if (forms > 0) {
			mCreateButton.setVisibility(View.VISIBLE);
			formRL.setVisibility(View.VISIBLE);
			formNumber.setVisibility(View.VISIBLE);
			formSubtext.setVisibility(View.VISIBLE);
			formRL.setBackgroundResource(R.drawable.gray);
			formNumber.setText(String.valueOf(forms));
			if (forms > 1) {
				formSubtext.setText(R.string.downloaded_forms);
				formSubtext.append(" ");
			} else {
				formSubtext.setText(R.string.downloaded_form);
				formSubtext.append(" ");
			}

		} else {
			mCreateButton.setVisibility(View.GONE);
			formRL.setVisibility(View.GONE);
			formNumber.setVisibility(View.GONE);
			formSubtext.setVisibility(View.GONE);
		}

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
		}

		if (c != null)
			c.close();

		mCla.close();

		if (!selectedInstances.isEmpty()) {
			Intent i = new Intent(this, InstanceUploaderActivity.class);
			i.putExtra(ClinicAdapter.KEY_INSTANCES, selectedInstances);
			startActivity(i);
		}

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
