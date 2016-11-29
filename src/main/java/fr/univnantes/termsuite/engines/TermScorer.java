package fr.univnantes.termsuite.engines;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import fr.univnantes.termsuite.model.OccurrenceStore;
import fr.univnantes.termsuite.model.RelationType;
import fr.univnantes.termsuite.model.Term;
import fr.univnantes.termsuite.model.TermIndex;
import fr.univnantes.termsuite.model.TermOccurrence;
import fr.univnantes.termsuite.model.TermProperty;
import fr.univnantes.termsuite.utils.StringUtils;
import fr.univnantes.termsuite.utils.TermOccurrenceUtils;
import fr.univnantes.termsuite.utils.TermSuiteConstants;
import fr.univnantes.termsuite.utils.TermUtils;

public class TermScorer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TermScorer.class);

	public void score(TermIndex index) {
		scoreIndependance(index);
		scoreOrthographic(index);
	}
	
	public void scoreIndependance(TermIndex index) {
		if(!index.getRelations(RelationType.HAS_EXTENSION).findAny().isPresent()) {
			LOGGER.info("No {} relation set. Computing extension detection.", RelationType.HAS_EXTENSION);
			new ExtensionDetecter().detectExtensions(index);
		}

		OccurrenceStore occStore = index.getOccurrenceStore();
		for(Term term:index.getTerms()) {
			Collection<TermOccurrence> occs = Lists.newLinkedList(occStore.getOccurrences(term));
			for(Term extension:TermUtils.getExtensions(index, term))
				TermOccurrenceUtils.removeOverlaps(occStore.getOccurrences(extension), occs);
			
			term.setProperty(TermProperty.INDEPENDANT_FREQUENCY, occs.size());
			term.setProperty(TermProperty.INDEPENDANCE, ((double)occs.size())/term.getFrequency());
		}
	}
	
	public void scoreOrthographic(TermIndex index) {
		for(Term term:index.getTerms()) 
			term.setProperty(
					TermProperty.ORTHOGRAPHIC_SCORE, 
					StringUtils.getOrthographicScore(term.getLemma().replaceAll(
							TermSuiteConstants.WHITESPACE_STRING, 
							TermSuiteConstants.EMPTY_STRING)));
	}

}