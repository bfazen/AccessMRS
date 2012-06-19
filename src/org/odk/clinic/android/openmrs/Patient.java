package org.odk.clinic.android.openmrs;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.util.Log;

public class Patient {

	private Integer patientId = null;
	private String identifier = null;

	private String givenName = null;
	private String familyName = null;
	private String middleName = null;

	private String gender = null;
	
//	private String birthDateDb = null;
	private String birthDate = null;
	private String age = null;
	private Boolean priorityStatus = false;
	private Integer priorityFormNumber = null;
	private String priorityForms = null;
	
	private Integer savedFormNumber = null;
	private String savedForms = null;
	
	private Integer totalCompletedForms = null;
	
	public Patient() {

	}

	@Override
	public String toString() {
		return givenName + " " + middleName + " " + familyName + " " + identifier;
	}

	public Integer getPatientId() {
		return patientId;
	}

	public String getIdentifier() {
		return identifier;
	}

	public String getGivenName() {
		return givenName;
	}

	public String getFamilyName() {
		return familyName;
	}

	public String getMiddleName() {
		return middleName;
	}

	public String getBirthdate() {
		return birthDate;
	}
	
	public String getAge() {
		return age;
	}

	public String getGender() {
		return gender;
	}
	
	public Boolean getPriority() {
		return priorityStatus;
	}
	
	public Integer getPriorityNumber() {
		return priorityFormNumber;
	}
	
	public String getPriorityForms() {
		return priorityForms;
	}

	public Integer getSavedNumber() {
		return savedFormNumber;
	}
	
	public String getSavedForms() {
		return savedForms;
	}
	
	
	
	
	public void setPatientId(Integer pid) {
		patientId = pid;
	}

	public void setIdentifier(String id) {
		identifier = id;
	}

	public void setFamilyName(String n) {
		familyName = n;
	}

	public void setGivenName(String n) {
		givenName = n;
	}

	public void setMiddleName(String n) {
		middleName = n;
	}

	public void setPriority(Boolean priority) {
		priorityStatus = priority;
	}
	
	public void setPriorityNumber(Integer formNumber) {
		priorityFormNumber = formNumber;
	}
	
	public void setPriorityForms(String forms) {
		priorityForms = forms;
	}
	
	public void setSavedNumber(Integer formNumber) {
		savedFormNumber = formNumber;
	}
	
	public void setSavedForms(String forms) {
		savedForms = forms;
	}
	
	public void setTotalCompletedForms(Integer totalforms) {
		totalCompletedForms = totalforms;
	}
	
	public Integer getTotalCompletedForms() {
		return totalCompletedForms;
	}
	
	public void setBirthDate(String date) {
		birthDate = date;
		
		// set Age as well
		Date patientBirthDate = null;
		String outputAge = "";
		
		SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy");
		try {
			patientBirthDate = inputFormat.parse(date);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Date systemDate = Calendar.getInstance().getTime();
		Long ageMillis = systemDate.getTime() - patientBirthDate.getTime();
		if (ageMillis < 0 || patientBirthDate == null) {
			outputAge = "unknown";
		} else {
			int years = (int) (ageMillis / (1000 * 60 * 60 * 24 * 365.25));
			outputAge = String.valueOf(years) + " yo";
		}
		age = outputAge;
	}

	public void setGender(String g) {
		gender = g;
	}

	public String getName() {
		String name = "";

		if (givenName != null)
			name = givenName;

		if (middleName != null) {
			if (name.length() > 0)
				name += " ";

			name += middleName;
		}

		if (familyName != null) {
			if (name.length() > 0)
				name += " ";

			name += familyName;
		}

		return name;
	}
}
