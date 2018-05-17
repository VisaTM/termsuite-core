package fr.univnantes.termsuite.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import org.apache.uima.UIMAException;
import org.apache.uima.cas.admin.CASAdminException;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.xml.sax.SAXException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.univnantes.termsuite.api.TXTCorpus;
import fr.univnantes.termsuite.api.TermSuite;
import fr.univnantes.termsuite.io.json.JsonOptions;
import fr.univnantes.termsuite.io.json.JsonTerminologyIO;
import fr.univnantes.termsuite.model.IndexedCorpus;
import fr.univnantes.termsuite.model.Tagger;
import fr.univnantes.termsuite.tools.opt.TermSuiteCliOption;

public class PreprocessorCLI extends CommandLineClient { // NO_UCD (public entry point)
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PreprocessorCLI.class);
	
	public PreprocessorCLI() {
		super("Applies TermSuite's preprocessings to given text corpus.");
	}

	@Override
	public void configureOpts() {
		clientHelper.declareResourceOpts();
		clientHelper.declareHistory();
		clientHelper.declareBigCorpusOptions();
		declareFacultative(TermSuiteCliOption.TAGGER);
		declareMandatory(TermSuiteCliOption.TAGGER_PATH);
		declareMandatory(TermSuiteCliOption.FROM_TXT_CORPUS_PATH);
		declareMandatory(TermSuiteCliOption.LANGUAGE);
		declareFacultative(TermSuiteCliOption.ENCODING);
		declareAtLeastOneOf(
				TermSuiteCliOption.CAS_JSON, 
				TermSuiteCliOption.CAS_TSV,
				TermSuiteCliOption.CAS_XMI,
				TermSuiteCliOption.PREPARED_TERMINO_JSON);
	}

	private String getXmiDocumentText(JCas jcas, InputStream is) throws IOException, SAXException {
	    XmiCasDeserializer.deserialize(is, jcas.getCas(), true /* specifie qu'il faut ignorer les typesystems inconnus */);
	    return jcas.getDocumentText();
	}
	
	private void processSomeDirectory(File dir) throws UIMAException, IOException, SAXException, CASAdminException {
	    JCas jcas = JCasFactory.createJCas(); /* creation d'un CAS, operation couteuse */
	    for (File f : dir.listFiles()) {
	    	if(f.getPath().toLowerCase().endsWith(".xmi")) {
		        try (InputStream is = new FileInputStream(f)) {
		            String text = getXmiDocumentText(jcas, is);
		            
		            // création d'un nouveau fichier .txt et ajout du XmiDocumentText
		            File txtFile = new File(f.getPath().replace(".xmi", ".txt"));
	            	OutputStream os = new FileOutputStream(txtFile);
	            	
	    			// if file doesn't exists, then create it
	    			if (!txtFile.exists()) {
	    				txtFile.createNewFile();
	    			}
	    			
	    			byte[] textInBytes = text.getBytes(); // get the text content in bytes	    			
	            	os.write(textInBytes);
	            	os.flush();
	            	os.close();
	            	is.close();
	            	//Files.deleteIfExists(f.toPath()); // remove original xmi file
		        }
	    	}
	       jcas.reset(); /* reutilisation du meme CAS pour eviter d'en creer plusieurs */
	    }
	}
	@Override
	protected void run() throws IOException, UIMAException, CASAdminException, SAXException {
		fr.univnantes.termsuite.api.Preprocessor preprocessor = TermSuite.preprocessor();
		
		// Conversion des fichiers XMI en TXT avec récupération du texte à partir d'un XMI
		if(isSet(TermSuiteCliOption.FROM_TXT_CORPUS_PATH)) {
			Path ascorpusPath = asDir(TermSuiteCliOption.FROM_TXT_CORPUS_PATH);
			processSomeDirectory(ascorpusPath.toFile());
		}
		
		if(isSet(TermSuiteCliOption.TAGGER))
			preprocessor.setTagger(Tagger.forName(asString(TermSuiteCliOption.TAGGER)));
		
		preprocessor.setTaggerPath(asDir(TermSuiteCliOption.TAGGER_PATH));

		if(clientHelper.getHistory().isPresent()) 
			preprocessor.setHistory(clientHelper.getHistory().get());
		
		preprocessor.setResourceOptions(clientHelper.getResourceConfig());
		
		TXTCorpus txtCorpus = clientHelper.getTxtCorpus();

		if(isSet(TermSuiteCliOption.CAS_XMI)) {
			Path dir = asDir(TermSuiteCliOption.CAS_XMI);
			preprocessor.exportAnnotationsToXMI(dir);
			LOGGER.debug("Configuring XMI CAS export to directory {}", dir);

		}

		if(isSet(TermSuiteCliOption.CAS_TSV)) {
			Path dir = asDir(TermSuiteCliOption.CAS_TSV);
			preprocessor.exportAnnotationsToTSV(dir);
			LOGGER.debug("Configuring TSV CAS export to directory {}", dir);
		}

		if(isSet(TermSuiteCliOption.CAS_JSON)) {
			Path dir = asDir(TermSuiteCliOption.CAS_JSON);
			preprocessor.exportAnnotationsToJSON(dir);
			LOGGER.debug("Configuring JSON CAS export to directory {}", dir);
		}

		if(isSet(TermSuiteCliOption.PREPARED_TERMINO_JSON)) {
			Path destJson = asPath(TermSuiteCliOption.PREPARED_TERMINO_JSON);
			try(FileWriter writer = new FileWriter(destJson.toFile())) {
				IndexedCorpus corpus = preprocessor.toIndexedCorpus(
						txtCorpus, 
						clientHelper.getCappedSize(), 
						clientHelper.getOccurrenceStore(txtCorpus.getLang()));
				JsonTerminologyIO.save(writer, corpus, new JsonOptions());
			}
		} else 
			// consume
			LOGGER.debug("Consuming the stream with #count() as there is no single-file terminology export.");
		
		// Returns this preprocessor as a stream of prepared CASes.
		preprocessor.asStream(txtCorpus).count();
	}


	public static void main(String[] args) {
		new PreprocessorCLI().runClient(args);
	}
}
