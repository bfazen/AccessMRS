package com.alphabetbloc.clinic.ui.user;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) (All Methods except where noted
 *         otherwise)
 * 
 */

// TODO! Delete this class!!!1
public class RefreshDataActivity extends Activity {

	public final static int ASK_TO_DOWNLOAD = 1;
	public final static int DIRECT_TO_DOWNLOAD = 2;
	public final static String DIALOG = "showdialog";
	private static final String TAG = RefreshDataActivity.class.getSimpleName();
	// private Context mContext;
	private ProgressDialog mSyncDialog;
	private AlertDialog mAlertDialog;
	private int showProgress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.download_patients));
		// mContext = this;

		if (!FileUtils.storageReady()) {
			UiUtils.toastAlert(this, getString(R.string.error_storage_title), getString(R.string.error_storage));
			setResult(RESULT_CANCELED);
			finish();
		}

		showProgress = getIntent().getIntExtra(DIALOG, ASK_TO_DOWNLOAD);

		// get the task if we've changed orientations.

		showDialog(showProgress);

	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mSyncDialog != null && !mSyncDialog.isShowing()) {

			mSyncDialog.show();
		}

	}

	// DIALOG SECTION
	@Override
	protected Dialog onCreateDialog(int id) {
		if (mSyncDialog != null && mSyncDialog.isShowing()) {
			mSyncDialog.dismiss();
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
			mSyncDialog = createDownloadDialog();
			return mSyncDialog;
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
					//
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					stopRefreshDataActivity(false);
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

				dialog.dismiss();
				stopRefreshDataActivity(true);

			}
		};

		pD.setIcon(android.R.drawable.ic_dialog_info);
		pD.setTitle(getString(R.string.uploading_patients));
		pD.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pD.setIndeterminate(false);
		pD.setCancelable(false);
		// pD.setButton(getString(R.string.cancel), loadingButtonListener);
		return pD;
	}

	private void stopRefreshDataActivity(boolean reloadDashboard) {

		Intent stopintent = new Intent(getApplicationContext(), RefreshDataService.class);
		stopService(stopintent);
		// reschedule alarms (b/c either user is hitting cancel or recent sync)
		// TODO! check if this should be false!
		// WakefulIntentService.scheduleAlarms(new RefreshDataListener(),
		// WakefulIntentService.REFRESH_DATA, mContext, true);

		if (reloadDashboard) {
			Intent startintent = new Intent(getApplicationContext(), DashboardActivity.class);
			startintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(startintent);
		}

		finish();
	}

	@Override
	protected void onDestroy() {
		Log.e("louis.fazen", "RefreshDataActivity.onDestroy is called");

		if (mSyncDialog != null && mSyncDialog.isShowing()) {
			mSyncDialog.dismiss();
		}
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mSyncDialog != null && mSyncDialog.isShowing()) {
			mSyncDialog.dismiss();
		}

		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

	}

}
