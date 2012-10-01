package org.odk.clinic.android.unused;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.odk.clinic.android.openmrs.Patient;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.ui.user.ViewDataActivity;
import com.alphabetbloc.clinic.utilities.FileUtils;

public class ObservationChartActivity extends Activity {

	private Patient mPatient;
	private String mObservationFieldName;

	private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();

	private GraphicalView mChartView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.observation_chart);
		
		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			finish();
		}

		String patientIdStr = getIntent().getStringExtra(ViewDataActivity.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);
		if (mPatient == null) {
			showCustomToast(getString(R.string.error, R.string.no_patient));
			finish();
		}
		
		mObservationFieldName = getIntent().getStringExtra(ViewDataActivity.KEY_OBSERVATION_FIELD_NAME);
		
		setTitle(getString(R.string.app_name) + " > "
				+ getString(R.string.view_patient_detail));
		
		TextView textView = (TextView) findViewById(R.id.title_text);
		if (textView != null) {
			textView.setText(mObservationFieldName);
		}
		
		XYSeriesRenderer r = new XYSeriesRenderer();
		r.setLineWidth(3.0f);
		r.setColor(getResources().getColor(R.color.chart_red));
		r.setPointStyle(PointStyle.CIRCLE);
		r.setFillPoints(true);
		
		mRenderer.addSeriesRenderer(r);
		mRenderer.setShowLegend(false);
		//mRenderer.setXTitle("Encounter Date");
		//mRenderer.setAxisTitleTextSize(18.0f);
		mRenderer.setLabelsTextSize(11.0f);
		//mRenderer.setXLabels(10);
		mRenderer.setShowGrid(true);
		mRenderer.setLabelsColor(getResources().getColor(android.R.color.black));
	}
	
	private Patient getPatient(Integer patientId) {

		Patient p = null;

		Cursor c = DbProvider.openDb().fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {
			int patientIdIndex = c
					.getColumnIndex(DbProvider.KEY_PATIENT_ID);
			int identifierIndex = c
					.getColumnIndex(DbProvider.KEY_IDENTIFIER);
			int givenNameIndex = c
					.getColumnIndex(DbProvider.KEY_GIVEN_NAME);
			int familyNameIndex = c
					.getColumnIndex(DbProvider.KEY_FAMILY_NAME);
			int middleNameIndex = c
					.getColumnIndex(DbProvider.KEY_MIDDLE_NAME);
			int birthDateIndex = c
					.getColumnIndex(DbProvider.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(DbProvider.KEY_GENDER);
			
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

		return p;
	}
	
	private void getObservations(Integer patientId, String fieldName) {
		
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		Cursor c = DbProvider.openDb().fetchPatientObservation(patientId, fieldName);
		
		if (c != null && c.getCount() >= 0) {
			
			XYSeries series;
			if (mDataset.getSeriesCount() > 0) {
				series = mDataset.getSeriesAt(0);
				series.clear();
			} else {
				series = new XYSeries(fieldName);
				mDataset.addSeries(series);
			}

			int valueIntIndex = c.getColumnIndex(DbProvider.KEY_VALUE_INT);
			int valueNumericIndex = c.getColumnIndex(DbProvider.KEY_VALUE_NUMERIC);
			int encounterDateIndex = c.getColumnIndex(DbProvider.KEY_ENCOUNTER_DATE);
			int dataTypeIndex = c.getColumnIndex(DbProvider.KEY_DATA_TYPE);

			do {
				try {
					Date encounterDate = df.parse(c.getString(encounterDateIndex));
					int dataType = c.getInt(dataTypeIndex);
					
					double value;
					if (dataType == DbProvider.TYPE_INT) {
						value = c.getInt(valueIntIndex);
						series.add(encounterDate.getTime(), value);
					} else if (dataType == DbProvider.TYPE_DOUBLE) {
						value = c.getDouble(valueNumericIndex);
						series.add(encounterDate.getTime(), value);
					}
				} catch (ParseException e) {
					e.printStackTrace();
				}

			} while(c.moveToNext());
		}

		if (c != null) {
			c.close();
		}

	}
	
	@Override
	protected void onResume() {
		super.onResume();

		if (mPatient != null && mObservationFieldName != null) {
			getObservations(mPatient.getPatientId(), mObservationFieldName);
		}
		
		if (mChartView == null) {
			LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
			mChartView = ChartFactory.getTimeChartView(this, mDataset,
					mRenderer, null);
			layout.addView(mChartView, new LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		} else {
			mChartView.repaint();
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
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}
}
