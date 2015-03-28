package de.monochromata.jactr.remma;

import java.util.Map;
import java.util.TreeMap;

import org.jactr.io.participant.impl.BasicASTParticipant;

/**
 * The IASTParticipant is responsible for providing IASTInjector and
 * IASTTrimmers, which modify the abstract syntax trees describing models. This
 * participant takes the location of a model descriptor (with no modules) and
 * installs the contents into the model passed to it.<br>
 * <br>
 * All you need to do is create the model file and set its location to
 * DEFAULT_LOCATION<br>
 * <br>
 * If your module has parameters (implements IParameterized), you can set the
 * default values via createParameterMap()
 */
public class REMMAParticipant extends BasicASTParticipant {
	
	/**
	 * default location of the model content to import or trim
	 */
	private static final String DEFAULT_LOCATION = "de/monochromata/jactr/remma/remma.jactr";
	
	public static final String PATH_TO_EVENTS = "pathToEvents";
	public static final String DEFAULT_PATH_TO_EVENTS = "remmaInput.log";
	
	public static final String PATH_TO_JSON = "pathToJSON";
	public static final String DEFAULT_PATH_TO_JSON = "ast.json";
	
	public static final String FEATURE_THAT_MARKS_METHOD_SCHEMATA = "featureThatMarksMethodSchemata";
	public static final String DEFAULT_FEATURE_THAT_MARKS_METHOD_SCHEMATA = "featuresOf:CS#Java$MethodDeclaration";
	
	public static final String ENCODING_FACTOR = "encodingFactor";
	public static final double DEFAULT_ENCODING_FACTOR = 0.006;
	public static final String ENCODING_EXPONENT_FACTOR = "encodingExponentFactor";
	public static final double DEFAULT_ENCODING_EXPONENT_FACTOR = 0.4;
	public static final String DEFAULT_FREQUENCY = "defaultFrequency";
	public static final double DEFAULT_DEFAULT_FREQUENCY = 0.01;
	public static final String SCREEN_WIDTH_MM = "screenWidthMM";
	public static final double DEFAULT_SCREEN_WIDTH_MM = 379;
	public static final String SCREEN_WIDTH_PX = "screenWidthPx";
	public static final double DEFAULT_SCREEN_WIDTH_PX = 1280;
	public static final String SCREEN_HEIGHT_MM = "screenHeightMM";
	public static final double DEFAULT_SCREEN_HEIGHT_MM = 304;
	public static final String SCREEN_HEIGHT_PX = "screenHeightPx";
	public static final double DEFAULT_SCREEN_HEIGHT_PX = 1024;
	public static final String DISTANCE_TO_SCREEN_MM = "distanceToScreenMM";
	public static final double DEFAULT_DISTANCE_TO_SCREEN_MM = 600;
	
	public static final String CANCELLABLE_PROGRAMMING_DURATION_S = "cancellableProgrammingDurationS";
	public static final double DEFAULT_CANCELLABLE_PROGRAMMING_DURATION_S = 0.135;
	public static final String NON_CANCELLABLE_PROGRAMMING_DURATION_S = "nonCancellableProgrammingDurationS";
	public static final double DEFAULT_NON_CANCELLABLE_PROGRAMMING_DURATION_S = 0.05;
	/*public static final String FIXED_EXECUTION_DURATION_S = "fixedExecutionDurationS";
	public static final double DEFAULT_FIXED_EXECUTION_DURATION_S = 0.02;
	public static final String EXECUTION_DURATION_PER_DEGREE_S = "executionDurationPerDegreeS";
	public static final double DEFAULT_EXECUTION_DURATION_PER_DEGREE_S = 0.002;*/
	
	/**
	 * must be a zero arg constructor
	 */
	public REMMAParticipant() {
		super(REMMAParticipant.class.getClassLoader().getResource(
				DEFAULT_LOCATION));
		setInstallableClass(REMMAModule.class);
		setParameterMap(createParameterMap());
	}

	public static Map<String, String> createParameterMap() {
		TreeMap<String, String> parameters = new TreeMap<String, String>();
		parameters.put(PATH_TO_EVENTS, DEFAULT_PATH_TO_EVENTS);
		parameters.put(PATH_TO_JSON, DEFAULT_PATH_TO_JSON);
		parameters.put(FEATURE_THAT_MARKS_METHOD_SCHEMATA, DEFAULT_FEATURE_THAT_MARKS_METHOD_SCHEMATA);
		
		parameters.put(ENCODING_FACTOR, ""+DEFAULT_ENCODING_FACTOR);
		parameters.put(ENCODING_EXPONENT_FACTOR, ""+DEFAULT_ENCODING_EXPONENT_FACTOR);
		parameters.put(DEFAULT_FREQUENCY, ""+DEFAULT_DEFAULT_FREQUENCY);
		parameters.put(SCREEN_WIDTH_MM, ""+DEFAULT_SCREEN_WIDTH_MM);
		parameters.put(SCREEN_WIDTH_PX, ""+DEFAULT_SCREEN_WIDTH_PX);
		parameters.put(SCREEN_HEIGHT_MM, ""+DEFAULT_SCREEN_HEIGHT_MM);
		parameters.put(SCREEN_HEIGHT_PX, ""+DEFAULT_SCREEN_HEIGHT_PX);
		parameters.put(DISTANCE_TO_SCREEN_MM, ""+DEFAULT_DISTANCE_TO_SCREEN_MM);
		
		parameters.put(CANCELLABLE_PROGRAMMING_DURATION_S, ""+DEFAULT_CANCELLABLE_PROGRAMMING_DURATION_S);
		parameters.put(NON_CANCELLABLE_PROGRAMMING_DURATION_S, ""+DEFAULT_NON_CANCELLABLE_PROGRAMMING_DURATION_S);
		/*parameters.put(FIXED_EXECUTION_DURATION_S, ""+DEFAULT_FIXED_EXECUTION_DURATION_S);
		parameters.put(EXECUTION_DURATION_PER_DEGREE_S, ""+DEFAULT_EXECUTION_DURATION_PER_DEGREE_S);*/
		
		return parameters;
	}
}
