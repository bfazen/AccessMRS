package com.alphabetbloc.accessmrs.ui.user;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.services.SyncManager;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.EncryptionUtil;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class DashboardActivity extends BaseUserActivity {

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

	private int patients = 0;
	private int priorityToDoForms = 0;
	private int completedForms = 0;
	private int incompleteForms = 0;
	private Context mContext;
	private static String mProviderId;
	private LayoutInflater mLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);

		TextView providerNumber = (TextView) findViewById(R.id.provider_number);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		mProviderId = settings.getString(getString(R.string.key_provider), getString(R.string.default_provider));
		providerNumber.setText(mProviderId);

		if (getIntent().getBooleanExtra(FIRST_RUN, false))
			UiUtils.toastInfo(this, getString(R.string.account_setup_complete), getString((R.string.account_password_reminder), EncryptionUtil.getPassword()));
	}

	// REFRESH UI
	@Override
	protected void refreshView() {
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
		long refreshDate = Db.open().fetchMostRecentDownload();
		Date date = new Date();
		date.setTime(refreshDate);
		String refreshDateString = new SimpleDateFormat("MMM dd, 'at' HH:mm").format(date) + " ";
		TextView refreshSubtext = (TextView) findViewById(R.id.refresh_subtext);
		refreshSubtext.setText(refreshDateString);

		// REFRESH BUTTONS
		ViewGroup refreshButtonGroup = (ViewGroup) findViewById(R.id.vertical_refresh_container);
		refreshButtonGroup.removeAllViews();

		long timeSinceRefresh = System.currentTimeMillis() - refreshDate;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		String maxRefreshSeconds = prefs.getString(App.getApp().getString(R.string.key_max_refresh_seconds), App.getApp().getString(R.string.default_max_refresh_seconds));
		long maxRefreshMs = 1000L * Long.valueOf(maxRefreshSeconds);

		if (timeSinceRefresh > maxRefreshMs) {
			View downloadButton = mLayout.inflate(R.layout.dashboard_refresh, null);
			downloadButton.setClickable(true);
			downloadButton.setOnClickListener(new OnClickListener() {
				public void onClick(View arg0) {
					SyncManager.syncData();
				}
			});
			refreshButtonGroup.addView(downloadButton);
		}
	}

	private void setPriorityListUi(ViewGroup vg) {
		// Suggested / Priority Forms
		priorityToDoForms = Db.open().countAllPriorityFormNumbers();
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
		incompleteForms = Db.open().countAllSavedFormNumbers();
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
		completedForms = Db.open().countAllCompletedUnsentForms();
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
		patients = Db.open().countAllPatients();
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
		
		if (App.DEBUG) Log.v(TAG, "total patients=" + patients);
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

	// LIFECYCLE
	@Override
	protected void onResume() {
		super.onResume();
		refreshView();
	}
}
