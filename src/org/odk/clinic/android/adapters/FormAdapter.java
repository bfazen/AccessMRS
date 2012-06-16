package org.odk.clinic.android.adapters;

import java.util.List;

import org.odk.clinic.android.R;
import org.odk.clinic.android.openmrs.Form;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class FormAdapter extends ArrayAdapter<Form> {
	private boolean mFormPriority;
	
	public FormAdapter(Context context, int textViewResourceId, List<Form> items, Boolean priority) {
		super(context, textViewResourceId, items);
		mFormPriority = priority;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (v == null) {
			v = vi.inflate(R.layout.default_form_item, null);
		}
		if (mFormPriority == true){
			v = vi.inflate(R.layout.priority_form_item, null);
		}
		else {
			v = vi.inflate(R.layout.default_form_item, null);
		}
		Form form = getItem(position);
		if (form != null) {

			TextView textView = (TextView) v.findViewById(R.id.name_text);
			if (textView != null) {
				textView.setText(form.getName());
			}
		}
		return v;
	}

	
}
