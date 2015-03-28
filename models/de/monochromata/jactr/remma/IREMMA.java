package de.monochromata.jactr.remma;

import org.jactr.core.chunktype.IChunkType;
import org.jactr.core.module.IModule;

public interface IREMMA extends IModule {

	public static final String BUFFER_NAME = "remma";
	
	/**
	 * Returns the configured encoding factor.
	 * 
	 */
	public double getEncodingFactor();
	
	/**
	 * Returns the configured encoding exponent factor.
	 */
	public double getEncodingExponentFactor();
	
	/**
	 * Returns a relative frequency of the given word, normalized to a range
	 * between 0.0 and 1.0 - Salvucci used the corpus of Francis and Kucera (1982)
	 * that provided frequencies relative to 1 million word occurrences. The
	 * implementation of EMMA in Java ACT-R uses a default frequency of 0.01
	 */
	public double getFrequency(Fixation.Word word);
	
	/**
	 * Returns the horizontal resolution in px per mm.
	 */
	public double getHorizontalResolutionPxPerMM();
	
	/**
	 * Returns the vertical resolution in px per mm.
	 */
	public double getVerticalResolutionPxPerMM();
	
	/**
	 * Returns the distance between the surface of the screen and the modelled
	 * eye in mm.
	 */
	public double getDistanceToScreenMM();
	
	/**
	 * The cancellable programming duration of a saccade in seconds.
	 */
	public double getCancellableProgrammingDurationS();
	
	/**
	 * The non-cancellable programming duration of a saccade in seconds.
	 */
	public double getNonCancellableProgrammingDurationS();
	
	/*
	 * The fixed execution duration of a saccade in seconds.
	 *
	public double getFixedExecutionDurationS();*/
	
	/*
	 * The variable part of the execution duration of a saccade in seconds per degree.
	 *
	public double getExecutionDurationPerDegreeS();*/
	
	public IChunkType getNextWordChunkType();
	
	public void addListener(IREMMAListener listener);
	public void removeListener(IREMMAListener listener);
	
}
