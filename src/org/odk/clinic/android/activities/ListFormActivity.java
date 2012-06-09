package org.odk.clinic.android.activities;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.InstanceLoaderListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.tasks.InstanceLoaderTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ListFormActivity extends ListActivity
		implements
			InstanceLoaderListener {
	public static final String tag = "ListFormActivity";

	// Request codes
	public static final int DOWNLOAD_FORM = 1;
	public static final int COLLECT_FORM = 2;

	private static final int PROGRESS_DIALOG = 1;

	private static final String SELECTED_FORM_ID_KEY = "selectedFormId";
	private static final DateFormat COLLECT_INSTANCE_NAME_DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd_HH-mm-ss");

	private Integer mPatientId;
//	private Button mActionButton;

	private ArrayAdapter<Form> mFormAdapter;
	private ArrayList<Form> mForms = new ArrayList<Form>();

	private Integer mSelectedFormId = null;

	private AlertDialog mAlertDialog;
	private ProgressDialog mProgressDialog;
	private static Element mFormNode = null;


	private InstanceLoaderTask mInstanceLoaderTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey(SELECTED_FORM_ID_KEY)) {
				mSelectedFormId = savedInstanceState
						.getInt(SELECTED_FORM_ID_KEY);
			}
		}

		setContentView(R.layout.form_list);

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			finish();
		}

		// TODO Check for invalid patient IDs
		String patientIdStr = getIntent().getStringExtra(
				Constants.KEY_PATIENT_ID);
		try {
			mPatientId = Integer.valueOf(patientIdStr);
		} catch (NumberFormatException e) {
			mPatientId = null;
			// here the id is empty meaning its a new patient
		}

		setTitle(getString(R.string.app_name) + " > "
				+ getString(R.string.forms));

		Object data = getLastNonConfigurationInstance();
		if (data instanceof InstanceLoaderTask)
			mInstanceLoaderTask = (InstanceLoaderTask) data;
/*
		mActionButton = (Button) findViewById(R.id.download_forms);
		mActionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent id = new Intent(getApplicationContext(),
						DownloadFormActivity.class);
				startActivityForResult(id, DOWNLOAD_FORM);
			}

		});
		*/
	}

	private void getDownloadedForms() {

		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			mForms.clear();

			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);

			Form f;
			if (c.getCount() > 0) {
				do {
					if (!c.isNull(formIdIndex)) {
						f = new Form();
						f.setFormId(c.getInt(formIdIndex));
						f.setPath(c.getString(pathIndex));
						f.setName(c.getString(nameIndex));
						mForms.add(f);
					}
				} while (c.moveToNext());
			}
		}

		refreshView();

		if (c != null)
			c.close();

		ca.close();
	}

	private Form getForm(Integer formId) {
		Form f = null;
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchForm(formId);

		if (c != null && c.getCount() > 0) {
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);

			f = new Form();
			f.setFormId(formId);
			f.setPath(c.getString(pathIndex));
			f.setName(c.getString(nameIndex));
		}

		if (c != null)
			c.close();
		ca.close();

		return f;
	}

	private FormInstance getFormInstance(Integer patientId, Integer formId) {
		FormInstance fi = null;
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPatientFormInstancesByStatus(patientId, formId,
				ClinicAdapter.STATUS_UNSUBMITTED);

		if (c != null && c.getCount() > 0) {
			int statusIndex = c
					.getColumnIndex(ClinicAdapter.KEY_FORMINSTANCE_STATUS);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);

			fi = new FormInstance();
			fi.setPatientId(patientId);
			fi.setFormId(formId);
			fi.setStatus(c.getString(statusIndex));
			fi.setPath(c.getString(pathIndex));
		}

		if (c != null) {
			c.close();
		}
		ca.close();

		return fi;
	}

	private void refreshView() {

		mFormAdapter = new ArrayAdapter<Form>(this,
				android.R.layout.simple_list_item_1, mForms);
		setListAdapter(mFormAdapter);

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
		instanceName = instanceName + "_unknown_"
				+ COLLECT_INSTANCE_NAME_DATE_FORMAT.format(new Date());

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
			insertValues.put("displayName","Unknown Patient");
			insertValues.put("instanceFilePath", instanceFilePath);
			insertValues.put("jrFormId", jrFormId);
			Uri insertResult = App.getApp().getContentResolver()
					.insert(InstanceColumns.CONTENT_URI, insertValues);
			
			// insert to clinic
			// Save form instance to db
			FormInstance fi = new FormInstance();
			fi.setPatientId(0);
			fi.setFormId(Integer.parseInt(jrFormId));
			fi.setPath(instanceFilePath);
			fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);

			ClinicAdapter ca = new ClinicAdapter();
			ca.open();
			ca.createFormInstance(fi, "Unknown Patient");
			ca.close();
			
			return Integer.valueOf(insertResult.getLastPathSegment());
			
			

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;

	}
	
	/*
	private int initializeFormInstance(String formId) {
		
		FormInstance fi = null;

		// Create instance folder
		String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
				.format(Calendar.getInstance().getTime());
		String instanceName = formId + "_unknown_" + time;

		String path = FileUtils.INSTANCES_PATH + instanceName;
		if (FileUtils.createFolder(path)) {
			String instancePath = path + "/" + instanceName + ".xml";

			// Save form instance to db
			fi = new FormInstance();
			fi.setPatientId(0);
			fi.setFormId(Integer.parseInt(formId));
			fi.setPath(instancePath);
			fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);

			ClinicAdapter ca = new ClinicAdapter();
			ca.open();
			ca.createFormInstance(fi, "Unknown Patient");
			ca.close();
			
			// register into content provider
			ContentValues insertValues = new ContentValues();
			insertValues.put("displayName", "Unknown Patient");
			insertValues.put("instanceFilePath", path);
			insertValues.put("jrFormId", formId);
			Uri insertResult = App.getApp().getContentResolver()
					.insert(InstanceColumns.CONTENT_URI, insertValues);
			
			return Integer.valueOf(insertResult.getLastPathSegment());

		}
		
		return -1;
	}
	*/


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

		// loop through all the children of this element
		for (int i = 0; i < element.getChildCount(); i++) {

			Element childElement = element.getElement(i);
			if (childElement != null) {

				String childName = childElement.getName();

				// value node
				if (childName.equalsIgnoreCase("date") || childName.equalsIgnoreCase("time") || childName.equalsIgnoreCase("value") ) {
					// remove xsi:null
					childElement.clear();

				}

				if (childElement.getChildCount() > 0) {
					traverseInstanceNodes(childElement);
				}
			}
		}

	}
	
	private void launchFormEntry(String formPath, String instancePath) {

		String jrFormId = null;
		int formId = -1;
		try {
			Cursor mCursor = App.getApp().getContentResolver()
					.query(FormsColumns.CONTENT_URI, null, null, null, null);
			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				int id = mCursor.getInt(mCursor
						.getColumnIndex(FormsColumns._ID));
				String filePath = mCursor.getString(mCursor
						.getColumnIndex(FormsColumns.FORM_FILE_PATH));
				jrFormId = mCursor.getString(mCursor
						.getColumnIndex(FormsColumns.JR_FORM_ID));

				if (formPath.equalsIgnoreCase(filePath)) {
					formId = id;
					break;
				}
			}
			if (mCursor!=null) {
				mCursor.close();
			}

			if (formId != -1) {
				Uri formUri = ContentUris.withAppendedId(
						FormsColumns.CONTENT_URI, formId);
				
				int instanceId = createFormInstance(formPath, jrFormId);

				if (instanceId != -1) {
					Intent intent = new Intent();
					intent.setComponent(new ComponentName(
							"org.odk.collect.android",
							"org.odk.collect.android.activities.FormEntryActivity"));
					intent.setAction(Intent.ACTION_EDIT);
					intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/"
							+ instanceId));

					startActivity(intent);

				} else {

					startActivity(new Intent(Intent.ACTION_EDIT, formUri));
				}

			} else {
				showCustomToast(
						getString(R.string.error,
								getString(R.string.form_find_error)));
			}

		} catch (ActivityNotFoundException e) {
			showCustomToast(
					getString(R.string.error,
							getString(R.string.odk_collect_error)));
		}
	}

	
	@Override
	protected void onListItemClick(ListView listView, View view, int position,
			long id) {
		// Get selected form
		Form f = (Form) getListAdapter().getItem(position);
		mSelectedFormId = f.getFormId();

		launchFormEntry(f.getPath(),null);
				
//		FormInstance fi = getFormInstance(mPatientId, f.getFormId());
//		if (fi == null) {
//			initializeFormInstance(f, mPatientId);
//		} else {
//			createFillDialog(f, fi);
//		}
	}

/*	private void createFillDialog(final Form f, final FormInstance fi) {

		mAlertDialog = new AlertDialog.Builder(this).create();
		// mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);

		mAlertDialog.setTitle("Fill Form");
		mAlertDialog
				.setMessage("Form already filled for this Patient. Select what you want to do.");
		DialogInterface.OnClickListener uploadDialogListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				switch (i) {
					case DialogInterface.BUTTON1 : // yes
						dialog.dismiss();
						initializeFormInstance(f, mPatientId);
						break;
					case DialogInterface.BUTTON2 : // no
						dialog.dismiss();
						launchFormEntry(f.getPath(), fi.getPath());
						break;
				}
			}
		};
		mAlertDialog.setCancelable(false);
		mAlertDialog.setButton("Fill a new one", uploadDialogListener);
		mAlertDialog.setButton2("Review Latest", uploadDialogListener);
		mAlertDialog.show();
	}
*/
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {

		if (requestCode == COLLECT_FORM) {
			// Remove form instance from db if user "discarded"
			if (mSelectedFormId != null) {
				boolean discarded = false;

				FormInstance fi = getFormInstance(mPatientId, mSelectedFormId);
				if (fi != null) {

					File file = new File(fi.getPath());
					discarded = !file.exists();
				}

				if (discarded) {
					ClinicAdapter ca = new ClinicAdapter();
					ca.open();
					ca.deleteFormInstance(mPatientId, mSelectedFormId);
					ca.close();

					mSelectedFormId = null;
				} else {
					// Launch Instance uploader instance
					Intent iu = new Intent(getApplicationContext(),
							InstanceUploaderList.class);
					startActivity(iu);
					finish();
				}
			}
		}

		if (resultCode == RESULT_CANCELED) {
			return;
		}

		super.onActivityResult(requestCode, resultCode, intent);

	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case PROGRESS_DIALOG :
				mProgressDialog = new ProgressDialog(this);
				DialogInterface.OnClickListener loadingButtonListener = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						mInstanceLoaderTask.cancel(true);
					}
				};
				mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
				mProgressDialog.setTitle("Loading Instance");
				mProgressDialog.setMessage(getString(R.string.please_wait));
				mProgressDialog.setIndeterminate(true);
				mProgressDialog.setCancelable(false);
				mProgressDialog.setButton(getString(R.string.cancel),
						loadingButtonListener);
				return mProgressDialog;
		}
		return null;
	}

	private void dismissDialogs() {
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}
	}

	@Override
	protected void onPause() {
		dismissDialogs();
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (mInstanceLoaderTask != null) {
			mInstanceLoaderTask.setInstanceLoaderListener(this);
		}

		getDownloadedForms();

		super.onResume();
	}

	@Override
	protected void onDestroy() {

		if (mInstanceLoaderTask != null) {
			mInstanceLoaderTask.setInstanceLoaderListener(null);
			if (mInstanceLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
				// Allow saving to finish
				mInstanceLoaderTask.cancel(false);
			}
		}
		super.onDestroy();
	}

	@Override
	public void loadingComplete(String result) {
		dismissDialog(PROGRESS_DIALOG);
		if (result != null && mSelectedFormId != null) {
			Form f = getForm(mSelectedFormId);
			String formPath = f.getPath();
			String instancePath = result;
			launchFormEntry(formPath, instancePath);
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mInstanceLoaderTask != null
				&& mInstanceLoaderTask.getStatus() != AsyncTask.Status.FINISHED)
			return mInstanceLoaderTask;

		return null;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mSelectedFormId != null)
			outState.putInt(SELECTED_FORM_ID_KEY, mSelectedFormId);
	}

	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}
}
