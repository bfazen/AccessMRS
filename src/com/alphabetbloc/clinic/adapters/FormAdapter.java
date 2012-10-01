package com.alphabetbloc.clinic.adapters;

import java.util.List;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.Form;

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
	int defaultLayoutId;
	Context mContext;

	/**
	 * FormAdapter is used for all lists of Forms now, including priority,
	 * saved, completed, additional, and instances
	 * 
	 * @param context
	 * @param textViewResourceId
	 *            The default layout resource id to use if no other parameters
	 *            supplied
	 * @param items
	 *            List of Forms
	 * @param priority
	 *            whether or not the form has an indicated priority
	 */

	public FormAdapter(Context context, int textViewResourceId, List<Form> items, Boolean priority) {
		super(context, textViewResourceId, items);
		mFormPriority = priority;
		defaultLayoutId = textViewResourceId;
		mContext = context;
	}

	public View getView(int position, View convertView, ViewGroup parent) {

		View v = convertView;
		LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(defaultLayoutId, null);
		Form form = getItem(position);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		TextView displayName = (TextView) v.findViewById(R.id.display_name);
		TextView completedDate = (TextView) v.findViewById(R.id.completed_date);
		ImageView imageView = (ImageView) v.findViewById(R.id.arrow_image);

		if (form != null) {
			if (textView != null)
				textView.setText(form.getName());

			// priority vs. non-priority
			if (mFormPriority) {
				textView.setTextColor(mContext.getResources().getColor(R.color.priority));
				imageView.setBackgroundResource(R.drawable.arrow_red);
			} else {
				textView.setTextColor(mContext.getResources().getColor(R.color.dark_gray));
				imageView.setBackgroundResource(R.drawable.arrow_gray);
			}
			
			// Saved Instances
			if (defaultLayoutId == R.layout.saved_instances) {
				if (displayName != null)
					displayName.setText(form.getDisplayName());
				if (completedDate != null)
					completedDate.setText(form.getDisplaySubtext() + " ");
			}

		}
		return v;
	}
}
