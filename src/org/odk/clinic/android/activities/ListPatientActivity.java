package org.odk.clinic.android.activities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.PatientAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.tasks.UpdateClockTask;
import org.odk.clinic.android.tasks.UploadInstanceTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

// TODO Display patient view data
// TODO Display ages instead of dates
// TODO Optimize download patient task

public class ListPatientActivity extends ListActivity implements UploadFormListener {

	// Menu ID's
	private static final int MENU_PREFERENCES = Menu.FIRST;

	// Request codes
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;

	// private static final int INSTANCE_UPLOADER = 0;

	private static final String DOWNLOAD_PATIENT_CANCELED_KEY = "downloadPatientCanceled";

	private Button mDownloadButton;
	private Button mCreateButton;
	// private Button mGetButton;
	// private Button mUploadButton;
	// private ImageButton mBarcodeButton;
	private EditText mSearchText;
	private TextWatcher mFilterTextWatcher;

	private ClinicAdapter mCla;

	private ArrayAdapter<Patient> mPatientAdapter;
	private ArrayList<Patient> mPatients = new ArrayList<Patient>();
	private ArrayList<String> mForms = new ArrayList<String>();

	private boolean mDownloadPatientCanceled = false;

	private UploadInstanceTask mUploadFormTask;

	/*
	 * (non-Javadoc)
	 * 
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey(DOWNLOAD_PATIENT_CANCELED_KEY)) {
				mDownloadPatientCanceled = savedInstanceState.getBoolean(DOWNLOAD_PATIENT_CANCELED_KEY);
			}
		}
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.find_patient);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.find_patient));

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			finish();
		}

		mFilterTextWatcher = new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (mPatientAdapter != null) {
					mPatientAdapter.getFilter().filter(s);
				}
			}

			@Override
			public void afterTextChanged(Editable s) {

			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}
		};

		mSearchText = (EditText) findViewById(R.id.search_text);
		mSearchText.addTextChangedListener(mFilterTextWatcher);
		/*
		 * mBarcodeButton = (ImageButton) findViewById(R.id.barcode_button);
		 * mBarcodeButton.setOnClickListener(new OnClickListener() { public void
		 * onClick(View v) { Intent i = new
		 * Intent("com.google.zxing.client.android.SCAN"); try {
		 * startActivityForResult(i, BARCODE_CAPTURE); } catch
		 * (ActivityNotFoundException e) { Toast t =
		 * Toast.makeText(getApplicationContext(), getString(R.string.error,
		 * getString(R.string.barcode_error)), Toast.LENGTH_SHORT);
		 * t.setGravity(Gravity.CENTER_VERTICAL, 0, 0); t.show(); } } });
		 */
		mDownloadButton = (Button) findViewById(R.id.download_patients);
		mDownloadButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				uploadAllForms();
				updateAllPatients();
				downloadNewForms();
				// Intent id = new Intent(getApplicationContext(),
				// DownloadPatientActivity.class);
				// startActivityForResult(id, DOWNLOAD_PATIENT);

			}
		});

		// mGetButton = (Button) findViewById(R.id.get_button);
		// mGetButton.setOnClickListener(new OnClickListener() {
		// public void onClick(View v) {
		//
		// downloadNewForms();
		// // Intent id = new Intent(getApplicationContext(),
		// DownloadPatientActivity.class);
		// // startActivityForResult(id, DOWNLOAD_PATIENT);
		//
		//
		// }
		// });

		mCreateButton = (Button) findViewById(R.id.create_patient);
		mCreateButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {

				// branch of id of form to fill...
				getDownloadedForms();
				if (mForms.size() > 0) {
					createFormsDialog();
				} else {
					showCustomToast(getString(R.string.no_forms));
				}

				// String patientIdStr = mPatient.getPatientId().toString();
				// Intent ip = new Intent(getApplicationContext(),
				// ListFormActivity.class);
				// ip.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
				// startActivity(ip);
			}

		});
		/*
		 * mUploadButton = (Button) findViewById(R.id.upload_button);
		 * mUploadButton.setOnClickListener(new OnClickListener() { public void
		 * onClick(View v) { Intent iu = new Intent(getApplicationContext(),
		 * InstanceUploaderList.class); startActivity(iu); } });
		 */

	}

	private void downloadNewForms() {
		Intent id = new Intent(getApplicationContext(), DownloadFormActivity.class);
		startActivity(id);
	}

	private void updateAllPatients() {
		Intent id = new Intent(getApplicationContext(), DownloadPatientActivity.class);
		startActivity(id);
	}

	private void uploadAllForms() {

		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
		}
		Cursor c = mCla.fetchFormInstancesByStatus(ClinicAdapter.STATUS_UNSUBMITTED);
		startManagingCursor(c);
		ArrayList<String> selectedInstances = new ArrayList<String>();

		if (c != null && c.getCount() > 0) {
			String s = c.getString(c.getColumnIndex(ClinicAdapter.KEY_PATH));
			selectedInstances.add(s);

			// bundle intent with upload files
			Intent i = new Intent(this, InstanceUploaderActivity.class);
			// i.putExtra(FormEntryActivity.KEY_INSTANCES, selectedInstances);
			i.putExtra(ClinicAdapter.KEY_INSTANCES, selectedInstances);
			startActivity(i);
		}

		if (c != null)
			c.close();

		mCla.close();

	}

	private void getDownloadedForms() {

		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
		}
		Cursor c = mCla.fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			mForms.clear();
			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);
			// int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);

			if (c.getCount() > 0) {
				do {
					if (!c.isNull(formIdIndex)) {
						mForms.add(c.getString(nameIndex) + " (" + c.getInt(formIdIndex) + ")");
					}
				} while (c.moveToNext());
			}
		}

		refreshView();

		if (c != null)
			c.close();

		mCla.close();
	}

	@Override
	protected void onListItemClick(ListView listView, View view, int position, long id) {
		// Get selected patient
		Patient p = (Patient) getListAdapter().getItem(position);
		String patientIdStr = p.getPatientId().toString();

		Intent ip = new Intent(getApplicationContext(), ViewPatientActivity.class);
		ip.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);
		startActivity(ip);
	}

	private void createFormsDialog() {

		final CharSequence[] items = mForms.toArray(new CharSequence[mForms.size()]);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Select a form to fill");

		builder.setSingleChoiceItems(items, -1, null);
		builder.setPositiveButton("Fill Form", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// extract form id
				int i = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
				String selected = null;
				if (i != -1) {
					Pattern p = Pattern.compile("\\([0-9]+\\)$");
					Matcher m = p.matcher(items[i]);
					while (m.find()) {
						selected = m.group(0).substring(1, m.group(0).length() - 1);
					}
					dialog.dismiss();
					launchFormEntry(selected);
				} else {
					dialog.dismiss();
				}

			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void launchFormEntry(String jrFormId) {

		int id = -1;
		try {
			Cursor mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				int dbid = mCursor.getInt(mCursor.getColumnIndex(FormsColumns._ID));
				String dbjrFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

				if (jrFormId.equalsIgnoreCase(dbjrFormId)) {
					id = dbid;
					break;
				}
			}
			if (mCursor != null) {
				mCursor.close();
			}

			if (id != -1) {

				Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, id);
				startActivityForResult(new Intent(Intent.ACTION_EDIT, formUri), FILL_BLANK_FORM);

			}

		} catch (ActivityNotFoundException e) {
			showCustomToast(getString(R.string.error, getString(R.string.odk_collect_error)));
		}
	}

	/*
	 * Customization by louis.fazen@gmail.com: ViewMenu based on
	 * ViewMenuPreference
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		Log.e("Dashboard", "OnCreateOptionsMenu called with IsMenuEnabled= " + settings.getBoolean("IsMenuEnabled", true));
		if (settings.getBoolean("IsMenuEnabled", true) == false) {
			return false;
		} else {
			menu.add(0, MENU_PREFERENCES, 0, getString(R.string.server_preferences)).setIcon(android.R.drawable.ic_menu_preferences);
			return true;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_PREFERENCES:
			Intent ip = new Intent(getApplicationContext(), PreferencesActivity.class);
			startActivity(ip);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

		if (resultCode == RESULT_CANCELED) {
			if (requestCode == DOWNLOAD_PATIENT) {
				mDownloadPatientCanceled = true;
			}
			return;
		}

		if (requestCode == BARCODE_CAPTURE && intent != null) {
			String sb = intent.getStringExtra("SCAN_RESULT");
			if (sb != null && sb.length() > 0) {
				mSearchText.setText(sb);
			}
		}

		if (requestCode == FILL_BLANK_FORM && intent != null) {

			Uri u = intent.getData();
			String dbjrFormId = null;
			String displayName = null;
			String filePath = null;

			Cursor mCursor = App.getApp().getContentResolver().query(u, null, null, null, null);
			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				dbjrFormId = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.JR_FORM_ID));
				displayName = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.DISPLAY_NAME));
				filePath = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
			}
			if (mCursor != null) {
				mCursor.close();
			}

			FormInstance fi = new FormInstance();
			fi.setPatientId(-1);
			fi.setFormId(Integer.parseInt(dbjrFormId));
			fi.setPath(filePath);
			fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);

			ClinicAdapter ca = new ClinicAdapter();
			ca.open();
			ca.createFormInstance(fi, displayName);
			ca.close();

		}

		super.onActivityResult(requestCode, resultCode, intent);

	}

	private void getPatients() {
		getPatients(null);
	}

	private void getPatients(String searchStr) {

		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = null;
		if (searchStr != null) {
			c = ca.fetchPatients(searchStr, searchStr);
		} else {
			c = ca.fetchAllPatients();

		}

		if (c != null && c.getCount() >= 0) {

			mPatients.clear();

			int patientIdIndex = c.getColumnIndex(ClinicAdapter.KEY_PATIENT_ID);
			int identifierIndex = c.getColumnIndex(ClinicAdapter.KEY_IDENTIFIER);
			int givenNameIndex = c.getColumnIndex(ClinicAdapter.KEY_GIVEN_NAME);
			int familyNameIndex = c.getColumnIndex(ClinicAdapter.KEY_FAMILY_NAME);
			int middleNameIndex = c.getColumnIndex(ClinicAdapter.KEY_MIDDLE_NAME);
			int birthDateIndex = c.getColumnIndex(ClinicAdapter.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(ClinicAdapter.KEY_GENDER);

			// TODO: louis.fazen check all the other occurrences of get and
			// setFamilyName and add get and set priority as well...
			int priorityIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NAMES);
			if (c.getCount() > 0) {

				Patient p;
				do {
					p = new Patient();
					p.setPatientId(c.getInt(patientIdIndex));
					p.setIdentifier(c.getString(identifierIndex));
					p.setGivenName(c.getString(givenNameIndex));
					p.setFamilyName(c.getString(familyNameIndex));
					p.setMiddleName(c.getString(middleNameIndex));
					p.setBirthDate(c.getString(birthDateIndex));
					p.setGender(c.getString(genderIndex));

					// TODO: louis.fazen check all the other occurrences of get
					// and setFamilyName and add get and set priority as well...
					p.setPriorityNumber(c.getInt(priorityIndex));
					p.setPriorityForms(c.getString(priorityFormIndex));

					if (c.getInt(priorityIndex) > 0) {

						p.setPriority(true);

					}

					mPatients.add(p);

				} while (c.moveToNext());
			}

		}

		// sort em
		// Collections.sort(mPatients, new Comparator<Patient>() {
		// public int compare(Patient p1, Patient p2) {
		// return p1.getName().compareTo(p2.getName());
		// }
		// });

		refreshView();

		if (c != null) {
			c.close();
		}
		ca.close();

	}

	private void refreshView() {

		if (mCla == null) {
			mCla = new ClinicAdapter();
		}
		mPatientAdapter = new PatientAdapter(this, R.layout.patient_list_item, mPatients);
		setListAdapter(mPatientAdapter);

	}

	@Override
	protected void onDestroy() {
		if (mUploadFormTask != null) {
			mUploadFormTask.setUploadListener(null);
			if (mUploadFormTask.getStatus() == AsyncTask.Status.FINISHED) {
				mUploadFormTask.cancel(true);
			}
		}

		super.onDestroy();
		mSearchText.removeTextChangedListener(mFilterTextWatcher);
		if (mCla != null) {
			mCla.close();
		}

	}

	@Override
	protected void onResume() {
		if (mUploadFormTask != null) {
			mUploadFormTask.setUploadListener(this);
		}
		super.onResume();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		boolean firstRun = settings.getBoolean(PreferencesActivity.KEY_FIRST_RUN, true);

		if (firstRun) {
			// Save first run status
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(PreferencesActivity.KEY_FIRST_RUN, false);
			editor.commit();

			// Start preferences activity
			Intent ip = new Intent(getApplicationContext(), PreferencesActivity.class);
			startActivity(ip);

		} else {
			getPatients();

			// if (mPatients.isEmpty() && !mDownloadPatientCanceled) {
			// // Download patients if no patients are in db
			// Intent id = new Intent(getApplicationContext(),
			// DownloadPatientActivity.class);
			// startActivityForResult(id, DOWNLOAD_PATIENT);
			// } else {
			// // hack to trigger refilter
			// mSearchText.setText(mSearchText.getText().toString());
			// }
			mSearchText.setText(mSearchText.getText().toString());
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putBoolean(DOWNLOAD_PATIENT_CANCELED_KEY, mDownloadPatientCanceled);
	}

	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_SHORT);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}

	@Override
	public void uploadComplete(ArrayList<String> result) {
		// TODO Auto-generated method stub

	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// TODO Auto-generated method stub

	}
}
