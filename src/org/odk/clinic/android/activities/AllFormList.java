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
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.tasks.ActivityLogTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 */

public class AllFormList extends ListActivity {

	// public static final int FILL_BLANK_FORM = 3;
	public static final int FILL_FORM = 3;
	public static final int VIEW_FORM_ONLY = 4;
	public static final int FILL_PRIORITY_FORM = 5;

	public static final int FILL_BLANK_FORM = 6;

	private ArrayList<Form> mTotalForms = new ArrayList<Form>();
	private static Patient mPatient;
	private static String mProviderId;
	private static HashMap<String, String> mInstanceValues = new HashMap<String, String>();
	private static final DateFormat COLLECT_INSTANCE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();
	private static Element mFormNode;
	private Context mContext;
	private ActivityLog mActivityLog;
	private Resources res;
	public static final String EDIT_FORM = "edit_form";
	private MergeAdapter adapter = null;
	private FormAdapter completedAdapter = null;
	private FormAdapter savedAdapter = null;
	private FormAdapter allFormsAdapter = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		res = this.getResources();

		if (!FileUtils.storageReady()) {
			Toast.makeText(this, getString(R.string.error, R.string.storage_error), Toast.LENGTH_SHORT).show();
			finish();
		}

		// set the title bar
		setContentView(R.layout.all_form_list);
		TextView textView = (TextView) findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) findViewById(R.id.section_image);
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.section_relative_layout);
		rl.setBackgroundResource(R.color.medium_gray);
		textView.setText("Fill a Blank Form");
		sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_additional));

		getDownloadedForms();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");

	}

	protected void onListItemClick(ListView listView, View view, int position, long id) {

		ListAdapter selectedAdapter = adapter.getAdapter(position);
		Form f = (Form) getListAdapter().getItem(position);

		String type = null;
		int priority = FILL_FORM;

		if (selectedAdapter == savedAdapter) {
			launchSavedFormEntry(f.getInstanceId(), priority);
			type = "Saved";
		} else if (selectedAdapter == completedAdapter) {
			launchFormViewOnly(f.getPath());
			type = "Completed-Unsent";
		} else {
			launchFormEntry(f.getFormId().toString(), f.getName(), priority);
			type = "New Form";
		}

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString(), type);
		}

	}

	// method excerpted from the ViewPatientActivity (delete from there?)
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

	// below this line is all excerpted from the ViewPatientActivity (delete
	// from there?)
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
		// Log.e("FormPriorityList", "Element number of children= " +
		// element.getChildCount());
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
					// Log.e("FormPriorityList", "childElement number= " +
					// childElement.getChildCount());
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

	private static int createFormInstance(String formPath, String jrFormId, String formname) {
		Log.e("louis.fazen", "CreateFormInstance jrformid: " + jrFormId);
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
			insertValues.put(InstanceColumns.DISPLAY_NAME, mPatient.getGivenName() + " " + mPatient.getFamilyName());
			insertValues.put(InstanceColumns.INSTANCE_FILE_PATH, instanceFilePath);
			insertValues.put(InstanceColumns.JR_FORM_ID, jrFormId);

			// louis.fazen is adding these variables to be added to the
			// Instances Db
			insertValues.put(InstanceColumns.PATIENT_ID, mPatient.getPatientId());
			insertValues.put(InstanceColumns.FORM_NAME, formname);

			Log.e("louis.fazen", "createFormInstance: adding insertValues: " + insertValues.toString());

			Uri insertResult = App.getApp().getContentResolver().insert(InstanceColumns.CONTENT_URI, insertValues);

			Log.e("louis.fazen", "createFormInstance has uri of insertResult: " + insertResult);

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

	private void launchFormEntry(String jrFormId, String formname, int priority) {
		String formPath = null;
		int id = -1;
		try {
			// TODO: louis.fazen: Yaw's query seems inefficient--could just
			// return one row
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

				// create instance in Collect
				int instanceId = createFormInstance(formPath, jrFormId, formname);

				if (instanceId != -1) {
					Intent intent = new Intent();
					intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
					// TODO: louis.fazen changed this from ACTION_VIEW????
					intent.setAction(Intent.ACTION_EDIT);
					intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
					startActivityForResult(intent, priority);
				} else {
					Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, id);
					startActivityForResult(new Intent(Intent.ACTION_EDIT, formUri), priority);
				}

			}

		} catch (ActivityNotFoundException e) {
			showCustomToast(getString(R.string.error, getString(R.string.odk_collect_error)));
		}
	}

	// NB... this != ViewCompletedForms.launchFormViewOnly()
	private void launchFormViewOnly(String uriString) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));

		// TODO: The proper way to do this would be via using ACTION_VIEW
		// as it allows for external apps to connect to Collect as ViewOnly
		// Functionality, not just my version of Collect
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		intent.setData(Uri.parse(uriString));
		showCustomToast(getString(R.string.view_only_form)); // difficult to see
																// TODO: this
																// should come
																// from collect!
		startActivityForResult(intent, VIEW_FORM_ONLY);
	}

	private void launchSavedFormEntry(int instanceId, int priority) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
		startActivityForResult(intent, priority);
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshView();
	}

	private void refreshView() {

		ClinicAdapter ca = new ClinicAdapter();
		ca.open();

		// 1. SAVED FORMS: gather the saved forms from Collect Instances.Db
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + " IS NULL";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE };
		Cursor csave = App.getApp().getContentResolver()
				.query(InstanceColumns.CONTENT_URI, new String[] { InstanceColumns._ID, InstanceColumns.JR_FORM_ID, InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_SUBTEXT, InstanceColumns.FORM_NAME, InstanceColumns.INSTANCE_FILE_PATH }, selection, selectionArgs, null);
		ArrayList<Form> savedForms = new ArrayList<Form>();

		if (csave != null) {
			csave.moveToFirst();
		}

		if (csave != null && csave.getCount() >= 0) {
			int instanceIdIndex = csave.getColumnIndex(InstanceColumns._ID);
			int formIdIndex = csave.getColumnIndex(InstanceColumns.JR_FORM_ID);
			int subtextIndex = csave.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT);
			int displayNameIndex = csave.getColumnIndex(InstanceColumns.DISPLAY_NAME);
			int pathIndex = csave.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
			int nameIndex = csave.getColumnIndex(InstanceColumns.FORM_NAME);

			if (csave.getCount() > 0) {
				Form form;
				do {
					if (!csave.isNull(instanceIdIndex)) {
						form = new Form();
						form.setInstanceId(csave.getInt(instanceIdIndex));
						form.setFormId(csave.getInt(formIdIndex));
						form.setName(csave.getString(nameIndex));
						form.setPath(csave.getString(pathIndex));
						form.setDisplaySubtext(csave.getString(subtextIndex));
						form.setDisplayName(csave.getString(displayNameIndex));
						savedForms.add(form);
					}
				} while (csave.moveToNext());
			}
		}

		if (csave != null)
			csave.close();

		ca.close();

		// 2. COMPLETED FORMS: gather the saved forms from Collect Instances.Db
		ArrayList<Form> completedForms = new ArrayList<Form>();

		Cursor c = ca.fetchCompletedByPatientId(-1);
		if (c != null && c.getCount() > 0) {
			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int instanceIdIndex = c.getColumnIndex(ClinicAdapter.KEY_ID);
			int subtextIndex = c.getColumnIndex(ClinicAdapter.KEY_FORMINSTANCE_DISPLAY);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_NAME);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(instanceIdIndex)) {
						form = new Form();
						form.setFormId(c.getInt(formIdIndex));
						form.setInstanceId(c.getInt(instanceIdIndex));
						form.setName(c.getString(nameIndex));
						form.setPath(c.getString(pathIndex));
						form.setDisplaySubtext(c.getString(subtextIndex));
						completedForms.add(form);
					}
				} while (c.moveToNext());
			}

		}

		if (c != null) {
			c.close();
		}

		// end of gathering data... add everything to the view:
		adapter = new MergeAdapter();

		// saved...
		if (!savedForms.isEmpty()) {
			savedAdapter = new FormAdapter(this, R.layout.saved_instances, savedForms, false);
			adapter.addView(buildSectionLabel(getString(R.string.saved_form_section)));
			adapter.addAdapter(savedAdapter);
		}

		// completed...
		if (!completedForms.isEmpty()) {
			completedAdapter = new FormAdapter(this, R.layout.saved_instances, completedForms, false);
			adapter.addView(buildSectionLabel(getString(R.string.completed_form_section)));
			adapter.addAdapter(completedAdapter);
		}

		// if no priority / non-priority divide, then add all forms
		adapter.addView(buildSectionLabel(getString(R.string.all_form_section)));
		allFormsAdapter = new FormAdapter(this, R.layout.default_form_item, mTotalForms, false);
		adapter.addAdapter(allFormsAdapter);

	}

	private View buildSectionLabel(String sectionlabel) {
		View v;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);

		textView.setText(sectionlabel);

		if (sectionlabel == getString(R.string.saved_form_section)) {
			v.setBackgroundResource(R.color.saved);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_saved));

		} else if (sectionlabel == getString(R.string.completed_form_section)) {
			v.setBackgroundResource(R.color.completed);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_completed));

		} else {
			v.setBackgroundResource(R.color.medium_gray);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_additional));
		}

		return (v);
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
		// // TODO: louis.fazen added RESULT_OK based on:
		// Collect.FormEntryActivity.finishReturnInstance() line1654
		// Uri instance = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id);
		// setResult(RESULT_OK, new Intent().setData(instance));
		// BUT ListPatientActivity does not include it

		if (resultCode == RESULT_OK) {

			if ((requestCode == FILL_BLANK_FORM) && intent != null) {

				// 1. GET instance from Collect
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
				}
				if (mCursor != null) {
					mCursor.close();
				}

				// 2. updateSavedForm Numbers
				ClinicAdapter ca = new ClinicAdapter();
				ca.open();
				ca.updateSavedFormNumbersByPatientId(mPatient.getPatientId().toString());
				ca.updateSavedFormsListByPatientId(mPatient.getPatientId().toString());

				// 3. Add to Clinic Db if complete
				if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {
					Log.e("lef-onActivityResult", "mCursor status:" + status + "=" + InstanceProviderAPI.STATUS_COMPLETE);
					FormInstance fi = new FormInstance();
					fi.setPatientId(mPatient.getPatientId());
					fi.setFormId(Integer.parseInt(dbjrFormId));
					fi.setPath(filePath);
					fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);

					ca.createFormInstance(fi, displayName);
					if (requestCode == FILL_PRIORITY_FORM) {
						ca.updatePriorityFormsByPatientId(mPatient.getPatientId().toString(), dbjrFormId);
					}
				}

				ca.close();

			}
			return;
		}
		super.onActivityResult(requestCode, resultCode, intent);
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
