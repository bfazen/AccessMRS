package org.odk.clinic.android.activities;

import java.util.ArrayList;

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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class RefreshDataActivity extends Activity implements UploadFormListener, DownloadListener {

	// private final static int COHORTS_PROGRESS_DIALOG = 2;
	// private final static int PATIENTS_PROGRESS_DIALOG = 3;
	private final static int PROGRESS_DIALOG = 1;
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

	private ClinicAdapter mCla;
	private int totalCount = -1;
	private boolean mUploadComplete;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setTitle(getString(R.string.app_name) + " > " + getString(R.string.download_patients));

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			setResult(RESULT_CANCELED);
			finish();
		}
		

		// get the task if we've changed orientations. If it's null, open up the
		// cohort selection dialog
		mUploadTask = (UploadDataTask) getLastNonConfigurationInstance();
		mDownloadTask = (DownloadTask) getLastNonConfigurationInstance();
		if (mUploadTask == null && mDownloadTask == null) {
			buildUrls();
			//keep same dialog through upload and download:
			showDialog(PROGRESS_DIALOG);
			if(uploadInstances())
				downloadPatientsAndForms();;
		}
	}

	// UPLOAD SECTION
	private boolean uploadInstances() {

		mUploadComplete = false;

		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
			mCla.open();
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

			totalCount = selectedInstances.size();

			if (totalCount < 1) {
				showCustomToast("No Clients to Upload");
				mUploadComplete = true;
			} else {
				// convert array list to an array
				String[] sa = selectedInstances.toArray(new String[totalCount]);
				mUploadTask = new UploadDataTask();
				mUploadTask.setUploadListener(this);
				mUploadTask.setUploadServer(formUploadUrl);
				mUploadTask.execute(sa);
			}

		} else {
			showCustomToast("No Clients to Upload");
			mUploadComplete = true;
		}

		return mUploadComplete;
	}

	@Override
	public void uploadComplete(ArrayList<String> result) {

		int resultSize = result.size();
		if (resultSize == totalCount) {
			showCustomToast(getString(R.string.upload_all_successful, totalCount));

		} else {
			String s = totalCount - resultSize + " of " + totalCount;
			showCustomToast(getString(R.string.upload_some_failed, s));

		}

		// for all successful update status
		ClinicAdapter cla = new ClinicAdapter();
		cla.open();
		Cursor c = null;
		for (int i = 0; i < resultSize; i++) {
			c = cla.fetchFormInstancesByPath(result.get(i));
			if (c != null) {
				cla.updateFormInstance(result.get(i), ClinicAdapter.STATUS_SUBMITTED);
			}

		}

		if (c != null)
			c.close();

		cla.close();

		mUploadTask = null;

		// no longer using the database, so start download
		downloadPatientsAndForms();
	}

	// DOWNLOAD SECTION
	private void downloadPatientsAndForms() {

		if (mDownloadTask != null)
			return;

		mDownloadTask = new DownloadDataTask();
		mDownloadTask.setDownloadListener(RefreshDataActivity.this);
		mDownloadTask.execute(patientDownloadUrl, username, password, savedSearch, cohortId, programId, formlistDownloadUrl, formDownloadUrl);

		// dialog section

	}

	public void downloadComplete(String result) {

		if (mProgressDialog != null) {
			Log.e("louis.fazen", "calling dismiss on the progressdialog");
			mProgressDialog.dismiss();
			// dismissDialog(PROGRESS_DIALOG);
		}

		if (result != null) {
			showCustomToast(getString(R.string.error, result));
			finish();
		}

		else {
			// both upload and download should be okay by this point, so:
			setResult(RESULT_OK);
			finish();
		}

		mDownloadTask = null;
	}

	// DIALOG SECTION
	@Override
	protected Dialog onCreateDialog(int id) {
		// may need to switch return type to ProgressDialog?

		mProgressDialog = new ProgressDialog(this);
		DialogInterface.OnClickListener loadingButtonListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				if (mUploadTask != null) {
					mUploadTask.setUploadListener(null);
					mUploadTask.cancel(true);
				}

				if (mDownloadTask != null) {
					mDownloadTask.setDownloadListener(null);
					mDownloadTask.cancel(true);
				}

				setResult(RESULT_CANCELED);
				finish();
			}
		};
		// initialize dialog to the upload text:
		mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
		mProgressDialog.setTitle(getString(R.string.uploading_patients));
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setIndeterminate(false);
		mProgressDialog.setCancelable(false);
		mProgressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
		return mProgressDialog;

	}

	// deleting this because it is different than the onCreateDialog...?
	// @Override
	// protected void onPrepareDialog(int id, Dialog dialog) {
	// if (id == PROGRESS_DIALOG) {
	// ProgressDialog progress = (ProgressDialog) dialog;
	// progress.setTitle(getString(R.string.connecting_server));
	// progress.setProgress(0);
	// }
	// }

	@Override
	public void progressUpdate(String message, int progress, int max) {
		Log.e("louis.fazen", "calling progressupdate with mProgressDialog=" + mProgressDialog);
		mProgressDialog.setMax(max);
		mProgressDialog.setProgress(progress);
		mProgressDialog.setTitle(getString(R.string.downloading, message));
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mUploadTask != null && mUploadTask.getStatus() != AsyncTask.Status.FINISHED)
			return mUploadTask;
		if (mDownloadTask != null && mDownloadTask.getStatus() != AsyncTask.Status.FINISHED)
			return mDownloadTask;

		return null;
	}

	@Override
	protected void onDestroy() {
		if (mUploadTask != null) {
			mUploadTask.setUploadListener(null);
			if (mUploadTask.getStatus() == AsyncTask.Status.FINISHED)
				mUploadTask.cancel(true);
		}
		if (mDownloadTask != null) {
			mDownloadTask.setDownloadListener(null);
			if (mDownloadTask.getStatus() == AsyncTask.Status.FINISHED)
				mDownloadTask.cancel(true);
		}
		super.onDestroy();
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

	@Override
	protected void onPause() {
		super.onPause();
		// letting processes run in background...

		// could be either upload or download dialog...
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}

		// was in original instanceUploaderActivity... not sure use of it here?
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

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
		t.setGravity(Gravity.BOTTOM, 0, -20);
		t.show();
	}
	
	//the other way to do this would simply be to pass the AsyncTask the context, then build them there
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
