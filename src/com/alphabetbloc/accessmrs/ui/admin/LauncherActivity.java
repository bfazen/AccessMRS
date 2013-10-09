/*
 * Copyright (C) 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alphabetbloc.accessmrs.ui.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.alphabetbloc.accessmrs.ui.user.DashboardActivity;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.LauncherUtil;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class LauncherActivity extends Activity {

	public static final String TAG = LauncherActivity.class.getSimpleName();
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
		sLaunching = true;
		refreshView();
	}

	private void refreshView() {

		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {

				boolean foregroundApp = getIntent().getBooleanExtra(LAUNCH_DASHBOARD, true);
				boolean closeLauncher = true;
				Intent i = LauncherUtil.getLaunchIntent();

				// Error
				if (i == null)
					return closeLauncher;

				// Don't Launch if AccessForms Not Installed
				else if (i.getAction() != null && i.getAction().equalsIgnoreCase(LauncherUtil.ACCESS_FORMS_NOT_INSTALLED)) {
					if (foregroundApp)
						UiUtils.toastAlert(App.getApp().getString(R.string.installation_error), App.getApp().getString(R.string.access_forms_not_installed));
				}

				// If launched in foreground, launch any intent
				else if (foregroundApp) {
					startActivity(i);

					if (!i.filterEquals(mDashboardIntent))
						closeLauncher = false;
				}

				// If launched from service (!foreground), only launch one setup
				// intent (one at a time... always closing launcher)
				else if (!i.filterEquals(mDashboardIntent))
					startActivity(i);

				return closeLauncher;
			}

			@Override
			protected void onPostExecute(Boolean finish) {
				super.onPostExecute(finish);
				if (finish)
					finish();
			}

		}.execute();

	}

	@Override
	protected void onDestroy() {
		if (App.DEBUG)
			Log.v(TAG, "Exiting AccessMRS Activity");
		sLaunching = false;
		super.onDestroy();
	}

}
