package org.odk.clinic.android.activities;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.DownloadListener;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.tasks.DownloadDataTask;
import org.odk.clinic.android.tasks.DownloadTask;
import org.odk.clinic.android.tasks.UploadDataTask;
import org.odk.clinic.android.utilities.FileUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.services.RefreshDataService;
import com.commonsware.cwac.wakeful.AlarmListener;
import com.commonsware.cwac.wakeful.WakefulIntentService;

public class RefreshDataActivity extends Activity implements UploadFormListener, DownloadListener {

	public final static int ASK_TO_DOWNLOAD = 1;
	public final static int DIRECT_TO_DOWNLOAD = 2;
	public final static String DIALOG = "showdialog";
	private Context mContext;
	private ProgressDialog mProgressDialog;
	private AlertDialog mAlertDialog;
	private DownloadTask mDownloadTask;
	private UploadDataTask mUploadTask;
	private String username;
	private String password;
	private String savedSearch;
	private String cohortId;
	private String programId;
	private String patientDownloadUrl;
	private String formDownloadUrl;
	private String formlistDownloadUrl;
	private String formUploadUrl;
	private int showProgress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.download_patients));
		mContext = this;
		
		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			setResult(RESULT_CANCELED);
			finish();
		}

		mUploadTask = (UploadDataTask) getLastNonConfigurationInstance();
		mDownloadTask = (DownloadTask) getLastNonConfigurationInstance();

		showProgress = getIntent().getIntExtra(DIALOG, ASK_TO_DOWNLOAD);

		// get the task if we've changed orientations.
		if (mUploadTask == null && mDownloadTask == null) {
			buildUrls();
			showDialog(showProgress);
			if (showProgress == DIRECT_TO_DOWNLOAD)
				uploadData();
		}

	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mUploadTask != null) {
			mUploadTask.setUploadListener(this);
		}
		if (mDownloadTask != null) {
			mDownloadTask.setDownloadListener(this);
		}

		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			mProgressDialog.show();
		}

	}

	private void uploadData() {
		mUploadTask = new UploadDataTask();
		mUploadTask.setUploadListener(this);
		mUploadTask.execute(formUploadUrl);

	}

	@Override
	public void uploadComplete(String result) {
		Log.e("louis.fazen", "uploadcomplete String=" + result);
		showCustomToast(result);
		mUploadTask = null;
		downloadData();
	}

	public void downloadData() {
		if (mDownloadTask != null)
			return;

		mDownloadTask = new DownloadDataTask();
		mDownloadTask.setDownloadListener(RefreshDataActivity.this);
		mDownloadTask.execute(patientDownloadUrl, username, password, savedSearch, cohortId, programId, formlistDownloadUrl, formDownloadUrl);
	}

	public void downloadComplete(String result) {

		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
		}

		if (result != null)
			showCustomToast(getString(R.string.error, result));

		mDownloadTask = null;
		stopRefreshDataActivity();
	}

	// DIALOG SECTION
	@Override
	protected Dialog onCreateDialog(int id) {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

		// if you are seeing this, then you either
		// 1. received an alarm or have plugged in while using clinic
		switch (id) {
		case ASK_TO_DOWNLOAD:
			mAlertDialog = createAskDialog();
			return mAlertDialog;
			// 2. or pressed update manually
		case DIRECT_TO_DOWNLOAD:
			mProgressDialog = createDownloadDialog();
			return mProgressDialog;
		default:
			mAlertDialog = createAskDialog();
			return mAlertDialog;
		}
	}

	private AlertDialog createAskDialog() {
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					dialog.dismiss();
					showDialog(DIRECT_TO_DOWNLOAD);
					uploadData();
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					stopRefreshDataActivity();
					break;
				}
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setTitle(getString(R.string.refresh_clients_title));
		builder.setMessage(getString(R.string.refresh_clients_text));
		builder.setPositiveButton(getString(R.string.refresh), dialogClickListener);
		builder.setNegativeButton(getString(R.string.cancel), dialogClickListener);
		return builder.create();
	}

	private ProgressDialog createDownloadDialog() {
		ProgressDialog pD = new ProgressDialog(this);
		DialogInterface.OnClickListener loadingButtonListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO: redundant with onDestroy(), no?
				dialog.dismiss();
				if (mUploadTask != null) {
					mUploadTask.setUploadListener(null);
					mUploadTask.cancel(true);
				}

				if (mDownloadTask != null) {
					mDownloadTask.setDownloadListener(null);
					mDownloadTask.cancel(true);
				}
				stopRefreshDataActivity();
			}
		};

		pD.setIcon(android.R.drawable.ic_dialog_info);
		pD.setTitle(getString(R.string.uploading_patients));
		pD.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pD.setIndeterminate(false);
		pD.setCancelable(false);
		pD.setButton(getString(R.string.cancel), loadingButtonListener);
		return pD;
	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		if (mProgressDialog != null) {
			mProgressDialog.setMax(max);
			mProgressDialog.setProgress(progress);
			mProgressDialog.setTitle(getString(R.string.downloading, message));
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mUploadTask != null && mUploadTask.getStatus() != AsyncTask.Status.FINISHED)
			return mUploadTask;
		if (mDownloadTask != null && mDownloadTask.getStatus() != AsyncTask.Status.FINISHED)
			return mDownloadTask;
		return null;
	}

	private void stopRefreshDataActivity() {

		Intent stopintent = new Intent(getApplicationContext(), RefreshDataService.class);
		stopService(stopintent);
		//reschedule alarms (b/c either user is hitting cancel or recent sync)
		WakefulIntentService.scheduleAlarms(new AlarmListener(), mContext, true);
		
		Intent startintent = new Intent(getApplicationContext(), DashboardActivity.class);
		startintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(startintent);
		
		finish();
	}

	@Override
	protected void onDestroy() {
		Log.e("louis.fazen", "RefreshDataActivity.onDestroy is called");

		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}
		if (mUploadTask != null) {
			mUploadTask.setUploadListener(null);
			if (mUploadTask.getStatus() == AsyncTask.Status.FINISHED) {
				mUploadTask.cancel(true);
			}
		}
		
		if (mDownloadTask != null) {
			mDownloadTask.setDownloadListener(null);
			if (mDownloadTask.getStatus() == AsyncTask.Status.FINISHED) {
				mDownloadTask.cancel(true);
			}
		}
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}

		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

	}

	private void showCustomToast(String message) {

		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.BOTTOM, 0, -20);
		t.show();

	}

	// asynctask has no context, so build here
	private void buildUrls() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String serverUrl = settings.getString(PreferencesActivity.KEY_SERVER, getString(R.string.default_server));
		username = settings.getString(PreferencesActivity.KEY_USERNAME, getString(R.string.default_username));
		password = settings.getString(PreferencesActivity.KEY_PASSWORD, getString(R.string.default_password));
		String userpwd = "&uname=" + username + "&pw=" + password;

		savedSearch = String.valueOf(settings.getBoolean(PreferencesActivity.KEY_USE_SAVED_SEARCHES, false));
		cohortId = settings.getString(PreferencesActivity.KEY_SAVED_SEARCH, "0");
		programId = settings.getString(PreferencesActivity.KEY_PROGRAM, "0");
		patientDownloadUrl = serverUrl + Constants.PATIENT_DOWNLOAD_URL;

		StringBuilder uploadUrl = new StringBuilder(serverUrl);
		uploadUrl.append(Constants.INSTANCE_UPLOAD_URL);
		uploadUrl.append(userpwd);
		formUploadUrl = uploadUrl.toString();

		StringBuilder formlistUrl = new StringBuilder(serverUrl);
		formlistUrl.append(Constants.FORMLIST_DOWNLOAD_URL);
		formlistUrl.append(userpwd);
		formlistDownloadUrl = formlistUrl.toString();

		StringBuilder formUrl = new StringBuilder(serverUrl);
		formUrl.append(Constants.FORM_DOWNLOAD_URL);
		formUrl.append(userpwd);
		formDownloadUrl = formUrl.toString();

	}

}
