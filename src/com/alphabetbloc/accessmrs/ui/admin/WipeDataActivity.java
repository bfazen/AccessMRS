package com.alphabetbloc.accessmrs.ui.admin;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Window;

import com.alphabetbloc.accessmrs.services.WakefulIntentService;
import com.alphabetbloc.accessmrs.services.WipeDataService;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) (All Methods except where noted
 *         otherwise)
 */
public class WipeDataActivity extends BaseAdminActivity {

	// private static final String TAG = WipeDataActivity.class.getSimpleName();
	private AlertDialog mAlertDialog;
	private Context mContext;
	private static final int WIPE_DATA = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.wipe_data);

		if (!FileUtils.storageReady()) {
			UiUtils.toastAlert(this, getString(R.string.error_storage_title), getString(R.string.error_storage));
			setResult(RESULT_CANCELED);
			finish();
		}

		showDialog(WIPE_DATA);

	}

	// DIALOG SECTION
	@Override
	protected Dialog onCreateDialog(int id) {
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

		switch (id) {
		case WIPE_DATA:
		default:
			mAlertDialog = createAskDialog();
			return mAlertDialog;
		}
	}

	private AlertDialog createAskDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setTitle(R.string.alert_title_first_warning);
		builder.setMessage(R.string.alert_odk_body_first_warning);
		builder.setIcon(R.drawable.ic_exclamation);

		builder.setPositiveButton(R.string.alert_ok_button, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
				builder.setTitle(R.string.alert_title_second_warning);
				builder.setMessage(R.string.alert_odk_body_second_warning);
				builder.setIcon(R.drawable.ic_exclamation);
				builder.setPositiveButton(R.string.alert_ok_button, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {

						Intent i = new Intent(mContext, WipeDataService.class);
						WakefulIntentService.sendWakefulWork(mContext, i);

					}
				});
				builder.setNegativeButton(R.string.alert_second_cancel_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
				builder.show();
			}
		});

		builder.setNegativeButton(R.string.alert_first_cancel_button, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
		});
		return builder.create();
	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(WipeDataService.WIPE_DATA_COMPLETE);
		registerReceiver(onNotice, filter);
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(onNotice);
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}
	}

	protected BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {
			if (FileUtils.isDataWiped())
				UiUtils.toastInfo(mContext, getString(R.string.wiping_data_successful_title), getString(R.string.wiping_data_successful));
			else
				UiUtils.toastAlert(mContext, getString(R.string.wiping_data_error_title), getString(R.string.wiping_data_error));

			Intent relaunch = new Intent(mContext, AccessMrsLauncherActivity.class);
			relaunch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			relaunch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(relaunch);
			finish();
		}
	};

}
