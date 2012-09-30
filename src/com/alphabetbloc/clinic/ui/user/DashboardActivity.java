package com.alphabetbloc.clinic.ui.user;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.odk.clinic.android.openmrs.Constants;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.DbAdapter;
import com.alphabetbloc.clinic.listeners.RefreshDataListener;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.services.WakefulIntentService;
import com.alphabetbloc.clinic.ui.admin.PreferencesActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.FileUtils;

public class DashboardActivity extends Activity implements SyncStatusObserver {

	// Menu ID's
	private static final int MENU_PREFERENCES = Menu.FIRST;
	private static final int MENU_REFRESH = 2;

	// Request codes
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;

	// Intent Extras
	public static final String TAG = DashboardActivity.class.getSimpleName();
	public static final String LIST_TYPE = "list_type";
	public static final int LIST_ALL = 1;
	public static final int LIST_SUGGESTED = 2;
	public static final int LIST_INCOMPLETE = 3;
	public static final int LIST_COMPLETE = 4;
	public static final int LIST_SIMILAR_CLIENTS = 5;
	public static final String FIRST_RUN = "first_run";
	public static final int PROGRESS_DIALOG = 1;

	private int patients = 0;
	private int priorityToDoForms = 0;
	private int completedForms = 0;
	private int incompleteForms = 0;
	private Context mContext;
	private static String mProviderId;
	private static Object mSyncObserverHandle;
	private LayoutInflater mLayout;
	private static ProgressDialog mProgressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;

		
		// Establish background service for downloading clients TODO! VERIFY
//		WakefulIntentService.scheduleAlarms(new RefreshDataListener(), WakefulIntentService.REFRESH_DATA, mContext, true);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);

		TextView providerNumber = (TextView) findViewById(R.id.provider_number);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(getString(R.string.key_provider), "0");
		providerNumber.setText(mProviderId);

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			finish();
		}

		if (getIntent().getBooleanExtra(FIRST_RUN, false))
			showCustomToast("Device setup Complete! Please Remember Your Password!" + "\n\n  PASSWORD= " + EncryptionUtil.getPassword());
	}

	// REFRESH UI
	private void refreshView() {
		mLayout = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		// Refresh Data UI
		setRefreshDataUi();

		// Client Lists UI
		ViewGroup buttonList = (ViewGroup) findViewById(R.id.vertical_container);
		buttonList.removeAllViews();
		setPriorityListUi(buttonList);
		setSavedListUi(buttonList);
		setCompletedListUi(buttonList);
		setAllClientsListUi(buttonList);

		// Add Client UI
		setAddClientUi(buttonList);

	}

	private void setRefreshDataUi() {
		// REFRESH TIME
		long refreshDate = DbAdapter.openDb().fetchMostRecentDownload();
		Date date = new Date();
		date.setTime(refreshDate);
		String refreshDateString = new SimpleDateFormat("MMM dd, 'at' HH:mm").format(date) + " ";
		TextView refreshSubtext = (TextView) findViewById(R.id.refresh_subtext);
		refreshSubtext.setText(refreshDateString);

		// REFRESH BUTTONS
		ViewGroup refreshButtonGroup = (ViewGroup) findViewById(R.id.vertical_refresh_container);
		refreshButtonGroup.removeAllViews();

		long timeSinceRefresh = System.currentTimeMillis() - refreshDate;
		if (timeSinceRefresh > Constants.MAXIMUM_REFRESH_TIME) {
			View downloadButton = mLayout.inflate(R.layout.dashboard_refresh, null);
			downloadButton.setClickable(true);
			downloadButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					refreshData();
				}
			});
			refreshButtonGroup.addView(downloadButton);
		}
	}

	private void setPriorityListUi(ViewGroup vg) {
		// Suggested / Priority Forms
		priorityToDoForms = DbAdapter.openDb().countAllPriorityFormNumbers();
		if (priorityToDoForms > 0) {
			View priorityButton = mLayout.inflate(R.layout.dashboard_patients, null);
			priorityButton.setClickable(true);
			priorityButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_SUGGESTED);
					startActivity(i);
				}
			});

			RelativeLayout priorityRL = (RelativeLayout) priorityButton.findViewById(R.id.number_block);
			TextView priorityNumber = (TextView) priorityButton.findViewById(R.id.number);
			TextView priorityText = (TextView) priorityButton.findViewById(R.id.text);
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
			// priorityText.setTextColor(R.color.priority);

			vg.addView(priorityButton);
		}
	}

	private void setSavedListUi(ViewGroup vg) {
		// Incomplete/Saved Form Section
		incompleteForms = DbAdapter.openDb().countAllSavedFormNumbers();
		if (incompleteForms > 0) {
			View incompleteButton = mLayout.inflate(R.layout.dashboard_patients, null);
			incompleteButton.setClickable(true);
			incompleteButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_INCOMPLETE);
					startActivity(i);
				}
			});

			RelativeLayout incompleteRL = (RelativeLayout) incompleteButton.findViewById(R.id.number_block);
			TextView incompleteNumber = (TextView) incompleteButton.findViewById(R.id.number);
			TextView incompleteText = (TextView) incompleteButton.findViewById(R.id.text);
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

			vg.addView(incompleteButton);
		}
	}

	private void setCompletedListUi(ViewGroup vg) {
		// Completed Form Section
		completedForms = DbAdapter.openDb().countAllCompletedUnsentForms();
		if (completedForms > 0) {
			View completedButton = mLayout.inflate(R.layout.dashboard_patients, null);
			completedButton.setClickable(true);
			completedButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_COMPLETE);
					startActivity(i);
				}
			});

			RelativeLayout completedRL = (RelativeLayout) completedButton.findViewById(R.id.number_block);
			TextView completedNumber = (TextView) completedButton.findViewById(R.id.number);
			TextView completedText = (TextView) completedButton.findViewById(R.id.text);
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

			vg.addView(completedButton);
		}
	}

	private void setAllClientsListUi(ViewGroup vg) {
		// All Clients Section
		patients = DbAdapter.openDb().countAllPatients();
		if (patients > 0) {
			View patientsButton = mLayout.inflate(R.layout.dashboard_patients, null);
			patientsButton.setClickable(true);
			patientsButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(LIST_TYPE, LIST_ALL);
					startActivity(i);
				}
			});

			RelativeLayout patientsRL = (RelativeLayout) patientsButton.findViewById(R.id.number_block);
			TextView patientsNumber = (TextView) patientsButton.findViewById(R.id.number);
			TextView patientsText = (TextView) patientsButton.findViewById(R.id.text);
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

			vg.addView(patientsButton);
		}
	}

	private void setAddClientUi(ViewGroup vg) {
		// Add New Client Section
		View addNewClientButton = mLayout.inflate(R.layout.dashboard_forms, null);
		addNewClientButton.setClickable(true);
		addNewClientButton.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				Intent i = new Intent(mContext, CreatePatientActivity.class);
				startActivity(i);
			}
		});

		vg.addView(addNewClientButton);
	}

	private void refreshData() {
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
			menu.add(0, MENU_PREFERENCES, 0, getString(R.string.pref_settings)).setIcon(android.R.drawable.ic_menu_preferences);
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

	@Override
	public void onStatusChanged(int which) {

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				checkSyncActivity();
			}
		});

	}

	public boolean checkSyncActivity() {
		boolean syncActive = false;
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));

		if (accounts.length <= 0)
			return false;

		syncActive = ContentResolver.isSyncActive(accounts[0], getString(R.string.app_provider_authority));

		if (syncActive) {

			showDialog(PROGRESS_DIALOG);

		} else {

			if (mProgressDialog != null) {
				mProgressDialog.dismiss();
			}
		}

		return syncActive;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
		mProgressDialog.setTitle(getString(R.string.sync_in_progress_title));
		mProgressDialog.setMessage(getString(R.string.sync_in_progress));
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		return mProgressDialog;
	}

	// LIFECYCLE
	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);
		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			mProgressDialog.show();
		}
		refreshView();
	}

	private BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {

			Intent intent = new Intent(mContext, RefreshDataActivity.class);
			intent.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.ASK_TO_DOWNLOAD);
			startActivity(intent);

		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}
}
