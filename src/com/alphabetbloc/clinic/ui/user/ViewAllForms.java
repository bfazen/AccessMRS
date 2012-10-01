package com.alphabetbloc.clinic.ui.user;

import java.util.ArrayList;
import java.util.Arrays;

import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.adapters.FormAdapter;
import com.alphabetbloc.clinic.adapters.MergeAdapter;
import com.alphabetbloc.clinic.data.Form;
import com.alphabetbloc.clinic.data.Patient;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.XformUtils;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 */

public class ViewAllForms extends ViewFormsActivity {

	private ArrayList<Form> mTotalForms = new ArrayList<Form>();
	private static Patient mPatient;
	private Context mContext;
	private ArrayList<Integer> mSelectedFormIds = new ArrayList<Integer>();
	private Resources res;
	private MergeAdapter adapter = null;
	private FormAdapter completedAdapter = null;
	private FormAdapter savedAdapter = null;
	private FormAdapter priorityAdapter = null;
	private FormAdapter nonPriorityAdapter = null;
	private FormAdapter allFormsAdapter = null;
	protected GestureDetector mFormSummaryDetector;
	protected OnTouchListener mFormSummaryListener;
	private ListView mAllFormLV;
	protected GestureDetector mFormDetector;
	protected OnTouchListener mFormListener;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		res = this.getResources();
		setContentView(R.layout.example_cw_main);

		if (!FileUtils.storageReady()) {
			Toast.makeText(this, getString(R.string.error, R.string.storage_error), Toast.LENGTH_SHORT).show();
			finish();
		}

		getDownloadedForms();

		// We should always have a patient here, so getPatient
		String patientIdStr = getIntent().getStringExtra(KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);

		mFormDetector = new GestureDetector(new onFormClick());
		mFormListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormDetector.onTouchEvent(event);
			}
		};

		mFormSummaryDetector = new GestureDetector(new onFormSummaryClick());
		mFormSummaryListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormSummaryDetector.onTouchEvent(event);
			}
		};

	}

	private void getDownloadedForms() {

		Cursor c = DbProvider.openDb().fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			mTotalForms.clear();
			int formIdIndex = c.getColumnIndex(DbProvider.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(DbProvider.KEY_NAME);
			int pathIndex = c.getColumnIndex(DbProvider.KEY_PATH);

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

	}

	@Override
	protected void onResume() {
		super.onResume();
		mSelectedFormIds = getPriorityForms(mPatient.getPatientId());
		createPatientHeader(mPatient.getPatientId());
		refreshView();
	}

	private void refreshView() {

		boolean selectedIds = false;
		if (mSelectedFormIds != null && mSelectedFormIds.size() > 0) {
			selectedIds = true;
		}

		// 1. COMPLETED FORMS: gather the saved forms from the Clinic.db
		String cSelection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String cSelectionArgs[] = { InstanceProviderAPI.STATUS_COMPLETE, String.valueOf(mPatient.getPatientId()) };
		ArrayList<Form> completedForms = queryInstances(cSelection, cSelectionArgs);

		// Venn diagram with completed and selected subsets of Forms
		// completedSelectedFormIds is their intersection
		ArrayList<Integer> completedSelectedFormIds = new ArrayList<Integer>();
		for (Form completed : completedForms) {
			Integer id = completed.getFormId();
			if (selectedIds && mSelectedFormIds.contains(id)) {
				completedSelectedFormIds.add(id);
			}
		}

		completedAdapter = new FormAdapter(this, R.layout.saved_instances, completedForms, false);

		// 2 and 3. SEPARATE PRIORITY & NONPRIORITY forms from the recent
		// download:
		Form[] allItems = new Form[mTotalForms.size()];
		Form[] starItems = new Form[mSelectedFormIds.size() - completedSelectedFormIds.size()];
		Form[] nonStarItems = new Form[(mTotalForms.size() - mSelectedFormIds.size()) + completedSelectedFormIds.size()];

		if (selectedIds) {
			int preferredCounter = 0;
			int nonPreferredCounter = 0;
			for (Form form : mTotalForms) {
				// Only non-completed priority forms are differentiated from
				// general form pool
				if (mSelectedFormIds.contains(form.getFormId()) && !completedSelectedFormIds.contains(form.getFormId())) {
					starItems[preferredCounter++] = form;
				} else
					// all other forms are added to the additional list
					// (including saved & completed forms)
					nonStarItems[nonPreferredCounter++] = form;
			}

		} else {
			// if no priority forms, then no separation
			int formcounter = 0;
			for (Form form : mTotalForms) {
				allItems[formcounter++] = form;
			}
		}

		// 4. SAVED FORMS: gather the saved forms from Collect Instances.Db
		String sSelection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String sSelectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, String.valueOf(mPatient.getPatientId()) };
		ArrayList<Form> savedForms = queryInstances(sSelection, sSelectionArgs);
		savedAdapter = new FormAdapter(this, R.layout.saved_instances, savedForms, false);

		// end of gathering data... add everything to the view:
		adapter = new MergeAdapter();

		// priority...
		if (starItems.length > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.priority_form_section), true));
			ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(starItems));
			priorityAdapter = new FormAdapter(this, R.layout.default_form_item, formList, true);
			adapter.addAdapter(priorityAdapter);
		}

		// saved...
		if (!savedForms.isEmpty()) {
			adapter.addView(buildSectionLabel(getString(R.string.saved_form_section), true));
			if (savedForms.size() < 4) {
				adapter.addAdapter(savedAdapter);
			} else {
				adapter.addView(formSummaryView(savedForms.size()));
			}
		}

		// completed...
		if (!completedForms.isEmpty()) {
			adapter.addView(buildSectionLabel(getString(R.string.completed_form_section), true));
			adapter.addAdapter(completedAdapter);
		}

		// add non-priority forms if there is a priority forms section
		if (starItems.length > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.nonpriority_form_section), true));
			ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(nonStarItems));
			nonPriorityAdapter = new FormAdapter(this, R.layout.default_form_item, formList, false);
			adapter.addAdapter(nonPriorityAdapter);

		} else {
			// if no priority / non-priority divide, then add all forms
			adapter.addView(buildSectionLabel(getString(R.string.all_form_section), true));
			ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(allItems));
			allFormsAdapter = new FormAdapter(this, R.layout.default_form_item, formList, false);
			adapter.addAdapter(allFormsAdapter);
		}

		mAllFormLV = getListView();
		mAllFormLV.setAdapter(adapter);
		mAllFormLV.setOnTouchListener(mFormListener);

		// setListAdapter(adapter);
	}

	@Override
	protected View buildSectionLabel(String sectionlabel, boolean icon) {
		View v;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);

		textView.setText(sectionlabel);

		if (sectionlabel == getString(R.string.priority_form_section)) {
			v.setBackgroundResource(R.color.priority);
			sectionImage.setImageResource(R.drawable.ic_priority);

		} else if (sectionlabel == getString(R.string.saved_form_section)) {
			v.setBackgroundResource(R.color.saved);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_saved));

		} else if (sectionlabel == getString(R.string.completed_form_section)) {
			v.setBackgroundResource(R.color.completed);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_completed));

		} else {
			v.setBackgroundResource(R.color.medium_gray);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_additional));
		}
		v.setOnTouchListener(mSwipeListener);
		return (v);
	}

	private View formSummaryView(int forms) {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

		// formsSummary.setOnClickListener(new View.OnClickListener() {
		// public void onClick(View v) {
		//
		// Intent i = new Intent(getApplicationContext(), ViewSavedForms.class);
		// i.putExtra(KEY_PATIENT_ID,
		// mPatient.getPatientId().toString());
		// startActivity(i);
		// }
		// });

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
				priorityNumber.setText(String.valueOf(forms));
				priorityImage.setVisibility(View.VISIBLE);
			}
		}

		formsSummary.setOnTouchListener(mFormSummaryListener);
		return (formsSummary);
	}

	// BUTTONS
	class onFormClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {

			int pos = mAllFormLV.pointToPosition((int) e.getX(), (int) e.getY());
			if (pos != -1) {
				Object o = adapter.getItem(pos);
				if (o instanceof Form) {
					launchFormView((Form) o, pos);
				}
			}
			return false;
		}
	}

	class onFormSummaryClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {

			Intent i = new Intent(getApplicationContext(), ViewSavedForms.class);
			i.putExtra(KEY_PATIENT_ID, mPatient.getPatientId().toString());
			startActivity(i);

			return false;
		}
	}

	protected void launchFormView(Form f, int position) {

		ListAdapter selectedAdapter = adapter.getAdapter(position);

		String type = null;
		int formType = FILL_FORM;
		boolean priority = false;
		if (mSelectedFormIds.contains(f.getFormId())) {
			formType = FILL_PRIORITY_FORM;
			priority = true;
		}

		if (selectedAdapter == savedAdapter) {
			launchSavedFormEntry(f.getInstanceId(), formType);
			type = "Saved";
		} else if (selectedAdapter == completedAdapter) {
			launchFormViewOnly(f.getPath(), f.getFormId().toString());
			type = "Completed-Unsent";
		} else {
			launchFormEntry(f.getFormId().toString(), f.getName(), formType);
			type = "New Form";
		}

		startActivityLog(mPatient.getPatientId().toString(), f.getFormId().toString(), type, priority);

	}

	private void launchFormViewOnly(String uriString, String formId) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		intent.putExtra("path_name", uriString);
		intent.putExtra("form_id", formId);
		startActivityForResult(intent, VIEW_FORM_ONLY);
	}

	private void launchSavedFormEntry(int instanceId, int priority) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
		startActivityForResult(intent, priority);
	}

	
	// NB: RESULT_OK based on:
	// Collect.FormEntryActivity.finishReturnInstance() line1654
	// Uri instance = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id);
	// setResult(RESULT_OK, new Intent().setData(instance));
	// BUT ListPatientActivity does not include it
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		
		if (resultCode == RESULT_OK && (requestCode == FILL_FORM || requestCode == FILL_PRIORITY_FORM) && intent != null)
			updateDatabases(requestCode, intent, mPatient.getPatientId());

	}
	
	// LAUNCH FORM ENTRY
	// TODO All of the below should be made into an loadForm IntentService
	private void launchFormEntry(String jrFormId, String formname, int priority) {
		String formPath = null;
		int id = -1;
		try {
			// TODO! Fix Yaw's inefficient query--could just return one row
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
				int instanceId = XformUtils.createFormInstance(mPatient, formPath, jrFormId, formname);

				if (instanceId != -1) {
					Intent intent = new Intent();
					intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
					// NB: changed this from ACTION_VIEW
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

}
