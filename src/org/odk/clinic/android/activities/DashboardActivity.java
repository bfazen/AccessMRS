package org.odk.clinic.android.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.tasks.UploadDataTask;
import org.odk.clinic.android.utilities.FileUtils;

import com.commonsware.cwac.wakeful.AlarmListener;
import com.commonsware.cwac.wakeful.WakefulIntentService;

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
	private static final int MENU_REFRESH = 2;
	
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
	public static final int LIST_SIMILAR_CLIENTS = 5;

	private static final String DOWNLOAD_PATIENT_CANCELED_KEY = "downloadPatientCanceled";

	private ClinicAdapter mCla;

	private ArrayList<String> mForms = new ArrayList<String>();

	private boolean mDownloadPatientCanceled = false;

	private UploadDataTask mUploadFormTask;

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

		//Establish background service for downloading clients
		//We do not reestablish existing alarms (set force to false), 
		// b/c every force reset (force=true) requires alarm to go off, so alarms would go off every time this OnCreate is called
	    WakefulIntentService.scheduleAlarms(new AlarmListener(), mContext, true);
	    Log.e("louis.fazen", "DashboardActivity WakefulIntentService.schedule alarms  is called");
	    
		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey(DOWNLOAD_PATIENT_CANCELED_KEY)) {
				mDownloadPatientCanceled = savedInstanceState.getBoolean(DOWNLOAD_PATIENT_CANCELED_KEY);
			}
		}
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);
//		setTitle(getString(R.string.app_name) + " > " + getString(R.string.find_patient));
		TextView providerNumber = (TextView) findViewById(R.id.provider_number);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
		providerNumber.setText(mProviderId);

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			finish();
		}

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
		long refreshDate = mCla.fetchMostRecentDownload();
		mCla.close();

		// Download Section
		// TextView refreshTitle = (TextView)
		// findViewById(R.id.refresh_subtext_title);
		// refreshTitle.setText("Update:");
		Date date = new Date();
		date.setTime(refreshDate);
		String refreshDateString = new SimpleDateFormat("MMM dd, 'at' HH:mm").format(date) + " ";
		TextView refreshSubtext = (TextView) findViewById(R.id.refresh_subtext);
		refreshSubtext.setText(refreshDateString);
				
		
		//Buttons Section
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		ViewGroup refreshButtonGroup = (ViewGroup) findViewById(R.id.vertical_refresh_container);
		refreshButtonGroup.removeAllViews();
		
		long timeSinceRefresh = System.currentTimeMillis() - refreshDate;
		if(timeSinceRefresh > Constants.MAXIMUM_REFRESH_TIME){
			View downloadButton = vi.inflate(R.layout.dashboard_refresh, null);
			downloadButton.setClickable(true);
			downloadButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					refreshData();
				}
			});	
			refreshButtonGroup.addView(downloadButton);
		}
		
		ViewGroup clientButtonGroup = (ViewGroup) findViewById(R.id.vertical_container);	
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
		
		// All Clients Section
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
		View addNewClientButton = vi.inflate(R.layout.dashboard_forms, null);
		addNewClientButton.setClickable(true);
		addNewClientButton.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				Intent i = new Intent(mContext, CreatePatientActivity.class);
				startActivity(i);
			}
		});
	
		clientButtonGroup.addView(addNewClientButton);

	}

	// BUTTONS....
	private void refreshData(){
		Intent id = new Intent(getApplicationContext(), RefreshDataActivity.class);
		id.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.DIRECT_TO_DOWNLOAD);
		startActivity(id);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_REFRESH, 0, getString(R.string.download_patients)).setIcon(R.drawable.ic_menu_refresh);
		
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		Log.e("Dashboard", "OnCreateOptionsMenu called with IsMenuEnabled= " + settings.getBoolean("IsMenuEnabled", true));
		if (!settings.getBoolean("IsMenuEnabled", true)) {
			return true;
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
		case MENU_REFRESH:
			refreshData();
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
