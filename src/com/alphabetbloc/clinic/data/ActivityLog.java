package com.alphabetbloc.clinic.data;

public class ActivityLog {

	private String patientId;
	private String providerId;
	private String formId;
	private String priorityForm;
	private Long activityStartTime;
	private Long activityStopTime;
    private String launchType = null;
    
    public String getFormType() {
        return launchType;
    }
    
    public void setFormType(String type) {
        this.launchType = type;
    }
	
	public void setActivityStartTime() {
		activityStartTime = System.currentTimeMillis();
	}
	
	public void setActivityStopTime() {
		activityStopTime = System.currentTimeMillis();
	}

	public void setPatientId(String patientid) {
		patientId = patientid;
	}
	
	public void setProviderId(String providerid) {
		providerId = providerid;
	}
	
	public void setFormId(String formid) {
		formId = formid;
	}
	
	public void setFormPriority(String priorityform) {
		priorityForm = priorityform;
	}
	
	public Long getActivityStartTime() {
		return activityStartTime;
	}
	
	public Long getActivityStopTime() {
		return activityStopTime;
	}

	public String getPatientId() {
		return patientId;
	}
	
	public String getProviderId() {
		return providerId;
	}
	
	public String getFormId() {
		return formId;
	}
	
	public String getFormPriority() {
		return priorityForm;
	}
	
}
