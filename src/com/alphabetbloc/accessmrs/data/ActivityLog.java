/*
 * Copyright (C) 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alphabetbloc.accessmrs.data;
/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
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
