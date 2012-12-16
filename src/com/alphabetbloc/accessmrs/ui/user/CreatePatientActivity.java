package com.alphabetbloc.accessmrs.ui.user;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
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

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI;
import com.alphabetbloc.accessforms.provider.FormsProviderAPI.FormsColumns;
import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.data.ActivityLog;
import com.alphabetbloc.accessmrs.data.FormInstance;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.tasks.ActivityLogTask;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.XformUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class CreatePatientActivity extends BaseUserActivity implements OnGestureListener {
	public static final Integer PERMANENT_NEW_CLIENT = 1;
	public static final Integer TEMPORARY_NEW_CLIENT = 2;
	public static final String MOST_RECENT_NEW_CLIENT_ID = "most_recent_new_client_id";
	private static final String TAG = CreatePatientActivity.class.getSimpleName();

	private Context mContext;
	private String mFirstName;
	private String mMiddleName;
	private String mLastName;
	private String mPatientIdentifier;
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

	// for launching the form into AccessForms:
	private ActivityLog mActivityLog;
	private static final int REGISTRATION = 5;
	private static final int VERIFY_SIMILAR_CLIENTS = 6;
	private static String mProviderId;
	private static Patient mPatient;
	private GestureDetector mGestureDetector;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.create_patient);
		mGestureDetector = new GestureDetector(this);
		mContext = this;

		getRegistrationForm();
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(getString(R.string.key_provider), getString(R.string.default_provider));

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
					if (App.DEBUG)
						Log.v(TAG, "client is similar");
					// ask provider for verification
					Intent i = new Intent(mContext, ListPatientActivity.class);
					i.putExtra(DashboardActivity.LIST_TYPE, DashboardActivity.LIST_SIMILAR_CLIENTS);
					i.putExtra("search_name_string", mFirstName + " " + mLastName);
					i.putExtra("search_id_string", mPatientIdentifier);
					startActivityForResult(i, VERIFY_SIMILAR_CLIENTS);
				} else {
					// Add client to db, create & save form to AccessForms
					addClientToAccessMrs();
					addFormToAccessForms();
				}

			}

		});

	}

	@Override
	protected void refreshView() {
		// do nothing
	}

	private void getRegistrationForm(){
		// check to see if form exists in AccessForms Db
		String dbjrFormName = "no_registration_form";
		Cursor cursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, new String[] { FormsColumns.DISPLAY_NAME }, FormsColumns.DISPLAY_NAME + "=?", new String[] { XformUtils.CLIENT_REGISTRATION_FORM_NAME }, null);
		if (cursor.moveToFirst()) {
			do {
				dbjrFormName = cursor.getString(cursor.getColumnIndex(FormsColumns.DISPLAY_NAME));
			} while (cursor.moveToNext());

			if (cursor != null) {
				cursor.close();
			}
		}

		if (!dbjrFormName.equals(XformUtils.CLIENT_REGISTRATION_FORM_NAME))
			XformUtils.insertRegistrationForm();
	}
	
	private boolean similarClientCheck() {
		// Verify the client against the db
		boolean similarFound = false;

		Cursor c = null;
		c = Db.open().searchPatients(mFirstName + " " + mLastName, null, DashboardActivity.LIST_SIMILAR_CLIENTS);
		if (c != null && c.getCount() > 0) {
			similarFound = true;
		}

		if (!similarFound && mPatientIdentifier != null && mPatientIdentifier.length() > 3) {
			c = Db.open().searchPatients(null, mPatientIdentifier, DashboardActivity.LIST_SIMILAR_CLIENTS);
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
			if (requestCode == VERIFY_SIMILAR_CLIENTS)
				finish();
			else
				return;

		} else if (resultCode == RESULT_OK) {

			if (requestCode == VERIFY_SIMILAR_CLIENTS) {
				addClientToAccessMrs();
				addFormToAccessForms();

			} else if (requestCode == REGISTRATION && intent != null) {
				addInstanceToAccessMrs(intent.getData());
				loadNewClientView();
			}
			return;
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	private void addInstanceToAccessMrs(Uri u) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		boolean logActivity = prefs.getBoolean(getString(R.string.key_enable_activity_log), true);
		if (logActivity) {
			new ActivityLogTask(mActivityLog).execute();
		}

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
			fi.setStatus(DataModel.STATUS_UNSUBMITTED);
			Date date = new Date();
			date.setTime(System.currentTimeMillis());
			String dateString = "Completed: " + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
			fi.setCompletionSubtext(dateString);

			Db.open().createFormInstance(fi, displayName);
		}

	}

	private void loadNewClientView() {

		// load the newly created patient into ViewPatientActivity
		Intent ip = new Intent(getApplicationContext(), ViewPatientActivity.class);
		ip.putExtra(BasePatientListActivity.KEY_PATIENT_ID, mPatient.getPatientId().toString());
		startActivity(ip);

		// and quit
		finish();
	}

	private void getNamesAndId() {
		mFirstName = ((EditText) findViewById(R.id.first_edit)).getText().toString();
		mMiddleName = ((EditText) findViewById(R.id.middle_edit)).getText().toString();
		mLastName = ((EditText) findViewById(R.id.last_edit)).getText().toString();
		mPatientIdentifier = ((EditText) findViewById(R.id.id_edit)).getText().toString();

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
			if (App.DEBUG)
				Log.v(TAG, "radiosexButton.getText()=" + radioSexButton.getText());
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
			if (App.DEBUG)
				Log.v(TAG, "radiosexButton.getText()=" + tempPermButton.getText());
			if (tempPermButton.getText().equals(permRadio)) {
				mCreateCode = PERMANENT_NEW_CLIENT;
			} else if (tempPermButton.getText().equals(tempRadio)) {
				mCreateCode = TEMPORARY_NEW_CLIENT;
			}
		}
	}

	private void addClientToAccessMrs() {

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		int lastNewPatientId = prefs.getInt(MOST_RECENT_NEW_CLIENT_ID, -1);
		int currentMinPatientId = Db.open().findLastClientCreatedId();
		Integer newPatientId = ((lastNewPatientId <= currentMinPatientId) ? lastNewPatientId : currentMinPatientId) - 1;
		prefs.edit().putInt(MOST_RECENT_NEW_CLIENT_ID, newPatientId).commit();

		mPatient = new Patient();
		mPatient.setPatientId(newPatientId);
		mPatient.setFamilyName(mLastName);
		mPatient.setMiddleName(mMiddleName);
		mPatient.setGivenName(mFirstName);
		mPatient.setGender(mSex);
		mPatient.setBirthDate(mBirthString);
		mPatient.setbirthEstimated(mEstimatedDob);
		mPatient.setDbBirthDate(mDbBirthString); // does not go into db

		if (mPatientIdentifier != null) {
			mPatient.setIdentifier(mPatientIdentifier);
		} else {
			mPatient.setIdentifier("Unknown");
		}

		mPatient.setCreateCode(mCreateCode);

		UUID uuid = UUID.randomUUID();
		String randomUUID = uuid.toString();
		mPatient.setUuid(randomUUID);

		Db.open().createPatient(mPatient);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	private void addFormToAccessForms() {
		if (mPatient == null)
			if (App.DEBUG) Log.e(TAG, "Lost a patient after adding them to AccessForms");
		int instanceId = XformUtils.createRegistrationFormInstance(mPatient);
		if (instanceId != -1) {
			Intent intent = new Intent();
			intent.setComponent(new ComponentName("com.alphabetbloc.accessforms", "org.odk.collect.android.activities.FormEntryActivity"));
			intent.setAction(Intent.ACTION_EDIT);
			intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
			intent.putExtra("Client_Registration", true);
			startActivityForResult(intent, REGISTRATION);
		} else {
			Toast.makeText(CreatePatientActivity.this, "Sorry, there was a problem saving this form.", Toast.LENGTH_SHORT).show();
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		boolean logActivity = prefs.getBoolean(getString(R.string.key_enable_activity_log), true);
		if (logActivity)
			startActivityLog("-1", "Patient Registration");

	}

	// TODO UI: Change Swipe to ViewPager with animation...
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
		// Do Nothing
	}

	@Override
	public void onShowPress(MotionEvent e) {
		// Do Nothing
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		// Do Nothing
		return false;
	}

}