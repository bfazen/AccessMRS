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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.alphabetbloc.accessmrs.services.RefreshDataService;
import com.alphabetbloc.accessmrs.services.SyncManager;
import com.alphabetbloc.accessmrs.ui.user.BaseUserActivity;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public abstract class BasePreferenceActivity extends PreferenceActivity implements SyncStatusObserver {
	// Menu ID's
	private static final String TAG = BaseUserActivity.class.getSimpleName();

	private static ProgressDialog mSyncActiveDialog;
	private static Object mSyncObserverHandle;
	private Context mToastCtx;
	private static boolean mPaused;
	private ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(5);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!FileUtils.storageReady()) {
			UiUtils.toastAlert(this, getString(R.string.error_storage_title), getString(R.string.error_storage));
			setResult(RESULT_CANCELED);
			finish();
		}
		mToastCtx = this;
	}

	@Override
	public void onStatusChanged(int which) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!RefreshDataService.isSyncActive) {
					// Sync is not yet active, so we must be starting a sync
					if (App.DEBUG)
						Log.v(TAG, "SyncStatusChanged: Preferences does not request syncs");
					SyncManager.sCancelSync.set(true);

				} else {
					// we are just completing a sync (whether success or not)
					if (App.DEBUG)
						Log.v(TAG, "SyncStatusChanged: completing sync");
					// dismiss dialog
					if (mSyncActiveDialog != null) {
						mSyncActiveDialog.dismiss();
						mSyncActiveDialog = null;
					}

				}
			}
		});

	}

	private void showProgressDialog() {
		SyncManager.sSyncStep.set(0);
		SyncManager.sLoopProgress.set(0);
		SyncManager.sLoopCount.set(0);
		mSyncActiveDialog = new ProgressDialog(this);
		mSyncActiveDialog.setIcon(android.R.drawable.ic_dialog_info);
		mSyncActiveDialog.setTitle(getString(R.string.sync_in_progress_title));
		mSyncActiveDialog.setMessage(getString(R.string.sync_in_progress));
		mSyncActiveDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mSyncActiveDialog.setCancelable(false);
		mSyncActiveDialog.setProgress(0);
		mSyncActiveDialog.show();
	}

	@Override
	protected void onResume() {
		mPaused = false;
		super.onResume();
		IntentFilter filter = new IntentFilter(SyncManager.SYNC_MESSAGE);
		LocalBroadcastManager.getInstance(this).registerReceiver(onSyncNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);

		if (RefreshDataService.isSyncActive)
			updateSyncProgress();
	}

	private void updateSyncProgress() {
		SyncManager.sEndSync.set(false);

		if (mSyncActiveDialog == null)
			showProgressDialog();

		mExecutor.schedule(new Runnable() {
			public void run() {

				if (!SyncManager.sEndSync.get() && !mPaused) {
					mExecutor.schedule(this, 800, TimeUnit.MILLISECONDS);
					BasePreferenceActivity.this.runOnUiThread(new Runnable() {

						@Override
						public void run() {
							if (mSyncActiveDialog != null) {
								int loop = (SyncManager.sLoopProgress == SyncManager.sLoopCount) ? 0 : ((int) Math.round(((float) SyncManager.sLoopProgress.get() / (float) SyncManager.sLoopCount.get()) * 10F));
								mSyncActiveDialog.setProgress((SyncManager.sSyncStep.get() * 10) + loop);
								mSyncActiveDialog.setMessage(SyncManager.sSyncTitle);
							}
						}
					});

				}
			}
		}, 0, TimeUnit.MILLISECONDS);

	}

	protected BroadcastReceiver onSyncNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {
			boolean requestSync = i.getBooleanExtra(SyncManager.REQUEST_NEW_SYNC, false);
			boolean newSync = i.getBooleanExtra(SyncManager.START_NEW_SYNC, false);
			if (requestSync) {
				// should never happen
			} else if (newSync) {
				// we are starting a new sync automatically (Should never happen
				// in Prefs)
				if (mSyncActiveDialog != null && mSyncActiveDialog.isShowing()) {
					mSyncActiveDialog.dismiss();
					mSyncActiveDialog = null;
				}
				updateSyncProgress();
			} else {
				// we have ongoing sync, with new sync message
				boolean error = i.getBooleanExtra(SyncManager.TOAST_ERROR, false);
				String toast = i.getStringExtra(SyncManager.TOAST_MESSAGE);
				UiUtils.toastSyncMessage(mToastCtx, toast, error);
			}
		}
	};

	@Override
	protected void onPause() {
		mPaused = true;
		super.onPause();

		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onSyncNotice);
	}

}