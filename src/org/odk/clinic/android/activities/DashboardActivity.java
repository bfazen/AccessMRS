package org.odk.clinic.android.activities;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.tasks.UploadInstanceTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class DashboardActivity extends Activity implements UploadFormListener {

	// Menu ID's
	private static final int MENU_PREFERENCES = Menu.FIRST;

	// Request codes
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;

	private static final String DOWNLOAD_PATIENT_CANCELED_KEY = "downloadPatientCanceled";

	private Button mDownloadButton;
	private Button mCreateButton;

	private ClinicAdapter mCla;

	private ArrayList<String> mForms = new ArrayList<String>();

	private boolean mDownloadPatientCanceled = false;

	private UploadInstanceTask mUploadFormTask;

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

		mDownloadButton = (Button) findViewById(R.id.download_patients);
		mDownloadButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				uploadAllForms();
				updateAllPatients();
				downloadNewForms();

			}
		});

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

			}

		});

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

	// TODO: but this seems like the best way to do it...as he seems to be
	// inserting the FormInstance after the fact?!
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

		// TODO: TODO: louis.fazen jun 15, 2012 this would need to be changed
		// depending on whether the form is complete or not...
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

			// TODO: but this seems like the best way to do it...best he seems
			// to be inserting the FormInstance after the fact after a query to
			// the Collect Instances Db?!
			// TODO: if completed vs. if incomplete...
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

	private void refreshView() {

		if (mCla == null) {
			mCla = new ClinicAdapter();
		}

		// TODO: louis.fazen VERIFY why is mCla never closed?!
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
			refreshView();

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

	@Override
	public void uploadComplete(ArrayList<String> result) {
		// Auto-generated method stub

	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// Auto-generated method stub

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
}
