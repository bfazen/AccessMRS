package org.odk.clinic.android.tasks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.odk.clinic.android.tasks.UpdateClockTask.ExecShell.SHELL_CMD;

import android.os.AsyncTask;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Config;
import android.util.Log;

//TODO: Delete this entire class?

public class UpdateClockTask extends AsyncTask<Void, Void, Void> {

    private Exception exception;
	private static final String TAG = "SntpClient-Me";

	private static final int REFERENCE_TIME_OFFSET = 16;
	private static final int ORIGINATE_TIME_OFFSET = 24;
	private static final int RECEIVE_TIME_OFFSET = 32;
	private static final int TRANSMIT_TIME_OFFSET = 40;
	private static final int NTP_PACKET_SIZE = 48;

	private static final int NTP_PORT = 123;
	private static final int NTP_MODE_CLIENT = 3;
	private static final int NTP_VERSION = 3;

	// Number of seconds between Jan 1, 1900 and Jan 1, 1970
	// 70 years plus 17 leap days
	private static final long OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L;

	// system time computed from NTP server response
	private long mNtpTime;

	// value of SystemClock.elapsedRealtime() corresponding to mNtpTime
	private long mNtpTimeReference;

	// round trip time in milliseconds
	private long mRoundTripTime;
	
	private int timeout = 10000;
	private String host = "pool.ntp.org";

    @Override
	protected Void doInBackground(Void... voids) {
    	try{
    	Log.e("UpdateClockTask", "UpdateClockTask is running!");
    	
    	getSystemTime();
    	if(requestTime()){
    		updateSystemClock();
    	}

		return null;
			
    	}
    	catch(Exception e){
    		if (Config.LOGD)
				Log.d(TAG, "request time failed: " + e);
    		return null;
    	}

	}
    
    
    private long getSystemTime(){
    	
    	Calendar c = Calendar.getInstance();
	    TimeZone z = c.getTimeZone();
	    
	    int offset = z.getRawOffset();
	    if(z.inDaylightTime(new Date())){
	        offset = offset + z.getDSTSavings();
	    }
	    int offsetHrs = offset / 1000 / 60 / 60;
	    int offsetMins = offset / 1000 / 60 % 60;

	    c.add(Calendar.HOUR_OF_DAY, (-offsetHrs));
	    c.add(Calendar.MINUTE, (-offsetMins));

	    System.out.println("Current Android GMT time: "+c.getTime());
	    Log.e(TAG, "sys is:" + c.getTime());
	    

//	    Africa/Nairobi
	    TimeZone nyctz = TimeZone.getTimeZone("America/New_York");
	    TimeZone nbotz = TimeZone.getTimeZone("Africa/Nairobi");
	    TimeZone defaulttz = TimeZone.getDefault();
	    
	    Log.e(TAG, "nyctz is: " + nyctz.getID());
	    Log.e(TAG, "nbotz is: " + nbotz.getID());
	    Log.e(TAG, "defaulttz is: " + defaulttz.getID());
	    
	    Calendar newC = null;
	    newC.setTimeZone(TimeZone.getTimeZone("Africa/Nairobi"));
	    
	    
	    
	    return c.getTimeInMillis();
    }
    

    
    private boolean requestTime() {
		try {
			DatagramSocket socket = new DatagramSocket();
			socket.setSoTimeout(timeout);
			InetAddress address = InetAddress.getByName(host);
			Log.e("Sntp Client", "SntpClient has inetaddress of: " + address);
			
			
			
			
			byte[] buffer = new byte[NTP_PACKET_SIZE];
			DatagramPacket request = new DatagramPacket(buffer, buffer.length,
					address, NTP_PORT);

			// set mode = 3 (client) and version = 3
			// mode is in low 3 bits of first byte
			// version is in bits 3-5 of first byte
			buffer[0] = NTP_MODE_CLIENT | (NTP_VERSION << 3);

			// get current time and write it to the request packet
			long requestTime = System.currentTimeMillis();
			long requestTicks = SystemClock.elapsedRealtime();
			writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime);

			socket.send(request);

			// read the response
			DatagramPacket response = new DatagramPacket(buffer, buffer.length);
			socket.receive(response);
			long responseTicks = SystemClock.elapsedRealtime();
			long responseTime = requestTime + (responseTicks - requestTicks);
			socket.close();

			// extract the results
			long originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET);
			long receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET);
			long transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET);
			long roundTripTime = responseTicks - requestTicks
					- (transmitTime - receiveTime);
			// receiveTime = originateTime + transit + skew
			// responseTime = transmitTime + transit - skew
			// clockOffset = ((receiveTime - originateTime) + (transmitTime -
			// responseTime))/2
			// = ((originateTime + transit + skew - originateTime) +
			// (transmitTime - (transmitTime + transit - skew)))/2
			// = ((transit + skew) + (transmitTime - transmitTime - transit +
			// skew))/2
			// = (transit + skew - transit + skew)/2
			// = (2 * skew)/2 = skew
			long clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2;
			// if (Config.LOGD) Log.d(TAG, "round trip: " + roundTripTime +
			// " ms");
			// if (Config.LOGD) Log.d(TAG, "clock offset: " + clockOffset +
			// " ms");

			// save our results - use the times on this side of the network
			// latency
			// (response rather than request time)
			mNtpTime = responseTime + clockOffset;
			mNtpTimeReference = responseTicks;
			mRoundTripTime = roundTripTime;
		} catch (Exception e) {
			if (Config.LOGD)
				Log.d(TAG, "request time failed: " + e);
			return false;
		}

		return true;
	}
    
    private void updateSystemClock() {
    	long now = mNtpTime + SystemClock.elapsedRealtime() - mNtpTimeReference;
    	long sys = getSystemTime();
		long sys2 = System.currentTimeMillis();
		
    	long delta = Math.abs(now - sys);
	
    	
    	/*
    	Long longDate = Long.valueOf(date);

    	Calendar cal = Calendar.getInstance();
    	int offset = cal.getTimeZone().getOffset(cal.getTimeInMillis());
    	Date da = new Date(); 
    	da = new Date(longDate-(long)offset);
    	cal.setTime(da);

    	String time =cal.getTime().toLocaleString(); 
    	//this is full string        

    	time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(da);
    	//this is only time

    	time = DateFormat.getDateInstance(DateFormat.MEDIUM).format(da);
    	//this is only date
	
    	DateFormat formatter = new SimpleDateFormat(dateFormat);
    	
    	*/
    	
    	Log.e(TAG, "now   is: " + now);
    	Log.e(TAG, "sys   is: " + sys);
    	Log.e(TAG, "sys2  is: " + sys2);
    	Log.e(TAG, "delta is:" + delta);
    	
    	if(delta > 60000){
    		if(isDeviceRooted()){
    			Log.e(TAG, "rooted!");
    			SystemClock.setCurrentTimeMillis(now);

    			
    			//doublecheck!
    			long newSys = System.currentTimeMillis();
    			long newDelta = Math.abs(now - newSys);
    			
    			Log.e(TAG, "new delta is:" + newDelta);
    			
    			if(newDelta == 0){
    				Log.e(TAG, "System clock has been updated to NTP time");
    				
    			}
    			
    		}
    		Log.e(TAG, "not rooted");
    	}
	
    }
    
    
    /**
	 * Reads an unsigned 32 bit big endian number from the given offset in the
	 * buffer.
	 */
	private long read32(byte[] buffer, int offset) {
		byte b0 = buffer[offset];
		byte b1 = buffer[offset + 1];
		byte b2 = buffer[offset + 2];
		byte b3 = buffer[offset + 3];

		// convert signed bytes to unsigned values
		int i0 = ((b0 & 0x80) == 0x80 ? (b0 & 0x7F) + 0x80 : b0);
		int i1 = ((b1 & 0x80) == 0x80 ? (b1 & 0x7F) + 0x80 : b1);
		int i2 = ((b2 & 0x80) == 0x80 ? (b2 & 0x7F) + 0x80 : b2);
		int i3 = ((b3 & 0x80) == 0x80 ? (b3 & 0x7F) + 0x80 : b3);

		return ((long) i0 << 24) + ((long) i1 << 16) + ((long) i2 << 8)
				+ (long) i3;
	}

	/**
	 * Reads the NTP time stamp at the given offset in the buffer and returns it
	 * as a system time (milliseconds since January 1, 1970).
	 */
	private long readTimeStamp(byte[] buffer, int offset) {
		long seconds = read32(buffer, offset);
		long fraction = read32(buffer, offset + 4);
		return ((seconds - OFFSET_1900_TO_1970) * 1000)
				+ ((fraction * 1000L) / 0x100000000L);
	}

	/**
	 * Writes system time (milliseconds since January 1, 1970) as an NTP time
	 * stamp at the given offset in the buffer.
	 */
	private void writeTimeStamp(byte[] buffer, int offset, long time) {
		long seconds = time / 1000L;
		long milliseconds = time - seconds * 1000L;
		seconds += OFFSET_1900_TO_1970;

		// write seconds in big endian format
		buffer[offset++] = (byte) (seconds >> 24);
		buffer[offset++] = (byte) (seconds >> 16);
		buffer[offset++] = (byte) (seconds >> 8);
		buffer[offset++] = (byte) (seconds >> 0);

		long fraction = milliseconds * 0x100000000L / 1000L;
		// write fraction in big endian format
		buffer[offset++] = (byte) (fraction >> 24);
		buffer[offset++] = (byte) (fraction >> 16);
		buffer[offset++] = (byte) (fraction >> 8);
		// low order bits should be random data
		buffer[offset++] = (byte) (Math.random() * 255.0);
	}
    
    
	
	/**
	 * @author Kevin Kowalewski
	 * 
	 */

//	    private static String LOG_TAG = Root.class.getName();

	    public boolean isDeviceRooted() {
	        if (checkRootMethod1()){return true;}
	        if (checkRootMethod2()){return true;}
	        if (checkRootMethod3()){return true;}
	        return false;
	    }

	    public boolean checkRootMethod1(){
	        String buildTags = android.os.Build.TAGS;

	        if (buildTags != null && buildTags.contains("test-keys")) {
	            return true;
	        }
	        return false;
	    }

	    public boolean checkRootMethod2(){
	        try {
	            File file = new File("/system/app/Superuser.apk");
	            if (file.exists()) {
	                return true;
	            }
	        } catch (Exception e) { }

	        return false;
	    }

	    public boolean checkRootMethod3() {
	        if (new ExecShell().executeCommand(SHELL_CMD.check_su_binary) != null){
	            return true;
	        }else{
	            return false;
	        }
	    }
	


	/**
	 * @author Kevin Kowalewski
	 *
	 */
	public static class ExecShell {

	    private String LOG_TAG = ExecShell.class.getName();

	    public static enum SHELL_CMD {
	        check_su_binary(new String[] {"/system/xbin/which","su"}),
	        ;

	        String[] command;

	        SHELL_CMD(String[] command){
	            this.command = command;
	        }
	    }

	    public ArrayList<String> executeCommand(SHELL_CMD shellCmd){
	        String line = null;
	        ArrayList<String> fullResponse = new ArrayList<String>();
	        Process localProcess = null;

	        try {
	            localProcess = Runtime.getRuntime().exec(shellCmd.command);
	        } catch (Exception e) {
	            return null;
	            //e.printStackTrace();
	        }

	        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(localProcess.getOutputStream()));
	        BufferedReader in = new BufferedReader(new InputStreamReader(localProcess.getInputStream()));

	        try {
	            while ((line = in.readLine()) != null) {
	                Log.d(LOG_TAG, "--> Line received: " + line);
	                fullResponse.add(line);
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        Log.d(LOG_TAG, "--> Full response was: " + fullResponse);

	        return fullResponse;
	    }

	}
	
	
	
	
	@Override
	protected void onCancelled() {
		// TODO Auto-generated method stub
		super.onCancelled();
	}

	
//	protected void onPostExecute(String result) {
//		//  Auto-generated method stub
//		super.onPostExecute(result);
//	}
//	@Override
//	protected void onPostExecute() {
//		//  Auto-generated method stub
//		super.onPostExecute();
//	}

	@Override
	protected void onPreExecute() {
		// TODO Auto-generated method stub
		super.onPreExecute();
	}

	@Override
	protected void onProgressUpdate(Void... values) {
		// TODO Auto-generated method stub
		super.onProgressUpdate(values);
	}
 }
