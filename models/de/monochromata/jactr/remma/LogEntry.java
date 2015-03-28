package de.monochromata.jactr.remma;

public class LogEntry {
	
	private final String group;
	private final int trialId;
	private final String pageId;
	private final String condition;
	private final int number;
	private final long startTimestampMs,
					   durationMs;
	
	protected LogEntry(String group, int trialId, String pageId,
			String condition, int number, long startTimestampMs,
			long durationMs) {
		this.group = group;
		this.trialId = trialId;
		this.pageId = pageId;
		this.condition = condition;
		this.number = number;
		this.startTimestampMs = startTimestampMs;
		this.durationMs = durationMs;
	}

	public String getGroup() {
		return group;
	}

	public int getTrialId() {
		return trialId;
	}

	public String getPageId() {
		return pageId;
	}

	public String getCondition() {
		return condition;
	}

	public int getNumber() {
		return number;
	}

	public long getStartTimestampMs() {
		return startTimestampMs;
	}

	public long getDurationMs() {
		return durationMs;
	}
	
	public double getDurationS() {
		return ((double)durationMs)/1000.0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((condition == null) ? 0 : condition.hashCode());
		result = prime * result + (int) (durationMs ^ (durationMs >>> 32));
		result = prime * result + ((group == null) ? 0 : group.hashCode());
		result = prime * result + number;
		result = prime * result + ((pageId == null) ? 0 : pageId.hashCode());
		result = prime * result
				+ (int) (startTimestampMs ^ (startTimestampMs >>> 32));
		result = prime * result + trialId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LogEntry other = (LogEntry) obj;
		if (condition == null) {
			if (other.condition != null)
				return false;
		} else if (!condition.equals(other.condition))
			return false;
		if (durationMs != other.durationMs)
			return false;
		if (group == null) {
			if (other.group != null)
				return false;
		} else if (!group.equals(other.group))
			return false;
		if (number != other.number)
			return false;
		if (pageId == null) {
			if (other.pageId != null)
				return false;
		} else if (!pageId.equals(other.pageId))
			return false;
		if (startTimestampMs != other.startTimestampMs)
			return false;
		if (trialId != other.trialId)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "LogEntry [group=" + group + ", trialId=" + trialId
				+ ", pageId=" + pageId + ", condition=" + condition
				+ ", number=" + number + ", startTimestampMs="
				+ startTimestampMs + ", durationMs=" + durationMs + "]";
	}
}
