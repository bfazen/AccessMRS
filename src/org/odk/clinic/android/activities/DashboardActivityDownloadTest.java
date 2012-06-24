package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.DownloadListener;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.Cohort;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.tasks.DownloadFormListTask;
import org.odk.clinic.android.tasks.DownloadPatientTask;
import org.odk.clinic.android.tasks.DownloadPatientTaskLouis;
import org.odk.clinic.android.tasks.DownloadTask;
import org.odk.clinic.android.tasks.UploadInstanceTask;
import org.odk.clinic.android.utilities.FileUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
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

public class DashboardActivityDownloadTest extends Activity implements UploadFormListener, DownloadListener {

//	private final static int COHORTS_PROGRESS_DIALOG = 2;
//	private final static int PATIENTS_PROGRESS_DIALOG = 3;
	private final static int PROGRESS_DIALOG = 1;
	private ProgressDialog mProgressDialog;

	private DownloadTask mDownloadTask;
	private UploadInstanceTask mInstanceUploaderTask;

	private String username;
	private String password;
	private String savedSearch;
	private String cohortId;
	private String programId;
	private String patientDownloadUrl;
	private String formDownloadUrl;
	private String formUploadUrl;

	private ClinicAdapter mCla;
	private int totalCount = -1;

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
		mDownloadTask = (DownloadTask) getLastNonConfigurationInstance();
		if (mDownloadTask == null) {
			buildUrls();

				uploadInstances();
				
			
		}
	}

	private void downloadPatientsAndForms() {

		if (mDownloadTask != null)
			return;

		showDialog(PROGRESS_DIALOG);
		mDownloadTask = new DownloadPatientTaskLouis();
		mDownloadTask.setDownloadListener(DashboardActivityDownloadTest.this);
		mDownloadTask.execute(patientDownloadUrl, username, password, savedSearch, cohortId, programId, formDownloadUrl);
	}


	private void uploadInstances() {

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

			 int totalCount = selectedInstances.size();
		        
		        if (totalCount < 1){
		        	showCustomToast("No Patients to Upload");
		        	return;
		        }

		        // convert array list to an array
		        String[] sa = selectedInstances.toArray(new String[totalCount]);
		        mInstanceUploaderTask = new UploadInstanceTask();
		        mInstanceUploaderTask.setUploadListener(this);
		        mInstanceUploaderTask.setUploadServer(formUploadUrl);
		        mInstanceUploaderTask.execute(sa);
		        showDialog(PROGRESS_DIALOG);
		}

		
		downloadPatientsAndForms();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		// may need to switch return type to ProgressDialog?

		ProgressDialog dialog = new ProgressDialog(this);
		DialogInterface.OnClickListener loadingButtonListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				mDownloadTask.setDownloadListener(null);
				setResult(RESULT_CANCELED);

				// mInstanceUploaderTask.setUploadListener(null);
				// mInstanceUploaderTask.cancel(true);

				finish();
			}
		};
		dialog.setTitle(getString(R.string.connecting_server));
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(false);
		dialog.setCancelable(false);
		dialog.setButton(getString(R.string.cancel_download), loadingButtonListener);

		// from instanceuploaderactivity... slightly different format:
		/*
		 * mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
		 * mProgressDialog.setTitle(getString(R.string.upload_patients));
		 * mProgressDialog.setMessage(getString(R.string.uploading_patients));
		 * mProgressDialog.setIndeterminate(true);
		 * mProgressDialog.setCancelable(false);
		 * mProgressDialog.setButton(getString(R.string.cancel),
		 * uploadButtonListener); return mProgressDialog;
		 */
		return dialog;
	}

	@Override
	public void uploadComplete(ArrayList<String> result) {
		dismissDialog(PROGRESS_DIALOG);
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
		finish();
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {

		if (id == PROGRESS_DIALOG) {
			ProgressDialog progress = (ProgressDialog) dialog;
			progress.setTitle(getString(R.string.connecting_server));
			progress.setProgress(0);
		}
	}

	public void downloadComplete(String result) {

		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
//			dismissDialog(PROGRESS_DIALOG);
		}

		if (result != null) {
			showCustomToast(getString(R.string.error, result));
			finish();
		}

		else {
			setResult(RESULT_OK);
			finish();
		}

		mDownloadTask = null;
	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		mProgressDialog.setMax(max);
		mProgressDialog.setProgress(progress);
		mProgressDialog.setTitle(getString(R.string.downloading, message));
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return mDownloadTask;
	}

	@Override
	protected void onDestroy() {
		if (mDownloadTask != null) {
			mDownloadTask.setDownloadListener(null);
		}
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();

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

		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
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
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}

	private void buildUrls() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String serverUrl = settings.getString(PreferencesActivity.KEY_SERVER, getString(R.string.default_server));

		patientDownloadUrl = serverUrl + Constants.PATIENT_DOWNLOAD_URL;
		username = settings.getString(PreferencesActivity.KEY_USERNAME, getString(R.string.default_username));
		password = settings.getString(PreferencesActivity.KEY_PASSWORD, getString(R.string.default_password));
		savedSearch = String.valueOf(settings.getBoolean(PreferencesActivity.KEY_USE_SAVED_SEARCHES, false));
		cohortId = settings.getString(PreferencesActivity.KEY_SAVED_SEARCH, "0");
		programId = settings.getString(PreferencesActivity.KEY_PROGRAM, "0");

		StringBuilder formUrl = new StringBuilder(serverUrl);
		formUrl.append(Constants.FORMLIST_DOWNLOAD_URL);
		formUrl.append("&uname=");
		formUrl.append(username);
		formUrl.append("&pw=");
		formUrl.append(password);
		formDownloadUrl = formUrl.toString();

		StringBuilder uploadUrl = new StringBuilder(serverUrl);
		uploadUrl.append(Constants.INSTANCE_UPLOAD_URL);
		uploadUrl.append("?uname=");
		uploadUrl.append(settings.getString(PreferencesActivity.KEY_USERNAME, getString(R.string.default_username)));
		uploadUrl.append("&pw=");
		uploadUrl.append(settings.getString(PreferencesActivity.KEY_PASSWORD, getString(R.string.default_password)));
		formUploadUrl = uploadUrl.toString();
	}

}
