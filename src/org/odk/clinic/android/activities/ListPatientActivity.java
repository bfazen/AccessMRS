package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.PatientAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.utilities.FileUtils;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

// TODO Display patient view data
// TODO Display ages instead of dates
// TODO Optimize download patient task
//TODO: louis.fazen: ha! just seeing this right now on June 16th!  I think this is all the stuff I have been working on... TODO: delete Yaw's To Dos!

public class ListPatientActivity extends ListActivity implements UploadFormListener {

	// Menu ID's
	private static final int MENU_PREFERENCES = Menu.FIRST;

	// Request codes
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;

	private EditText mSearchText;
	private TextWatcher mFilterTextWatcher;
	private ClinicAdapter mCla;
	private ArrayAdapter<Patient> mPatientAdapter;
	private ArrayList<Patient> mPatients = new ArrayList<Patient>();

	public static int mListType;
	private Context mContext;

	private String mSearchPatientStr = null;
	private String mSearchPatientId = null;
	private Button mSimilarClientButton;
	private LinearLayout mSearchBar;
	private ImageButton mAddClientButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.find_patient);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.find_patient));

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			finish();
		}
		// get intents
		mListType = getIntent().getIntExtra(DashboardActivity.LIST_TYPE, 1);
		if (mListType == DashboardActivity.LIST_SIMILAR_CLIENTS) {
			mSearchPatientStr = getIntent().getStringExtra("search_name_string");
			mSearchPatientId = getIntent().getStringExtra("search_id_string");
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

		mAddClientButton = (ImageButton) findViewById(R.id.add_client);
		mAddClientButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				Intent i = new Intent(mContext, CreatePatientActivity.class);
				startActivity(i);

			}
		});

		// Verify Similar Clients on Add New Client
		mSimilarClientButton = (Button) findViewById(R.id.similar_client_button);
		mSearchBar = (LinearLayout) findViewById(R.id.searchholder);
		mSimilarClientButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				setResult(RESULT_OK);
				finish();
			}
		});
		mSimilarClientButton.setVisibility(View.GONE);
	}

	@Override
	protected void onListItemClick(ListView listView, View view, int position, long id) {
		// Get selected patient
		Patient p = (Patient) getListAdapter().getItem(position);
		String patientIdStr = p.getPatientId().toString();

		Intent ip = new Intent(getApplicationContext(), ViewPatientActivity.class);
		ip.putExtra(Constants.KEY_PATIENT_ID, patientIdStr);

		// for client created patients, use the UUID to identify them...
		if (p.getPatientId() < 0) {
			String uuidStr = p.getUuid();
			ip.putExtra(Constants.KEY_UUID, uuidStr);
		}

		startActivity(ip);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
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

	private void findPatients() {

		if (mSearchPatientStr == null && mSearchPatientId == null) {
			mPatients.clear();
			getPatients(null, null);
		} else {
			mPatients.clear();
			if (mSearchPatientStr != null) {

				getPatients(mSearchPatientStr, null);
			}
			if (mSearchPatientId != null && mSearchPatientId.length() > 3) {

				getPatients(null, mSearchPatientId);
			}
		}

		refreshView();
	}

	private void getPatients(String searchString, String patientId) {

		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = null;
		if (mSearchPatientStr != null || mSearchPatientId != null) {
			
			c = ca.fetchPatients(searchString, patientId, mListType);
		} else {
			c = ca.fetchAllPatients(mListType);
		}

		if (c != null && c.getCount() >= 0) {

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
			int savedIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_SAVED_FORM_NUMBER);
			int savedFormIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_SAVED_FORM_NAMES);
			int uuidIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_UUID);

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
					p.setSavedNumber(c.getInt(savedIndex));
					p.setSavedForms(c.getString(savedFormIndex));
					p.setUuid(c.getString(savedFormIndex));

					if (c.getInt(priorityIndex) > 0) {
						p.setPriority(true);
					} else {
						p.setPriority(false);
					}

					if (c.getInt(savedIndex) > 0) {
						p.setSaved(true);
					} else {
						p.setSaved(false);
					}

					mPatients.add(p);

				} while (c.moveToNext());
			}

		}

		if (c != null) {
			c.close();
		}
		ca.close();
	}

	private void refreshView() {

		RelativeLayout listLayout = (RelativeLayout) findViewById(R.id.list_type);
		TextView listText = (TextView) findViewById(R.id.name_text);
		ImageView listIcon = (ImageView) findViewById(R.id.section_image);

		switch (mListType) {
		case DashboardActivity.LIST_SUGGESTED:
			listLayout.setBackgroundResource(R.color.priority);
			listIcon.setBackgroundResource(R.drawable.ic_priority);
			listText.setText(R.string.suggested_clients_section);
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listLayout.setBackgroundResource(R.color.saved);
			listIcon.setBackgroundResource(R.drawable.ic_saved);
			listText.setText(R.string.incomplete_clients_section);
			break;
		case DashboardActivity.LIST_COMPLETE:
			listLayout.setBackgroundResource(R.color.completed);
			listIcon.setBackgroundResource(R.drawable.ic_completed);
			listText.setText(R.string.completed_clients_section);
			break;
		case DashboardActivity.LIST_SIMILAR_CLIENTS:
			listLayout.setBackgroundResource(R.color.priority);
			listIcon.setBackgroundResource(R.drawable.ic_priority);
			String similarClient;
			if (mPatients.size() < 2) {
				similarClient = getString(R.string.similar_client_section);
			} else {
				similarClient = getString(R.string.similar_clients_section);
			}
			listText.setText(String.valueOf(mPatients.size()) + " " + similarClient);
			mSearchText.setVisibility(View.GONE);
			mAddClientButton.setVisibility(View.GONE);
			mSearchBar.setVisibility(View.GONE);
			mSimilarClientButton.setVisibility(View.VISIBLE);

			break;
		case DashboardActivity.LIST_ALL:
			listLayout.setBackgroundResource(R.color.dark_gray);
			listIcon.setBackgroundResource(R.drawable.ic_additional);
			listText.setText(R.string.all_clients_section);
			break;
		}

		if (mCla == null) {
			mCla = new ClinicAdapter();
		}
		mPatientAdapter = new PatientAdapter(this, R.layout.patient_list_item, mPatients);
		setListAdapter(mPatientAdapter);

		// TODO: louis.fazen VERIFY why is mCla never closed?!
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
		mSearchText.removeTextChangedListener(mFilterTextWatcher);
		if (mCla != null) {
			mCla.close();
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		findPatients();
		mSearchText.setText(mSearchText.getText().toString());
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
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
		// Auto-generated method stub

	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// Auto-generated method stub

	}
}
