package org.odk.clinic.android.activities;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.FormAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.adapters.ObservationAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 */

public class FormPriorityList extends ListActivity {

	
	private ArrayList<Form> mTotalForms = new ArrayList<Form>();

	// from ViewPatientAcitivity Class from Yaw:
	private static Patient mPatient;
	private static String mProviderId;
	private static HashMap<String, String> mInstanceValues = new HashMap<String, String>();
	private static final DateFormat COLLECT_INSTANCE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	private static final String TAG = "FormPriorityList";
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();
	private static Element mFormNode;
	private Context mContext;


	// TODO: if I am going to be using this class... I need to go back and delete all the classes and other things I created for the info_dialog...!
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		setContentView(R.layout.example_cw_main);

		if (!FileUtils.storageReady()) {
			Toast.makeText(this, getString(R.string.error, R.string.storage_error), Toast.LENGTH_SHORT).show();
			finish();
		}

		getDownloadedForms();
		
		String patientIdStr = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");
	
	}


	private FormAdapter buildFormsList(Form[] forms, boolean priority) {
		ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(forms));
		Collections.shuffle(formList);
		FormAdapter formAdapter = new FormAdapter(this, android.R.layout.simple_list_item_1, formList, priority);
		return (formAdapter);
	}
	private View buildSectionLabel(String sectionlabel) {
		View v;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if(sectionlabel == getString(R.string.priority_form_section)){
			v = vi.inflate(R.layout.priority_label, null);
		}
		else if(sectionlabel == getString(R.string.nonpriority_form_section)){
			v = vi.inflate(R.layout.nonpriority_label, null);
		}
		else {
			v = vi.inflate(R.layout.default_form_label, null);
		}

		TextView textView = (TextView) v.findViewById(R.id.name_text);
		textView.setText(sectionlabel);		
		
		return (v);
	}


	protected void onListItemClick(ListView listView, View view, int position, long id) {
		Form f = (Form) getListAdapter().getItem(position);
		String formIdStr = f.getFormId().toString();
		Toast.makeText(this, formIdStr, Toast.LENGTH_SHORT).show();
		Log.d("MergeAdapterDemo", String.valueOf(position));
		launchFormEntry(formIdStr);
	}
	
	// method excerpted from the ViewPatientActivity
	private void getDownloadedForms() {

		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			mTotalForms.clear();
			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(formIdIndex)) {
						form = new Form();
						form.setFormId(c.getInt(formIdIndex));
						form.setName(c.getString(nameIndex));
						form.setPath(c.getString(pathIndex));
						mTotalForms.add(form);
					}
				} while (c.moveToNext());
			}
		}

		if (c != null)
			c.close();

		ca.close();
	}

	// below this line is all excerpted from the ViewPatientActivity

	private static String parseDate(String s) {
		SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
		try {
			return outputFormat.format(inputFormat.parse(s));
		} catch (ParseException e) {
			return "";
		}
	}

	private static void traverseInstanceNodes(Element element) {

		// extract 'WEIGHT (KG)' from '5089^WEIGHT (KG)^99DCT'
		Pattern pattern = Pattern.compile("\\^.*\\^");
		Log.e("FormPriorityList", "Element number of children= " + element.getChildCount());
		// loop through all the children of this element
		for (int i = 0; i < element.getChildCount(); i++) {

			Element childElement = element.getElement(i);
			Log.e("FormPriorityList", "childElement i= " + i);
			if (childElement != null) {

				String childName = childElement.getName();

				// patient id
				if (childName.equalsIgnoreCase("patient.patient_id")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getPatientId().toString());
				}
				if (childName.equalsIgnoreCase("patient.birthdate")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, parseDate(mPatient.getBirthdate()));
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
					Log.e("FormPriorityList", "childElement number= " + childElement.getChildCount());
					traverseInstanceNodes(childElement);
				}
			}
		}

	}

	private static void prepareInstanceValues() {

		for (int i = 0; i < mObservations.size(); i++) {
			Observation o = mObservations.get(i);
			mInstanceValues.put(o.getFieldName(), o.toString());

		}
	}

	private static int createFormInstance(String formPath, String jrFormId) {

		// reading the form
		Document doc = new Document();
		KXmlParser formParser = new KXmlParser();
		try {
			formParser.setInput(new FileReader(formPath));
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
		File formFile = new File(formPath);
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
			insertValues.put("displayName", mPatient.getGivenName() + " " + mPatient.getFamilyName());
			insertValues.put("instanceFilePath", instanceFilePath);
			insertValues.put("jrFormId", jrFormId);
			Uri insertResult = App.getApp().getContentResolver().insert(InstanceColumns.CONTENT_URI, insertValues);

			// insert to clinic
			// Save form instance to db
			FormInstance fi = new FormInstance();
			fi.setPatientId(mPatient.getPatientId());
			fi.setFormId(Integer.parseInt(jrFormId));
			fi.setPath(instanceFilePath);
			fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);

			ClinicAdapter ca = new ClinicAdapter();
			ca.open();
			ca.createFormInstance(fi, mPatient.getGivenName() + " " + mPatient.getFamilyName());
			ca.close();

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

	private void launchFormEntry(String jrFormId) {

		String formPath = null;
		int id = -1;
		try {
			Cursor mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				int dbid = mCursor.getInt(mCursor.getColumnIndex(FormsColumns._ID));
				String dbjrFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

				formPath = mCursor.getString(mCursor.getColumnIndex(FormsColumns.FORM_FILE_PATH));

				if (jrFormId.equalsIgnoreCase(dbjrFormId)) {
					id = dbid;
					break;
				}
			}
			if (mCursor != null) {
				mCursor.close();
			}

			if (id != -1) {

				// create instance
				int instanceId = createFormInstance(formPath, jrFormId);

				if (instanceId != -1) {
					Intent intent = new Intent();
					intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
					intent.setAction(Intent.ACTION_EDIT);
					intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));

					startActivity(intent);

				} else {
					Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, id);
					startActivity(new Intent(Intent.ACTION_EDIT, formUri));
				}

			}

		} catch (ActivityNotFoundException e) {
			showCustomToast(getString(R.string.error, getString(R.string.odk_collect_error)));
		}
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

	// end of code imported from ViewPatientActivity
	
	
	//louis also imported these two methods....
	private Patient getPatient(Integer patientId) {

		Patient p = null;
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {
			int patientIdIndex = c.getColumnIndex(ClinicAdapter.KEY_PATIENT_ID);
			int identifierIndex = c
					.getColumnIndex(ClinicAdapter.KEY_IDENTIFIER);
			int givenNameIndex = c.getColumnIndex(ClinicAdapter.KEY_GIVEN_NAME);
			int familyNameIndex = c
					.getColumnIndex(ClinicAdapter.KEY_FAMILY_NAME);
			int middleNameIndex = c
					.getColumnIndex(ClinicAdapter.KEY_MIDDLE_NAME);
			int birthDateIndex = c.getColumnIndex(ClinicAdapter.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(ClinicAdapter.KEY_GENDER);

			p = new Patient();
			p.setPatientId(c.getInt(patientIdIndex));
			p.setIdentifier(c.getString(identifierIndex));
			p.setGivenName(c.getString(givenNameIndex));
			p.setFamilyName(c.getString(familyNameIndex));
			p.setMiddleName(c.getString(middleNameIndex));
			p.setBirthDate(c.getString(birthDateIndex));
			p.setGender(c.getString(genderIndex));
		}

		if (c != null) {
			c.close();
		}
		ca.close();

		return p;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (mPatient != null) {
		// TODO Create more efficient SQL query to get only the latest observation values
		getAllObservations(mPatient.getPatientId());
		}
	}
	
	private void getAllObservations(Integer patientId) {
		ArrayList<Integer> selectedFormIds = new ArrayList<Integer>();
		
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPatientObservations(patientId);

		if (c != null && c.getCount() > 0) {
			mObservations.clear();
			int valueTextIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_TEXT);
			int valueIntIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_INT);
			int valueDateIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_DATE);
			int valueNumericIndex = c
					.getColumnIndex(ClinicAdapter.KEY_VALUE_NUMERIC);
			int fieldNameIndex = c.getColumnIndex(ClinicAdapter.KEY_FIELD_NAME);
			int encounterDateIndex = c
					.getColumnIndex(ClinicAdapter.KEY_ENCOUNTER_DATE);
			int dataTypeIndex = c.getColumnIndex(ClinicAdapter.KEY_DATA_TYPE);

			Observation obs;
			String prevFieldName = null;
			do {
				String fieldName = c.getString(fieldNameIndex);

				if (fieldName.equalsIgnoreCase("odkconnector.property.form"))
					selectedFormIds.add(c.getInt(valueIntIndex));
				else {
					// We only want most recent observation, so only get first
					// observation
					if (!fieldName.equals(prevFieldName)) {

						obs = new Observation();
						obs.setFieldName(fieldName);
						obs.setEncounterDate(c.getString(encounterDateIndex));

						int dataType = c.getInt(dataTypeIndex);
						obs.setDataType((byte) dataType);
						switch (dataType) {
						case Constants.TYPE_INT:
							obs.setValueInt(c.getInt(valueIntIndex));
							break;
						case Constants.TYPE_DOUBLE:
							obs.setValueNumeric(c.getDouble(valueNumericIndex));
							break;
						case Constants.TYPE_DATE:
							obs.setValueDate(c.getString(valueDateIndex));

							break;
						default:
							obs.setValueText(c.getString(valueTextIndex));
						}

						mObservations.add(obs);

						prevFieldName = fieldName;
					}
				}

			} while (c.moveToNext());
		}

		refreshView(selectedFormIds);

		if (c != null) {
			c.close();
		}
		ca.close();
	}
	
	
	private void refreshView(ArrayList<Integer> selectedFormIds) {
		MergeAdapter adapter = new MergeAdapter();

		Form[] allItems = new Form[mTotalForms.size()];
		Log.e(TAG, "mTotalForms.size(): " + mTotalForms.size());
		
		if (selectedFormIds != null && selectedFormIds.size() > 0) {
			Form[] starItems = new Form[selectedFormIds.size()];
			Form[] nonStarItems = new Form[mTotalForms.size() - selectedFormIds.size()];
			Log.e(TAG, "nonStarForms.size(): " + (mTotalForms.size() - selectedFormIds.size()));
			Log.e(TAG, "mStarForms.size(): " + selectedFormIds.size());
			
			int preferredCounter = 0;
			int nonPreferredCounter = 0;

			for (Form form : mTotalForms) {
				if (selectedFormIds.contains(form.getFormId())) {
					starItems[preferredCounter++] = form;
					Log.e(TAG, "mStarForms.size(): " + selectedFormIds.size());
					Log.e(TAG, "preferredCounter: " + preferredCounter);
				} else
					nonStarItems[nonPreferredCounter++] = form;
					Log.e(TAG, "nonStarForms.size(): " + (mTotalForms.size() - selectedFormIds.size()));
					Log.e(TAG, "nonpreferredCounter: " + nonPreferredCounter);
			}
			
				Log.e(TAG, "mStarForms.size(): " + selectedFormIds.size());
				adapter.addView(buildSectionLabel(getString(R.string.priority_form_section)));
				adapter.addAdapter(buildFormsList(starItems, true));
	
				adapter.addView(buildSectionLabel(getString(R.string.nonpriority_form_section)));
				adapter.addAdapter(buildFormsList(nonStarItems, false));
			
		} else {
			int formcounter = 0;
			for (Form form : mTotalForms) {
				allItems[formcounter++] = form;
			}
			Log.e(TAG, "About to Add mTotalForms.size(): " + mTotalForms.size());
			Log.e(TAG, "About to add forms: " + allItems);
			adapter.addView(buildSectionLabel(getString(R.string.all_form_section)));
			adapter.addAdapter(buildFormsList(allItems, false));

			Toast.makeText(this, "There are no preferred forms for this patient", Toast.LENGTH_SHORT).show();
		}

		setListAdapter(adapter);
	
	}

}
