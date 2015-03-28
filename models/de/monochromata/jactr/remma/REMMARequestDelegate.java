package de.monochromata.jactr.remma;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jactr.core.buffer.IActivationBuffer;
import org.jactr.core.buffer.delegate.AsynchronousRequestDelegate;
import org.jactr.core.chunk.IChunk;
import org.jactr.core.chunktype.IChunkType;
import org.jactr.core.logging.Logger;
import org.jactr.core.model.IModel;
import org.jactr.core.module.declarative.IDeclarativeModule;
import org.jactr.core.production.request.ChunkTypeRequest;
import org.jactr.core.production.request.IRequest;
import org.jactr.core.queue.ITimedEvent;
import org.jactr.core.queue.timedevents.BlockingTimedEvent;
import org.jactr.core.queue.timedevents.DelayedBufferInsertionTimedEvent;

import de.monochromata.jactr.remma.Fixation.Word;
import de.monochromata.jactr.tls.AnaphorInfo;
import de.monochromata.jactr.tls.ReferencePotential;
import de.monochromata.jactr.twm.ITWM;

public class REMMARequestDelegate extends AsynchronousRequestDelegate {

	private static final transient Log LOGGER = LogFactory.getLog(REMMARequestDelegate.class);
	
	private REMMAModule module;
	private REMMABuffer buffer;
	private IChunkType nextWordChunkType;
	private double cancellableProgrammingDurationS,
			 	   nonCancellableProgrammingDurationS/*,
			 	   fixedExecutionDurationS,
			 	   executionDurationPerDegreeS*/;
	
	private Encoding encodingState = new Encoding();
	private PreparationState preparationState = new PreparationState();
	private ExecutionState executionState = new Initialised(false);
	
	private Fixation lastRecordedFixation;
	private double lastGeneratedFixationStart = Double.NaN; 
	
	private int foveaX = Integer.MIN_VALUE,
			    foveaY = Integer.MIN_VALUE;
	private Fixation.Word foveatedWord = null;
	private SortedSet<Fixation.Word> parafoveatedWords = null;
	
	public REMMARequestDelegate(REMMAModule module, REMMABuffer buffer) {
		this.module = module;
		this.buffer = buffer;
		this.nextWordChunkType = module.getNextWordChunkType();
		this.cancellableProgrammingDurationS = module.getCancellableProgrammingDurationS();
		this.nonCancellableProgrammingDurationS = module.getNonCancellableProgrammingDurationS();
		/*this.fixedExecutionDurationS = module.getFixedExecutionDurationS();
		this.executionDurationPerDegreeS = module.getExecutionDurationPerDegreeS(); */
		setAsynchronous(true);
		setUseBlockingTimedEvents(false);
		setDelayStart(false);
	}
	
	@Override
	public boolean willAccept(IRequest request) {
		return request != null
				&& request instanceof ChunkTypeRequest
				&& ((ChunkTypeRequest)request).getChunkType().isA(nextWordChunkType);
	}

	@Override
	public void clear() {
		super.clear();
	    ITimedEvent previous = getCurrentTimedEvent();
	    if (previous != null && !previous.hasAborted() && !previous.hasFired())
	      previous.abort();
	}

	@Override
	protected boolean isValid(IRequest request, IActivationBuffer buffer) {
		IChunkType requestChunkType = ((ChunkTypeRequest)request).getChunkType();
		if(!requestChunkType.isA(nextWordChunkType)) {
			throw new IllegalArgumentException("Invalid chunk type: "+requestChunkType);
		} else if(isBusy(buffer)) {
			LOGGER.debug("REMMA is busy, cannot get next word");
			return false;
		} else {
			return true;
		}
	}

	@Override
	protected double computeCompletionTime(double startTime, IRequest request,
			IActivationBuffer buffer) {
		// The super method shall not be overwritten because it specifies a 50ms
		// delay that in conjunction with setDelayStart(false)in the constructor
		// ensures that the request is not executed before the production has fired
		// (i.e. after 50ms).
		return super.computeCompletionTime(startTime, request, buffer);
	}

	@Override
	protected Object startRequest(IRequest request, IActivationBuffer buffer,
			double requestTime) {
		// The completion time of the super-class is the completion of the
		// start of the request: the arrival of the request + 50ms that the
		// production takes to fire.
		double startTime = computeCompletionTime(requestTime, request, buffer);
		WordResult result = encodingState.getNextWord(startTime);
		buffer.removeSourceChunk(buffer.getSourceChunk());
		setBusy(buffer);
		return result;
	}

	@Override
	protected void abortRequest(IRequest request, IActivationBuffer buffer,
			Object startValue) {
		setFree(buffer);
		super.abortRequest(request, buffer, startValue);
	}

	@Override
	protected void finishRequest(IRequest request, IActivationBuffer buffer,
			Object startValue) {
		WordResult result = (WordResult)startValue;
		if(result instanceof Error) {
			// No further saccades and fixations available or an internal
			// error occured.
			setError(buffer);
		} else {
			NextWord nextWord = (NextWord)result;
			double encodingStart = getCurrentTimedEvent().getEndTime();
			double encodingEnd = encodingStart+nextWord.encodingDuration;
			IModel model = buffer.getModel();
			IChunk chunk = createWordChunk(nextWord);
			
			if(Logger.hasLoggers(model) || LOGGER.isDebugEnabled()) {
				String message = "Got next word "+chunk+" in "+nextWord.encodingDuration
						+" @ "+encodingEnd;
				Logger.log(model, Logger.Stream.VISUAL, message);
				LOGGER.debug(message);
			}
			
			module.fireEncodingWord(nextWord.word, chunk, encodingStart, encodingEnd);
			// TODO: Maybe also fire when word has been encoded
			ITimedEvent finish = new DelayedBufferInsertionTimedEvent(buffer,
					chunk, encodingStart, encodingEnd);
			model.getTimedEventQueue().enqueue(finish);
			setCurrentTimedEvent(finish);
		}
	}
	
	private IChunk createWordChunk(NextWord nextWord) {
		IChunk chunk = null;
		ITWM twm = (ITWM)module.getModel().getModule(ITWM.class);
		IDeclarativeModule dm = module.getModel().getDeclarativeModule();
		
		// Create anaphor info
		AnaphorInfo anaphorInfo = null;
		if(nextWord.fixation.getRegressionInfo() != null) {
			anaphorInfo = new AnaphorInfo(nextWord.fixation.getRegressionInfo().getId(),
					nextWord.fixation.getCondition(),
					nextWord.fixation.getRegressionInfo().getDaia(),
					nextWord.fixation.getRegressionInfo().getKind(),
					nextWord.fixation.getRegressionInfo().getRelationActivation(),
					nextWord.fixation.getRegressionInfo().getUri(),
					nextWord.fixation.getRegressionInfo().getLine(),
					nextWord.fixation.getRegressionInfo().getColumn(),
					nextWord.fixation.getRegressionInfo().getWord());
		}
		
		// Try to get a matching reference potential
		ReferencePotential refPot = module.getReferencePotential(nextWord.word.getUri(),
				nextWord.word.getLine(), nextWord.word.getColumn());
		if(refPot != null) {
			if(!refPot.getGraphemic().equals(nextWord.word.getWord())) {
				LOGGER.warn("Word from ET ("+nextWord.word.getWord()+")"
						+" does not match reference potential in REMMA module ("+refPot.getGraphemic()+")"
						+" at "+nextWord.word.getUri()+":"+nextWord.word.getLine()+":"+nextWord.word.getColumn());
			}
			refPot.setAnaphorInfo(anaphorInfo);
			try {
				chunk = dm.getChunk(refPot.getId()).get();
				if(chunk == null) {
					chunk = twm.toChunk(refPot);
					twm.addToDMAndEnsureNameIsUnique(dm, chunk);
				} else {
					try {
						chunk.getWriteLock().lock();
						twm.setAnaphorMetaData(chunk, refPot);
					} finally {
						chunk.getWriteLock().unlock();
					}
				}
				module.ensureLexicalizedSchemaNotTargetedByReferencePotentialIsInDM(refPot);
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error("Failed to obtain chunk for "+nextWord.word+": "+e.getMessage(), e);
			}
		}
		
		// Alternatively, try to get a matching word
		if(chunk == null) {
			de.monochromata.jactr.tls.Word word = module.getWord(nextWord.word.getUri(),
					nextWord.word.getLine(), nextWord.word.getColumn());
			if(word != null) {
				if(!word.getGraphemic().equals(nextWord.word.getWord())) {
					LOGGER.warn("Word from ET ("+nextWord.word.getWord()+")"
							+" does not match word in REMMA module ("+word.getGraphemic()+")"
							+" at "+nextWord.word.getUri()+":"+nextWord.word.getLine()+":"+nextWord.word.getColumn());
				}
				word.setAnaphorInfo(anaphorInfo);
				try {
					chunk = dm.getChunk(word.getId()).get();
					if(chunk == null) {
						chunk = twm.toChunk(word);
						twm.addToDMAndEnsureNameIsUnique(dm, chunk);
					} else {
						try {
							chunk.getWriteLock().lock();
							twm.setAnaphorMetaData(chunk, word);
						} finally {
							chunk.getWriteLock().unlock();
						}
					}
					module.ensureLexicalizedSchemaNotTargetedByReferencePotentialIsInDM(word);
				} catch (InterruptedException | ExecutionException e) {
					LOGGER.error("Failed to obtain chunk for "+nextWord.word+": "+e.getMessage(), e);
				}
			}
		}
		
		// Alternatively, create a matching word and (lexicalized schema with a
		// single unqiue feature, if necessary)
		if(chunk == null) {
			de.monochromata.jactr.tls.Word word = module.createWordAndEnsureLexicalizedSchema(nextWord.word);
			word.setAnaphorInfo(anaphorInfo);
			try {
				chunk = twm.toChunk(word);
				twm.addToDMAndEnsureNameIsUnique(dm, chunk);
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error("Failed to obtain chunk for "+nextWord.word+": "+e.getMessage(), e);
			}
			LOGGER.debug("Created "+chunk+" from "+nextWord.word.getWord()+"@"+nextWord.word.getUri()+":"+nextWord.word.getLine()+":"+nextWord.word.getColumn());
		} else {
			LOGGER.debug("Encoded "+chunk+" from "+nextWord.word.getWord()+"@"+nextWord.word.getUri()+":"+nextWord.word.getLine()+":"+nextWord.word.getColumn());
		}
		
		return chunk; 
	}
	
	/**
	 * Compute the encoding duration in seconds for encoding the given word
	 * from the currently foveated position.
	 * 
	 * @param word
	 * @see #foveaX
	 * @see #foveaY
	 */
	private double computeEncodingDurationS(Fixation.Word word) {
		return computeEncodingDurationS(foveaX, foveaY, word);
	}
	
	/**
	 * Compute the encoding duration in seconds for encoding the given word
	 * from the given position.
	 * 
	 * @param currentX
	 * @param currentY
	 * @param word
	 * @return
	 */
	private double computeEncodingDurationS(int currentX, int currentY, Fixation.Word word) {
		double distance = getDistanceInDegreesOfVisualAngle(currentX, currentY, word);
		double duration = module.getEncodingFactor()
				* ( - Math.log(module.getFrequency(word)) )
				* Math.exp(module.getEncodingExponentFactor()
						* distance);
		LOGGER.debug("Enc "+duration+"s: "+word.getWord()
				+" @ "+word.getAbsoluteCenterX()+","+word.getAbsoluteCenterY()
				+" vs. "+foveaX+","+foveaY+" ("+distance+"°)");
		return duration;
	}
	
	private double getDistanceInDegreesOfVisualAngle(int x, int y, Fixation.Word word) {
		return getDistanceInDegreesOfVisualAngle(x, y, word.getAbsoluteCenterX(), word.getAbsoluteCenterY());
	}
	
	private double getDistanceInDegreesOfVisualAngle(double x1, double y1, double x2, double y2) {
		double distanceXMM = (x1-x2)/module.getHorizontalResolutionPxPerMM();
		double distanceYMM = (y1-y2)/module.getVerticalResolutionPxPerMM();
		double distanceInMM = Math.sqrt( (distanceXMM*distanceXMM) + (distanceYMM*distanceYMM) );
		return (180.0/Math.PI) * 2 * Math.atan( distanceInMM / module.getDistanceToScreenMM() );
	}
	
	private interface State {
	}
	
	private class Encoding {

		public WordResult getNextWord(double requestStartS) {
			
			// Like in RetrievalRequestDelegate.startRequest(...), might
			// be redundant, though, because isValid(...) already rejects
			// requests when the buffer is still busy.
			ITimedEvent previousNextWord = getCurrentTimedEvent();
			if(previousNextWord != null
				&& !previousNextWord.hasAborted()) {
				if(!previousNextWord.hasFired()) {
					previousNextWord.abort();
					LOGGER.debug("Aborting get-next-word request "+previousNextWord);
				} // event has already fired i.e. processing is complete
			}
			
			// Return currently available words, if there are any
			Fixation.Word nextWord = null;
			double nextEncodingDurationS = 0.0;
			if(foveatedWord != null) {
				nextWord = foveatedWord;
				foveatedWord = null;
			} else if (parafoveatedWords != null) {
				if(!parafoveatedWords.isEmpty()) {
					nextWord = parafoveatedWords.first();
					parafoveatedWords.remove(nextWord);
					if(parafoveatedWords.isEmpty())
						parafoveatedWords = null;
				}
			}
			
			// TODO: Diese Fallunterscheidung loggen, ggf. mit Instrument? Mal den Wizard ausprobieren
			// (a) no next word, need to get next saccade,
			// (b) encoding after next saccade faster than with parafoveal preview
			// (c) encoding from parafoveal preview faster than after next saccade
			WordResult returnValue = null;
			if(nextWord == null) {
				returnValue = performNextSaccadeAndGetNextWord(requestStartS);
			} else {
				double nextWordEncodingDuration = computeEncodingDurationS(nextWord);
				
				// TODO: (Otherwise) start new preparation? Will need to figure out
				// if current next encoded word will be encoded more quickly than
				// the first word available via the upcoming saccade/fixation.
				FixationResult forecastResult = preparationState.forecastNextFixation(requestStartS);
				if(forecastResult instanceof Error) {
					returnValue = (Error)forecastResult;
				} else {
					NextFixation forecastFixation = (NextFixation)forecastResult;
					double forecastDuration = computeEncodingDurationS(forecastFixation.fixation.getPorX(),
							forecastFixation.fixation.getPorY(), forecastFixation.fixation.getFoveatedWord());
					
					if(nextWord.equals(forecastFixation.fixation.getFoveatedWord())) {
						// The next fixation is a re-fixation on the currently fixated word
						double missingToCompletion = 1.0-(forecastDuration/nextWordEncodingDuration);
						double reEncodingDurationS = missingToCompletion*forecastDuration;
						double durationWithRefixationAndReEncoding = forecastFixation.durationToFixation+reEncodingDurationS;
						if(durationWithRefixationAndReEncoding < nextWordEncodingDuration) {
							// The refixation speeds up the encoding of the currently fixated word: perform
							// the refixation.
							returnValue = performNextSaccadeAndGetNextWord(requestStartS, durationWithRefixationAndReEncoding);
						} else {
							// The refixation is still too far away (ahead of time) to speed up the encoding of the currently available word.
							returnValue = new NextWord(nextWordEncodingDuration, forecastFixation.fixation, nextWord);
						}
					} else {
						if((forecastFixation.durationToFixation+forecastDuration) < nextWordEncodingDuration) {
							// Encoding the foveated word from the next fixation will be faster than
							// encoding from parafoveal preview: perform the next saccade.
							returnValue = performNextSaccadeAndGetNextWord(requestStartS);
						} else {
							// The next fixation is still too far away: encoding the currently available word
							// will be faster.
							returnValue = new NextWord(nextWordEncodingDuration, lastRecordedFixation, nextWord);
						}
					}
				}
			}
			
			// (Re-)Start preparation for subsequent saccade
			preparationState.startNewPreparation(requestStartS);
			
			return returnValue;
		}
		
		protected WordResult performNextSaccadeAndGetNextWord(double requestStart) {
			return performNextSaccadeAndGetNextWord(requestStart, (secondsToSaccadeCompletion, nextWord) -> {
				double encodingDuration = computeEncodingDurationS(nextWord);
				LOGGER.debug("sec to sacc. completion: "+secondsToSaccadeCompletion+", "+encodingDuration);
				return secondsToSaccadeCompletion + encodingDuration;
				});
		}

		protected WordResult performNextSaccadeAndGetNextWord(double requestStart, double duration) {
			return performNextSaccadeAndGetNextWord(requestStart, (defaultDuration,nextWord) -> { return duration; });
		}
		
		protected WordResult performNextSaccadeAndGetNextWord(double requestStart,
				BiFunction<Double,Fixation.Word,Double> duration) {
			FixationResult result = preparationState.getNextFixation(requestStart);
			if(result instanceof Error) {
				return (Error)result;
			} else if(result instanceof NextFixation) {
				Fixation.Word nextWord = foveatedWord;
				foveatedWord = null;
				NextFixation nextFixation = (NextFixation)result;
				double durationS = duration.apply(nextFixation.durationToFixation, nextWord);
				return new NextWord(durationS, nextFixation.fixation, nextWord);
			} else {
				LOGGER.error("Unknown fixation result type: "+result.getClass().getName());
				return new InternalError();
			}
		}
		
	}
	
	
	
	private class PreparationState implements State {

		private double preparationStart = Double.NaN;
				
		public void startNewPreparation(double requestStart) {
			// Restart preparation
			preparationStart = requestStart;
			LOGGER.debug("Resetting REMMA preparation at "+requestStart);
		}

		public FixationResult forecastNextFixation(double requestStart) {
			return forecastOrGetNextFixation(requestStart,
					(reqStart,contributionToDuration) -> {
						return executionState.forecastNextFixation(requestStart,
								contributionToDuration);
					});
		}

		public FixationResult getNextFixation(double requestStart) {
			return forecastOrGetNextFixation(requestStart,
					(reqStart,contributionToDuration) -> {
						return executionState.getNextFixation(reqStart, contributionToDuration);
					});
		}
		
		protected FixationResult forecastOrGetNextFixation(double requestStart,
				BiFunction<Double,Double,FixationResult> passOn) {
			if(preparationStart == Double.NaN) {
				preparationStart = requestStart;
			}
			double preparationDuration = requestStart-preparationStart;
			double contributionToForecastDuration = Double.NaN;
			if(preparationDuration < cancellableProgrammingDurationS) {
				// Preparation will need to be restarted before next saccade can
				// be performed.
				contributionToForecastDuration = cancellableProgrammingDurationS;
			} else {
				// Preparation is complete, only execution time can contribute to
				// the forecast duration.
				contributionToForecastDuration = 0;
			}
			return passOn.apply(requestStart, contributionToForecastDuration);
			
		}
	}
	
	
	
	private abstract class ExecutionState implements State {
		public abstract FixationResult forecastNextFixation(double requestStart, double forecastDuration);
		public abstract FixationResult getNextFixation(double requestStartS, double preparationDuration);
	}
	

	/**
	 * Like FixationGeneration, but does not require a leading saccade.
	 */
	private class Initialised extends ExecutionState {
		
		protected boolean forecastFixationFollowsImmediately;
		protected Saccade forecastSaccade;
		protected Fixation forecastFixation;

		protected Initialised(boolean fixationFollowsImmediately) {
			this.forecastFixationFollowsImmediately = fixationFollowsImmediately;
		}
		
		protected double getDurationToFixation(double preparationDurationS, Saccade forecastSaccade) {
			// If there is no saccade, only preparation duration will contribute
			return preparationDurationS+nonCancellableProgrammingDurationS
					+(forecastSaccade!=null?forecastSaccade.getDurationS():0);
		}
		
		@Override
		public FixationResult forecastNextFixation(double requestStartS,
				double forecastDurationS) {
			BlockingTimedEvent bte = module.synchronizedTimedEvent(requestStartS, requestStartS);
			FixationResult result = null;
			try {
				// Try to load the next saccade-fixation-pair
				if(forecastSaccade == null && forecastFixation == null) {
					if(!forecastFixationFollowsImmediately) {
						forecastSaccade = (Saccade)module.loadNextLogEntry();
					} else {
						forecastSaccade = null;
					}
					forecastFixation = (Fixation)module.loadNextLogEntry();
					if(forecastFixation != null)
						forecastFixationFollowsImmediately = forecastFixation.isFixationFollowingImmediately();
					// else: end of data
				}
				
				// If there is no further saccade-fixation-pair, the end of the
				// log file has been reached.
				if(forecastSaccade == null && forecastFixation == null) {
					result = new EndOfData();
				} else {
					// No saccade is currently being finally prepared or executed,
					// preparation cannot overlap execution. Instead, a whole
					// preparation and execution cycle is required.
					double duration = getDurationToFixation(forecastDurationS, forecastSaccade);
					if(forecastFixation != null)
						result = new NextFixation(duration, forecastFixation);
					else
						result = new EndOfData();
				}
			} catch (IOException e) {
				LOGGER.error("Failed to forecast next fixation: "+e.getMessage(), e);
				result = new InternalError();
			} finally {
				bte.abort();
			}
			return result;
		}
		
		@Override
		public FixationResult getNextFixation(double requestStartS, double preparationDuration) {
			BlockingTimedEvent bte = module.synchronizedTimedEvent(requestStartS, requestStartS);
			FixationResult result = null;
			try {
				if(forecastSaccade == null && forecastFixation == null)
					forecastNextFixation(requestStartS, preparationDuration);
				//if(forecastSaccade != null && forecastFixation != null) {
					// Re-use log entries obtained for forecasting
					result = getNextFixation(forecastSaccade, forecastFixation, requestStartS, preparationDuration);
				/*} else {
					// Obtain next log entries
					result = getNextFixation(module.loadNextLogEntry(), requestStartS, preparationDuration);
				}*/
			/*} catch (IOException e) {
				LOGGER.error("Failed to read next log entry: "+e.getMessage(), e);
				result = new InternalError();*/
			} finally {
				bte.abort();
			}
			return result;
		}
		/*
		protected FixationResult getNextFixation(LogEntry logEntry, double requestStartS, double preparationDuration) {
			if(logEntry instanceof Saccade) {
				return getNextFixation((Saccade)logEntry, requestStartS, preparationDuration);
			} else if(logEntry instanceof Fixation) {
				return getNextFixation((Fixation)logEntry, requestStartS, preparationDuration);
			} else {
				LOGGER.error("Unknown log entry: "+logEntry.getClass().getName());
				return new EndOfData();
			}
		}
		
		protected FixationResult getNextFixation(Saccade saccade, double requestStartS, double preparationDuration) {
			try {
				Fixation fixation = (Fixation)module.loadNextLogEntry();
				return getNextFixation(saccade, fixation, requestStartS, preparationDuration);
			} catch (IOException e) {
				LOGGER.error("Failed to get next fixation: "+e.getMessage(), e);
				return new InternalError();
			}
		}*/
		
		protected FixationResult getNextFixation(Saccade saccade, Fixation fixation,
				double requestStartS, double preparationDurationS) {
			if(fixation == null) {
				// End of data reached
				LOGGER.debug("End of data reached");
				executionState = new ExecutionTerminated();
				return new EndOfData();
			} else {
				finishAndLogCurrentFixation(requestStartS);
				double durationToFixation = getDurationToFixation(preparationDurationS, saccade);
				double fixationStartS = requestStartS + durationToFixation;
				executionState = new FixationGeneration(forecastFixationFollowsImmediately, fixationStartS, fixation);
				return new NextFixation(durationToFixation, fixation);
			}
		}
		/*
		protected FixationResult getNextFixation(Fixation fixation, double requestStartS,
				double preparationDurationS) {
			double saccadeDurationS = 0;
			double durationToFixation = getDurationToFixation(preparationDurationS, saccadeDurationS);
			double fixationStartS = requestStartS + durationToFixation;
			executionState = new FixationGeneration(forecastFixationFollowsImmediately, fixationStartS, fixation);
			return new NextFixation(durationToFixation, fixation);
		}*/
		
		protected void finishAndLogCurrentFixation(double end) {
			// Right after initialization, there is no previous fixation
		}
		
	}
	
	/**
	 * Re-generate the duration of a recorded fixation that is preceded by a recorded saccade.
	 */
	private class FixationGeneration extends Initialised {
		
		private final double fixationStartS;
		private final Fixation recordedFixation;

		/**
		 * Re-generation of the given recorded fixation. This instance may be created
		 * before the model has completed processing of the saccade leading to the
		 * given fixation.
		 * 
		 * @param fixationStartS The start of the fixation (model time) i.e. when
		 * 	the saccade that lead to the fixation has been completed.
		 * @param recordedFixation The recorded fixation
		 */
		public FixationGeneration(boolean fixationFollowsImmediately,
				double fixationStartS, Fixation recordedFixation) {
			super(fixationFollowsImmediately);
			this.fixationStartS = fixationStartS;
			this.recordedFixation = recordedFixation;
			lastRecordedFixation = recordedFixation;
			lastGeneratedFixationStart = fixationStartS;
			foveaX = recordedFixation.getPorX();
			foveaY = recordedFixation.getPorY();
			foveatedWord = recordedFixation.getFoveatedWord();
			parafoveatedWords = new TreeSet<Fixation.Word>(new LeftToRight());
			parafoveatedWords.addAll(recordedFixation.getParafoveatedWords());
			module.fireFixationStarted(recordedFixation, fixationStartS);
		}
		
		@Override
		public FixationResult forecastNextFixation(double requestStartS,
				double forecastDurationS) {
			if(requestStartS < fixationStartS) {
				LOGGER.error("Attemping to forecast next fixation at "+requestStartS
						+" before start of current fixation at "+fixationStartS);
				return new InternalError();
			} else {
				return super.forecastNextFixation(requestStartS, forecastDurationS);
			}
		}
		
		@Override
		public FixationResult getNextFixation(double requestStartS, double preparationDuration) {
			if(requestStartS < fixationStartS) {
				LOGGER.error("Attemping to get next fixation at "+requestStartS
						+" before start of current fixation at "+fixationStartS);
				return new InternalError();
			} else {
				return super.getNextFixation(requestStartS, preparationDuration);
			}
		}
		
		/*
		@Override
		protected FixationResult getNextFixation(Fixation fixation, double requestStartS, double preparationDuration) {
			LOGGER.error("Missing saccade before fixation: "+fixation);
			return new InternalError();
		}*/
		
		protected void finishAndLogCurrentFixation(double end) {
			module.fireFixationFinished(lastRecordedFixation,
					lastGeneratedFixationStart, end);
			lastRecordedFixation = null;
			lastGeneratedFixationStart = Double.NaN;
		}
		
	}
	
	/**
	 * Execution state when no further fixations are available: returns errors only. 
	 */
	private class ExecutionTerminated extends ExecutionState {
		
		@Override
		public FixationResult forecastNextFixation(double requestStart,
				double forecastDuration) {
			return new InternalError();
		}

		@Override
		public FixationResult getNextFixation(double requestStartS,
				double preparationDuration) {
			return new InternalError();
		}
		
	}
	
	private interface Result { }
	private interface WordResult extends Result { }
	private interface FixationResult extends Result { }
	
	private interface Error extends WordResult, FixationResult { }
	private class InternalError implements Error { }
	private class EndOfData implements Error { }
	
	private class NextWord implements WordResult {
		
		double encodingDuration;
		Fixation fixation;
		Fixation.Word word;
		
		public NextWord(double encodingDuration, Fixation fixation, Fixation.Word word) {
			this.encodingDuration = encodingDuration;
			this.word = word;
			this.fixation = fixation;
		}
		
	}
	
	private class NextFixation implements FixationResult {
		
		double durationToFixation;
		Fixation fixation;
		
		public NextFixation(double durationToFixation, Fixation fixation) {
			this.durationToFixation = durationToFixation;
			this.fixation = fixation;
		}
		
	}
	
	/**
	 * Used to sort parafoveated words from left to right by the the horizontal
	 * position of their center points (in absolute coordinated).
	 */
	private class LeftToRight implements Comparator<Fixation.Word> {

		@Override
		public int compare(Word word1, Word word2) {
			if(!word1.getUri().equals(word2.getUri()))
				throw new IllegalArgumentException("Word from different URI's: "+word1.getUri()+" vs. "+word2.getUri());
			return word1.getAbsoluteCenterX()-word2.getAbsoluteCenterX();
		}


		
	}
}
