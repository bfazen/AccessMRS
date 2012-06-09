package org.odk.clinic.android.tasks;

public class EventLogger {

	private String eventName;
	private Long eventStart;
	private Long eventStop;
	private Boolean sameEvent;

	public void setEventStart(String eventname) {
		eventName = eventname;
		eventStart = System.currentTimeMillis();
	}

	public void setEventStop(String eventname) {
		eventStop = System.currentTimeMillis();
		if (eventname != null && eventname == "eventStart") {
			sameEvent = true;
		}

	}

	public String getEventName() {
		return eventName;
	}

	public Long getEventStart() {
		return eventStart;
	}

	public Long getEventStop() {
		return eventStop;
	}
	
	public Boolean sameEvent() {
		return sameEvent;
	}

}
