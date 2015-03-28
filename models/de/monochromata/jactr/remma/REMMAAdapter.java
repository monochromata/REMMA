package de.monochromata.jactr.remma;

import org.jactr.core.chunk.IChunk;

import de.monochromata.jactr.remma.Fixation.Word;

/**
 * A default implementation of {@link IREMMAListener} that
 * ignores all events.
 */
public class REMMAAdapter implements IREMMAListener {

	@Override
	public void fixationStarted(Fixation fixation, double start) {
	}

	@Override
	public void fixationFinished(Fixation fixation, double start, double end) {
	}

	@Override
	public void encodingWord(Word word, IChunk chunk, double encodingStart,
			double encodingEnd) {
	}

}
