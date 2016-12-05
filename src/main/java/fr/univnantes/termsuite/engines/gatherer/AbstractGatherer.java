package fr.univnantes.termsuite.engines.gatherer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;

import fr.univnantes.termsuite.model.RelationProperty;
import fr.univnantes.termsuite.model.RelationType;
import fr.univnantes.termsuite.model.Term;
import fr.univnantes.termsuite.model.TermIndex;
import fr.univnantes.termsuite.model.TermRelation;
import fr.univnantes.termsuite.model.termino.CustomTermIndex;
import fr.univnantes.termsuite.model.termino.TermIndexes;
import fr.univnantes.termsuite.utils.TermHistory;

public class AbstractGatherer {
	
	private static final int DEFAULT_MAX_CLASS_SIZE = 2500;

	private Optional<TermHistory> history = Optional.empty();
	protected Set<VariantRule> variantRules;
	private GroovyService groovyService;
	private RuleType ruleType;
	private RelationType relationType;
//	private Optional<Predicate<Term>> termPredicate = Optional.empty();
	private String indexName = TermIndexes.ALLCOMP_PAIRS;

	private int maxClassSize = DEFAULT_MAX_CLASS_SIZE;
	

	AbstractGatherer setRuleType(RuleType ruleType) {
		this.ruleType = ruleType;
		return this;
	}
	
	AbstractGatherer setRelationType(RelationType relationType) {
		this.relationType = relationType;
		return this;
	}

	AbstractGatherer setIndexName(String indexName) {
		this.indexName = indexName;
		return this;
	}

//	AbstractGatherer setTermFilter(Predicate<Term> termPredicate) {
//		this. termPredicate = Optional.of(termPredicate);
//		return this;
//	}

	AbstractGatherer setHistory(TermHistory history) {
		if(history != null)
			this.history = Optional.of(history);
		return this;
	}
	
	AbstractGatherer setGroovyAdapter(GroovyService groovyService) {
		this.groovyService = groovyService;
		return this;
	}
	
	AbstractGatherer setVariantRules(Collection<VariantRule> variantRules) {
		for(VariantRule rule:variantRules)
			Preconditions.checkArgument(rule.getRuleType() == ruleType, 
			"Bad rule type for gatherer of type %s. Expected rule type %s, got: %s.",
				this.getClass().getSimpleName(),
				ruleType,
				rule.getRuleType()
			);
		this.variantRules = new HashSet<>(variantRules);
		return this;
	}
	
	public void gather(TermIndex termIndex) {
		CustomTermIndex index = termIndex.getCustomIndex(indexName);
		index.cleanSingletonKeys();
		index.dropBiggerEntries(maxClassSize, true);
		for(String key:index.keySet()) {
			Collection<Term> terms = index.getTerms(key);
//			if(termPredicate.isPresent())
//				terms = terms.stream().filter(termPredicate.get()).collect(Collectors.toSet());
			gather(termIndex, terms);
		}
	}

	private void watch(Term source, Term target, TermRelation tv) {
		if(history.isPresent()) {
			if(history.get().isWatched(source.getGroupingKey()))
				history.get().saveEvent(
						source.getGroupingKey(),
						this.getClass(), 
						"Term has a new variation: " + tv);
			if(history.get().isWatched(target.getGroupingKey()))
				history.get().saveEvent(
						target.getGroupingKey(),
						this.getClass(), 
						"Term has a new variation base: " + tv);
		}
	}

	private void gather(TermIndex termIndex, Collection<Term> termClass) {
		for(VariantRule rule:variantRules) {
			Set<Term> sources = termClass.stream()
				.filter(rule::isSourceAcceptable)
				.collect(Collectors.toSet());
			if(sources.isEmpty())
				continue;

			Set<Term> targets = termClass.stream()
					.filter(rule::isTargetAcceptable)
					.collect(Collectors.toSet());
			if(targets.isEmpty())
				continue;
			
			for(Term source:sources) {
				for(Term target:targets) {
					if(source.equals(target))
						continue;
					
					if(groovyService.matchesRule(rule, source, target)) 
						createVariationRelation(termIndex, source, target, rule);
				}
			}
		}
	}

	private void createVariationRelation(TermIndex termIndex, Term source, Term target, VariantRule rule) {
		TermRelation relation = new TermRelation(relationType, source, target);
		relation.setProperty(RelationProperty.VARIATION_RULE, rule.getName());
		termIndex.addRelation(relation);
		watch(source, target, relation);
	}
}