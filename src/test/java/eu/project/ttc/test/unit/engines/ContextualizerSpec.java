package eu.project.ttc.test.unit.engines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.Before;
import org.junit.Test;

import eu.project.ttc.engines.Contextualizer;
import eu.project.ttc.models.Term;
import eu.project.ttc.models.TermIndex;
import eu.project.ttc.test.unit.Fixtures;

public class ContextualizerSpec {

	private Term termWithContext1;
	private Term termWithContext2;
	private Term termWithContext3;
	private Contextualizer contextualizer;
	private TermIndex termIndex;
	
	
	@Before
	public void setup() {
		termIndex = Fixtures.termIndexWithOccurrences();
		termWithContext1 = termIndex.getTermByGroupingKey("n: énergie");
		termWithContext2 = termIndex.getTermByGroupingKey("a: éolien");
		termWithContext3 = termIndex.getTermByGroupingKey("n: accès");
		contextualizer = new Contextualizer(termIndex);
	}
	
	@Test
	public void computeContextVectorScope1() {
		contextualizer.setScope(1).contextualize();
		
		// T1 T2 T3 T1 T3 T3 T1

		assertThat(termWithContext1.getContext().getEntries())
			.hasSize(2)
			.extracting("coTerm.groupingKey", "nbCooccs")
			.contains(tuple("a: éolien", 1), tuple("n: accès", 3));

		assertThat(termWithContext2.getContext().getEntries())
			.hasSize(2)
			.extracting("coTerm.groupingKey", "nbCooccs")
			.contains(tuple("n: énergie", 1), tuple("n: accès", 1));

		assertThat(termWithContext3.getContext().getEntries())
			.hasSize(2)
			.extracting("coTerm.groupingKey", "nbCooccs")
			.contains(tuple("n: énergie", 3), tuple("a: éolien", 1));
	}

	@Test
	public void computeContextVectorScope3() {
		contextualizer.setScope(3).contextualize();
		
		// T1 T2 T3 T1 T3 T3 T1

		assertThat(termWithContext1.getContext().getEntries())
			.hasSize(2)
			.extracting("coTerm.groupingKey", "nbCooccs")
			.contains(tuple("a: éolien", 2), tuple("n: accès", 6));
	
		assertThat(termWithContext2.getContext().getEntries())
			.hasSize(2)
			.extracting("coTerm.groupingKey", "nbCooccs")
			.contains(tuple("n: énergie", 2), tuple("n: accès", 2));
	
		assertThat(termWithContext3.getContext().getEntries())
			.hasSize(2)
			.extracting("coTerm.groupingKey", "nbCooccs")
			.contains(tuple("n: énergie", 6), tuple("a: éolien", 2));
	}
}
