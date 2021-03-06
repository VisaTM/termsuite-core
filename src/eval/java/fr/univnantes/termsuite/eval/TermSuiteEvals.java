package fr.univnantes.termsuite.eval;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import fr.univnantes.termsuite.api.ExtractorOptions;
import fr.univnantes.termsuite.api.IndexedCorpusIO;
import fr.univnantes.termsuite.api.TXTCorpus;
import fr.univnantes.termsuite.api.TermSuite;
import fr.univnantes.termsuite.eval.bilangaligner.TerminoConfig;
import fr.univnantes.termsuite.eval.model.Corpus;
import fr.univnantes.termsuite.eval.model.LangPair;
import fr.univnantes.termsuite.io.json.JsonOptions;
import fr.univnantes.termsuite.model.IndexedCorpus;
import fr.univnantes.termsuite.model.Lang;
import fr.univnantes.termsuite.model.TermProperty;
import fr.univnantes.termsuite.test.func.FunctionalTests;

public class TermSuiteEvals {
	private static final Logger LOGGER = LoggerFactory.getLogger(TermSuiteEvals.class);
	
	private static final String EVAL_CONFIG = "termsuite-eval.properties";
	private static final String PROP_TREETAGGER_HOME_PATH = "treetagger.home.path";
	private static final String PROP_OUTPUTDIR = "eval.output";
	private static final String PROP_DICTIONARIES = "eval.dictionaries";

	/**
	 * The path to cached computed terminologies.
	 * 
	 * @return
	 */
	public static Path getTerminoDirectory() {
		return getSubDir("terminologies");		
	}
	
	
	public static Path getAlignmentDirectory() {
		return getSubDir("alignment");		
	}


	private static Path getSubDir(String other) {
		Path alignmentDir = getEvalOuputDirectory().resolve(other);
		if(!alignmentDir.toFile().exists())
			alignmentDir.toFile().mkdirs();
		return alignmentDir;
	}


	public static Object getCheckedProperty(String propertyName) {
		Object configProperty = getConfigProperty(propertyName);
		if(configProperty == null)
			configProperty = System.getProperty(propertyName);
		Preconditions.checkNotNull(configProperty, "No such property set: %s", propertyName);
		return configProperty;
	}
	
	public static Path getTreeTaggerPath() {
		String treeTaggerPathValue = getCheckedProperty(PROP_TREETAGGER_HOME_PATH).toString();
		return Paths.get(treeTaggerPathValue.toString());
	}

	public static Path getDictionariesPath() {
		String dicoPathValue = getCheckedProperty(PROP_DICTIONARIES).toString();
		return Paths.get(dicoPathValue.toString());
	}

	public static Path getEvalOuputDirectory() {
		String outputDirValue = getCheckedProperty(PROP_OUTPUTDIR).toString();
		return Paths.get(outputDirValue.toString());
	}

	private static Object getConfigProperty( String propName) {
		InputStream is = FunctionalTests.class.getClassLoader().getResourceAsStream(EVAL_CONFIG);
		Properties properties = new Properties();
		try {
			properties.load(is);
			is.close();
			return properties.get(propName);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static IndexedCorpus getTerminology(Corpus corpus, Lang lang, TerminoConfig config) throws IOException {
		Path path = getTerminologyPath(lang, corpus, config);
		if(!path.toFile().isFile()) {
			LOGGER.info("Terminology {} not found in cache", getTerminologyFileName(lang, corpus, config));
			TXTCorpus textCorpus = new TXTCorpus(lang, corpus.getRootDir().resolve(lang.getName()));
			IndexedCorpus extracted = toIndexedCorpus(textCorpus, config);
			IndexedCorpusIO.toJson(extracted, path, new JsonOptions().withOccurrences(false).withContexts(true));
		} else
			LOGGER.info("Terminology {} found in cache", getTerminologyFileName(lang, corpus, config));

		return IndexedCorpusIO.fromJson(path);
	}
	
	public static IndexedCorpus toIndexedCorpus(TXTCorpus textCorpus, TerminoConfig config) {
		IndexedCorpus corpus = TermSuite.preprocessor()
				.setTaggerPath(TermSuiteEvals.getTreeTaggerPath())
				.toIndexedCorpus(textCorpus, 1000000);
			
		ExtractorOptions extractorOptions = TermSuite.getDefaultExtractorConfig(textCorpus.getLang());
		extractorOptions.getContextualizerOptions().setEnabled(true);
		extractorOptions.getContextualizerOptions().setScope(config.getScope());
		extractorOptions.getContextualizerOptions().setMinimumCooccFrequencyThreshold(config.getCoocFrequencyTh());
		
		if(config.getFrequencyTh() > 1)
			extractorOptions.getPreFilterConfig().by(TermProperty.FREQUENCY).keepOverTh(config.getFrequencyTh());

		TermSuite.terminoExtractor()
					.setOptions(extractorOptions)
					.execute(corpus);
		
		return corpus;
	}

	
	
	public static String getTerminologyFileName(Lang lang, Corpus corpus, TerminoConfig config) {
		return String.format("%s-%s-th%s-scope%d.json", 
				corpus.getShortName(), 
				lang.getCode(), 
				Integer.toString(config.getFrequencyTh()), 
				config.getScope());
	}

	public static Path getTerminologyPath(Lang lang, Corpus corpus, TerminoConfig config) {
		return TermSuiteEvals.getTerminoDirectory().resolve(getTerminologyFileName(lang, corpus, config));
	}

	
	/**
	 * 
	 * Loads a bilingual dictionary from the directory denoted by system property {@link #PROP_DICTIONARIES}
	 * 
	 * @param langPair
	 * 				lang pair
	 * @return
	 * 			The path to the existing bilingual dico.
	 */
	public static Path getDictionaryPath(LangPair langPair) {
		return getDictionariesPath().resolve(String.format("%s-%s.txt", langPair.getSource().getCode(), langPair.getTarget().getCode()));
	}

	

	public static String getRunName(Lang source, Lang target, Corpus corpus, TerminoConfig config) {
		return String.format("alignment-results-%s-%s-%s-th%s-scope%d", 
				corpus.getShortName(),
				source.getCode(),
				target.getCode(),
				Integer.toString((int)config.getFrequencyTh()),
				config.getScope()
			);
	}

}
