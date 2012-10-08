package com.alphabetbloc.clinic.utilities;

/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.sqlcipher.database.SQLiteException;

import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Element;
import com.alphabetbloc.clinic.R;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import com.alphabetbloc.clinic.data.Observation;
import com.alphabetbloc.clinic.data.Patient;
import com.alphabetbloc.clinic.providers.Db;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.ui.user.CreatePatientActivity;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) (Other remaining methods)
 * 
 * @author Yaw Anokwa (InsertSingleForm(), getNameFromId(),
 *         CreateFormInstance(), findFormNode(), parseDate(),
 *         traverseInstanceNodes(), and prepareInstanceValues() are all taken
 *         wholesale from ODK Clinic!)
 */
public class XformUtils {

	private static final String REGISTRATION_FORM_ID = "-99";
	public static final String REGISTRATION_FORM_XML = "patient_registration.xml";
	private static final String REGISTRATION_FORM_NAME = "PatientRegistrationForm";

	private static final DateFormat COLLECT_INSTANCE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	private static final String TAG = XformUtils.class.getSimpleName();
	private static HashMap<String, String> mInstanceValues = new HashMap<String, String>();
	private static Element mFormNode;

	// Suppress default constructor for noninstantiability
	private XformUtils() {
		throw new AssertionError();
	}

	// XFORMS Utils (excerpted from the ViewPatientActivity, DownloadFormTask)
	public static boolean insertRegistrationForm() {
		File registrationForm = new File(FileUtils.getExternalFormsPath(), REGISTRATION_FORM_XML);
		if (!registrationForm.exists())
			registrationForm = getDefaultRegistrationForm();
		return insertSingleForm(registrationForm.getAbsolutePath());
	}

	private static File getDefaultRegistrationForm() {

		// input path: find default form in assets
		AssetManager assetManager = App.getApp().getAssets();
		String[] allAssetPaths = null;
		String assetPath = null;
		try {
			allAssetPaths = assetManager.list("");

			for (String path : allAssetPaths) {
				if (path.contains(REGISTRATION_FORM_XML)) {
					assetPath = path;
				}
			}

		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}

		// output path:
		File registrationForm = new File(FileUtils.getExternalFormsPath(), REGISTRATION_FORM_XML);
		registrationForm.getParentFile().mkdirs();
		String sdPath = registrationForm.getAbsolutePath();

		// move to working dir on sd
		return copyAssetToSd(assetPath, sdPath);
	}

	private static File copyAssetToSd(String assetPath, String sdPath) {

		File sdFile = new File(sdPath);
		if (sdFile.exists())
			return sdFile;

		AssetManager assetManager = App.getApp().getAssets();
		try {

			InputStream in = assetManager.open(assetPath);
			OutputStream out = new FileOutputStream(sdPath);

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}

			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}

		if (sdFile.exists())
			Log.i(TAG, "File has been successfully moved: " + assetPath + " -> " + sdPath);

		return sdFile;
	}

	// PARSE AND INSERT NEW FORM TO FORMS DB
	public static boolean insertSingleForm(String formPath) {

		ContentValues values = new ContentValues();
		File addMe = new File(formPath);

		// Ignore invisible files that start with periods.
		if (!addMe.getName().startsWith(".") && (addMe.getName().endsWith(".xml") || addMe.getName().endsWith(".xhtml"))) {

			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			Document document = null;

			try {
				documentBuilder = documentBuilderFactory.newDocumentBuilder();
				document = documentBuilder.parse(addMe);
				document.normalize();
			} catch (Exception e) {
				Log.e(TAG, e.getLocalizedMessage());
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

			if (name != null) {
				if (getNameFromId(id) == null) {
					values.put(FormsColumns.DISPLAY_NAME, name);
				} else {
					values.put(FormsColumns.DISPLAY_NAME, getNameFromId(id));
				}
			}
			if (id != -1 && version != null) {
				values.put(FormsColumns.JR_FORM_ID, id);
			}
			values.put(FormsColumns.FORM_FILE_PATH, addMe.getAbsolutePath());

			// NB: COLECT REQUIRED FIELDS
			// Assigned from Xform:
			// + FormsColumns.JR_FORM_ID
			// + FormsColumns.DISPLAY_NAME

			// Assigned here:
			// + FormsColumns.FORM_FILE_PATH

			// Assigned in Collect:
			// + FormsColumns.DISPLAY_SUBTEXT
			// + FormsColumns.DATE
			// + FormsColumns.MD5_HASH
			// + FormsColumns.FORM_MEDIA_PATH
			// + FormsColumns.JRCACHE_FILE_PATH

			boolean alreadyExists = false;

			Cursor mCursor;
			try {
				mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
			} catch (SQLiteException e) {
				Log.e("DownloadFormTask", e.getLocalizedMessage());
				return false;
				// TODO handle exception
			}

			if (mCursor == null) {
				System.out.println("Something bad happened");
				DbProvider.openDb().deleteAllForms();
				return false;
			}

			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				String dbmd5 = mCursor.getString(mCursor.getColumnIndex(FormsColumns.MD5_HASH));
				String dbFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

				// if the exact form exists, leave it be. else, insert it.
				if (dbmd5.equalsIgnoreCase(md5) && dbFormId.equalsIgnoreCase(id + "")) {
					alreadyExists = true;
				}

			}

			if (!alreadyExists) {
				App.getApp().getContentResolver().delete(FormsColumns.CONTENT_URI, "md5Hash=?", new String[] { md5 });
				App.getApp().getContentResolver().delete(FormsColumns.CONTENT_URI, "jrFormId=?", new String[] { id + "" });
				App.getApp().getContentResolver().insert(FormsColumns.CONTENT_URI, values);
			}

			if (mCursor != null) {
				mCursor.close();
			}

		}

		return true;

	}

	// TODO! change this to retrive the one form you want!
	private static String getNameFromId(Integer id) {
		String formName = null;
		Cursor c = DbProvider.openDb().fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			int formIdIndex = c.getColumnIndex(Db.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(Db.KEY_NAME);

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

		return formName;
	}

	// PARSE FORM AND INJECT WITH OBS
	public static int createRegistrationFormInstance(Patient mPatient) {
		File registrationForm = new File(FileUtils.getExternalFormsPath(), REGISTRATION_FORM_XML);
		return createFormInstance(mPatient, registrationForm.getAbsolutePath(), REGISTRATION_FORM_ID, REGISTRATION_FORM_NAME);
	}

	public static int createFormInstance(Patient mPatient, String formPath, String jrFormId, String formname) {

		if (mPatient == null)
			Log.e(TAG, "lost a patient!");

		// reading the form
		// TODO! is this the right document type?!
		org.kxml2.kdom.Document doc = new org.kxml2.kdom.Document();
		KXmlParser formParser = new KXmlParser();
		try {
			formParser.setInput(new FileReader(formPath));
			doc.parse(formParser);
		} catch (Exception e) {
			e.printStackTrace();
		}

		findFormNode(doc.getRootElement());
		if (mFormNode != null) {
			prepareInstanceValues(mPatient.getPatientId());
			traverseInstanceNodes(mFormNode, mPatient);
		} else {
			return -1;
		}

		// writing the instance file
		File formFile = new File(formPath);
		String formFileName = formFile.getName();
		String instanceName = "";
		if (formFileName.endsWith(FileUtils.XML_EXT)) {
			instanceName = formFileName.substring(0, formFileName.length() - 4);
		} else {
			instanceName = jrFormId;
		}
		instanceName = instanceName + "_" + COLLECT_INSTANCE_NAME_DATE_FORMAT.format(new Date());

		// make db path and internal path
		String dbPath = File.separator + instanceName + File.separator + instanceName + FileUtils.XML_EXT;
		File instanceFile = new File(FileUtils.getDecryptedFilePath(dbPath));
		instanceFile.getParentFile().mkdirs();

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
			insertValues.put(InstanceColumns.INSTANCE_FILE_PATH, dbPath);
			insertValues.put(InstanceColumns.JR_FORM_ID, jrFormId);
			insertValues.put(InstanceColumns.PATIENT_ID, mPatient.getPatientId());
			insertValues.put(InstanceColumns.FORM_NAME, formname);
			if (jrFormId.equalsIgnoreCase(REGISTRATION_FORM_ID))
				insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_COMPLETE);
			else
				insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INITIALIZED);
			Uri insertResult = App.getApp().getContentResolver().insert(InstanceColumns.CONTENT_URI, insertValues);
			return Integer.valueOf(insertResult.getLastPathSegment());

		} catch (IOException e) {
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

	private static String parseDate(String s) {
		SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
		try {
			return outputFormat.format(inputFormat.parse(s));
		} catch (ParseException e) {
			return "";
		}
	}

	private static void traverseInstanceNodes(Element element, Patient mPatient) {

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
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, parseDate(mPatient.getBirthdate()));
				}
				if (childName.equalsIgnoreCase("patient.birthdate_estimated")) {
					childElement.clear();
					String birthdate = mPatient.getbirthEstimated();
					if (birthdate != null)
						childElement.addChild(0, org.kxml2.kdom.Node.TEXT, birthdate);
				}
				if (childName.equalsIgnoreCase("patient.family_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getFamilyName());
				}
				if (childName.equalsIgnoreCase("patient.given_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getGivenName());
				}

				if (childName.equalsIgnoreCase("patient.middle_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getMiddleName());

				}
				if (childName.equalsIgnoreCase("patient.sex")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getGender());

				}
				if (childName.equalsIgnoreCase("patient.uuid")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getUuid());

				}
				if (childName.equalsIgnoreCase("patient.medical_record_number")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getIdentifier());
				}

				// provider id
				if (childName.equalsIgnoreCase("encounter.provider_id")) {
					SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
					String providerId = settings.getString(App.getApp().getString(R.string.key_provider), "0");
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, providerId);
				}

				if (childName.equalsIgnoreCase("encounter.location_id")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, "CHV Mobile Form Entry");
				}

				if (childName.equalsIgnoreCase("encounter.encounter_datetime")) {
					childElement.clear();
					Date date = new Date();
					date.setTime(System.currentTimeMillis());
					String dateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, dateString);
				}

				if (childName.equalsIgnoreCase("chv_provider_list")) {
					childElement.clear();
					String addClient = "";
					if (mPatient.getCreateCode() == CreatePatientActivity.PERMANENT_NEW_CLIENT)
						addClient = "Add Client to CHV Provider List";
					else if (mPatient.getCreateCode() == CreatePatientActivity.TEMPORARY_NEW_CLIENT)
						addClient = "Temporary Visit Only";
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, addClient);
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
						childElement.addChild(0, org.kxml2.kdom.Node.TEXT, value);
					}
				}

				if (childElement.getChildCount() > 0) {
					traverseInstanceNodes(childElement, mPatient);
				}
			}
		}
	}

	private static void prepareInstanceValues(Integer patientId) {

		if (patientId > 0) {

			ArrayList<Observation> mObservations = DbProvider.openDb().fetchPatientObservationList(patientId);

			for (int i = 0; i < mObservations.size(); i++) {
				Observation o = mObservations.get(i);
				mInstanceValues.put(o.getFieldName(), o.toString());
			}
		}

	}
}
