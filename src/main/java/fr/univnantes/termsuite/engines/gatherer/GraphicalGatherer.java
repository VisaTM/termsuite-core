package fr.univnantes.termsuite.engines.gatherer;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.Lists;

import fr.univnantes.termsuite.framework.Parameter;
import fr.univnantes.termsuite.framework.TerminologyEngine;
import fr.univnantes.termsuite.metrics.EditDistance;
import fr.univnantes.termsuite.metrics.FastDiacriticInsensitiveLevenshtein;
import fr.univnantes.termsuite.model.RelationProperty;
import fr.univnantes.termsuite.model.Term;
import fr.univnantes.termsuite.model.TermRelation;
import fr.univnantes.termsuite.model.termino.CustomTermIndex;
import fr.univnantes.termsuite.model.termino.TermIndexes;

public class GraphicalGatherer extends TerminologyEngine {
	
	private EditDistance distance = new FastDiacriticInsensitiveLevenshtein(false);
	
	@Parameter
	private GathererOptions options;
	
	protected String indexName = TermIndexes.FIRST_LETTERS_2;

	/*
	 * Gives the direction of the graphical relation between two terms.
	 *  
	 */
	private static class GraphicalDirection implements Comparator<Term> {
		@Override
		public int compare(Term t1, Term t2) {
			if(t1.getFrequency() > t2.getFrequency())
				return -1;
			else if(t1.getFrequency() < t2.getFrequency())
				return 1;
			else if(t1.getSpecificity() > t2.getSpecificity())
				return -1;
			else if(t1.getSpecificity() < t2.getSpecificity())
				return 1;
			else {
				return t1.getGroupingKey().compareTo(t2.getGroupingKey());
			}
		}
	}
	
	@Override
	public void execute() {
		getLogger().info("Gathering graphical variants");
		AtomicLong comparisonCounter = new AtomicLong(0);
		CustomTermIndex index = terminology.getTerminology().getCustomIndex(indexName);
		index.cleanSingletonKeys();
		index.keySet().stream()
			.parallel()
			.forEach(key -> {
				Collection<Term> terms = index.getTerms(key);
				gather(terms, key, comparisonCounter);
			});
		terminology.getTerminology().dropCustomIndex(indexName);
		setIsGraphicalVariantProperties();
		getLogger().debug("Number of graphical comparison computed: {}", comparisonCounter.longValue());
	}
	
	protected void gather(Collection<Term> termClass, String clsName, AtomicLong comparisonCounter) {
		GraphicalDirection ordering = new GraphicalDirection();
		List<Term> terms = termClass.getClass().isAssignableFrom(List.class) ? 
				(List<Term>) termClass
					: Lists.newArrayList(termClass);
	
		Term t1, t2;
		double dist;
		for(int i=0; i<terms.size();i++) {
			t1 = terms.get(i);
			for(int j=i+1; j<terms.size();j++) {
				comparisonCounter.incrementAndGet();
				t2 = terms.get(j);
				dist = distance.computeNormalized(t1.getLemma(), t2.getLemma(), this.options.getGraphicalSimilarityThreshold());
				if(dist >= this.options.getGraphicalSimilarityThreshold()) {
					List<Term> pair = Lists.newArrayList(t1, t2);
					Collections.sort(pair, ordering);
					createGraphicalRelation(pair.get(0), pair.get(1), dist);
					
				}
			}
		}
	}
	
	protected TermRelation createGraphicalRelation(Term from, Term to, Double similarity) {
		TermRelation rel = terminology.createVariation(VariationType.GRAPHICAL, from, to);
		rel.setProperty(RelationProperty.GRAPHICAL_SIMILARITY, similarity);
		watch(from, to, similarity);
		return rel;
	}
	
	private void watch(Term from, Term to, double dist) {
		if(history.isPresent()) {
			if(history.get().isGKeyWatched(from.getGroupingKey()))
				history.get().saveEvent(
						from.getGroupingKey(),
						this.getClass(), 
						"Term has a new graphical variant " + to + " (dist="+dist+")");
			if(history.get().isGKeyWatched(to.getGroupingKey()))
				history.get().saveEvent(
						to.getGroupingKey(),
						this.getClass(), 
						"Term has a new graphical base " + from + " (dist="+dist+")");
		}
	}

	private void setIsGraphicalVariantProperties() {
		terminology.variations()
		.filter(r -> r.isPropertySet(RelationProperty.IS_GRAPHICAL) 
				&& !r.getPropertyBooleanValue(RelationProperty.IS_GRAPHICAL))
		.forEach(r -> {
			double similarity = distance.computeNormalized(
					r.getFrom().getLemma(), 
					r.getTo().getLemma(), 
					this.options.getGraphicalSimilarityThreshold()) ;
			boolean isGraphicalVariant = similarity >= this.options.getGraphicalSimilarityThreshold();
			r.setProperty(RelationProperty.IS_GRAPHICAL, isGraphicalVariant);
			if(isGraphicalVariant)
				r.setProperty(RelationProperty.GRAPHICAL_SIMILARITY, similarity);
			
			if(history.isPresent()) {
				if(history.get().isGKeyWatched(r.getFrom().getGroupingKey())
						|| history.get().isGKeyWatched(r.getTo().getGroupingKey()))
					history.get().saveEvent(
							r.getFrom().getGroupingKey(),
							this.getClass(), 
							"Variation "+r+" is marked as graphical (dist="+similarity+")");
					history.get().saveEvent(
						r.getTo().getGroupingKey(),
							this.getClass(), 
							"Variation "+r+" is marked as graphical (dist="+similarity+")");
			}

		});

		
	}

}
