package fr.univnantes.termsuite.engines.postproc;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.univnantes.termsuite.engines.ExtensionDetecter;
import fr.univnantes.termsuite.framework.TerminologyService;
import fr.univnantes.termsuite.model.RelationType;
import fr.univnantes.termsuite.model.Term;
import fr.univnantes.termsuite.model.TermProperty;
import fr.univnantes.termsuite.model.TermRelation;
import jetbrains.exodus.core.dataStructures.hash.HashSet;

public class IndependanceScorer {
	private static final Logger LOGGER = LoggerFactory.getLogger(IndependanceScorer.class);
	

	public Set<Term> getExtensions(TerminologyService terminology, Term from) {
		Set<Term> extensions = new HashSet<>();
		
		terminology
			.extensions(from)
			.forEach(rel -> {
				extensions.add(rel.getTo());
				extensions.addAll(getExtensions(terminology, rel.getTo()));
			});
		
		return extensions;
	}

	public void checkNoCycleInExtensions(TerminologyService terminology) {
		Predicate<TermRelation> badExtension = rel -> rel.getFrom().getWords().size() >= rel.getTo().getWords().size();
		if(terminology.extensions().filter(badExtension).findFirst().isPresent()) {
			terminology.extensions()
				.filter(badExtension)
				.limit(10)
				.forEach(extension -> {
					LOGGER.warn("Bad relation found: {}", extension);
				});
			throw new IllegalStateException("Terminology contains potential extension cycles");
		}
	}

	public void setIndependance(TerminologyService terminology) {

		LOGGER.debug("Checking potential cycles in terminology");
		checkNoCycleInExtensions(terminology);
		if(!terminology.extensions().findAny().isPresent()) {
			LOGGER.info("No {} relation set. Computing extension detection.", RelationType.HAS_EXTENSION);
			new ExtensionDetecter().detectExtensions(terminology.getTerminology());
		}

		
		/*
		 * 1. Init independant frequency
		 */
		LOGGER.debug("Init INDEPENDANT_FREQUENCY for all terms");
		terminology
			.terms()
			.forEach(t-> t.setProperty(
					TermProperty.INDEPENDANT_FREQUENCY, 
					t.getFrequency()));
		
		
		/*
		 * 2. Compute depths
		 */
		LOGGER.debug("Computing DEPTH property for all terms");
		final AtomicInteger depth = setDepths(terminology);
		LOGGER.debug("Depth of terminology is {}", depth.intValue());
		
		
		/*
		 * 3. Score INDEPENDANT_FREQUENCY
		 */
		LOGGER.debug("Computing INDEPENDANT_FREQUENCY by  for all terms");
		do {
			terminology
				.terms()
				.filter(t -> t.isPropertySet(TermProperty.DEPTH))
				.filter(t -> t.getDepth() == depth.intValue())
				.forEach(t -> {
					final int frequency = t.getPropertyIntegerValue(TermProperty.INDEPENDANT_FREQUENCY);
					getBases(terminology, t)
						.forEach(base -> {
							int baseFrequency = base.getPropertyIntegerValue(TermProperty.INDEPENDANT_FREQUENCY);
							baseFrequency -= frequency;
							base.setProperty(TermProperty.INDEPENDANT_FREQUENCY, baseFrequency);
						});
				});
				depth.decrementAndGet();
		} while(depth.intValue() > 0);
		
		
		/*
		 * 4. Score INDEPENDANCE
		 */
		LOGGER.debug("Computing INDEPENDANCE for all terms");
		terminology.terms()
			.forEach(t -> {
				double iFreq = (double)t.getPropertyIntegerValue(TermProperty.INDEPENDANT_FREQUENCY);
				int freq = t.getPropertyIntegerValue(TermProperty.FREQUENCY);
				t.setProperty(TermProperty.INDEPENDANCE, 
						iFreq / freq);
			});

	}

	public AtomicInteger setDepths(TerminologyService terminology) {
		terminology
			.terms()
			.filter(t -> t.isSingleWord())
			.forEach(t -> {t.setDepth(1);});
		
		final AtomicInteger currentDepth = new AtomicInteger(0);
		final AtomicBoolean anyAtCurrentDepth = new AtomicBoolean(false);
		do {
			anyAtCurrentDepth.set(false);
			currentDepth.incrementAndGet();
			terminology
				.terms()
				.filter(t -> t.isPropertySet(TermProperty.DEPTH) 
						&& t.getDepth() == currentDepth.intValue())
				.forEach(t -> {
					anyAtCurrentDepth.set(true);
					terminology
						.extensions(t)
						.forEach(rel -> rel.getTo().setDepth(currentDepth.intValue() + 1));
				});
		} while(anyAtCurrentDepth.get());
		currentDepth.decrementAndGet();
		
		Set<Term> noDepthTerms = terminology
			.terms()
			.filter(t -> !t.isPropertySet(TermProperty.DEPTH))
			.collect(toSet());
		
		if(!noDepthTerms.isEmpty()) {
			LOGGER.warn(
					String.format("Property %s not set for %d terms. Example: %s", 
							TermProperty.DEPTH, 
							noDepthTerms.size(),
							noDepthTerms.stream().limit(10).collect(toSet())));
		}
		
		return currentDepth;
	}
	
	public Set<Term> getBases(TerminologyService termino, Term term) {
		Set<Term> allBases = new java.util.HashSet<>(); 
		
		termino
			.extensionBases(term)
			.forEach(rel -> {
				allBases.add(rel.getFrom());
				allBases.addAll(getBases(termino, rel.getFrom()));
			});
		
		return allBases;
	}
}
