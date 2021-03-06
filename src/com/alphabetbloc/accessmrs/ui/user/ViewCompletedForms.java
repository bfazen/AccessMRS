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
package com.alphabetbloc.accessmrs.ui.user;

import java.io.File;


import android.app.AlarmManager;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ListView;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.adapters.MergeAdapter;
import com.alphabetbloc.accessmrs.data.Form;
import com.alphabetbloc.accessmrs.listeners.DecryptionListener;
import com.alphabetbloc.accessmrs.listeners.DeleteDecryptedDataListener;
import com.alphabetbloc.accessmrs.services.WakefulIntentService;
import com.alphabetbloc.accessmrs.tasks.DecryptionTask;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * Displays all the Completed/Finalized forms from AccessForms instances.db If
 * called from ViewPatient, then it has a patient, and must have Patient heading
 * If called from AllFormList, then is has patientId = -1, and has different
 * heading
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class ViewCompletedForms extends ViewFormsActivity implements DecryptionListener {

	public static final String EDIT_FORM = "edit_form";
	public static final int VIEW_FORM_ONLY = 4;
	private static final String TAG = ViewCompletedForms.class.getSimpleName();
	private ProgressDialog mDecryptDialog;
	private static Integer mPatientId;
	private Context mContext;
	private Form mClickedForm;
	private DecryptionTask mDecryptionTask;
	private ListView mListView;
	private MergeAdapter mMergeAdapter;
	private GestureDetector mFormDetector;
	private OnTouchListener mFormListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.view_forms);

		mContext = this;
		String patientIdString = getIntent().getStringExtra(KEY_PATIENT_ID);
		mPatientId = Integer.valueOf(patientIdString);

		mDecryptionTask = (DecryptionTask) getLastNonConfigurationInstance();

		mFormDetector = new GestureDetector(new onFormClick());
		mFormListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormDetector.onTouchEvent(event);
			}
		};

	}

	@Override
	protected void onResume() {
		super.onResume();

		createPatientHeader(mPatientId);
		refreshView();

		if (mDecryptDialog != null && !mDecryptDialog.isShowing()) {
			if (App.DEBUG) Log.v(TAG, "mDecryptDialog is SHOWING FROM ON RESUME!");
			mDecryptDialog.show();
		}

		if (mDecryptionTask != null)
			mDecryptionTask.setDecryptionListener(this);
	}

	protected void refreshView() {
		mMergeAdapter = new MergeAdapter();
		mMergeAdapter = createFormHistoryList(mMergeAdapter, getCompletedForms(mPatientId.toString()));
		mListView = getListView();
		mListView.setAdapter(mMergeAdapter);
		mListView.setOnTouchListener(mFormListener);
	}

	class onFormClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			int pos = mListView.pointToPosition((int) e.getX(), (int) e.getY());
			if (pos != -1) {
				Object o = mMergeAdapter.getItem(pos);
				if (o instanceof Form) {
					decryptForm((Form) o);
				}
			}
			return false;
		}
	}

	protected void launchFormView(Form f) {

		startActivityLog(mPatientId.toString(), f.getFormId().toString(), "Previous Encounter", false);

		Intent intent = new Intent();
		intent.setComponent(new ComponentName("com.alphabetbloc.accessforms", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		
		//KOSIRAI TRIAL ONLY:
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		String kosiraiRct = prefs.getString(getString(R.string.key_kosirai_rct), getString(R.string.default_kosirai_rct));
		intent.putExtra(getString(R.string.key_kosirai_rct), kosiraiRct);
		
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + f.getInstanceId()));
		startActivityForResult(intent, VIEW_FORM_ONLY);
	}

	private void decryptForm(final Form f) {

		if (isFileEncrypted(f) && !isRecentlyDecrypted(f)) {
			if (mDecryptionTask != null && mDecryptionTask.getStatus() != AsyncTask.Status.FINISHED)
				return;

			// schedule alarm to delete the decrypted files even before decrypt
			WakefulIntentService.scheduleAlarms(new DeleteDecryptedDataListener(), WakefulIntentService.DELETE_DECRYPTED_DATA, mContext, true);
			
			//save the form until completion of the asynctask, show dialog
			mClickedForm = f;
			mDecryptDialog = createDecryptDialog();
			mDecryptDialog.show();

			mDecryptionTask = new DecryptionTask();
			mDecryptionTask.setDecryptionListener(ViewCompletedForms.this);
			mDecryptionTask.execute(f.getInstanceId(), f.getPath());

		} else {
			launchFormView(f);
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mDecryptionTask != null && mDecryptionTask.getStatus() != AsyncTask.Status.FINISHED)
			return mDecryptionTask;
		return null;
	}

	private boolean isFileEncrypted(Form f) {
		boolean encrypted = false;
		String encPath = FileUtils.getEncryptedFilePath(f.getPath()) + FileUtils.ENC_EXT;
		File encFile = new File(encPath);
		if (encFile.exists())
			encrypted = true;
		if (App.DEBUG) Log.v(TAG, "File is Encrypted=" + encrypted);
		return encrypted;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
	}

	private boolean isRecentlyDecrypted(Form f) {
		// Make new decryption OR use existing decrypted files
		// IF exists, should be < MAX_DECRYPT_TIME as gets automatically deleted
		// IF not, a decrypt process was killed: file may be corrupt, so delete
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Long maxDecrypt = prefs.getLong(DecryptionTask.MAX_DECRYPT_TIME, AlarmManager.INTERVAL_HOUR);
		String decPath = FileUtils.getDecryptedFilePath(f.getPath());
		File decFile = new File(decPath);

		if (decFile.exists() && ((System.currentTimeMillis() - decFile.lastModified()) < maxDecrypt)) {
			if (App.DEBUG) Log.v(TAG, "File is already decrytped!");
			return true;

		} else if (decFile.exists()) {
			FileUtils.deleteFile(decFile.getParentFile().getAbsolutePath());
		}

		return false;
	}

	@Override
	public void decryptComplete(Boolean allFilesDecrypted) {
		if (mDecryptDialog != null) {
			mDecryptDialog.cancel();
			mDecryptDialog = null;
			if (App.DEBUG) Log.v(TAG, "mDecryptDialog is CANCELLED!");
		}
		if (mDecryptionTask != null) {
			mDecryptionTask.setDecryptionListener(null);
		}

		if (allFilesDecrypted && mClickedForm != null)
			launchFormView(mClickedForm);
		else {
			UiUtils.toastAlert(mContext, getString(R.string.error_filesystem), getString(R.string.error_opening_file));
			if (App.DEBUG) Log.v(TAG, "Error in Decrypting the file: " + mClickedForm.getPath());
		}
	}

	@Override
	public void setDeleteDecryptedFilesAlarm(boolean anyFileDecrypted) {
		// if nothing was decrypted, then can safely cancel the alarm
		if (!anyFileDecrypted)
			WakefulIntentService.cancelAlarms(WakefulIntentService.DELETE_DECRYPTED_DATA, mContext);
	}

	private ProgressDialog createDecryptDialog() {
		ProgressDialog pD = new ProgressDialog(this);
		DialogInterface.OnClickListener loadingButtonListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		};

		pD.setIcon(android.R.drawable.ic_dialog_info);
		pD.setTitle(getString(R.string.decrypting_data));
		pD.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		pD.setIndeterminate(true);
		pD.setCancelable(false);
		pD.setButton(getString(R.string.cancel), loadingButtonListener);
		return pD;
	}
	
	@Override
	protected void onPause() {
		super.onPause();

		if (mDecryptDialog != null && mDecryptDialog.isShowing()) {
			mDecryptDialog.dismiss();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mDecryptionTask != null) {
			mDecryptionTask.setDecryptionListener(null);
			mDecryptionTask.cancel(true);
		}
	}

}
