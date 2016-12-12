package fr.univnantes.termsuite.engines.gatherer;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

import fr.univnantes.termsuite.metrics.EditDistance;
import fr.univnantes.termsuite.metrics.FastDiacriticInsensitiveLevenshtein;
import fr.univnantes.termsuite.model.RelationProperty;
import fr.univnantes.termsuite.model.RelationType;
import fr.univnantes.termsuite.model.Term;
import fr.univnantes.termsuite.model.TermRelation;
import fr.univnantes.termsuite.model.Terminology;
import fr.univnantes.termsuite.model.termino.TermIndexes;

public class GraphicalGatherer extends AbstractGatherer {
	
	private EditDistance distance = new FastDiacriticInsensitiveLevenshtein(false);
	private double similarityThreshold = 1d;
	
	public GraphicalGatherer() {
		super();
		
		setRelationType(RelationType.VARIATION);
		setNbFixedLetters(2);
	}

	public GraphicalGatherer setDistance(EditDistance distance) {
		this.distance = distance;
		return this;
	}
	
	public GraphicalGatherer setSimilarityThreshold(double similarityThreshold) {
		this.similarityThreshold = similarityThreshold;
		return this;
	}
	
	public GraphicalGatherer setNbFixedLetters(int nbFixedLetters) {
		switch(nbFixedLetters){
		case 1:
			setIndexName(TermIndexes.FIRST_LETTERS_1, true);
			break;
		case 2:
			setIndexName(TermIndexes.FIRST_LETTERS_2, true);
			break;
		case 3:
			setIndexName(TermIndexes.FIRST_LETTERS_3, true);
			break;
		case 4:
			setIndexName(TermIndexes.FIRST_LETTERS_4, true);
			break;
		default:
			throw new IllegalArgumentException("Bad value for number of letters for a n-first-letters index: " + nbFixedLetters);
		}
		return this;
	}
	
	@Override
	protected void gather(Terminology termino, Collection<Term> termClass, String clsName) {
		
		List<Term> terms = termClass.getClass().isAssignableFrom(List.class) ? 
				(List<Term>) termClass
					: Lists.newArrayList(termClass);
	
		Term t1, t2;
		double dist;
		for(int i=0; i<terms.size();i++) {
			t1 = terms.get(i);
			for(int j=i+1; j<terms.size();j++) {
				cnt.incrementAndGet();
//				if(nbComparisons % OBSERVER_STEP == 0 && taskObserver.isPresent())
//					taskObserver.get().work(OBSERVER_STEP);
				t2 = terms.get(j);
				dist = distance.computeNormalized(t1.getLemma(), t2.getLemma(), this.similarityThreshold);
				if(dist >= this.similarityThreshold) {
//					gatheredCnt++;
					TermRelation rel1 = new TermRelation(RelationType.VARIATION, t1, t2);
					rel1.setProperty(RelationProperty.SIMILARITY, dist);
					rel1.setProperty(RelationProperty.VARIATION_TYPE, VariationType.GRAPHICAL);
					termino.addRelation(rel1);
					TermRelation rel2 = new TermRelation(RelationType.VARIATION, t2, t1);
					rel2.setProperty(RelationProperty.SIMILARITY, dist);
					rel2.setProperty(RelationProperty.VARIATION_TYPE, VariationType.GRAPHICAL);
					termino.addRelation(rel2);
					watch(t1, t2, dist);
					watch(t2, t1, dist);

				}
			}
		}
		
		// log some stats
//		logger.debug("Graphical gathering {} terms gathered / {} pairs compared", gatheredCnt, nbComparisons);
		
//		progressLoggerTimer.cancel();
	}
	
	private void watch(Term t1, Term t2, double dist) {
		if(history.isPresent()) {
			if(history.get().isWatched(t1.getGroupingKey()))
				history.get().saveEvent(
						t1.getGroupingKey(),
						this.getClass(), 
						"Term has a new graphical variant " + t2 + " (dist="+dist+")");
		}
	}

}
