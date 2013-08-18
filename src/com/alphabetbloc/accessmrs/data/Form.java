package com.alphabetbloc.accessmrs.data;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author Yaw Anokwa (starting version was from ODK Clinic)
 */
public class Form {
	//_id in AccessForms instances.db
	private Integer instanceId = null;
	//number of form as posted in Xform
    private Integer formId = null;
    private String path = null;
    private String name = null;
    private Long date = null;
    private String displayName = null;
    private String displaySubtext = null;
	
    @Override
    public String toString() {
        return name;
    }
    
    public Integer getInstanceId() {
        return instanceId;
    }
    
    public void setInstanceId(Integer instanceid) {
        this.instanceId = instanceid;
    }
    
    
    public Integer getFormId() {
        return formId;
    }
    
    public void setFormId(Integer formid) {
        this.formId = formid;
    }
    
    public long getDate() {
        return date;
    }
    
    public void setDate(long l) {
        this.date = l;
    }
    
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    
    public void setDisplayName(String displayname) {
        this.displayName = displayname;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplaySubtext(String subtext) {
        this.displaySubtext = subtext;
    }
    
    public String getDisplaySubtext() {
        return displaySubtext;
    }
    
}
