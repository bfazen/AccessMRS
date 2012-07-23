package org.odk.clinic.android.deleteactivities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.PreferencesActivity;
import org.odk.clinic.android.activities.ViewSavedForms;
import org.odk.clinic.android.adapters.FormAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.tasks.ActivityLogTask;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
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
	public static final String EDIT_FORM = "edit_form";
	
	private ArrayList<Form> mTotalForms = new ArrayList<Form>();
	private static String mProviderId;
	private Context mContext;
	private ActivityLog mActivityLog;
	private Resources res;

	private MergeAdapter adapter = null;
	private FormAdapter completedAdapter = null;
	private FormAdapter savedAdapter = null;
	private FormAdapter allFormsAdapter = null;
	private int savedFormsSize = 0;

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
		View viewTitle = (View) findViewById(R.id.title);
		viewTitle.setBackgroundResource(R.color.dark_gray);

		textView.setText("Forms For New Clients");
		sectionImage.setImageDrawable(res.getDrawable(R.drawable.icon));

		getDownloadedForms();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");

	}

	protected void onListItemClick(ListView listView, View view, int position, long id) {

		ListAdapter selectedAdapter = adapter.getAdapter(position);
		Form f = (Form) getListAdapter().getItem(position);
		String type = null;

		if (selectedAdapter == savedAdapter) {
			launchSavedFormEntry(f.getInstanceId());
			type = "Saved";
		} else if (selectedAdapter == completedAdapter) {
			launchFormViewOnly(f.getPath(), f.getFormId().toString());
			type = "Completed-Unsent";
		} else {
			launchBlankFormEntry(f.getFormId().toString());
			type = "New Blank Form";
		}

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString(), type);
		}
	}

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

	private void launchBlankFormEntry(String jrFormId) {

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
				startActivityForResult(new Intent(Intent.ACTION_EDIT, formUri), FILL_FORM);

			}

		} catch (ActivityNotFoundException e) {
			showCustomToast(getString(R.string.error, getString(R.string.odk_collect_error)));
		}
	}
	
	// NB... this != ViewCompletedForms.launchFormViewOnly()
	private void launchFormViewOnly(String uriString, String formId) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));

		// TODO: Could be improved... using ACTION_VIEW, etc.
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		intent.putExtra("path_name", uriString);
		intent.putExtra("form_id", formId);
		startActivityForResult(intent, VIEW_FORM_ONLY);
	}

	private void launchSavedFormEntry(int instanceId) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
		startActivityForResult(intent, FILL_FORM);
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
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, "-1" };
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
		savedFormsSize = savedForms.size();
		
		// 2. COMPLETED FORMS: gather the saved forms from Clinic Instances Table
		ArrayList<Form> completedForms = new ArrayList<Form>();

		Cursor c = ca.fetchCompletedByPatientId(-1);
		if (c != null && c.getCount() > 0) {
			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int instanceIdIndex = c.getColumnIndex(ClinicAdapter.KEY_ID);
			int displayNameIndex = c.getColumnIndex(ClinicAdapter.KEY_FORMINSTANCE_DISPLAY);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_NAME);
			int dateIndex = c.getColumnIndex(ClinicAdapter.KEY_FORMINSTANCE_SUBTEXT);
			Log.e("louis.fazen", "dateindex=" + dateIndex);
			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(instanceIdIndex)) {
						form = new Form();
						
						form.setFormId(c.getInt(formIdIndex));
						form.setInstanceId(c.getInt(instanceIdIndex));
						form.setDisplayName(c.getString(displayNameIndex));
						form.setName(c.getString(nameIndex));
						form.setPath(c.getString(pathIndex));						
						form.setDisplaySubtext(c.getString(dateIndex));
						completedForms.add(form);
					}
				} while (c.moveToNext());
			}

		}

		if (c != null) {
			c.close();
		}

		ca.close();

		// end of gathering data... add everything to the view:
		adapter = new MergeAdapter();
		// saved...
		if (!savedForms.isEmpty()) {
			if(savedForms.size() < 4){
			savedAdapter = new FormAdapter(this, R.layout.saved_instances, savedForms, false);
			adapter.addView(buildSectionLabel(getString(R.string.saved_form_section)));
			adapter.addAdapter(savedAdapter);
			} else {
				adapter.addView(buildSectionLabel(getString(R.string.saved_form_section)));
				adapter.addView(formSummaryView());
			}
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

		setListAdapter(adapter);
	}

	private View formSummaryView() {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

		formsSummary.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
					Intent i = new Intent(getApplicationContext(), ViewSavedForms.class);
					i.putExtra(Constants.KEY_PATIENT_ID, "-1");
					startActivity(i);
			}
		});

		ImageView priorityArrow = (ImageView) formsSummary.findViewById(R.id.arrow_image);
		ImageView priorityImage = (ImageView) formsSummary.findViewById(R.id.priority_image);
		RelativeLayout priorityBlock = (RelativeLayout) formsSummary.findViewById(R.id.priority_block);
		TextView priorityNumber = (TextView) formsSummary.findViewById(R.id.priority_number);
		TextView allFormTitle = (TextView) formsSummary.findViewById(R.id.all_form_title);
		TextView formNames = (TextView) formsSummary.findViewById(R.id.form_names);

		if (priorityArrow != null && formNames != null && allFormTitle != null) {
			priorityImage.setImageDrawable(res.getDrawable(R.drawable.ic_gray_block));
			priorityBlock.setPadding(0, 0, 0, 0);
			
			priorityArrow.setImageResource(R.drawable.arrow_gray);
			formNames.setVisibility(View.GONE);
			allFormTitle.setTextColor(res.getColor(R.color.dark_gray));

			allFormTitle.setText("View All Saved Forms");

			if (priorityNumber != null && priorityImage != null) {
				priorityNumber.setText(String.valueOf(savedFormsSize));
				priorityImage.setVisibility(View.VISIBLE);
			}
		}

		return (formsSummary);
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
		
		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(mProviderId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId("New Patient");
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
		// BUT Original ListPatientActivity does not include it

		if (resultCode == RESULT_OK) {

			if ((requestCode == FILL_FORM) && intent != null) {

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

				// 2. updateSavedForm Numbers by Patient ID--Not necessary here, as we have no ID
				ClinicAdapter ca = new ClinicAdapter();
				ca.open();

				// 3. Add to Clinic Db if complete, even without ID
				if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {
					FormInstance fi = new FormInstance();
					fi.setPatientId(-1); //TODO: should change this to look up the patient ID in Collect first, just in case...
					fi.setFormId(Integer.parseInt(dbjrFormId));
					fi.setPath(filePath);
					fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);
					Date date = new Date();
					date.setTime(System.currentTimeMillis());
					String dateString = "Completed: " + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
					fi.setCompletionSubtext(dateString);
					ca.createFormInstance(fi, displayName);
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
