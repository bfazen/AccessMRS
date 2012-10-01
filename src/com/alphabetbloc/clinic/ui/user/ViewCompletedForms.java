package com.alphabetbloc.clinic.ui.user;

import java.io.File;

import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

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
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.adapters.MergeAdapter;
import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.listeners.DecryptionListener;
import com.alphabetbloc.clinic.listeners.DeleteDecryptedDataListener;
import com.alphabetbloc.clinic.services.WakefulIntentService;
import com.alphabetbloc.clinic.tasks.DecryptionTask;
import com.alphabetbloc.clinic.utilities.FileUtils;

/**
 * Displays all the Completed/Finalized forms from Collect instances.db If
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
	private ProgressDialog mProgressDialog;
	private static Integer mPatientId;
	private Context mContext;
	private Form mClickedForm;
	private DecryptionTask mDecryptionTask;
	private ListView mListView;
	private MergeAdapter mMergeAdapter;
	protected GestureDetector mFormDetector;
	protected OnTouchListener mFormListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.example_cw_main);

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

		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			Log.e(TAG, "mProgressDialog is SHOWING FROM ON RESUME!");
			mProgressDialog.show();
		}

		if (mDecryptionTask != null)
			mDecryptionTask.setDecryptionListener(this);
	}

	private void refreshView() {
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
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
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
			mProgressDialog = createDecryptDialog();
			mProgressDialog.show();

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
		Log.e(TAG, "File is Encrypted=" + encrypted);
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
			Log.e(TAG, "File is already decrytped!");
			return true;

		} else if (decFile.exists()) {
			FileUtils.deleteFile(decFile.getParentFile().getAbsolutePath());
		}

		return false;
	}

	@Override
	public void decryptComplete(Boolean allFilesDecrypted) {
		if (mProgressDialog != null) {
			mProgressDialog.cancel();
			mProgressDialog = null;
			Log.e(TAG, "mProgressDialog is CANCELLED!");
		}
		if (mDecryptionTask != null) {
			mDecryptionTask.setDecryptionListener(null);
		}

		if (allFilesDecrypted && mClickedForm != null)
			launchFormView(mClickedForm);
		else {
			Toast.makeText(mContext, "Sorry, there has been an error opening this file.", Toast.LENGTH_SHORT);
			Log.e(TAG, "Error in Decrypting the file: " + mClickedForm.getPath());
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

		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
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
