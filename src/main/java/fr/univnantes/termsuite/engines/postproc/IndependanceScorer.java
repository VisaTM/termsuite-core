package fr.univnantes.termsuite.engines.postproc;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.slf4j.Logger;

import fr.univnantes.termsuite.engines.SimpleEngine;
import fr.univnantes.termsuite.framework.InjectLogger;
import fr.univnantes.termsuite.framework.service.RelationService;
import fr.univnantes.termsuite.framework.service.TermService;
import fr.univnantes.termsuite.model.TermProperty;
import jetbrains.exodus.core.dataStructures.hash.HashSet;

public class IndependanceScorer extends SimpleEngine {
	@InjectLogger Logger logger;

	public Set<TermService> getExtensions(TermService from) {
		Set<TermService> extensions = new HashSet<>();
		
		from
			.extensions()
			.forEach(rel -> {
				extensions.add(rel.getTo());
				extensions.addAll(getExtensions(rel.getTo()));
			});
		
		return extensions;
	}

	public void checkNoCycleInExtensions() {
		Predicate<RelationService> badExtension = rel -> rel.getFrom().getWords().size() >= rel.getTo().getWords().size();
		if(terminology
				.extensions()
				.filter(badExtension)
				.findFirst().isPresent()) {
			terminology.extensions()
				.filter(badExtension)
				.limit(10)
				.forEach(extension -> {
					logger.warn("Bad relation found: {}", extension);
				});
			throw new IllegalStateException("Terminology contains potential extension cycles");
		}
	}

	@Override
	public void execute() {
		
		checkNoCycleInExtensions();
		
		/*
		 * 1. Init independant frequency
		 */
		logger.debug("Init INDEPENDANT_FREQUENCY for all terms");
		terminology
			.terms()
			.forEach(t-> t.setProperty(
					TermProperty.INDEPENDANT_FREQUENCY, 
					t.getFrequency()));
		
		
		/*
		 * 2. Compute depths
		 */
		logger.debug("Computing DEPTH property for all terms");
		final AtomicInteger depth = setDepths();
		logger.debug("Depth of terminology is {}", depth.intValue());
		
		
		/*
		 * 3. Score INDEPENDANT_FREQUENCY
		 */
		logger.debug("Computing INDEPENDANT_FREQUENCY by  for all terms");
		do {
			terminology
				.terms()
				.filter(t -> t.isPropertySet(TermProperty.DEPTH))
				.filter(t -> t.getDepth() == depth.intValue())
				.forEach(t -> {
					final int frequency = t.getIndependantFrequency();
					getBases(t)
						.forEach(base -> {
							int baseFrequency = base.getIndependantFrequency();
							baseFrequency -= frequency;
							base.setProperty(TermProperty.INDEPENDANT_FREQUENCY, baseFrequency);
						});
				});
				depth.decrementAndGet();
		} while(depth.intValue() > 0);
		
		
		/*
		 * 4. Score INDEPENDANCE
		 */
		logger.debug("Computing INDEPENDANCE for all terms");
		terminology.terms()
			.forEach(t -> {
				double iFreq = (double)t.getIndependantFrequency();
				int freq = t.getFrequency();
				t.setProperty(TermProperty.INDEPENDANCE, 
						iFreq / freq);
			});

	}

	public AtomicInteger setDepths() {
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
					t.extensions()
						.forEach(rel -> rel.getTo().setDepth(currentDepth.intValue() + 1));
				});
		} while(anyAtCurrentDepth.get());
		currentDepth.decrementAndGet();
		
		Set<TermService> noDepthTerms = terminology
			.terms()
			.filter(t -> !t.isPropertySet(TermProperty.DEPTH))
			.collect(toSet());
		
		if(!noDepthTerms.isEmpty()) {
			logger.warn(
					String.format("Property %s not set for %d terms. Example: %s", 
							TermProperty.DEPTH, 
							noDepthTerms.size(),
							noDepthTerms.stream().limit(10).collect(toSet())));
		}
		
		return currentDepth;
	}
	
	public Set<TermService> getBases(TermService term) {
		Set<TermService> allBases = new java.util.HashSet<>(); 
		
		term
			.extensionBases()
			.forEach(rel -> {
				allBases.add(rel.getFrom());
				allBases.addAll(getBases(rel.getFrom()));
			});
		
		return allBases;
	}
}
