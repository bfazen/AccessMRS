package com.alphabetbloc.clinic.ui.user;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import com.alphabetbloc.clinic.R;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import com.alphabetbloc.clinic.data.ActivityLog;
import com.alphabetbloc.clinic.data.DbAdapter;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.tasks.ActivityLogTask;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.XformUtils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class CreatePatientActivity extends Activity implements OnGestureListener, SyncStatusObserver {
	public static final Integer PERMANENT_NEW_CLIENT = 1;
	public static final Integer TEMPORARY_NEW_CLIENT = 2;
	public static final String TAG = CreatePatientActivity.class.getSimpleName();
	public static final int PROGRESS_DIALOG = 1;
	
	private Context mContext;
	private String mFirstName;
	private String mMiddleName;
	private String mLastName;
	private String mPatientID;
	private String mSex;
	private String mEstimatedDob;

	private DatePicker mBirthDatePicker;
	private String mBirthString;
	private String mDbBirthString;
	private int mYear;
	private int mMonth;
	private int mDay;
	private Date mDbBirthDate;
	private Integer mCreateCode = null;

	// for launching the form into collect:
	private ActivityLog mActivityLog;
	private static final int REGISTRATION = 5;
	private static final int VERIFY_SIMILAR_CLIENTS = 6;
	private static String mProviderId;
	private static Patient mPatient;
	private static ProgressDialog mProgressDialog;
	private GestureDetector mGestureDetector;
	private static Object mSyncObserverHandle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.create_patient);
		mGestureDetector = new GestureDetector(this);
		mContext = this;

		// check to see if form exists in Collect Db
		String dbjrFormId = "no_registration_form";
		String registrationFormId = "-1";
		Cursor cursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, new String[] { FormsColumns.JR_FORM_ID }, FormsColumns.JR_FORM_ID + "=?", new String[] { registrationFormId }, null);
		if (cursor.moveToFirst()) {
			do {
				dbjrFormId = cursor.getString(cursor.getColumnIndex(FormsColumns.JR_FORM_ID));
			} while (cursor.moveToNext());

			if (cursor != null) {
				cursor.close();
			}
		}

		if (!dbjrFormId.equals(registrationFormId))
			XformUtils.insertRegistrationForm();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(getString(R.string.key_provider), "0");

		// get Birthday
		mBirthDatePicker = (DatePicker) findViewById(R.id.birthdate_widget);
		final Calendar c = Calendar.getInstance();
		mYear = c.get(Calendar.YEAR);
		mMonth = 1;
		mDay = 1;
		mBirthDatePicker.init(mYear, mMonth, mDay, null);

		Button btnSubmit = (Button) findViewById(R.id.btnSubmit);
		btnSubmit.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				// get the values
				getNamesAndId();
				getSex();
				getBirthDate();
				getPatientStatus();

				// check for missing data
				if (mFirstName.length() < 2 || mMiddleName.length() < 2 || mLastName.length() < 2) {
					Toast.makeText(CreatePatientActivity.this, "Incorrect Name. Do Not Use Abbreviations.  All names should be at least 2 letters long.", Toast.LENGTH_SHORT).show();
				} else if (mSex == null) {
					Toast.makeText(CreatePatientActivity.this, "Please enter Male or Female.", Toast.LENGTH_SHORT).show();
				} else if (mDbBirthDate.after(new Date(System.currentTimeMillis()))) {
					Toast.makeText(CreatePatientActivity.this, "Birthdate should not be in the future.", Toast.LENGTH_SHORT).show();
				} else if (mCreateCode == null) {
					Toast.makeText(CreatePatientActivity.this, "Please specify whether you wish to receive updates on this client in the future.", Toast.LENGTH_SHORT).show();
				} else if (similarClientCheck()) {
					Log.e("louis.fazen", "client is similar");
					// ask provider for verification
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(DashboardActivity.LIST_TYPE, DashboardActivity.LIST_SIMILAR_CLIENTS);
					i.putExtra("search_name_string", mFirstName + " " + mLastName);
					i.putExtra("search_id_string", mPatientID);
					startActivityForResult(i, VERIFY_SIMILAR_CLIENTS);
				} else {
					// Add client to db, create & save form to Collect
					addClientToDb();
					addFormToCollect();
				}

			}

		});

	}

	// LIFECYCLE
	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);
		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			mProgressDialog.show();
		}
	}
	
	private BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {

			Intent intent = new Intent(mContext, RefreshDataActivity.class);
			intent.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.ASK_TO_DOWNLOAD);
			startActivity(intent);

		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	
	
	@Override
	public void onStatusChanged(int which) {

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				checkSyncActivity();
			}
		});

	}

	public boolean checkSyncActivity() {
		boolean syncActive = false;
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));

		if (accounts.length <= 0)
			return false;

		syncActive = ContentResolver.isSyncActive(accounts[0], getString(R.string.app_provider_authority));

		if (syncActive) {

			showDialog(PROGRESS_DIALOG);

		} else {

			if (mProgressDialog != null) {
				mProgressDialog.dismiss();
			}
		}

		return syncActive;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
		mProgressDialog.setTitle(getString(R.string.sync_in_progress_title));
		mProgressDialog.setMessage(getString(R.string.sync_in_progress));
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		
		return mProgressDialog;
	}
	
	private boolean similarClientCheck() {
		// Verify the client against the db
		boolean similarFound = false;

		Cursor c = null;
		c = DbAdapter.openDb().fetchPatients(mFirstName + " " + mLastName, null, DashboardActivity.LIST_SIMILAR_CLIENTS);
		if (c != null && c.getCount() > 0) {
			similarFound = true;
		}

		if (!similarFound && mPatientID != null && mPatientID.length() > 3) {
			c = DbAdapter.openDb().fetchPatients(null, mPatientID, DashboardActivity.LIST_SIMILAR_CLIENTS);
			if (c != null && c.getCount() > 0) {
				similarFound = true;
			}
		}

		if (c != null) {
			c.close();
		}

		return similarFound;

	}

	private void startActivityLog(String formId, String formType) {
		String patientId = String.valueOf(mPatient.getPatientId());
		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(mProviderId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId(patientId);
		mActivityLog.setActivityStartTime();
		mActivityLog.setFormType(formType);
		mActivityLog.setFormPriority("false");

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

		if (resultCode == RESULT_CANCELED) {
			if (requestCode == VERIFY_SIMILAR_CLIENTS) {
				finish();
			} else {
				return;
			}
		} else if (resultCode == RESULT_OK) {

			if (requestCode == VERIFY_SIMILAR_CLIENTS) {
				addClientToDb();
				addFormToCollect();

			} else if (requestCode == REGISTRATION && intent != null) {
				SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
				if (settings.getBoolean("IsLoggingEnabled", true)) {
					mActivityLog.setActivityStopTime();
					new ActivityLogTask(mActivityLog).execute();
				}

				Uri u = intent.getData();
				String dbjrFormId = null;
				String displayName = null;
				String fileDbPath = null;
				String status = null;

				Cursor mCursor = App.getApp().getContentResolver().query(u, null, null, null, null);
				mCursor.moveToPosition(-1);
				while (mCursor.moveToNext()) {
					status = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.STATUS));
					dbjrFormId = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.JR_FORM_ID));
					displayName = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.DISPLAY_NAME));
					fileDbPath = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
				}
				if (mCursor != null) {
					mCursor.close();
				}

				if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {
					FormInstance fi = new FormInstance();
					fi.setPatientId(mPatient.getPatientId());
					fi.setFormId(Integer.parseInt(dbjrFormId));
					fi.setPath(fileDbPath);
					fi.setStatus(DbAdapter.STATUS_UNSUBMITTED);
					Date date = new Date();
					date.setTime(System.currentTimeMillis());
					String dateString = "Completed: " + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
					fi.setCompletionSubtext(dateString);

					DbAdapter.openDb().createFormInstance(fi, displayName);
				}

				loadNewClientView();

			}
			return;
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	private void loadNewClientView() {

		// load the newly created patient into ViewPatientActivity
		Intent ip = new Intent(getApplicationContext(), ViewPatientActivity.class);
		ip.putExtra(Constants.KEY_PATIENT_ID, mPatient.getPatientId().toString());
		startActivity(ip);

		// and quit
		finish();
	}

	private void getNamesAndId() {
		mFirstName = ((EditText) findViewById(R.id.first_edit)).getText().toString();
		mMiddleName = ((EditText) findViewById(R.id.middle_edit)).getText().toString();
		mLastName = ((EditText) findViewById(R.id.last_edit)).getText().toString();
		mPatientID = ((EditText) findViewById(R.id.id_edit)).getText().toString();

	}

	private void getSex() {
		// get Sex
		RadioGroup sexRadioGroup;
		CharSequence femaleRadio = "Female";
		CharSequence maleRadio = "Male";
		sexRadioGroup = (RadioGroup) findViewById(R.id.sex_radio);
		int sexRadioId = sexRadioGroup.getCheckedRadioButtonId();
		RadioButton radioSexButton = (RadioButton) findViewById(sexRadioId);
		if (radioSexButton != null) {
			Log.e("louis.fazen", "radiosexButton.getText()=" + radioSexButton.getText());
			if (radioSexButton.getText().equals(femaleRadio)) {
				mSex = "F";
			} else if (radioSexButton.getText().equals(maleRadio)) {
				mSex = "M";

			}
		}
	}

	private void getBirthDate() {
		// get BirthDate and BirthTime
		CheckBox estimatedDob = (CheckBox) findViewById(R.id.estimated_dob);
		if (estimatedDob.isChecked())
			mEstimatedDob = "1";
		else
			mEstimatedDob = "0";

		mYear = mBirthDatePicker.getYear();
		mMonth = mBirthDatePicker.getMonth();
		mDay = mBirthDatePicker.getDayOfMonth();

		// mHour = mBirthTime.getCurrentHour();
		// mMinute = mBirthTime.getCurrentMinute();

		mDbBirthString = String.valueOf(mYear) + "-" + String.valueOf(mMonth) + "-" + String.valueOf(mDay);
		SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat viewFormat = new SimpleDateFormat("MMM dd, yyyy");
		try {
			mDbBirthDate = dbFormat.parse(mDbBirthString);
			mBirthString = viewFormat.format(mDbBirthDate);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getPatientStatus() {
		// get patient status
		// temporary clients will not be added to the CHVs patient list on the
		// server side and may be deleted after the next download?
		// allows for getting rid of people who you do not regularly
		// see, or will only see one time
		RadioGroup tempPermRG;
		CharSequence permRadio = "Permanently: Keep Me Updated on This Client";
		CharSequence tempRadio = "For this visit only";
		tempPermRG = (RadioGroup) findViewById(R.id.temp_or_perm);
		int tempPermId = tempPermRG.getCheckedRadioButtonId();
		RadioButton tempPermButton = (RadioButton) findViewById(tempPermId);

		if (tempPermButton != null) {
			Log.e("louis.fazen", "radiosexButton.getText()=" + tempPermButton.getText());
			if (tempPermButton.getText().equals(permRadio)) {
				mCreateCode = PERMANENT_NEW_CLIENT;
			} else if (tempPermButton.getText().equals(tempRadio)) {
				mCreateCode = TEMPORARY_NEW_CLIENT;
			}
		}
	}

	private void addClientToDb() {
		mPatient = new Patient();

		int minPatientId = DbAdapter.openDb().findLastClientCreatedId();
		if (minPatientId < 0)
			mPatient.setPatientId(Integer.valueOf(minPatientId - 1));
		else
			mPatient.setPatientId(Integer.valueOf(-1));
		mPatient.setFamilyName(mLastName);
		mPatient.setMiddleName(mMiddleName);
		mPatient.setGivenName(mFirstName);
		mPatient.setGender(mSex);
		mPatient.setBirthDate(mBirthString);
		mPatient.setbirthEstimated(mEstimatedDob);
		mPatient.setDbBirthDate(mDbBirthString); // does not go into db

		if (mPatientID != null) {
			mPatient.setIdentifier(mPatientID);
		} else {
			mPatient.setIdentifier("Unknown");
		}

		mPatient.setCreateCode(mCreateCode);

		UUID uuid = UUID.randomUUID();
		String randomUUID = uuid.toString();
		mPatient.setUuid(randomUUID);

		DbAdapter.openDb().createPatient(mPatient);
	}

	private void addFormToCollect() {
		if (mPatient == null)
			Log.e(TAG, "Mpatient is null!?");
		int instanceId = XformUtils.createRegistrationFormInstance(mPatient);
		if (instanceId != -1) {
			Intent intent = new Intent();
			intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
			intent.setAction(Intent.ACTION_EDIT);
			intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
			intent.putExtra("Client_Registration", true);
			startActivityForResult(intent, REGISTRATION);
		} else {
			Toast.makeText(CreatePatientActivity.this, "Sorry, there was a problem saving this form.", Toast.LENGTH_SHORT).show();
		}

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog("-1", "Patient Registration");
		}

	}

	
	
	
	// TODO! delete this type of swipe action...
	@Override
	public boolean onTouchEvent(MotionEvent me) {
		return mGestureDetector.onTouchEvent(me);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent mv) {
		boolean handled = mGestureDetector.onTouchEvent(mv);
		if (!handled) {
			return super.dispatchTouchEvent(mv);
		}

		return handled; // this is always true
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

		InputMethodManager inputMM = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		// inputMM.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0); //
		// this version worked, but was slow...
		inputMM.hideSoftInputFromWindow(mBirthDatePicker.getWindowToken(), 0);
		return false;
	}

	@Override
	public boolean onDown(MotionEvent e) {
		// TODO Auto-generated method stub

		// InputMethodManager inputMM = (InputMethodManager)
		// mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
		// inputMM.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(),
		// InputMethodManager.HIDE_NOT_ALWAYS);
		return false;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		int xPixelLimit = (int) (dm.xdpi * .25);
		int yPixelLimit = (int) (dm.ydpi * .25);

		if ((Math.abs(e1.getX() - e2.getX()) > xPixelLimit && Math.abs(e1.getY() - e2.getY()) < yPixelLimit) || Math.abs(e1.getX() - e2.getX()) > xPixelLimit * 2) {
			if (velocityX > 0) {
				finish();
				return true;
			}
		}

		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onShowPress(MotionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}

}