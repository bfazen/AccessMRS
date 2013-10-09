/*
 * Copyright (C) 2009 University of Washington
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

import com.alphabetbloc.accessmrs.providers.DataModel;

/**
 * 
 * @author Yaw Anokwa (starting version was from ODK Clinic)
 *
 */
public class Observation {

	private Integer patientId = null;
	private Double valueNumeric;
	private Integer valueInt;
	private String valueDate;
	private String valueText;
	private String encounterDate;
	private String fieldName;
	private byte dataType;

	public String toString() {
		switch (dataType) {
			case DataModel.TYPE_INT :
				return valueInt.toString();
			case DataModel.TYPE_DOUBLE:
				return valueNumeric.toString();
			case DataModel.TYPE_DATE :
				return valueDate.toString();
			default :
				return valueText;
		}
	}

	public Integer getPatientId() {
		return patientId;
	}

	public void setPatientId(Integer patientId) {
		this.patientId = patientId;
	}

	public Double getValueNumeric() {
		return valueNumeric;
	}

	public void setValueNumeric(Double valueNumeric) {
		this.valueNumeric = valueNumeric;
	}

	public String getValueDate() {
		return valueDate;
	}

	public void setValueDate(String valueDate) {
		this.valueDate = valueDate;
	}

	public String getValueText() {
		return valueText;
	}

	public void setValueText(String valueText) {
		this.valueText = valueText;
	}

	public String getEncounterDate() {
		return encounterDate;
	}

	public void setEncounterDate(String encounterDate) {
		this.encounterDate = encounterDate;
	}

	public Integer getValueInt() {
		return valueInt;
	}

	public void setValueInt(Integer valueInt) {
		this.valueInt = valueInt;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public byte getDataType() {
		return dataType;
	}

	public void setDataType(byte dataType) {
		this.dataType = dataType;
	}
}