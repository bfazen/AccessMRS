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
		toastMessage(null, null, message, 0, Gravity.CENTER, Toast.LENGTH_SHORT);
	}

	public static void toastText(Context context, String message) {
		toastMessage(context, null, message, 0, Gravity.CENTER, Toast.LENGTH_SHORT);
	}

	public static void toastText(String message, int position, int length) {
		toastMessage(null, null, message, 0, position, length);
	}

	public static void toastText(Context context, String message, int position, int length) {
		toastMessage(context, null, message, 0, position, length);
	}

	// Toast Alert Message
	public static void toastAlert(String title, String message) {
		toastMessage(null, title, message, R.drawable.ic_exclamation, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	public static void toastAlert(Context context, String title, String message) {
		toastMessage(context, title, message, R.drawable.ic_exclamation, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	// Toast Info Message
	public static void toastInfo(String title, String message) {
		toastMessage(null, title, message, android.R.drawable.ic_dialog_info, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	public static void toastInfo(Context context, String title, String message) {
		toastMessage(context, title, message, android.R.drawable.ic_dialog_info, Gravity.CENTER, Toast.LENGTH_LONG);
	}

	// Toast Sync Message
	public static void toastSyncMessage(Context context, String message, boolean error) {
		if (error)
			toastMessage(context, App.getApp().getString(R.string.sync_error), message, R.drawable.ic_exclamation, Gravity.BOTTOM, Toast.LENGTH_LONG);
		else
			toastMessage(context, null, message, 0, Gravity.BOTTOM, Toast.LENGTH_SHORT);
	}

	// Toast Sync Text (No context, always on bottom)
	public static void toastSyncText(String message) {
		toastMessage(null, null, message, 0, Gravity.BOTTOM, Toast.LENGTH_SHORT);
	}

	/**
	 * Toast a message to the UI.
	 * 
	 * @param context
	 *            Context in which to show the toast. If null, toast will be
	 *            shown on the UI thread (e.g. even if app is not in foreground)
	 * @param title
	 *            Title of the dialog. If null, a simple toast with no title bar
	 *            will be shown
	 * @param message
	 *            Content of the toast message
	 * @param titleIcon
	 *            Icon to display in the title, if title bar is displayed
	 * @param position
	 *            Position of the toast (based on Gravity constants)
	 * @param length
	 *            Duration of which to show the toast (based on Toast constants)
	 */
	public static void toastMessage(Context context, String title, String message, int titleIcon, final int position, final int length) {

		// set the view and message
		LayoutInflater inflater = (LayoutInflater) App.getApp().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(R.layout.toast_view, null);
		RelativeLayout titleRL = (RelativeLayout) view.findViewById(R.id.title_section);

		if (title != null) {
			titleRL.setVisibility(View.VISIBLE);
			TextView titleView = (TextView) view.findViewById(R.id.title);
			titleView.setText(title);
			ImageView imageView = (ImageView) view.findViewById(R.id.title_icon);
			imageView.setBackgroundResource(titleIcon);
		} else {
			titleRL.setVisibility(View.GONE);
		}

		TextView messageView = (TextView) view.findViewById(R.id.message);
		messageView.setText(message);

		if (context != null) {

			// Toast in the activity context
			Toast t = new Toast(context);
			t.setView(view);
			t.setDuration(length);
			if (position == Gravity.BOTTOM)
				t.setGravity(Gravity.BOTTOM, 0, -20);
			else if (position == Gravity.TOP)
				t.setGravity(Gravity.TOP, 0, +20);
			else
				t.setGravity(Gravity.CENTER, 0, 0);
			t.show();

		} else {

			// Toast on UI thread
			Handler h = new Handler(Looper.getMainLooper());
			h.post(new Runnable() {
				public void run() {
					try {
						Toast t = new Toast(App.getApp());
						t.setView(view);
						t.setDuration(length);
						if (position == Gravity.BOTTOM)
							t.setGravity(Gravity.BOTTOM, 0, -20);
						else
							t.setGravity(Gravity.CENTER, 0, 0);
						t.show();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});

		}

		// create the toast
		// Context ctx;
		// if (context != null)
		// ctx = context;
		// else
		// ctx = App.getApp();
		// final Toast t = new Toast(ctx);
		// t.setView(view);
		// t.setDuration(length);
		// if (position == Gravity.BOTTOM)
		// t.setGravity(Gravity.BOTTOM, 0, -20);
		// else
		// t.setGravity(Gravity.CENTER, 0, 0);
		//
		// Handler h = new Handler(Looper.getMainLooper());
		// h.post(new Runnable() {
		// public void run() {
		// try {
		// t.show();
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		// });

	}

}
