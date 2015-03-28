package de.monochromata.jactr.remma;

import org.jactr.core.chunk.IChunk;

public interface IREMMAListener {
	
	public void fixationStarted(Fixation fixation, double start);
	public void fixationFinished(Fixation fixation, double start, double end);
	
	public void encodingWord(Fixation.Word word, IChunk chunk,
			double encodingStart, double encodingEnd);
}
