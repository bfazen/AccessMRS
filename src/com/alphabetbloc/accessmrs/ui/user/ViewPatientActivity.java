package com.alphabetbloc.accessmrs.ui.user;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI;
import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.R;
import com.alphabetbloc.accessmrs.adapters.MergeAdapter;
import com.alphabetbloc.accessmrs.adapters.ObservationAdapter;
import com.alphabetbloc.accessmrs.data.Observation;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class ViewPatientActivity extends BasePatientListActivity {

	private static final int OBTAIN_CONSENT = 1;
	private static final int VIEW_CONSENT = 2;
	private static final String TAG = ViewPatientActivity.class.getSimpleName();
	private static Patient mPatient;
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();
	private String patientIdStr;
	private Context mContext;
	private Resources res;
	private Integer mConsent;
	private OnTouchListener mConsentListener;
	private OnTouchListener mFormListener;
	private OnTouchListener mFormHistoryListener;
	private OnTouchListener mSwipeListener;
	private GestureDetector mSwipeDetector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.view_patient);
		mContext = this;
		res = this.getResources();

		patientIdStr = getIntent().getStringExtra(KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = Db.open().getPatient(patientId);

		if (mPatient == null) {
			if (App.DEBUG)
				Log.e(TAG, "mPatient is missing?!");
			UiUtils.toastAlert(mContext, getString(R.string.error_db), getString(R.string.error, R.string.no_patient));
			finish();
		} else {

			if (mPatient.getPatientId() < 0)
				mConsent = -1;
			else
				mConsent = mPatient.getConsent();
		}
		final GestureDetector mFormDetector = new GestureDetector(new onFormClick());
		mFormListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormDetector.onTouchEvent(event);
			}
		};

		final GestureDetector mFormHistoryDetector = new GestureDetector(new onFormHistoryClick());
		mFormHistoryListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mFormHistoryDetector.onTouchEvent(event);
			}
		};

		final GestureDetector mConsentDetector = new GestureDetector(new onConsentClick());
		mConsentListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mConsentDetector.onTouchEvent(event);
			}
		};

		mSwipeDetector = new GestureDetector(new myGestureListener());
		mSwipeListener = new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return mSwipeDetector.onTouchEvent(event);
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mPatient != null) {

			if (mConsent == null) {
				Intent i = new Intent(getApplicationContext(), PatientConsentActivity.class);
				i.putExtra(KEY_PATIENT_ID, patientIdStr);
				startActivityForResult(i, OBTAIN_CONSENT);
			}

			getAllObservations(mPatient.getPatientId());
			getPatientForms(mPatient.getPatientId());
			mPatient.setTotalCompletedForms(findPreviousEncounters());
			createPatientHeader(mPatient.getPatientId());
			refreshView();

		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case OBTAIN_CONSENT:
			if (resultCode == RESULT_OK) {
				mPatient = Db.open().getPatient(Integer.valueOf(patientIdStr));
				mConsent = mPatient.getConsent();
			} else {
				mConsent = DataModel.CONSENT_DECLINED;
			}
			break;
		case VIEW_CONSENT:
			if (resultCode == RESULT_OK) {
				mPatient = Db.open().getPatient(Integer.valueOf(patientIdStr));
				mConsent = mPatient.getConsent();
			}
		default:
			break;
		}

		// Bundle b = data.getExtras();
		// int consent = b.getInt(DataModel.CONSENT_VALUE);
		// if (App.DEBUG)
		// Log.v(TAG, "Consent Result = " + consent);
	}

	private View formHistoryView() {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

		// formsSummary.setOnClickListener(new View.OnClickListener() {
		// public void onClick(View v) {
		// Intent i = new Intent(getApplicationContext(),
		// ViewCompletedForms.class);
		// i.putExtra(KEY_PATIENT_ID, patientIdStr);
		// startActivity(i);
		//
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

			allFormTitle.setText("View All Visits");

			if (priorityNumber != null && priorityImage != null) {
				priorityNumber.setText(mPatient.getTotalCompletedForms().toString());
				priorityImage.setVisibility(View.VISIBLE);
			}
		}

		return (formsSummary);
	}

	private boolean checkForForms() {

		boolean checkForms = false;
		Cursor c = Db.open().fetchAllForms();

		if (c != null) {
			if (c.getCount() >= 0)
				checkForms = true;
			c.close();
		}

		return checkForms;
	}

	private void getPatientForms(Integer patientId) {

		Cursor c = Db.open().fetchPatient(patientId);

		if (c != null) {
			if (c.moveToFirst()) {
				int savedNumberIndex = c.getColumnIndexOrThrow(DataModel.KEY_SAVED_FORM_NUMBER);
				mPatient.setSavedNumber(c.getInt(savedNumberIndex));
				if (c.getInt(savedNumberIndex) > 0)
					mPatient.setSaved(true);
				else
					mPatient.setSaved(false);
				if (App.DEBUG)
					Log.v(TAG, "Setting Saved Form Number to = " + c.getInt(savedNumberIndex));

				int priorityIndex = c.getColumnIndexOrThrow(DataModel.KEY_PRIORITY_FORM_NUMBER);
				if (mConsent != null && mConsent == DataModel.CONSENT_OBTAINED) {
					mPatient.setPriorityNumber(c.getInt(priorityIndex));
					if (c.getInt(priorityIndex) > 0)
						mPatient.setPriority(true);
					else
						mPatient.setPriority(false);
				} else {
					mPatient.setPriorityNumber(0);
					mPatient.setPriority(false);
				}

			}

			c.close();
		}
	}

	// TODO Performance: Improve SQL query to get only latest obs
	private void getAllObservations(Integer patientId) {
		if (mConsent != null && mConsent == DataModel.CONSENT_OBTAINED)
			mObservations = Db.open().fetchPatientObservationList(patientId);
		else
			mObservations.clear();
	}

	protected void refreshView() {

		View mFormView;
		MergeAdapter adapter = new MergeAdapter();

		// Forms Section
		ArrayAdapter<Observation> obsAdapter = new ObservationAdapter(this, R.layout.observation_list_item, mObservations);
		mFormView = formView();

		adapter.addView(buildSectionLabel(getString(R.string.clinical_form_section), true));
		adapter.addView(mFormView);
		mFormView.setOnTouchListener(mFormListener);

		// Clinical Observations Section
		if (!mObservations.isEmpty()) {
			adapter.addView(buildSectionLabel(getString(R.string.clinical_data_section), true));
			adapter.addAdapter(obsAdapter);
		}

		// Previous Encounters Section
		if (mPatient.getTotalCompletedForms() > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.form_history_section), true));
			View v = formHistoryView();
			adapter.addView(v);
			v.setOnTouchListener(mFormHistoryListener);
		}

		// Consent Section (don't add for patients who have no OpenMRS data)
		if (mConsent == null || mConsent >= 0) {
			adapter.addView(buildSectionLabel(getString(R.string.consent_section), true));
			View cv = consentView();
			adapter.addView(cv);
			cv.setOnTouchListener(mConsentListener);
		}

		ListView lv = getListView();
		lv.setAdapter(adapter);
		lv.setOnTouchListener(mSwipeListener);
	}

	private View formView() {
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View formSummaryGroup = vi.inflate(R.layout.form_summary_group, null);
		ViewGroup parent = (ViewGroup) formSummaryGroup.findViewById(R.id.vertical_container);
		formSummaryGroup.setClickable(true);

		// formSummaryGroup.setOnClickListener(new View.OnClickListener() {
		// public void onClick(View v) {
		// if (checkForForms()) {
		// Intent i = new Intent(getApplicationContext(), ViewAllForms.class);
		// i.putExtra(KEY_PATIENT_ID, patientIdStr);
		// startActivity(i);
		// } else {
		// UiUtils.toastAlert(mContext, getString(R.string.error),
		// getString(R.string.no_forms));
		// }
		// }
		// });

		ImageView formArrow = (ImageView) formSummaryGroup.findViewById(R.id.arrow_image);

		if (formArrow != null) {
			if (mPatient.getPriority()) {
				formArrow.setImageResource(R.drawable.arrow_red);
				View priorityCounter = vi.inflate(R.layout.form_summary, null);
				ImageView priorityImage = (ImageView) priorityCounter.findViewById(R.id.counter_image);
				priorityImage.setImageDrawable(res.getDrawable(R.drawable.priority));
				TextView priorityNumber = (TextView) priorityCounter.findViewById(R.id.counter_number);
				TextView priorityText = (TextView) priorityCounter.findViewById(R.id.counter_text);
				if (priorityNumber != null && priorityText != null) {
					priorityNumber.setText(mPatient.getPriorityNumber().toString());
					priorityText.setText(R.string.to_do_forms);
					priorityText.append(" ");
				}
				parent.addView(priorityCounter);

			} else {
				formArrow.setImageResource(R.drawable.arrow_gray);

			}
		}

		if (mPatient.getSaved()) {
			View savedCounter = vi.inflate(R.layout.form_summary, null);
			ImageView savedImage = (ImageView) savedCounter.findViewById(R.id.counter_image);
			savedImage.setImageDrawable(res.getDrawable(R.drawable.incomplete));
			TextView savedNumber = (TextView) savedCounter.findViewById(R.id.counter_number);
			TextView savedText = (TextView) savedCounter.findViewById(R.id.counter_text);
			if (savedNumber != null && savedText != null) {
				savedNumber.setText(mPatient.getSavedNumber().toString());
				savedText.setText(R.string.incomplete_forms);
				savedText.append(" ");
			}
			parent.addView(savedCounter);
		}

		return (formSummaryGroup);
	}

	private Integer findPreviousEncounters() {
		Integer completedForms = 0;
		String patientIdStr = String.valueOf(mPatient.getPatientId());
		String selection = "(" + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? or " + InstanceColumns.STATUS + "=? ) and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_ENCRYPTED, InstanceProviderAPI.STATUS_COMPLETE, InstanceProviderAPI.STATUS_SUBMITTED, patientIdStr };
		Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, new String[] { InstanceColumns.PATIENT_ID, "count(*) as count" }, selection, selectionArgs, null);

		if (c != null) {
			if (c.moveToFirst()) {
				// if (patientIdStr ==
				// c.getString(c.getColumnIndex(InstanceColumns.PATIENT_ID))) {
				completedForms = c.getInt(c.getColumnIndex("count"));
				// }
			}
			c.close();
		}
		return completedForms;
	}

	private View consentView() {

		View consentSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		consentSummary = vi.inflate(R.layout.consent_summary, null);
		consentSummary.setClickable(true);

		ImageView arrow = (ImageView) consentSummary.findViewById(R.id.arrow_image);
		RelativeLayout consentBlock = (RelativeLayout) consentSummary.findViewById(R.id.prior_consent_text);
		TextView noConsent = (TextView) consentSummary.findViewById(R.id.no_prior_consent);
		TextView consentDateView = (TextView) consentSummary.findViewById(R.id.consent_obtained_date);
		TextView expiryDateView = (TextView) consentSummary.findViewById(R.id.consent_expiry_date);

		if (arrow != null && consentBlock != null && noConsent != null) {

			// default visibility
			noConsent.setVisibility(View.VISIBLE);
			consentBlock.setVisibility(View.GONE);

			if (App.DEBUG)
				Log.v(TAG, "\tPatient Consent =" + String.valueOf(mPatient.getConsent()) + "\n\tConsent Date=" + String.valueOf(mPatient.getConsentDate()));
			if (mPatient.getConsent() == null) {

				Long consentTime = mPatient.getConsentDate();
				if (consentTime != null) {
					// OPTION 1: Prior Consent Expired
					noConsent.setTextColor(getResources().getColor(R.color.priority));
					arrow.setImageResource(R.drawable.arrow_red);
					noConsent.setText(getString(R.string.consent_expired));
				} else {
					// OPTION 2: Never Received and temporarily Deferred
					noConsent.setTextColor(getResources().getColor(R.color.dark_gray));
					arrow.setImageResource(R.drawable.arrow_gray);
					noConsent.setText(getString(R.string.consent_deferred));
				}

			} else if (mPatient.getConsent() == DataModel.CONSENT_DECLINED) {
				// OPTION 3: Prior Consent Declined
				arrow.setImageResource(R.drawable.arrow_red);
				noConsent.setText(getString(R.string.consent_declined));
				noConsent.setTextColor(getResources().getColor(R.color.priority));

			} else if (mPatient.getConsent() == DataModel.CONSENT_OBTAINED && consentDateView != null && expiryDateView != null) {
				// OPTION 4: Consent is Active
				arrow.setImageResource(R.drawable.arrow_gray);
				noConsent.setVisibility(View.GONE);
				consentBlock.setVisibility(View.VISIBLE);

				// Set Obtained and Expiry Dates
				Long consentTime = mPatient.getConsentDate();
				if (consentTime != null) {
					// Set Consent Obtained Date
					Date consentDate = new Date();
					consentDate.setTime(consentTime);
					String consentString = new SimpleDateFormat("MMM dd, yyyy").format(consentDate) + " ";
					consentDateView.setText(consentString);
				}

				Long expiryTime = mPatient.getConsentExpirationDate();
				if (expiryTime != null) {
					// Set Consent Expiration Date
					Date expiryDate = new Date();
					expiryDate.setTime(expiryTime);
					String expiryString = new SimpleDateFormat("MMM dd, yyyy").format(expiryDate) + " ";
					expiryDateView.setText(expiryString);
				}

			}
		}

		return (consentSummary);
	}

	protected class onFormClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			if (checkForForms()) {
				Intent i = new Intent(getApplicationContext(), ViewAllForms.class);
				i.putExtra(KEY_PATIENT_ID, patientIdStr);
				startActivity(i);
			} else {
				UiUtils.toastAlert(mContext, getString(R.string.error), getString(R.string.no_forms));
			}
			return false;
		}
	}

	protected class onFormHistoryClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			Intent i = new Intent(getApplicationContext(), ViewCompletedForms.class);
			i.putExtra(KEY_PATIENT_ID, patientIdStr);
			startActivity(i);
			return false;
		}

	}

	protected class onConsentClick extends myGestureListener {

		@Override
		public boolean onSingleTapUp(MotionEvent e) {

			if (mConsent == DataModel.CONSENT_OBTAINED) {
				Intent i = new Intent(getApplicationContext(), ViewConsentActivity.class);
				i.putExtra(KEY_PATIENT_ID, patientIdStr);
				startActivityForResult(i, VIEW_CONSENT);
			} else {
				Intent i = new Intent(getApplicationContext(), PatientConsentActivity.class);
				i.putExtra(KEY_PATIENT_ID, patientIdStr);
				startActivityForResult(i, OBTAIN_CONSENT);
			}

			return false;
		}

	}

}
