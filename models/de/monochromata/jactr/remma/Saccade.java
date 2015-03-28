package de.monochromata.jactr.remma;

public class Saccade extends LogEntry {

	public Saccade(String group, int trialId, String pageId,
			String condition, int number, long startTimestampMs,
			long durationMs) {
		super(group, trialId, pageId, condition,
				number, startTimestampMs, durationMs);
	}

	@Override
	public String toString() {
		return "Saccade [getGroup()=" + getGroup() + ", getTrialId()="
				+ getTrialId() + ", getPageId()=" + getPageId()
				+ ", getCondition()=" + getCondition() + ", getNumber()="
				+ getNumber() + ", getStartTimestampMs()="
				+ getStartTimestampMs() + ", getDurationMs()="
				+ getDurationMs() + "]";
	}
	
}
