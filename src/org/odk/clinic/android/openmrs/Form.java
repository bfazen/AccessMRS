package org.odk.clinic.android.openmrs;


public class Form {

    private Integer formId = null;
    private String path = null;
    private String name = null;
    
//louis adding the following (i.e. not in yaw's code)
//    public Object item;
//    public String section;
//
//    public Form(final Object item, final String section) {
//        super();
//        this.item = item;
//        this.section = section;
//    }
    // end added
    
    @Override
    public String toString() {
        return name;
    }
    
    public Integer getFormId() {
        return formId;
    }
    
    public void setFormId(Integer formId) {
        this.formId = formId;
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
}
