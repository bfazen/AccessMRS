package org.odk.clinic.android.unused;

import java.util.List;

import com.alphabetbloc.clinic.R;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Observation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class EncounterAdapter extends ArrayAdapter<Observation> {

	public EncounterAdapter(Context context, int textViewResourceId,
			List<Observation> items) {
		super(context, textViewResourceId, items);
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		if (v == null) {
			LayoutInflater vi = (LayoutInflater) getContext().getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.encounter_list_item, null);
		}

		Observation obs = getItem(position);
		if (obs != null) {

			TextView textView = (TextView) v.findViewById(R.id.value_text);
			if (textView != null) {
				switch (obs.getDataType()) {
				case Constants.TYPE_INT:
					textView.setText(obs.getValueInt().toString());
					break;
				case Constants.TYPE_DOUBLE:
					textView.setText(obs.getValueNumeric().toString());
					break;
				case Constants.TYPE_DATE:
					textView.setText(obs.getValueDate());
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
