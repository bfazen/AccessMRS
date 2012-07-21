/***
  Copyright (c) 2009-11 CommonsWare, LLC
  
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

package com.commonsware.cwac.wakeful;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.alphabetbloc.clinic.services.RefreshClientsService;
import com.commonsware.cwac.wakeful.WakefulIntentService;

public class AlarmListener implements WakefulIntentService.AlarmListener {
  
	public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctxt) {
		//set the alarm to wake up the device at interval of 1 day, and go off at inexact times (for better performance)
		mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime()+100000,
                            300000, pi);
		 Log.e("louis.fazen", "WakefulIntentService.scheduleAlarms  is called");
  }
	//I have set it to 5 minutes, but should be changed...!

  public void sendWakefulWork(Context ctxt) {
	  //want to run the business logic in a background thread, so call our service...
	  Log.e("louis.fazen", "sendWakefulWork is called");
    WakefulIntentService.sendWakefulWork(ctxt, RefreshClientsService.class);
  }

  public long getMaxAge() {
	  // if interval between alarms is > maxAge, then alarm probably was reset  by force-stopping application in settings, 
	  // so we need to reestablish the Alarm.  This logic is taken care of by WakefulIntentService scheduleAlarms()
	  // Here we only set the maxAge paramater, suggested to be 2xInterval by CommonsWare
    return(AlarmManager.INTERVAL_FIFTEEN_MINUTES*2);
  }
}
