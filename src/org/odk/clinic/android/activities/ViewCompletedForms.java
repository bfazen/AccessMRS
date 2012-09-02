package org.odk.clinic.android.activities;

import java.io.File;
import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.listeners.DecryptionListener;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.tasks.ActivityLogTask;
import org.odk.clinic.android.tasks.DecryptionTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

import com.alphabetbloc.clinic.services.EncryptionService;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.commonsware.cwac.wakeful.DeleteDecryptedDataListener;
import com.commonsware.cwac.wakeful.DeleteDecryptedDataReceiver;
import com.commonsware.cwac.wakeful.EncryptDataListener;
import com.commonsware.cwac.wakeful.WakefulIntentService;

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
	private static final String TAG = "ViewCompletedForms";
	private ProgressDialog mProgressDialog;
	private static Integer mPatientId;
	private ActivityLog mActivityLog;
	private Context mContext;
	private Form mClickedForm;
	private DecryptionTask mDecryptionTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.app_name) + " > " + "Previous Encounters");
		mContext = this;
		String patientIdString = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
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

		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);

		if (mPatientId != null) {
			createPatientHeader(mPatientId);
			refreshView();
		}

		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			Log.e(TAG, "mProgressDialog is SHOWING FROM ON RESUME!");
			mProgressDialog.show();
		}

		if (mDecryptionTask != null)
			mDecryptionTask.setDecryptionListener(this);
	}

	private void refreshView() {
		String selection = "(" + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? ) and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_ENCRYPTED, InstanceProviderAPI.STATUS_COMPLETE, InstanceProviderAPI.STATUS_SUBMITTED, mPatientId.toString() };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, InstanceColumns.LAST_STATUS_CHANGE_DATE + " desc");

		ArrayList<Form> selectedForms = new ArrayList<Form>();

		if (c != null) {
			c.moveToFirst();
		}

		if (c != null && c.getCount() >= 0) {
			int formPathIndex = c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
			int formIdIndex = c.getColumnIndex(InstanceColumns.JR_FORM_ID);
			int displayNameIndex = c.getColumnIndex(InstanceColumns.DISPLAY_NAME);
			int formNameIndex = c.getColumnIndex(InstanceColumns.FORM_NAME);
			int displaySubtextIndex = c.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT);
			int idIndex = c.getColumnIndex(InstanceColumns._ID);
			int dateIndex = c.getColumnIndex(InstanceColumns.LAST_STATUS_CHANGE_DATE);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(idIndex)) {
						form = new Form();
						form.setInstanceId(c.getInt(idIndex));
						form.setFormId(c.getInt(formIdIndex));
						form.setName(c.getString(formNameIndex));
						form.setDisplayName(c.getString(displayNameIndex));
						form.setPath(c.getString(formPathIndex));
						form.setDisplaySubtext(c.getString(displaySubtextIndex));
						form.setDate(c.getLong(dateIndex));

						selectedForms.add(form);
					}
				} while (c.moveToNext());
			}
		}

		if (c != null)
			c.close();

		createFormHistoryList(selectedForms);
	}

	class onFormClick extends formGestureDetector {

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

	// protected void decryptForm(Form f) {
	// mProgressDialog = createDecryptDialog();
	// if (EncryptionUtil.decryptFormInstance(f.getInstanceId(), f.getPath())) {
	// launchFormView(f);
	// if (mProgressDialog != null)
	// mProgressDialog.dismiss();
	// }
	// }

	private void decryptForm(final Form f) {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("crypto$*^@crypto");
		alert.setIcon(R.drawable.id_icon_inverse);
		alert.setMessage("Select cryptography type:");
		alert.setPositiveButton("Encrypt", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				WakefulIntentService.sendWakefulWork(mContext, EncryptionService.class);
				Toast.makeText(mContext, "Encrypting Data...", Toast.LENGTH_SHORT);
			}
		});

		alert.setNegativeButton("Decrypt", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				if (isFileEncrypted(f) && !isRecentlyDecrypted(f)) {
					if (mDecryptionTask != null && mDecryptionTask.getStatus() != AsyncTask.Status.FINISHED)
						return;
					Log.e(TAG, "file is not recently decrypted!");
					mClickedForm = f;
					// always schedule an alarm to delete the decrypted files
					// before we decrypt
					WakefulIntentService.scheduleAlarms(new DeleteDecryptedDataListener(), WakefulIntentService.DELETE_DECRYPTED_DATA, mContext, true);
					mProgressDialog = createDecryptDialog();
					mProgressDialog.show();
					mDecryptionTask = new DecryptionTask();
					mDecryptionTask.setDecryptionListener(ViewCompletedForms.this);
					Log.e(TAG, "setDecryptionTask with formId=" + f.getInstanceId());
					mDecryptionTask.execute(f.getInstanceId());
				} else {
					launchFormView(f);
				}
			}
		});
		alert.show();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mDecryptionTask != null && mDecryptionTask.getStatus() != AsyncTask.Status.FINISHED)
			return mDecryptionTask;
		return null;
	}

	private boolean isFileEncrypted(Form f) {
		boolean encrypted = false;
		CharSequence decryptDir = "/.dec/";
		if (f.getPath().contains(decryptDir))
			encrypted = true;
		Log.e(TAG, "isFileEncrypted=" + encrypted);
		return encrypted;
	}

	private boolean isRecentlyDecrypted(Form f) {
		// This is path to decrypted form: instanceDir/.dec/instance-date.xml
		// NB: file does not exist, b/c we have not decrypted!
		File decryptedfile = new File(f.getPath());
		File decryptHiddenDir = decryptedfile.getParentFile();
		File instanceDir = decryptHiddenDir.getParentFile();

		// Make new decryption OR use existing decrypted files
		// IF exists, should be < MAX_DECRYPT_TIME as gets automatically deleted
		// IF not, a decrypt process was killed: file may be corrupt, so delete
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Long maxDecrypt = prefs.getLong(DecryptionTask.MAX_DECRYPT_TIME, AlarmManager.INTERVAL_HOUR);
		if (decryptedfile.exists() && ((System.currentTimeMillis() - decryptedfile.lastModified()) < maxDecrypt)) {

			
			Log.e(TAG, "file is already decrytped!");
			return true;
		} else if (decryptedfile.exists())
			FileUtils.deleteFile(decryptHiddenDir.getPath());

		Log.e(TAG, "file is not recently decrypted!");
		FileUtils.createFolder(decryptHiddenDir.getPath());
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

	protected void launchFormView(Form f) {
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString());
		}

		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + f.getInstanceId()));
		startActivityForResult(intent, VIEW_FORM_ONLY);
	}

	private void startActivityLog(String formId) {

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String providerId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(providerId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId(mPatientId.toString());
		mActivityLog.setActivityStartTime();
		mActivityLog.setFormPriority("not applicable");
		mActivityLog.setFormType("Previous Encounter");

	}

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		mActivityLog.setActivityStopTime();
		new ActivityLogTask(mActivityLog).execute();
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
