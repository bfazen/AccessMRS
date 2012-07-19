package org.odk.clinic.android.activities;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.tasks.ActivityLogTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class CreatePatientActivity extends Activity {
	public static final Integer PERMANENT_NEW_CLIENT = 1;
	public static final Integer TEMPORARY_NEW_CLIENT = 2;

	private Context mContext;
	private String mFirstName;
	private String mMiddleName;
	private String mLastName;
	private String mPatientID;
	private String mSex;
	private String mEstimatedDob;

	private DatePicker mBirthDatePicker;
	// private TimePicker mBirthTime;
	private String mBirthString;
	private String mDbBirthString;
	private int mYear;
	private int mMonth;
	private int mDay;
	// private int mHour;
	// private int mMinute;
	private Date mDbBirthDate;
	private Integer mCreateCode = null;

	// for launching the form into collect:
	private ActivityLog mActivityLog;
	private static final int REGISTRATION = 5;
	private static final String REGISTRATION_FORM_PATH = "/mnt/sdcard/odk/clinic/forms/patient_registration.xml";

	// brought in from Yaw's ViewPatient and my FormPriorityList
	private static final DateFormat COLLECT_INSTANCE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

	private static Element mFormNode;
	private static String mProviderId;
	private static Patient mPatient;
	private static HashMap<String, String> mInstanceValues = new HashMap<String, String>();
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.create_patient);

		// check to see if form exists in Collect Db
		String dbjrFormId = "no_registration_form";
		Cursor cursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, new String[] { FormsColumns.JR_FORM_ID }, FormsColumns.JR_FORM_ID + "=?", new String[] { "-1" }, null);
		if (cursor.moveToFirst()) {
			do {
				// if registration form does not exist, then create it
				dbjrFormId = cursor.getString(cursor.getColumnIndex(FormsColumns.JR_FORM_ID));
//				String neg = "-1";
//				if (dbjrFormId.equals(neg)) {
//
//				}
			} while (cursor.moveToNext());

			if (cursor != null) {
				cursor.close();
			}
		}
		Log.e("louis.fazen", "dbjrFormId=" + dbjrFormId);
		String neg = "-1";
		if (!dbjrFormId.equals(neg)) {
			insertSingleForm(REGISTRATION_FORM_PATH);
			Log.e("louis.fazen", "insertSingleForm is called");
		}
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");

		// get Birthday
		mBirthDatePicker = (DatePicker) findViewById(R.id.birthdate_widget);
		final Calendar c = Calendar.getInstance();
		mYear = c.get(Calendar.YEAR);
		mMonth = 1;
		mDay = 1;
		mBirthDatePicker.init(mYear, mMonth, mDay, null);

		// mBirthTime = (TimePicker) findViewById(R.id.birthtime_widget);
		// mBirthTime.setCurrentHour(0);
		// mBirthTime.setCurrentMinute(0);

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
				} else {

					// Data is OK: add client to db, create form, save to
					// Collect
					addClientToDb();
					int instanceId = createFormInstance();
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

			}

		});

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
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			mActivityLog.setActivityStopTime();
			new ActivityLogTask(mActivityLog).execute();
		}

		if (resultCode == RESULT_CANCELED) {
			return;
		}

		if (resultCode == RESULT_OK) {

			if (requestCode == REGISTRATION && intent != null) {

				Uri u = intent.getData();
				String dbjrFormId = null;
				String displayName = null;
				String filePath = null;
				String status = null;

				Cursor mCursor = App.getApp().getContentResolver().query(u, null, null, null, null);
				mCursor.moveToPosition(-1);
				while (mCursor.moveToNext()) {
					status = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.STATUS));
					dbjrFormId = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.JR_FORM_ID));
					displayName = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.DISPLAY_NAME));
					filePath = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
					Log.e("lef-onActivityResult", "mCursor status:" + status + " FormId:" + dbjrFormId + " displayName:" + displayName + " filepath:" + filePath);
				}
				if (mCursor != null) {
					mCursor.close();
				}

				if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {
					Log.e("lef-onActivityResult", "mCursor status:" + status + "=" + InstanceProviderAPI.STATUS_COMPLETE);
					FormInstance fi = new FormInstance();
					fi.setPatientId(mPatient.getPatientId());
					fi.setFormId(Integer.parseInt(dbjrFormId));
					fi.setPath(filePath);
					fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);
					Date date = new Date();
					date.setTime(System.currentTimeMillis());
					String dateString = "Completed: " + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
					fi.setCompletionSubtext(dateString);

					ClinicAdapter ca = new ClinicAdapter();
					ca.open();
					ca.createFormInstance(fi, displayName);
					ca.close();
				}
			}

			loadNewClientView();
			return;
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	private void loadNewClientView() {
		// load the newly created patient into ViewPatientActivity
		Intent ip = new Intent(getApplicationContext(), ViewPatientActivity.class);
		ip.putExtra(Constants.KEY_PATIENT_ID, mPatient.getPatientId().toString());
		Log.e("louis.fazen", "createpatient.mpatient.getPatientId=" + mPatient.getPatientId());
		ip.putExtra(Constants.KEY_UUID, mPatient.getUuid());
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

		ClinicAdapter ca = new ClinicAdapter();
		ca.open();

		int pId = ca.findLastClientCreatedId() - 1;
		Log.e("louis.fazen", "piD=" + ca.findLastClientCreatedId());
		Log.e("louis.fazen", "piD=" + pId);
		Log.e("louis.fazen", "integer.valueOf piD=" + Integer.valueOf(pId));
		mPatient.setPatientId(Integer.valueOf(pId));
		mPatient.setFamilyName(mLastName);
		mPatient.setMiddleName(mMiddleName);
		mPatient.setGivenName(mFirstName);
		mPatient.setGender(mSex);
		mPatient.setBirthDate(mBirthString);
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

		ca.createPatient(mPatient);
		ca.close();
	}

	// create a HasMap of mInstanceValues... but I know everything that should
	// go into it... so maybe do not need to do that?
	private static void prepareInstanceValues() {

		for (int i = 0; i < mObservations.size(); i++) {
			Observation o = mObservations.get(i);
			mInstanceValues.put(o.getFieldName(), o.toString());

		}
	}

	private static int createFormInstance() {

		String jrFormId = "-1";
		// reading the form
		Document doc = new Document();
		KXmlParser formParser = new KXmlParser();
		try {
			formParser.setInput(new FileReader(REGISTRATION_FORM_PATH));
			doc.parse(formParser);
		} catch (Exception e) {
			e.printStackTrace();
		}

		findFormNode(doc.getRootElement());
		if (mFormNode != null) {
			prepareInstanceValues();
			traverseInstanceNodes(mFormNode);
		} else {
			return -1;
		}

		// writing the instance file
		File formFile = new File(REGISTRATION_FORM_PATH);
		String formFileName = formFile.getName();
		String instanceName = "";
		if (formFileName.endsWith(".xml")) {
			instanceName = formFileName.substring(0, formFileName.length() - 4);
		} else {
			instanceName = jrFormId;
		}
		instanceName = instanceName + "_" + COLLECT_INSTANCE_NAME_DATE_FORMAT.format(new Date());

		String instancePath = FileUtils.INSTANCES_PATH + instanceName;
		(new File(instancePath)).mkdirs();
		String instanceFilePath = instancePath + "/" + instanceName + ".xml";
		File instanceFile = new File(instanceFilePath);

		KXmlSerializer instanceSerializer = new KXmlSerializer();
		try {
			instanceFile.createNewFile();
			FileWriter instanceWriter = new FileWriter(instanceFile);
			instanceSerializer.setOutput(instanceWriter);
			mFormNode.write(instanceSerializer);
			instanceSerializer.endDocument();
			instanceSerializer.flush();
			instanceWriter.close();

			// register into content provider
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.DISPLAY_NAME, mPatient.getGivenName() + " " + mPatient.getFamilyName());
			insertValues.put(InstanceColumns.INSTANCE_FILE_PATH, instanceFilePath);
			insertValues.put(InstanceColumns.JR_FORM_ID, jrFormId);

			// louis.fazen is adding new variables to Instances.Db
			insertValues.put(InstanceColumns.PATIENT_ID, mPatient.getPatientId());
			insertValues.put(InstanceColumns.FORM_NAME, "PatientRegistrationForm");
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_COMPLETE);

			Uri insertResult = App.getApp().getContentResolver().insert(InstanceColumns.CONTENT_URI, insertValues);
			return Integer.valueOf(insertResult.getLastPathSegment());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

	private static void findFormNode(Element element) {

		// loop through all the children of this element
		for (int i = 0; i < element.getChildCount(); i++) {

			Element childElement = element.getElement(i);
			if (childElement != null) {

				String childName = childElement.getName();
				if (childName.equalsIgnoreCase("form")) {
					mFormNode = childElement;
				}

				if (childElement.getChildCount() > 0) {
					findFormNode(childElement);
				}
			}
		}

	}

	private static void traverseInstanceNodes(Element element) {

		// extract 'WEIGHT (KG)' from '5089^WEIGHT (KG)^99DCT'
		Pattern pattern = Pattern.compile("\\^.*\\^");
		// loop through all the children of this element
		for (int i = 0; i < element.getChildCount(); i++) {

			Element childElement = element.getElement(i);
			if (childElement != null) {

				String childName = childElement.getName();

				// patient id
				if (childName.equalsIgnoreCase("patient.patient_id")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getPatientId().toString());
				}
				if (childName.equalsIgnoreCase("patient.birthdate")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getDbBirthDate().toString());
				}
				if (childName.equalsIgnoreCase("patient.family_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getFamilyName().toString());
				}
				if (childName.equalsIgnoreCase("patient.given_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getGivenName().toString());
				}

				if (childName.equalsIgnoreCase("patient.middle_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getMiddleName().toString());

				}
				if (childName.equalsIgnoreCase("patient.sex")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getGender().toString());

				}
				if (childName.equalsIgnoreCase("patient.medical_record_number")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getIdentifier().toString());

				}

				// provider id
				if (childName.equalsIgnoreCase("encounter.provider_id")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mProviderId.toString());
				}

				if (childName.equalsIgnoreCase("date") || childName.equalsIgnoreCase("time")) {
					childElement.clear();

				}

				// value node
				if (childName.equalsIgnoreCase("value")) {

					childElement.clear();

					// parent of value node
					Element parentElement = ((Element) childElement.getParent());
					String parentConcept = parentElement.getAttributeValue("", "openmrs_concept");

					// match the text inside ^^
					String match = null;
					Matcher matcher = pattern.matcher(parentConcept);
					while (matcher.find()) {
						match = matcher.group(0).substring(1, matcher.group(0).length() - 1);
					}

					// write value into value n
					String value = mInstanceValues.get(match);
					if (value != null) {
						childElement.addChild(0, org.kxml2.kdom.Node.TEXT, value.toString());
					}
				}

				if (childElement.getChildCount() > 0) {
					traverseInstanceNodes(childElement);
				}
			}
		}
	}

	// taken from DownLoadFormTask (which is an Async Task)
	private boolean insertSingleForm(String formPath) {

		ContentValues values = new ContentValues();
		File addMe = new File(formPath);

		// Ignore invisible files that start with periods.
		if (!addMe.getName().startsWith(".") && (addMe.getName().endsWith(".xml") || addMe.getName().endsWith(".xhtml"))) {
			Log.e("louis.fazen", "make a document");
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			org.w3c.dom.Document document = null;

			try {
				documentBuilder = documentBuilderFactory.newDocumentBuilder();
				document = documentBuilder.parse(addMe);
				document.normalize();
			} catch (Exception e) {
				Log.e("DownloadFormTask", e.getLocalizedMessage());
			}

			String name = null;
			int id = -1;
			String version = null;
			String md5 = FileUtils.getMd5Hash(addMe);

			NodeList form = document.getElementsByTagName("form");
			NamedNodeMap nodemap = form.item(0).getAttributes();
			for (int i = 0; i < nodemap.getLength(); i++) {
				Attr attribute = (Attr) nodemap.item(i);
				if (attribute.getName().equalsIgnoreCase("id")) {
					id = Integer.valueOf(attribute.getValue());
				}
				if (attribute.getName().equalsIgnoreCase("version")) {
					version = attribute.getValue();
				}
				if (attribute.getName().equalsIgnoreCase("name")) {
					name = attribute.getValue();
				}
			}

			values.put(FormsColumns.DISPLAY_NAME, "Client Registration Form");
			values.put(FormsColumns.JR_FORM_ID, "-1");
			values.put(FormsColumns.FORM_FILE_PATH, addMe.getAbsolutePath());
			
			boolean alreadyExists = false;

			Cursor mCursor;
			try {
				mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
			} catch (SQLiteException e) {
				Log.e("DownloadFormTask", e.getLocalizedMessage());
				return false;
				// TODO: handle exception
			}

			if (mCursor == null) {
				System.out.println("Something bad happened");
				ClinicAdapter ca = new ClinicAdapter();
				ca.open();
				ca.deleteAllForms();
				ca.close();
				return false;
			}

			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {
				Log.e("louis.fazen", "insertsingleform... move to next");
				String dbmd5 = mCursor.getString(mCursor.getColumnIndex(FormsColumns.MD5_HASH));
				String dbFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

				// if the exact form exists, leave it be. else, insert it.
				if (dbmd5.equalsIgnoreCase(md5) && dbFormId.equalsIgnoreCase(id + "")) {
					Log.e("louis.fazen", "insertsingleform... alreadyexists");
					alreadyExists = true;
				}

			}

			if (!alreadyExists) {
				Log.e("louis.fazen", "insertsingleform... !alreadyexists");
				App.getApp().getContentResolver().delete(FormsColumns.CONTENT_URI, "md5Hash=?", new String[] { md5 });
				App.getApp().getContentResolver().delete(FormsColumns.CONTENT_URI, "jrFormId=?", new String[] { id + "" });
				App.getApp().getContentResolver().insert(FormsColumns.CONTENT_URI, values);
			}

			if (mCursor != null) {
				mCursor.close();
			}

		}
		Log.e("louis.fazen", "return true");
		return true;

	}

	private String getNameFromId(Integer id) {
		String formName = null;
		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		Cursor c = ca.fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);

			if (c.getCount() > 0) {
				do {
					if (c.getInt(formIdIndex) == id) {
						formName = c.getString(nameIndex);
						break;
					}
				} while (c.moveToNext());
			}
		}

		if (c != null)
			c.close();

		ca.close();
		return formName;
	}

	// private static String parseDate(String s) {
	// SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy");
	// SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
	// try {
	// return outputFormat.format(inputFormat.parse(s));
	// } catch (ParseException e) {
	// return "";
	// }
	// }

}