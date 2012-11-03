package com.alphabetbloc.clinic.ui.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.ClinicLauncher;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class ClinicLauncherActivity extends Activity {

	public static final String TAG = ClinicLauncherActivity.class.getSimpleName();
	public static final String LAUNCH_DASHBOARD = "launch_dashboard";
	public static boolean sLaunching = false;
	private Intent mDashboardIntent = new Intent(App.getApp(), DashboardActivity.class);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		boolean launchDashboard = getIntent().getBooleanExtra(LAUNCH_DASHBOARD, true);
		if (launchDashboard)
			setContentView(R.layout.loading);
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshView();
	}

	private void refreshView() {

		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {

				boolean foregroundApp = getIntent().getBooleanExtra(LAUNCH_DASHBOARD, true);
				sLaunching = true;
				Intent i = ClinicLauncher.getLaunchIntent();

				// Error
				if (i == null)
					sLaunching = false;

				// Don't Launch if Collect Not Installed
				else if (i.getAction() != null && i.getAction().equalsIgnoreCase(ClinicLauncher.COLLECT_NOT_INSTALLED)) {
					if (foregroundApp)
						UiUtils.toastAlert(App.getApp().getString(R.string.installation_error), App.getApp().getString(R.string.collect_not_installed));
					sLaunching = false;
				}

				// If launched in foreground, launch any intent
				else if (foregroundApp) {
					startActivity(i);

					if (i.filterEquals(mDashboardIntent))
						sLaunching = false;
				}
				
				// If launched from service, only launch setup intents
				else if (!i.filterEquals(mDashboardIntent))
					startActivity(i);

				return sLaunching;
			}

			@Override
			protected void onPostExecute(Boolean launching) {
				super.onPostExecute(launching);
				if(!launching)
					finish();
			}
			
		}.execute();

	}

	@Override
	protected void onDestroy() {
		Log.i(TAG, "Exiting Clinic Launcher Activity");
		super.onDestroy();
	}
	
	
}
