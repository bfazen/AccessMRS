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
public class Certificate {

	private String sO = null;
	private String sOU = null;
	private String sE = null;
	private String sL = null;
	private String sST = null;
	private String sC = null;
	private String sCN = null;
	private String iO = null;
	private String mAlias = null;
	private String mName = null;
	private String mDate = null;
	
	
	public void setName(String name){
		mName = name;
	}
	
	public void setDate(String date){
		mDate = date;
	}
	
	public String getName(){
		return mName;
	}
	
	public String getDate(){
		return mDate;
	}
	
	public void setAlias(String alias){
		mAlias = alias;
	}
	
	public String getAlias(){
		return mAlias;
	}

	public void setO(String string) {
		this.sO = string;
	}

	public String getO() {
		if (sO != null)
			return sO;
		else
			return "Organization Name Not Provided";
	}

	public boolean hasLocation() {
		if (sL != null || sST != null || sC != null)
			return true;
		else
			return false;
	}

	public void setIO(String string) {
		this.iO = string;
	}

	public String getIO() {
		return iO;
	}

	public void setOU(String string) {
		this.sOU = string;
	}

	public String getOU() {
		return sOU;
	}

	public void setE(String string) {
		this.sE = string;
	}

	public String getE() {
		return sE;
	}

	public void setL(String string) {
		this.sL = string;
	}

	public String getL() {
		return sL;
	}

	public void setST(String string) {
		this.sST = string;
	}

	public String getST() {
		return sST;
	}

	public void setC(String string) {
		this.sC = string;
	}

	public String getC() {
		return sC;
	}

	public void setCN(String string) {
		this.sCN = string;
	}

	public String getCN() {
		return sCN;
	}

}
