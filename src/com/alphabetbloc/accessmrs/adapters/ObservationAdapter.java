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

//TODO!  This is a remnant from ODK Clinic and needs to be changed significantly to work...
// TODO! Add the ability to click into obs when there are many observations of the same type..
// TODO! redo the observation_list_item to make it thinner to allow for more obs to be veiwed at one time
// if many obs, should change the view, as was done with saved forms etc. 
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
