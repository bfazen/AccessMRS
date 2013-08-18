package com.alphabetbloc.accessmrs.adapters;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.data.Observation;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.R;

// TODO Feature: rewrite this to view observation details.  originally from ODK Clinic and needs to be changed significantly to work...
// 1. Add the ability to click into obs when there are many observations of the same type..
// 2. Redo the observation_list_item to make it thinner to allow for more obs to be veiwed at one time
// if many obs, should change the view, as was done with saved forms etc. 

/* Loading Obs Code From ViewPatientActivity
 * 3. Obs Graph on long press, unless only one value
 * 
 * @Override protected void onListItemClick(ListView listView, View view,
 * int position, long id) {
 * 
 * if (mPatient != null) { // Get selected observation Observation obs =
 * (Observation) getListAdapter().getItem(position);
 * 
 * Intent ip; int dataType = obs.getDataType(); if (dataType ==
 * DbProvider.TYPE_INT || dataType == DbProvider.TYPE_DOUBLE) { ip = new
 * Intent(getApplicationContext(), ObservationChartActivity.class);
 * ip.putExtra(KEY_PATIENT_ID, mPatient.getPatientId() .toString());
 * ip.putExtra(KEY_OBSERVATION_FIELD_NAME, obs.getFieldName());
 * startActivity(ip); } else { ip = new Intent(getApplicationContext(),
 * ObservationTimelineActivity.class); ip.putExtra(KEY_PATIENT_ID,
 * mPatient.getPatientId() .toString());
 * ip.putExtra(KEY_OBSERVATION_FIELD_NAME, obs.getFieldName());
 * startActivity(ip); } } }
 */

/**
 * @author Yaw Anokwa (starting version was from ODK Clinic)
 * 
 */
public class ObservationAdapter extends ArrayAdapter<Observation> {

	public ObservationAdapter(Context context, int textViewResourceId, List<Observation> items) {
		super(context, textViewResourceId, items);
	}

	public boolean isEnabled(int position) {
		return true;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return true;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		if (v == null) {
			LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.observation_list_item, null);
			v.setEnabled(false);
			v.setClickable(false);
			v.setSelected(false);

		}

		Observation obs = getItem(position);
		if (obs != null) {

			TextView textView = (TextView) v.findViewById(R.id.fieldname_text);
			if (textView != null) {
				textView.setText(obs.getFieldName());
			}

			textView = (TextView) v.findViewById(R.id.value_text);
			if (textView != null) {
				switch (obs.getDataType()) {
				case DataModel.TYPE_INT:
					textView.setText(obs.getValueInt().toString());
					break;
				case DataModel.TYPE_DOUBLE:
					textView.setText(obs.getValueNumeric().toString());
					break;
				case DataModel.TYPE_DATE:
					textView.setText(obs.getValueDate());
					break;
				default:
					textView.setText(obs.getValueText());
				}
			}

			textView = (TextView) v.findViewById(R.id.encounterdate_text);
			if (textView != null) {
				textView.setText(obs.getEncounterDate());
			}
		}
		return v;
	}
}
