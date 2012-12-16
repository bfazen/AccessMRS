package com.alphabetbloc.accessmrs.utilities;

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

import com.alphabetbloc.accessmrs.R;

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

	/**
	 * Convert a second duration to a string format
	 * 
	 * @param millis
	 *            A duration to convert to a string form
	 * @return A string of the form "X Days Y Hours Z Minutes A Seconds".
	 */
	// public static String getTimeString(long seconds) {
	// if (seconds < 0) {
	// // throw new
	// // IllegalArgumentException("Duration must be greater than zero!");
	// return "requested time is negative";
	// }
	//
	// int years = (int) (seconds / (60 * 60 * 24 * 365.25));
	// seconds -= (years * (60 * 60 * 24 * 365.25));
	// int days = (int) ((seconds / (60 * 60 * 24)) % 365.25);
	// seconds -= (days * (60 * 60 * 24));
	// int hours = (int) ((seconds / (60 * 60)) % 24);
	// seconds -= (hours * (60 * 60));
	// int minutes = (int) ((seconds / 60) % 60);
	// seconds -= (minutes * (60));
	//
	// StringBuilder sb = new StringBuilder(64);
	// if (years > 0) {
	// sb.append(years);
	// sb.append(" Years ");
	// }
	// if (days > 0 || years > 0) {
	// sb.append(days);
	// sb.append(" Days ");
	// }
	// if (hours > 0 || days > 0 || years > 0) {
	// sb.append(hours);
	// sb.append(" Hours ");
	// }
	// if (minutes > 0 || hours > 0 || days > 0 || years > 0) {
	// sb.append(minutes);
	// sb.append(" Min ");
	// }
	// sb.append(seconds);
	// sb.append(" Sec");
	//
	// return (sb.toString());
	// }
	public static String getTimeString(long seconds) {
		return getTimeString(seconds, 3, 0);
	}

	public static String getTimeString(long seconds, int periods, int remainderSecs) {
		if (seconds < 0) {
			// throw new
			// IllegalArgumentException("Duration must be greater than zero!");
			return "requested time is negative";
		}

		int yearSeconds = (int) (60 * 60 * 24 * 365.25);
		int monthSeconds = (60 * 60 * 24 * 30);
		int daySeconds = (60 * 60 * 24);
		int hourSeconds = (60 * 60);
		int minSeconds = 60;

		int years = (int) (seconds / yearSeconds);
		seconds -= (years * yearSeconds);
		int months = (int) (seconds / monthSeconds);
		seconds -= (months * monthSeconds);
		int days = (int) ((seconds / daySeconds));
		seconds -= (days * daySeconds);
		int hours = (int) ((seconds / hourSeconds));
		seconds -= (hours * hourSeconds);
		int minutes = (int) ((seconds / minSeconds));
		seconds -= (minutes * minSeconds);

		// if number of s is specified, then don't use periods
		if (remainderSecs > 0)
			periods = 0;
		// if only periods specified, dont use remainderMs logic
		else if (periods > 0 && remainderSecs == 0)
			remainderSecs = yearSeconds;

		StringBuilder sb = new StringBuilder(64);
		boolean add = false;
		int count = 1;
		if (years > 0) {
			sb.append(years);
			sb.append(" ");
			sb.append(App.getApp().getResources().getQuantityString(R.plurals.time_years, years));
			sb.append(" ");
			add = true;
			count++;
		}
		if ((periods >= count || remainderSecs < monthSeconds) && (months > 0 || add)) {
			sb.append(months);
			sb.append(" ");
			sb.append(App.getApp().getResources().getQuantityString(R.plurals.time_months, months));
			sb.append(" ");
			add = true;
			count++;
		}
		if ((periods >= count || remainderSecs < daySeconds) && (days > 0 || add)) {
			sb.append(days);
			sb.append(" ");
			sb.append(App.getApp().getResources().getQuantityString(R.plurals.time_days, days));
			sb.append(" ");
			add = true;
			count++;
		}
		if ((periods >= count || remainderSecs < hourSeconds) && (hours > 0 || add)) {
			sb.append(hours);
			sb.append(" ");
			sb.append(App.getApp().getResources().getQuantityString(R.plurals.time_hours, hours));
			sb.append(" ");
			add = true;
			count++;
		}
		if ((periods >= count || remainderSecs < minSeconds) && (minutes > 0 || add)) {
			sb.append(minutes);
			sb.append(" ");
			sb.append(App.getApp().getResources().getQuantityString(R.plurals.time_mins, minutes));
			sb.append(" ");
			add = true;
			count++;
		}
		if ((periods >= count || remainderSecs < seconds)) {
			sb.append(seconds);
			sb.append(" ");
			sb.append(App.getApp().getResources().getQuantityString(R.plurals.time_secs, (int) seconds));
		}

		return (sb.toString());
	}

}
