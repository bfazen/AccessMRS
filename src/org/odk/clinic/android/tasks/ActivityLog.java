package org.odk.clinic.android.tasks;

public class ActivityLog {

	private String startActivity;
	private String stopActivity;
	private String startParam;
	private Long activityStartTime;
	private Long activityStopTime;

	public void setActivityStartTime() {
		activityStartTime = System.currentTimeMillis();
	}

	public void setActivityStopTime() {
		activityStopTime = System.currentTimeMillis();
	}

	public void setStartActivity(String activityname) {
		startActivity = activityname;
	}
	
	public void setStopActivity(String activityname) {
		stopActivity = activityname;
	}
	
	public void setStartParam(String parametername) {
		startParam = parametername;
	}
	
	public String getStartActivity() {
		return startActivity;
	}

	public String getStopActivity() {
		return stopActivity;
	}
	
	public String getStartParam() {
		return startParam;
	}

	public Long getActivityStartTime() {
		return activityStartTime;
	}
	
	public Long getActivityStopTime() {
		return activityStopTime;
	}
	


}
