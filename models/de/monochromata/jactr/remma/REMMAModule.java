package de.monochromata.jactr.remma;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.agents.IAgent;
import org.commonreality.object.manager.event.IAfferentListener;
import org.commonreality.object.manager.event.IEfferentListener;
import org.jactr.core.buffer.IActivationBuffer;
import org.jactr.core.chunk.IChunk;
import org.jactr.core.chunktype.IChunkType;
import org.jactr.core.concurrent.ExecutorServices;
import org.jactr.core.model.IModel;
import org.jactr.core.model.event.IModelListener;
import org.jactr.core.model.event.ModelEvent;
import org.jactr.core.model.event.ModelListenerAdaptor;
import org.jactr.core.module.declarative.IDeclarativeModule;
import org.jactr.core.runtime.ACTRRuntime;
import org.jactr.modules.pm.AbstractPerceptualModule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import de.monochromata.jactr.dm.INonMergingDeclarativeModule;
import de.monochromata.jactr.remma.Fixation.RegressionInfo;
import de.monochromata.jactr.tls.ConceptualSchema;
import de.monochromata.jactr.tls.LexicalFeatures;
import de.monochromata.jactr.tls.LexicalizedConceptualSchema;
import de.monochromata.jactr.tls.ReferencePotential;
import de.monochromata.jactr.tls.Scope;
import de.monochromata.jactr.tls.SpatialInfo;
import de.monochromata.jactr.tls.Word;
import de.monochromata.jactr.twm.ITWM;
import de.monochromata.jactr.twm.TWMModule;
import static de.monochromata.jactr.remma.REMMAParticipant.*;

/**
 * IModule is the entry point to extend an ACT-R model from a theoretical point.
 * Usually, modules are instantiated, have their parameters set and are then
 * attached to the IModel via IModel.install(IModule), which in turn calls
 * IModule.install(IModel). <br>
 * <br>
 * Most behavior is extended by attaching listeners to the model and its
 * contents. Care must be taken when doing this because of the threaded nature
 * of models.<br>
 * <br>
 */
public class REMMAModule extends AbstractPerceptualModule implements IREMMA {

	/**
	 * Standard logger used through out jACT-R
	 */

	private static final transient Log LOGGER = LogFactory.getLog(REMMAModule.class);

	public static final String MODULE_NAME = "REMMA";
	
	/**
	 * Keys in the objects in the JSON file with knowledge representations, words
	 * and reference potentials.
	 */
	public enum JSONKey {
		ID, SCOPE, CT, ACT, TEC, ATTR, SF, LF, CF;
	}
	
	public enum TechnicalAttribute {
		referent, isDefinite, coReferenceChain, declaredIn, schema, roleIn, roleId, returnId;
	}
	
	/**
	 * if you need to be notified when an IAfferentObject is added, modified, or
	 * removed you've instantiate one of these and attach it down in #install
	 */
	private IAfferentListener _afferentListener;

	/**
	 * if you need to be notified when an IEfferentObject is aded,removed or
	 * modified you'd instantiate one of these and attach it down in #install
	 */
	private IEfferentListener _efferentListener;

	private final Map<String,String> parameterMap = REMMAParticipant.createParameterMap();
	private final List<IREMMAListener> listeners = new ArrayList<>();
	
	
	private String featureThatMarksMethodSchemata;
	private long nextUniqueFeatureId = 0;
	
	private Map<String,Map<Integer,List<Word>>> wordsByUriAndLine = new HashMap<>();
	private Map<String,Word> words = new HashMap<>();
	
	private Map<String,Map<Integer,List<ReferencePotential>>> referencePotentialsByUriAndLine = new HashMap<>();
	
	private Map<String,ConceptualSchema> conceptualSchemataByName = new HashMap<>();
	private LinkedList<ConceptualSchema> conceptualSchemata = new LinkedList<>();
	private Map<String,List<String>> lexicalizedConceptualSchemataByWord = new HashMap<>();
	
	private BufferedReader eventsReader;
	private REMMABuffer remmaBuffer;
	
	private LogEntry lookAhead;
	private IChunkType nextWordChunkType;
	private double encodingFactor, encodingExponentFactor, defaultWordFrequency,
			horizontalResolutionPxPerMM, verticalResolutionPxPerMM, distanceToScreenMM;
	private double cancellableProgrammingDurationS,
				   nonCancellableProgrammingDurationS/*,
				   fixedExecutionDurationS,
				   executionDurationPerDegreeS*/;
	
	/**
	 * standard 0 argument constructor must always be present. this should do
	 * very little.
	 */
	public REMMAModule() {
		super(MODULE_NAME);
	}
		
	/**
	 * @see org.jactr.core.module.AbstractModule#install(org.jactr.core.model.IModel)
	 */
	@Override
	public void install(IModel model) {
		super.install(model);

		IModelListener startUp = new ModelListenerAdaptor() {

			/**
			 * called once the connection to common reality is established. Once
			 * this occurs, we can get access to the common reality executor
			 * 
			 * @see org.jactr.core.model.event.ModelListenerAdaptor#modelConnected(org.jactr.core.model.event.ModelEvent)
			 */
			public void modelConnected(ModelEvent event) {
				if (LOGGER.isDebugEnabled())
					LOGGER.debug("Connected to common reality, attaching listeners");

				/*
				 * all AbstractPerceptualModules within a single model share a
				 * common executor (on a separate thread) that is to be used to
				 * process events coming from Common Reality. This executor is
				 * only available after modelConnected()
				 *
				Executor executor = getCommonRealityExecutor();

				/*
				 * the agent interface is how we communicate with common reality
				 *
				IAgent agentInterface = ACTRRuntime.getRuntime().getConnector()
						.getAgent(event.getSource());

				/*
				 * now, whenever an event comes from common reality to the agent
				 * interface, we will receive notification of the changes on the
				 * common reality executor thread.
				 */
				// agentInterface.addListener(_afferentListener, executor);
				// agentInterface.addListener(_efferentListener, executor);
			}

			@Override
			public void modelStarted(ModelEvent me) {
				
			}
			
		};
		
		/*
		 * we attach this listener with the inline executor - i.e. it will be
		 * called on the same thread that issued the event (the ModelThread),
		 * immediately after it occurs.
		 */
		model.addListener(startUp, ExecutorServices.INLINE_EXECUTOR);
		
		try {
			eventsReader = createEventsReader();
			
			featureThatMarksMethodSchemata = getParameter(FEATURE_THAT_MARKS_METHOD_SCHEMATA);
			encodingFactor = Double.parseDouble(getParameter(ENCODING_FACTOR));
			encodingExponentFactor = Double.parseDouble(getParameter(ENCODING_EXPONENT_FACTOR));
			defaultWordFrequency = Double.parseDouble(getParameter(DEFAULT_FREQUENCY));
			horizontalResolutionPxPerMM = Double.parseDouble(getParameter(SCREEN_WIDTH_PX))
					/ Double.parseDouble(getParameter(SCREEN_WIDTH_MM));
			verticalResolutionPxPerMM = Double.parseDouble(getParameter(SCREEN_HEIGHT_PX))
					/ Double.parseDouble(getParameter(SCREEN_HEIGHT_MM));
			distanceToScreenMM = Double.parseDouble(getParameter(DISTANCE_TO_SCREEN_MM));
			
			cancellableProgrammingDurationS = Double.parseDouble(getParameter(CANCELLABLE_PROGRAMMING_DURATION_S));
			nonCancellableProgrammingDurationS = Double.parseDouble(getParameter(NON_CANCELLABLE_PROGRAMMING_DURATION_S));
			/*fixedExecutionDurationS = Double.parseDouble(getParameter(FIXED_EXECUTION_DURATION_S));
			executionDurationPerDegreeS = Double.parseDouble(getParameter(EXECUTION_DURATION_PER_DEGREE_S));*/
			
			// TODO: How to correctly signal initialisation errors?
			loadSchemaAndWordsJSON();
			checkReferences();
			// TODO: Split camelCase into constituent concepts and add them besides the camelCase concepts
		} catch (IOException e) {
			LOGGER.error("Failed to files: "+e.getMessage(), e);
		}
	}
	
	protected BufferedReader createEventsReader() throws FileNotFoundException {
		return new BufferedReader(new FileReader(getParameter(PATH_TO_EVENTS)));
	}

	private void loadSchemaAndWordsJSON() {
		FileReader reader = null;
		try {
			String pathToJSON = getParameter(PATH_TO_JSON);
			File jsonFile = new File(pathToJSON);
			reader = new FileReader(jsonFile);
			JSONTokener tokener = new JSONTokener(reader);
			Object nextValue = tokener.nextValue();
			if(nextValue instanceof JSONArray)
				loadJSON0((JSONArray)nextValue);
			else
				throw new RuntimeException("No outermost JSONArray"); 
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e.getMessage(), e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					LOGGER.error("Failed to close JSON file reader", e);
				}
			}
		}
	}
	
	/**
	 * Loads the elements of the outermost JSON array
	 * 
	 * @param array
	 */
	private void loadJSON0(JSONArray array) {
		int length = array.length();
		// TODO: Not very memory-efficient to parse the entire array at once
		for(int i=0;i<length;i++) {
			loadJSON1(array.getJSONObject(i));
		}
	}
	
	/**
	 * Reads a JSONObject representing a Token, Type, ReferencePotential or Word
	 * from the outermost JSON array.
	 * 
	 * @param obj
	 */
	private void loadJSON1(JSONObject obj) {
		String ct = obj.getString(JSONKey.CT.name());
		switch(ct) {
		case "Token":
		case "Type": loadLexicalizedConceptualSchema(obj, ct); break;
		case "ReferencePotential": loadReferencePotential(obj); break;
		case "Word": loadWord(obj); break;
		default: throw new IllegalStateException("Unknown conceptual type: "+ct);
		}
	}
	
	private void loadLexicalizedConceptualSchema(JSONObject obj, String ct) {
		try {
			String id = obj.getString(JSONKey.ID.name());
			Scope scope = Scope.create(obj.getString(JSONKey.SCOPE.name()));
			double activation = obj.getDouble(JSONKey.ACT.name());
			boolean isTechnical = obj.getBoolean(JSONKey.TEC.name());
			
			Map<String,Object> lexicalFeatures = getFeatureMap(obj.getJSONObject(JSONKey.LF.name()));
			List<String> conceptualFeatures = getFeatureList(obj.getJSONArray(JSONKey.CF.name()));
			boolean isObject = conceptualFeatures.contains(featureThatMarksMethodSchemata);
			if(obj.has(JSONKey.SF.name())
					&& obj.getJSONObject(JSONKey.SF.name()).length() > 0) {
				String graphemic = (String)lexicalFeatures.get(LexicalFeatures.graphemic.name());
				String[] unqualifiedGraphemics = graphemic.split("\\.");
				for(String unqualifiedGraphemic: unqualifiedGraphemics) {
					int arrayDimensionsIndex = unqualifiedGraphemic.indexOf('[');
					if(arrayDimensionsIndex != -1) {
						unqualifiedGraphemic = unqualifiedGraphemic.substring(0, arrayDimensionsIndex);
					}
					JSONObject attr = new JSONObject();
					attr.put(TechnicalAttribute.isDefinite.name(), false);
					attr.put(TechnicalAttribute.referent.name(), "null");
					attr.put(TechnicalAttribute.schema.name(), id);
					JSONObject refPotObj = new JSONObject(obj, JSONObject.getNames(obj));
					refPotObj.getJSONObject(JSONKey.LF.name()).put(LexicalFeatures.graphemic.name(), unqualifiedGraphemic);
					refPotObj.getJSONObject(JSONKey.SF.name()).put("length", unqualifiedGraphemic.length());
					refPotObj.put(JSONKey.ID.name(), getReferencePotentialIdForLexicalizedConceptualSchema(id));
					refPotObj.putOnce(JSONKey.ATTR.name(), attr);
					// TODO: Aber aktivieren diese Teile dann auch die passenden (Teil-Schemata)?
					loadReferencePotential(refPotObj);
				}
			}
			// TODO: Note that the encoding (from the Java AST to JSON) needs to be adapted
			// for fields and methods declared by types marked as mock or read from class files:
			// these fields and methods are not declared in the type schema of the declaring
			// class or interface, because the their body declarations are not processed (to
			// keep ast.json brief).
			
			LexicalizedConceptualSchema result = new LexicalizedConceptualSchema(id, ct, scope, activation,
					isTechnical, isObject, lexicalFeatures, conceptualFeatures);
			
			// This will purposely overwrite old schema definitions under the same ID
			// that might have been generated from Type- and MethodBindings before the
			// Type- or MethodDeclaration has been processed.
			conceptualSchemataByName.put(id, result);
			conceptualSchemata.add(result);
			String graphemic = (String)lexicalFeatures.get(LexicalFeatures.graphemic.name());
			addLexicalizedConceptualSchemaByWord(graphemic, id);
		} catch (Exception e) {
			LOGGER.error("Failed to load lexicalized conceptual schema "+obj+": "+e.getMessage(), e);
			throw e;
		}
	}

	private String getReferencePotentialIdForLexicalizedConceptualSchema(
			String id) {
		return id+".referencePotential";
	}

	private void loadReferencePotential(JSONObject obj) {
		try {
			String id = obj.getString(JSONKey.ID.name());
			Scope scope = Scope.create(obj.getString(JSONKey.SCOPE.name()));
			Map<String,Object> lexicalFeatures = getFeatureMap(obj.getJSONObject(JSONKey.LF.name()));
			String graphemic = (String)lexicalFeatures.get(LexicalFeatures.graphemic.name());
			SpatialInfo spatial = getSpatialInfo(obj);
			Map<String,Object> attributes = getFeatureMap(obj.getJSONObject(JSONKey.ATTR.name()));
			String referent = (String)attributes.get(TechnicalAttribute.referent);
			String declaredIn = (String)attributes.get(TechnicalAttribute.declaredIn.name());
			String roleIn = (String)attributes.get(TechnicalAttribute.roleIn.name());
			String roleId = (String)attributes.get(TechnicalAttribute.roleId.name());
			String returnId = (String)attributes.get(TechnicalAttribute.returnId.name());
			ReferencePotential result = createReferencePotential(id, scope, graphemic, spatial,
					(Boolean)attributes.get(TechnicalAttribute.isDefinite.name()),
					(referent == null || referent.equals("null")?null:referent),
					(String)attributes.get(TechnicalAttribute.coReferenceChain.name()),
					declaredIn,
					(String)attributes.get(TechnicalAttribute.schema.name()),
					roleIn,
					roleId,
					returnId);
			
			ITWM twmModule = ((ITWM)getModel().getModule(ITWM.class));
			if(declaredIn != null)
				twmModule.prepareArguments(id);
			if(roleIn != null)
				twmModule.addArgument(roleIn, id, roleId);
			twmModule.addReferencePotential(result);
			indexReferencePotential(result);
		} catch (Exception e) {
			LOGGER.error("Failed to load reference potential "+obj+": "+e.getMessage(), e);
			throw e;
		}
	}
	
	private void loadWord(JSONObject obj) {
		String id = obj.getString(JSONKey.ID.name());
		Scope scope = Scope.create(obj.getString(JSONKey.SCOPE.name()));
		Map<String,Object> lexicalFeatures = getFeatureMap(obj.getJSONObject(JSONKey.LF.name()));
		String graphemic = (String)lexicalFeatures.get(LexicalFeatures.graphemic.name());
		SpatialInfo spatial = getSpatialInfo(obj);
		loadWord(id, scope, graphemic, spatial);
	}

	private Word loadWord(String id, Scope scope, String graphemic,
			SpatialInfo spatial) {
		Word result = new Word(id, scope, graphemic, spatial, null);
		words.put(id, result);
		indexWord(result);
		return result;
	}
	
	private void indexWord(Word word) {
		indexWordOrReferencePotential(word, word.getSpatial(), () -> {
			return wordsByUriAndLine;
		} );
	}
	
	private void indexReferencePotential(ReferencePotential referencePotential) {
		indexWordOrReferencePotential(referencePotential, referencePotential.getSpatial(), () -> {
			return referencePotentialsByUriAndLine;
		});
	}
	
	protected <T> void indexWordOrReferencePotential(T object, SpatialInfo spatial,
			Supplier<Map<String,Map<Integer,List<T>>>> index) {
		String uri = spatial.getUri();
		int line = spatial.getLine();
		Map<Integer,List<T>> objectsAtUri = index.get().get(uri);
		if(objectsAtUri == null) {
			objectsAtUri = new HashMap<Integer,List<T>>();
			List<T> objectsAtLine = new LinkedList<T>();
			objectsAtLine.add(object);
			objectsAtUri.put(line, objectsAtLine);
			index.get().put(uri, objectsAtUri);
		} else {
			List<T> objectsAtLine = objectsAtUri.get(line);
			if(objectsAtLine == null) {
				objectsAtLine = new LinkedList<T>();
				objectsAtUri.put(line, objectsAtLine);
			}
			objectsAtLine.add(object);
		}		
	}
	
	/**
	 * Returns a word at the given location, if it has been added to REMMA.
	 * 
	 * @param uri
	 * @param line
	 * @param column
	 * @return Null, if no word has been added at the given position.
	 */
	public Word getWord(String uri, int line, int column) {
		return getWordOrReferencePotential(uri, line, column,
				() -> { return wordsByUriAndLine; } );
	}
	
	/**
	 * Returns a reference potential at the given location, if it has been added to REMMA.
	 * 
	 * @param uri
	 * @param line
	 * @param column
	 * @return Null, if no reference potential has been added at the given position.
	 */
	public ReferencePotential getReferencePotential(String uri, int line, int column) {
		return getWordOrReferencePotential(uri, line, column,
				() -> { return referencePotentialsByUriAndLine; } );
	}
	
	protected <T extends Word> T getWordOrReferencePotential(String uri, int line, int column,
			Supplier<Map<String,Map<Integer,List<T>>>> index) {
		T result = null;
		Map<Integer, List<T>> objectsAtUri = index.get().get(uri);
		if(objectsAtUri != null) {
			List<T> objectsAtLine = objectsAtUri.get(line);
			if(objectsAtLine != null) {
				for(T object: objectsAtLine) {
					int objColumn = object.getSpatial().getColumn();
					if(objColumn == column) {
						result = object;
						break;
					}
				}
			}
		}
		return result;
	}
	
	private String getSchemaId(String potentialTypeName) {
		return "CS#"+potentialTypeName;
	}
	
	/**
	 * TODO: The creation should contribute to a duration 
	 * @param word
	 */
	public void ensureLexicalizedSchemaNotTargetedByReferencePotentialIsInDM(Word word) {

		INonMergingDeclarativeModule dm = (INonMergingDeclarativeModule)
				getModel().getDeclarativeModule();
		ITWM twm = (ITWM)getModel().getModule(ITWM.class);
		
		String graphemic = word.getGraphemic();
		String schemaId = getSchemaId(graphemic);
		if(!twm.isReferredToByReferencePotentials(schemaId)) {
			// Create the named schema if it does not exist yet
			ConceptualSchema schema = conceptualSchemataByName.get(schemaId);
			if(schema != null) {
				try {
					IChunk schemaChunk = dm.getChunk(schemaId).get();
					if(schemaChunk == null) {
						twm.createConceptualSchemaAndDependentSchemataAndAddThemToDM(schemaId);
					}
				} catch(InterruptedException|ExecutionException e) {
					LOGGER.error("Failed to create schema chunk for "
							+schemaId+": "+e.getMessage(), e);
				}
				// TODO: was ist mit dependent schemata?
			}
		}
	}
	
	public Word createWordAndEnsureLexicalizedSchema(Fixation.Word input) {
		// Create word
		String id = "CS#UniqueWord$"+(nextUniqueFeatureId++);
		Word word = loadWord(id, Scope.GLOBAL, input.getWord(),
				new SpatialInfo(input.getUri(), input.getLine(),
						input.getColumn(), input.getLength()));
		
		// Ensure that there is a type schema with at least a single unique feature
		String graphemic = word.getGraphemic();
		List<String> schemata = lexicalizedConceptualSchemataByWord.get(graphemic);
		boolean schemaAvailable = false;
		if(schemata != null) {
			// Make sure that chunks for the existing type schemata are available in
			// declarative memory, but only those, for which no reference potential
			// exists (i.e. which cannot be activated by reading them).
			INonMergingDeclarativeModule dm = (INonMergingDeclarativeModule)
					getModel().getDeclarativeModule();
			ITWM twm = (ITWM)getModel().getModule(ITWM.class);
			
			// TODO: Does not work correctly for qualified IDs of schemata
			// with an unqualified graphemic representation
			String cSchemaId = getSchemaId(word.getGraphemic());
			if(schemata.contains(cSchemaId)) {
				// Encode a class that will later be read
				if(existingSchemaIsInDMOrHasNowBeenAdded(dm, twm, cSchemaId)) {
					schemaAvailable = true;
				}
			} else {
				// Encode meta-data like Java keywords
				for(String schemaId: schemata) {
					String refPotId = getReferencePotentialIdForLexicalizedConceptualSchema(schemaId);
					if(!twm.hasReferencePotential(refPotId)
							&& !twm.isDependentSchema(schemaId)
							&& existingSchemaIsInDMOrHasNowBeenAdded(dm, twm, schemaId)) { 
						schemaAvailable = true;
					}
				}
			}
		}
		if(!schemaAvailable) {
			// Create a new type schema with a unique feature
			createTypeSchemaFromGraphemicAndAddItToDM(graphemic);
		}
		return word;
	}
	
	private boolean existingSchemaIsInDMOrHasNowBeenAdded(INonMergingDeclarativeModule dm, ITWM twm, String schemaId) {
		try {
			IChunk schemaChunk = dm.getChunk(schemaId).get();
			if(schemaChunk == null) {
				ConceptualSchema schema = conceptualSchemataByName.get(schemaId);
				schemaChunk = twm.toChunk(schema);
				twm.addToDMAndEnsureNameIsUnique(dm, schemaChunk);
			}
			return true;
		} catch (InterruptedException | ExecutionException e) {
			LOGGER.error("Failed to obtain chunk for schema "+schemaId+": "+e.getMessage(), e);
			return false;
		}
	}
	
	private void addLexicalizedConceptualSchemaByWord(String word, String schemaId) {
		List<String> schemaIds = lexicalizedConceptualSchemataByWord.get(word);
		if(schemaIds == null) {
			lexicalizedConceptualSchemataByWord.put(word, Collections.singletonList(schemaId));
		} else if(!schemaIds.contains(schemaId)) {
			List<String> newIds = new ArrayList<String>(schemaIds.size()+1);
			newIds.addAll(schemaIds);
			newIds.add(schemaId);
			lexicalizedConceptualSchemataByWord.put(word, newIds);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String,Object> getFeatureMap(JSONObject obj) {
		Map<String,Object> map = new HashMap<String,Object>();
		Iterator<String> iter = (Iterator<String>)obj.keys();
		while(iter.hasNext()) {
			String key = iter.next();
			Object value = obj.get(key);
			if(value == JSONObject.NULL) {
				throw new NullPointerException("NULL value at "+key+" in "+obj);
			}
			map.put(key, value);
		}
		return map;
	}
	
	/**
	 * The feature list may only contain featuresOf-References
	 * to types and direct references to tokens.
	 * 
	 * @param array
	 * @return
	 */
	private List<String> getFeatureList(JSONArray array) {
		int length = array.length();
		List<String> list = new ArrayList<String>(length);
		for(int i=0;i<length;i++) {
			list.add(array.getString(i));
		}
		return list;
	}
	
	private SpatialInfo getSpatialInfo(JSONObject obj) {
		Map<String,Object> spatialFeatures = getFeatureMap(obj.getJSONObject(JSONKey.SF.name()));
		return new SpatialInfo((String)spatialFeatures.get("uri"),
				(Integer)spatialFeatures.get("startLine"),
				(Integer)spatialFeatures.get("startColumn"),
				(Integer)spatialFeatures.get("length"));
	}
	
	private ReferencePotential createReferencePotential(String id, Scope scope, String graphemic,
			SpatialInfo spatial, Boolean isDefinite, String referent, String coReferenceChain,
			String declaredIn, String schema, String roleIn, String roleId, String returnId) {
		if(coReferenceChain != null)
			((ITWM)getModel().getModule(ITWM.class)).addToCoReferenceChain(id, coReferenceChain);
		return new ReferencePotential(id, scope, graphemic, spatial, null, isDefinite, referent,
				coReferenceChain, declaredIn, schema, roleIn, roleId, returnId);
	}
	
	/**
	 * Ensures
	 * - that all conceptual features are schema ids or schema ids prefixed by featuresOf:,
	 * - that all reference potentials' schema, declaredIn, roleId and returnId attributes are null or schema ids, and
	 * - that all reference potentials' roleIn attributes are reference potential ids.
	 */
	private void checkReferences() {
		// Ensure that all conceptual features are schema ids or schema ids prefixed by featuresOf:
		for(ConceptualSchema schema: conceptualSchemata) {
			for(Object conceptualFeature: schema.getConceptualFeatures()) {
				if(!(conceptualFeature instanceof String)) {
					throw new IllegalStateException("Non-string feature "+conceptualFeature+" in schema "+schema.getId());
				} else if (((String)conceptualFeature).startsWith("featuresOf:")){
					String importSource = ((String)conceptualFeature).substring(11);
					if(!conceptualSchemataByName.containsKey(importSource))
						throw new IllegalStateException("Schema "+schema.getId()+" cannot import features from missing schema "+importSource);
				} else {
					if(!conceptualSchemataByName.containsKey(conceptualFeature))
						throw new IllegalStateException("Schema "+schema.getId()+" refers to missing conceptual feature "+conceptualFeature);
				}
			}
		}
		
		// Ensure
		// - that all reference potentials' schema, declaredIn, roleId and returnId attributes are null or schema ids, and
		// - that all reference potentials' roleIn attributes are reference potential ids.
		ITWM twmModule = ((ITWM)getModel().getModule(ITWM.class));
		for(String refPotId: twmModule.getReferencePotentialIds()) {
			ReferencePotential refPot = twmModule.getReferencePotential(refPotId);
			String schema = refPot.getSchema();
			if(schema != null && !conceptualSchemataByName.containsKey(schema))
				throw new IllegalStateException("Reference potential "+refPotId+" refers to missing schema="+schema);
			String declaredIn = refPot.getDeclaredIn();
			if(declaredIn != null && !conceptualSchemataByName.containsKey(declaredIn))
				throw new IllegalStateException("Reference potential "+refPotId+" refers to missing declaration="+declaredIn);
			String roleId = refPot.getRoleId();
			if(roleId != null && !conceptualSchemataByName.containsKey(roleId))
				throw new IllegalStateException("Reference potential "+refPotId+" refers to missing role="+roleId);
			String returnId = refPot.getReturnId();
			if(returnId != null && !conceptualSchemataByName.containsKey(returnId))
				throw new IllegalStateException("Reference potential "+refPotId+" refers to missing return="+returnId);
			String roleIn = refPot.getRoleIn();
			if(roleIn != null && !twmModule.hasReferencePotential(roleIn))
				throw new IllegalStateException("Reference potential "+refPotId+" refers to missing reference potential="+roleIn);
		}
	}
	
	/**
	 * Adds empty lexicalized concepts for all words and reference potentials that do not
	 * have a lexicalized concept for the name they contain.
	 * 
	 * @see LexicalizedConceptualSchema
	 */
	private void addConceptsLackedByWordsAndReferencePotentials() {
		// Add missing concepts for reference potentials
		ITWM twmModule = ((ITWM)getModel().getModule(ITWM.class));
		for(ReferencePotential pot: twmModule.getReferencePotentials()) {
			String graphemic = pot.getGraphemic();
			List<String> schemata = lexicalizedConceptualSchemataByWord.get(graphemic);
			if(schemata == null) {
				createTypeSchemaFromGraphemicAndAddItToDM(graphemic);
			}
		}
		
		// Add missing concepts for words
		for(Word word: words.values()) {
			String graphemic = word.getGraphemic();
			List<String> schemata = lexicalizedConceptualSchemataByWord.get(graphemic);
			if(schemata == null) {
				createTypeSchemaFromGraphemicAndAddItToDM(graphemic);
			}
		}
	}
	
	/**
	 * Adds a schema without conceptual features, but with the given word. 
	 * It is assumed that the given word refers to an object schema.
	 * 
	 * Note that this method adds a unique feature to the schema and adds
	 * the schema to declarative memory already, because it is assumed that
	 * this method is invoked while the model is already running or that
	 * it will not be possible to determine the schema when the given word
	 * is processed by the model.
	 * 
	 * @param word
	 */
	private LexicalizedConceptualSchema createTypeSchemaFromGraphemicAndAddItToDM(String word) {
		
		// Make sure a non-merging declarative module is used
		INonMergingDeclarativeModule dm = (INonMergingDeclarativeModule)
				getModel().getDeclarativeModule();
		
		// Create a schema from the word
		String id = "CS#Synthetic$"+word;
		Map<String,Object> lexicalFeatures = new HashMap<String,Object>();
		lexicalFeatures.put(LexicalFeatures.graphemic.name(), word);
		LexicalizedConceptualSchema schema = new LexicalizedConceptualSchema(id, "Type",
				Scope.GLOBAL, 0.0, false, true,
				lexicalFeatures, new LinkedList<String>());
		
		// Add a unique feature
		ConceptualSchema featureSchema = createUniqueTokenSchema(true);
		schema.getConceptualFeatures().add(featureSchema.getId());

		// Index
		conceptualSchemataByName.put(id, schema);
		conceptualSchemata.add(schema);
		addLexicalizedConceptualSchemaByWord(word, id);
		
		// Add to declarative memory
		try {
			ITWM twm = (ITWM)getModel().getModule(ITWM.class);
			twm.addToDMAndEnsureNameIsUnique(dm, twm.toChunk(featureSchema));
			twm.addToDMAndEnsureNameIsUnique(dm, twm.toChunk(schema));
		} catch (InterruptedException | ExecutionException e) {
			LOGGER.error("Failed to add synthetic schema "+schema.getId()
					+" or its unique feature "+featureSchema.getId()
					+" to declarative memory");
		}
		return schema;
	}
	
	/**
	 * Replaces all conceptual features with the prefix featuresOf:&lt;schemaId&gt; by the
	 * the features of the schema identified by &lt;schemaId&gt;.
	 */
	private void resolveFeaturesOf() {
		int iterations = 0;
		List<ConceptualSchema> schemataWithFeaturesOf = new LinkedList<>(conceptualSchemata);
		do {
			if(iterations++ > 10) {
				throw new IllegalStateException("Could not remove all featuresOf after "
						+iterations+" iterations, "+schemataWithFeaturesOf.size()
						+" featuresOf declarations remaining");
			} else {
				Iterator<ConceptualSchema> iterator = schemataWithFeaturesOf.iterator();
				while(iterator.hasNext()) {
					ConceptualSchema conceptualSchema = iterator.next();
					List<String> conceptualFeatures = conceptualSchema.getConceptualFeatures();
					List<String> featuresOfEntries = getFeaturesOfEntries(conceptualFeatures);
					if(featuresOfEntries.isEmpty()) {
						iterator.remove();
					} else {
						for(Object featuresOfEntry: featuresOfEntries) {
							String importSource = ((String)featuresOfEntry).substring(11);
							ConceptualSchema featureSource = conceptualSchemataByName.get(importSource);
							if(featureSource != null) {
								conceptualFeatures.remove(featuresOfEntry);
								conceptualFeatures.addAll(featureSource.getConceptualFeatures());
							} else {
								throw new IllegalStateException("Could not find feature "
										+importSource+" referenced from "+conceptualSchema.getId());
							}
						}
						if(getFeaturesOfEntries(conceptualFeatures).isEmpty()) {
							iterator.remove();
						}
					}
				}
			}
		} while(schemataWithFeaturesOf.size() > 0);
	}

	private List<String> getFeaturesOfEntries(List<String> conceptualFeatures) {
		return conceptualFeatures
				.stream()
				.filter(f -> f instanceof String && ((String)f).startsWith("featuresOf:"))
				.collect(Collectors.toList());
	}
	
	/**
	 * Adds a unique feature to each conceptual schema that does not have a single feature.
	 */
	private void addUniqueFeaturesToEmptyConcepts() {
		// Make sure a non-merging declarative module is used
		INonMergingDeclarativeModule dm = (INonMergingDeclarativeModule)
				getModel().getDeclarativeModule();
		
		// Create unique features for empty schemata, but warn,
		// if the schema has been added to declarative memory
		// already.
		LinkedList<ConceptualSchema> schemataToAdd = new LinkedList<>();
		for(ConceptualSchema schema: conceptualSchemata) {
			// Add unique features to empty schemata that are not unique
			// features themselves.
			if(!schema.getId().startsWith("CS#Unique$")
					&& schema.getConceptualFeatures().isEmpty()) {
				try {
					if(dm.getChunk(schema.getId()).get() != null) {
						throw new IllegalStateException("Cannot add unique feature to schema"
							+" that has already been added to declarative memory: "
							+schema.getId());
					}
					ConceptualSchema featureSchema = createUniqueTokenSchema(false);
					schemataToAdd.add(featureSchema);
					schema.getConceptualFeatures().add(featureSchema.getId());
				} catch (InterruptedException | ExecutionException e) {
					LOGGER.error("Failed to check if the empty schema "+schema.getId()
							+" is in declarative memory already, before adding a "
							+"unique feature: "+e.getMessage(), e);
				}
			}
		}
		conceptualSchemata.addAll(schemataToAdd);
	}
	
	/**
	 * Adds a schema without conceptual features, but with the given word. 
	 * 
	 * @param add Can be set to false when creating new schemata while
	 * 	iterating over the list of existing schemata to avoid
	 *  {@link ConcurrentModificationException}, collect the new schemata
	 *  and add them when iterations are finished. Even if false, the new
	 *  schema will already be added to {@link #conceptualSchemataByName}
	 *  (but not to {@link #conceptualSchemata}.
	 * @param word
	 */
	private ConceptualSchema createUniqueTokenSchema(boolean add) {
		String id = "CS#Unique$"+(nextUniqueFeatureId++);
		ConceptualSchema schema = new ConceptualSchema(id, "Token", Scope.GLOBAL, 0.0, false, true,
				new LinkedList<String>());
		conceptualSchemataByName.put(id, schema);
		if(add) {
			conceptualSchemata.add(schema);
		}
		return schema;
	}
	


	/**
	 * called after all the chunktypes, chunks, and productions have been
	 * installed, but before any instruments or extensions have been installed.
	 * If you need to attach to any other modules it should be done here.
	 * However, if you need to know about production or chunk creation events,
	 * you should attach listenes during install(IModel)
	 */
	@Override
	public void initialize() {
		super.initialize();
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("initializing " + getClass().getSimpleName());
		
		addConceptsLackedByWordsAndReferencePotentials();
		resolveFeaturesOf();
		addUniqueFeaturesToEmptyConcepts();
		addConceptualSchemataToTLS(getModel());
	}

	private void addConceptualSchemataToTLS(IModel model) {
		ITWM twm = (ITWM) model.getModule(ITWM.class);
		twm.setConceptualSchemata(conceptualSchemata, conceptualSchemataByName);
		LOGGER.info("Added "+conceptualSchemataByName.size()+" schemata to three-level semantics.");
	}
	
	/**
	 * Reads the next log entry that represents a recorded saccade or fixation.
	 * 
	 * @return the next log entry, or null, if no further log entries are available
	 * @throws IOException
	 */
	LogEntry loadNextLogEntry() throws IOException {
		// Fill lookahead
		if(lookAhead == null) {
			lookAhead = loadNextLogEntry0();
		}
		
		if(lookAhead == null) {
			// There are no further log entries
			return null;
		} else {
			// If two saccades are read in a sequence, merge them.
			// If two fixations are read in a sequence, merge them, if
			// their foveated and parafoveated words are identical. 
			LogEntry nextEntry = lookAhead;
			boolean keepLookingAhead = false;
			do {
				lookAhead = loadNextLogEntry0();
				boolean saccadeAhead = lookAhead instanceof Saccade;
				boolean fixationAhead = lookAhead instanceof Fixation;
				keepLookingAhead = lookAhead != null
						&& ((nextEntry instanceof Saccade && saccadeAhead)
							 || (nextEntry instanceof Fixation
								 && fixationAhead
								 && ((Fixation)nextEntry).getFoveatedWord().equals(((Fixation)lookAhead).getFoveatedWord())
								 && ((((Fixation)nextEntry).getRegressionInfo() == null && ((Fixation)lookAhead).getRegressionInfo() == null)
										 || (((Fixation)nextEntry).getRegressionInfo() != null
										 		&& ((Fixation)lookAhead).getRegressionInfo() != null
										 		&& ((Fixation)nextEntry).getRegressionInfo().equals(((Fixation)lookAhead).getRegressionInfo()))) 
								 && ((Fixation)nextEntry).getParafoveatedWords().equals(((Fixation)lookAhead).getParafoveatedWords())));
				if(keepLookingAhead) {
					// Note: Because regression path durations in the original analysis were
					// computed as sums of fixation durations, combined saccades and fixations
					// will also have a duration equal to the sum of their individual durations
					// instead of the difference between start of the first and end of the second
					// event.
					if(saccadeAhead) {
						// Combine successive saccades
						long combinedDurationMs = lookAhead.getDurationMs()+nextEntry.getDurationMs();
						nextEntry = new Saccade(nextEntry.getGroup(), nextEntry.getTrialId(),
								nextEntry.getPageId(), nextEntry.getCondition(),
								nextEntry.getNumber(), nextEntry.getStartTimestampMs(),
								combinedDurationMs);
					} else if(fixationAhead) {
						// Combine successive fixations
						Fixation lastFixation = (Fixation)nextEntry;
						long combinedDuration = lookAhead.getDurationMs()+lastFixation.getDurationMs();
						nextEntry = new Fixation(lastFixation.getGroup(), lastFixation.getTrialId(),
								lastFixation.getPageId(), lastFixation.getCondition(),
								lastFixation.getNumber(), lastFixation.getStartTimestampMs(),
								combinedDuration, lastFixation.getPorX(), lastFixation.getPorY(),
								lastFixation.getRegressionInfo(),
								lastFixation.getFoveatedWord(), lastFixation.getParafoveatedWords());
					} else {
						throw new IllegalStateException("Failed to look ahead");
					}
				} else if(nextEntry instanceof Fixation && !keepLookingAhead && fixationAhead) {
					// There is another fixation with other words ahead
					((Fixation)nextEntry).setFixationFollowsImmediately(true);
				}
			} while(keepLookingAhead);
			return nextEntry;
		}
	}
	
	protected LogEntry loadNextLogEntry0() throws IOException {
		String line = eventsReader.readLine();
		if(line == null) {
			return null;
		} else {
			JSONTokener tokener = new JSONTokener(line);
			JSONArray array = (JSONArray)tokener.nextValue();
			String type = array.getString(0);
			if(type.equals("SAC")) {
				return loadSaccade(array);
			} else if(type.equals("FIX")) {
				return loadFixation(array);
			} else {
				throw new IllegalStateException("Unknown event type: "+type);
			}
		}
	}
	
	Saccade loadSaccade(JSONArray array) {
		if(array.length() != 8)
			throw new IllegalStateException("Unexpected length of saccade array: "+array.length()+" expected 8");
		return new Saccade(array.getString(1), array.getInt(2), array.getString(3),
				getStringOrNull(array.get(4)),  array.getInt(5), array.getLong(6), array.getLong(7));
	}
	
	Fixation loadFixation(JSONArray array) {
		if(array.length() != 12)
			throw new IllegalStateException("Unexpected length of fixation array: "+array.length()+" expected 12");
		RegressionInfo regressionInfo = getRegressionInfo(array.getJSONArray(10));
		JSONArray wordsArray = array.getJSONArray(11);
		Fixation.Word foveatedWord = getFoveatedWord(wordsArray);
		Set<Fixation.Word> parafoveatedWords = getParafoveatedWords(wordsArray);
		return new Fixation(array.getString(1), array.getInt(2), array.getString(3),
				getStringOrNull(array.get(4)),
				array.getInt(5), array.getLong(6), array.getLong(7),
				array.getInt(8), array.getInt(9),
				regressionInfo, foveatedWord, parafoveatedWords);
	}
	
	private String getStringOrNull(Object value) {
		return value == JSONObject.NULL?null:(String)value;
	}
	
	Fixation.RegressionInfo getRegressionInfo(JSONArray info) {
		if(info.length() == 0) {
			return null;
		} else if(info.length() != 9) {
			throw new IllegalArgumentException("Unexpected length of regression info array: "+info.length()+" expected "+9);
		} else {
			return new RegressionInfo(info.getInt(0), info.getInt(1), info.getString(2),
						info.getString(3), info.getString(4),
						info.getString(5), info.getInt(6), info.getInt(7),
						info.getString(8));
		}
	}
	
	Fixation.Word getFoveatedWord(JSONArray words) {
		if(words.length() < 1)
			throw new IllegalStateException("No foveated word available");
		JSONArray word = words.getJSONArray(0);
		if(!word.getBoolean(0))
			throw new IllegalStateException("Foveated word is not marked as foveated: "+word.toString());
		return getWord(word);
	}

	private Fixation.Word getWord(JSONArray word) {
		if(word.length() != 8)
			throw new IllegalStateException("Unexpected length of word array: "+word.length()+" expected 8");
		return new Fixation.Word(word.getString(1), word.getInt(2), word.getInt(3), word.getInt(4),
				word.getString(5), word.getInt(6), word.getInt(7));
	}
	
	Set<Fixation.Word> getParafoveatedWords(JSONArray words) {
		if(words.length() < 2) {
			return Collections.emptySet();
		} else {
			int numberOfParafoveatedWords = words.length()-1;
			Set<Fixation.Word> parafoveatedWords = new HashSet<Fixation.Word>();
			for(int i=1;i<numberOfParafoveatedWords;i++) {
				JSONArray word = words.getJSONArray(i);
				if(word.getBoolean(0))
					throw new IllegalStateException("Parafoveated word is marked as foveated: "+word.toString());
				parafoveatedWords.add(getWord(word));
			}
			return parafoveatedWords;
		}
	}
	
	public IChunkType getNextWordChunkType() {
		if(nextWordChunkType == null) {
			try {
				nextWordChunkType = getModel().getDeclarativeModule().getChunkType("get-next-word").get();
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error("Failed to load get-next-word chunk type: "+e.getMessage(), e);
			}
		}
		return nextWordChunkType;
	}
	
	@Override
	public double getEncodingFactor() {
		return encodingFactor;
	}

	@Override
	public double getEncodingExponentFactor() {
		return encodingExponentFactor;
	}

	@Override
	public double getFrequency(Fixation.Word word) {
		// TODO: Might actually use an approximation based on previous retrievals?
		// After collecting the first 100 retrievals? Currently the frequency for
		// referential words is implicitly reflected in the retrieval time of
		// re-activated referents.
		return defaultWordFrequency;
	}

	@Override
	public double getHorizontalResolutionPxPerMM() {
		return horizontalResolutionPxPerMM;
	}

	@Override
	public double getVerticalResolutionPxPerMM() {
		return verticalResolutionPxPerMM;
	}

	@Override
	public double getDistanceToScreenMM() {
		return distanceToScreenMM;
	}

	@Override
	public double getCancellableProgrammingDurationS() {
		return cancellableProgrammingDurationS;
	}

	@Override
	public double getNonCancellableProgrammingDurationS() {
		return nonCancellableProgrammingDurationS;
	}
	/*
	@Override
	public double getFixedExecutionDurationS() {
		return fixedExecutionDurationS;
	}

	@Override
	public double getExecutionDurationPerDegreeS() {
		return executionDurationPerDegreeS;
	}*/

	/**
	 * if you want to install some buffers, replace this code
	 */
	protected Collection<IActivationBuffer> createBuffers() {
		remmaBuffer = new REMMABuffer(this);
		return Collections.singleton(remmaBuffer);
	}

	@Override
	public void addListener(IREMMAListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(IREMMAListener listener) {
		listeners.remove(listener);
	}
	
	protected void fireFixationStarted(Fixation fixation, double startTime) {
		listeners.forEach(l -> {
			l.fixationStarted(fixation, startTime);
		});
	}
	
	protected void fireFixationFinished(Fixation fixation, double startTime, double endTime) {
		listeners.forEach(l -> {
			l.fixationFinished(fixation, startTime, endTime);
		});
	}
	
	protected void fireEncodingWord(Fixation.Word word, IChunk chunk,
			double encodingStart, double encodingEnd) {
		listeners.forEach( l -> {
			l.encodingWord(word, chunk, encodingStart, encodingEnd);
		});
	}

	@Override
	public Collection<String> getSetableParameters() {
		return getPossibleParameters();
	}

	@Override
	public Collection<String> getPossibleParameters() {
		return parameterMap.keySet();
	}

	@Override
	public void setParameter(String key, String value) {
		if(parameterMap.containsKey(key)) {
			parameterMap.put(key, value);
		} else {
			super.setParameter(key, value);
		}
	}

	@Override
	public String getParameter(String key) {
		if(parameterMap.containsKey(key)) {
			return parameterMap.get(key);
		} else {
			return super.getParameter(key);
		}
	}

	@Override
	public void reset() {
		nextUniqueFeatureId = 0;
		words.clear();
		wordsByUriAndLine.clear();
		referencePotentialsByUriAndLine.clear();
		conceptualSchemataByName.clear();
		conceptualSchemata.clear();
		lexicalizedConceptualSchemataByWord.clear();
		try {
			eventsReader.close();
			eventsReader = createEventsReader();
		} catch (IOException e) {
			LOGGER.error("Failed to re-open events reader: "+e.getMessage(), e);
		}
	}

	/**
	 * please, for the love of god, dispose of your resources
	 * 
	 * @see org.jactr.core.module.AbstractModule#dispose()
	 */
	@Override
	public void dispose() {
		super.dispose();
		try {
			lookAhead = null;
			eventsReader.close();
			eventsReader = null;
		} catch (IOException e) {
			LOGGER.error("Failed to close file access: "+e.getMessage(), e);
		}
	}
}
