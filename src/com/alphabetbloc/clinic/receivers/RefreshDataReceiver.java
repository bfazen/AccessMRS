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

import com.alphabetbloc.clinic.services.WakefulIntentService;
import com.alphabetbloc.clinic.services.WakefulIntentService.AlarmListener;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author Louis Fazen (louis.fazen@gmail.com), commonsware
 * 
 *         NB: On Connection to AC or USB power is a convenient time to check
 *         for an update: 1. it may mean you are closer to a power source and
 *         signal is better 2. it means we do not need to worry about battery
 *         dying mid-update 3. the user is probably not planning on using the
 *         phone while connected the more updates occur on battery connection,
 *         the fewer occur when not connected (saving battery later)
 */
//TODO!  DELETE if the SyncAdapter works!
public class RefreshDataReceiver extends BroadcastReceiver {
	private static final String WAKEFUL_META_DATA = "com.commonsware.cwac.wakeful";

	@Override
	public void onReceive(Context ctxt, Intent intent) {
		AlarmListener listener = getListener(ctxt);

		//TODO!: TESTING OUT THE SYNCADAPTER INSTEAD OF ALARMS>>>
//		if (listener != null) {
//			if (intent.getAction() == null) {
//				SharedPreferences prefs = ctxt.getSharedPreferences(WakefulIntentService.NAME, 0);
//
//				prefs.edit().putLong(WakefulIntentService.REFRESH_DATA, System.currentTimeMillis()).commit();
//
//				listener.sendWakefulWork(ctxt);
//			} else {
//				WakefulIntentService.scheduleAlarms(listener, WakefulIntentService.REFRESH_DATA, ctxt, true);
//			}
//		}
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