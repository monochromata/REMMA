package de.monochromata.jactr.remma;

import java.util.Set;

public class Fixation extends LogEntry {

	private final int porX, porY;
	private final RegressionInfo regressionInfo;
	private final Word foveatedWord;
	private final Set<Word> parafoveatedWords;
	private boolean fixationFollowsImmediately = false;
	
	public Fixation(String group, int trialId, String pageId,
			String condition, int number, long startTimestampMs,
			long durationMs, int porX, int porY,
			RegressionInfo regressionInfo, Word foveatedWord,
			Set<Word> parafoveatedWords) {
		super(group, trialId, pageId, condition, number,
				startTimestampMs, durationMs);
		this.porX = porX;
		this.porY = porY;
		this.regressionInfo = regressionInfo;
		this.foveatedWord = foveatedWord;
		this.parafoveatedWords = parafoveatedWords;
	}

	public int getPorX() {
		return porX;
	}

	public int getPorY() {
		return porY;
	}

	public boolean hasRegressionInfo() {
		return getRegressionInfo() != null;
	}
	
	public RegressionInfo getRegressionInfo() {
		return regressionInfo;
	}

	public Word getFoveatedWord() {
		return foveatedWord;
	}

	public Set<Word> getParafoveatedWords() {
		return parafoveatedWords;
	}
	
	public boolean isFixationFollowingImmediately() {
		return fixationFollowsImmediately;
	}
	
	public void setFixationFollowsImmediately(boolean value) {
		fixationFollowsImmediately = value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (fixationFollowsImmediately ? 1231 : 1237);
		result = prime * result
				+ ((foveatedWord == null) ? 0 : foveatedWord.hashCode());
		result = prime
				* result
				+ ((parafoveatedWords == null) ? 0 : parafoveatedWords
						.hashCode());
		result = prime * result + porX;
		result = prime * result + porY;
		result = prime * result
				+ ((regressionInfo == null) ? 0 : regressionInfo.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Fixation other = (Fixation) obj;
		if (fixationFollowsImmediately != other.fixationFollowsImmediately)
			return false;
		if (foveatedWord == null) {
			if (other.foveatedWord != null)
				return false;
		} else if (!foveatedWord.equals(other.foveatedWord))
			return false;
		if (parafoveatedWords == null) {
			if (other.parafoveatedWords != null)
				return false;
		} else if (!parafoveatedWords.equals(other.parafoveatedWords))
			return false;
		if (porX != other.porX)
			return false;
		if (porY != other.porY)
			return false;
		if (regressionInfo == null) {
			if (other.regressionInfo != null)
				return false;
		} else if (!regressionInfo.equals(other.regressionInfo))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Fixation [porX=" + porX + ", porY=" + porY
				+ ", regressionInfo=" + regressionInfo + ", foveatedWord="
				+ foveatedWord + ", parafoveatedWords=" + parafoveatedWords
				+ ", fixationFollowsImmediately=" + fixationFollowsImmediately
				+ ", getGroup()=" + getGroup() + ", getTrialId()="
				+ getTrialId() + ", getPageId()=" + getPageId()
				+ ", getCondition()=" + getCondition() + ", getNumber()="
				+ getNumber() + ", getStartTimestampMs()="
				+ getStartTimestampMs() + ", getDurationMs()="
				+ getDurationMs() + "]";
	}
	
	public static class RegressionInfo {
		
		private final int pathId,
						  id;
		private final String daia,
							 kind,
							 relationActivation;
		private final String uri;
		private final int line,
						  column;
		private final String word;
		
		public RegressionInfo(int pathId, int id, String daia, String kind,
				String relationActivation, String uri, int line, int column, String word) {
			this.pathId = pathId;
			this.id = id;
			this.daia = daia;
			this.kind = kind;
			this.relationActivation = relationActivation;
			this.uri = uri;
			this.line = line;
			this.column = column;
			this.word = word;
		}

		public int getPathId() {
			return pathId;
		}

		public int getId() {
			return id;
		}

		public String getDaia() {
			return daia;
		}

		public String getKind() {
			return kind;
		}

		public String getRelationActivation() {
			return relationActivation;
		}

		public String getUri() {
			return uri;
		}

		public int getLine() {
			return line;
		}

		public int getColumn() {
			return column;
		}

		public String getWord() {
			return word;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + column;
			result = prime * result + ((daia == null) ? 0 : daia.hashCode());
			result = prime * result + id;
			result = prime * result + ((kind == null) ? 0 : kind.hashCode());
			result = prime * result + line;
			result = prime * result + pathId;
			result = prime
					* result
					+ ((relationActivation == null) ? 0 : relationActivation
							.hashCode());
			result = prime * result + ((uri == null) ? 0 : uri.hashCode());
			result = prime * result + ((word == null) ? 0 : word.hashCode());
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
			RegressionInfo other = (RegressionInfo) obj;
			if (column != other.column)
				return false;
			if (daia == null) {
				if (other.daia != null)
					return false;
			} else if (!daia.equals(other.daia))
				return false;
			if (id != other.id)
				return false;
			if (kind == null) {
				if (other.kind != null)
					return false;
			} else if (!kind.equals(other.kind))
				return false;
			if (line != other.line)
				return false;
			if (pathId != other.pathId)
				return false;
			if (relationActivation == null) {
				if (other.relationActivation != null)
					return false;
			} else if (!relationActivation.equals(other.relationActivation))
				return false;
			if (uri == null) {
				if (other.uri != null)
					return false;
			} else if (!uri.equals(other.uri))
				return false;
			if (word == null) {
				if (other.word != null)
					return false;
			} else if (!word.equals(other.word))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "RegressionInfo [pathId=" + pathId + ", id=" + id
					+ ", daia=" + daia + ", kind=" + kind
					+ ", relationActivation=" + relationActivation + ", uri="
					+ uri + ", line=" + line + ", column=" + column + ", word="
					+ word + "]";
		}
	}

	public static class Word {
		
		private final String uri;
		private final int line, column, length;
		private final String word;
		private final int absoluteCenterX, absoluteCenterY;
		
		public Word(String uri, int line, int column, int length, String word,
				int absoluteCenterX, int absoluteCenterY) {
			this.uri = uri;
			this.line = line;
			this.column = column;
			this.length = length;
			this.word = word;
			this.absoluteCenterX = absoluteCenterX;
			this.absoluteCenterY = absoluteCenterY;
		}

		public String getUri() {
			return uri;
		}

		public int getLine() {
			return line;
		}

		public int getColumn() {
			return column;
		}

		public int getLength() {
			return length;
		}

		public String getWord() {
			return word;
		}

		public int getAbsoluteCenterX() {
			return absoluteCenterX;
		}

		public int getAbsoluteCenterY() {
			return absoluteCenterY;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + absoluteCenterX;
			result = prime * result + absoluteCenterY;
			result = prime * result + column;
			result = prime * result + length;
			result = prime * result + line;
			result = prime * result + ((uri == null) ? 0 : uri.hashCode());
			result = prime * result + ((word == null) ? 0 : word.hashCode());
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
			Word other = (Word) obj;
			if (absoluteCenterX != other.absoluteCenterX)
				return false;
			if (absoluteCenterY != other.absoluteCenterY)
				return false;
			if (column != other.column)
				return false;
			if (length != other.length)
				return false;
			if (line != other.line)
				return false;
			if (uri == null) {
				if (other.uri != null)
					return false;
			} else if (!uri.equals(other.uri))
				return false;
			if (word == null) {
				if (other.word != null)
					return false;
			} else if (!word.equals(other.word))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Word [uri=" + uri + ", line=" + line + ", column=" + column
					+ ", length=" + length + ", word=" + word
					+ ", absoluteCenterX=" + absoluteCenterX
					+ ", absoluteCenterY=" + absoluteCenterY + "]";
		}
		
	}
}
