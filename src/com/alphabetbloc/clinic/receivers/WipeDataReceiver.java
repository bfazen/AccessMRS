/***
  Copyright (c) 2011 CommonsWare, LLC
  
  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.alphabetbloc.clinic.receivers;

import java.io.IOException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.XmlResourceParser;
import android.util.Log;

import com.alphabetbloc.clinic.services.WakefulIntentService;
import com.alphabetbloc.clinic.services.WakefulIntentService.AlarmListener;
import com.alphabetbloc.clinic.services.WipeDataService;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * This is started either as result of alarm, or if no alarms, then as a result
 * of the power being turned on. Then we check to see if there is need to
 * encrypt data before canceling the alarm.
 * 
 * @author Louis Fazen (louis.fazen@gmail.com), commonsware
 * 
 */
public class WipeDataReceiver extends BroadcastReceiver {
	private static final String WAKEFUL_META_DATA = "com.commonsware.cwac.wakeful";
	private static final String TAG = WipeDataReceiver.class.getSimpleName();
	private static final String WIPE_DATA_FROM_ADMIN_SMS_REQUEST = "com.alphabetbloc.clinic.WIPE_DATA_FROM_ADMIN_SMS_REQUEST";
	public static final String WIPE_DATA_SERVICE = "com.alphabetbloc.clinic.WIPE_DATA_SERVICE";
	
	@Override
	public void onReceive(Context ctxt, Intent intent) {
		AlarmListener listener = getListener(ctxt);
		String action = intent.getAction();
		
		if (listener != null) {
			if (action == null || action.equalsIgnoreCase(WIPE_DATA_SERVICE)) {
		
				//Stared from ALARM MANAGER (action == null) OR Specific Broadcast
				SharedPreferences prefs = ctxt.getSharedPreferences(WakefulIntentService.NAME, 0);
				
				// check for intent
				boolean wipeData = intent.getBooleanExtra(WIPE_DATA_FROM_ADMIN_SMS_REQUEST, false);
				if (wipeData)
					prefs.edit().putBoolean(WipeDataService.WIPE_DATA_REQUESTED, wipeData).commit();
				// check for existing wipe data request
				wipeData = prefs.getBoolean(WipeDataService.WIPE_DATA_REQUESTED, false);
				if (wipeData) {
					prefs.edit().putLong(WakefulIntentService.WIPE_DATA, System.currentTimeMillis()).commit();
					listener.sendWakefulWork(ctxt);
				} else {
					Log.d(TAG, "Not wiping data...");
				}
				// otherwise, do nothing
			} else {
				//Stared from a BOOT_COMPLETED broadcast
				WakefulIntentService.scheduleAlarms(listener, WakefulIntentService.WIPE_DATA, ctxt, true);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private WakefulIntentService.AlarmListener getListener(Context ctxt) {
		PackageManager pm = ctxt.getPackageManager();
		ComponentName cn = new ComponentName(ctxt, getClass());

		try {
			ActivityInfo ai = pm.getReceiverInfo(cn, PackageManager.GET_META_DATA);
			XmlResourceParser xpp = ai.loadXmlMetaData(pm, WAKEFUL_META_DATA);

			while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
				if (xpp.getEventType() == XmlPullParser.START_TAG) {
					if (xpp.getName().equals("WakefulIntentService")) {
						String clsName = xpp.getAttributeValue(null, "listener");
						Class<AlarmListener> cls = (Class<AlarmListener>) Class.forName(clsName);

						return (cls.newInstance());
					}
				}

				xpp.next();
			}
		} catch (NameNotFoundException e) {
			throw new RuntimeException("Cannot find own info???", e);
		} catch (XmlPullParserException e) {
			throw new RuntimeException("Malformed metadata resource XML", e);
		} catch (IOException e) {
			throw new RuntimeException("Could not read resource XML", e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Listener class not found", e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Listener is not public or lacks public constructor", e);
		} catch (InstantiationException e) {
			throw new RuntimeException("Could not create instance of listener", e);
		}

		return (null);
	}
}