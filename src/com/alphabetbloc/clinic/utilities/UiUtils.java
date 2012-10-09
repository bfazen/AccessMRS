package com.alphabetbloc.clinic.utilities;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;

public class UiUtils {

	// Toast Simple Text
	public static void toastText(String message) {
		toastMessage(null, false, null, message, 0, Gravity.CENTER, Toast.LENGTH_SHORT);
	}

	public static void toastText(Context context, String message) {
		toastMessage(context, false, null, message, 0, Gravity.CENTER, Toast.LENGTH_SHORT);
	}

	public static void toastText(String message, int position, int length) {
		toastMessage(null, false, null, message, 0, position, length);
	}

	public static void toastText(Context context, String message, int position, int length) {
		toastMessage(context, false, null, message, 0, position, length);
	}

	// Toast Alert Message
	public static void toastAlert(String title, String message) {
		toastMessage(null, true, title, message, R.drawable.ic_exclamation, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	public static void toastAlert(Context context, String title, String message) {
		toastMessage(context, true, title, message, R.drawable.ic_exclamation, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	// Toast Info Message
	public static void toastInfo(String title, String message) {
		toastMessage(null, true, title, message, android.R.drawable.ic_dialog_info, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	public static void toastInfo(Context context, String title, String message) {
		toastMessage(context, true, title, message, android.R.drawable.ic_dialog_info, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	// Toast Sync Message
	public static void toastSyncMessage(String message, boolean error) {
		if (error)
			toastMessage(null, true, App.getApp().getString(R.string.sync_error), message, R.drawable.ic_exclamation, Gravity.BOTTOM, Toast.LENGTH_SHORT);
		else
			toastMessage(null, false, null, message, 0, Gravity.BOTTOM, Toast.LENGTH_SHORT);
	}

	// Toast Sync Text (No context, always on bottom)
	public static void toastSyncText(String message) {
		toastMessage(null, false, null, message, 0, Gravity.BOTTOM, Toast.LENGTH_SHORT);
	}

	// Toast General Method
	public static void toastMessage(Context context, boolean showTitle, String title, String message, int titleIcon, int position, int length) {

		LayoutInflater inflater = (LayoutInflater) App.getApp().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);
		RelativeLayout titleRL = (RelativeLayout) view.findViewById(R.id.title_section);

		if (showTitle) {
			titleRL.setVisibility(View.VISIBLE);
			TextView titleView = (TextView) view.findViewById(R.id.title);
			titleView.setText(title);
			ImageView imageView = (ImageView) view.findViewById(R.id.title_icon);
			imageView.setBackgroundResource(titleIcon);
		} else {
			titleRL.setVisibility(View.VISIBLE);
		}

		TextView messageView = (TextView) view.findViewById(R.id.message);
		messageView.setText(message);

		final Toast t = new Toast(context);
		t.setView(view);
		t.setDuration(length);
		if (position == Gravity.BOTTOM)
			t.setGravity(Gravity.BOTTOM, 0, -20);
		else
			t.setGravity(Gravity.CENTER, 0, 0);

		if (context != null)
			t.show();
		else {
			Handler h = new Handler(Looper.getMainLooper());
			h.post(new Runnable() {
				public void run() {
					try {
						t.show();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}

	}

}
