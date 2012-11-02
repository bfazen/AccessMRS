package com.alphabetbloc.clinic.ui.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Window;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.ClinicLauncher;

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
		setContentView(R.layout.loading);
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshView();
	}

	private void refreshView() {
		
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {

				boolean launchDashboard = getIntent().getBooleanExtra(LAUNCH_DASHBOARD, true);
				sLaunching = true;
				Intent i = ClinicLauncher.getLaunchIntent();

				if (i == null)
					closeLauncher();

				else {

					if (!i.filterEquals(mDashboardIntent) || launchDashboard)
						startActivity(i);

					if (i.filterEquals(mDashboardIntent))
						closeLauncher();
				}

				return null;
			}
		}.execute();

	}

	private void closeLauncher() {
		sLaunching = false;
		finish();
	}

	// if (i.filterEquals(mDashboardIntent) && !launchDashboard)
	// closeClinic();
	//
	// else {
	//
	// startActivity(i);
	//
	// if (i.filterEquals(mDashboardIntent))
	// closeClinic();
	// }
	//
	// if (i.filterEquals(mDashboardIntent)) {
	//
	// if (launchDashboard)
	// startActivity(i);
	//
	// closeClinic();
	//
	// } else
	// startActivity(i);

}
