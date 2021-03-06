
/*******************************************************************************
 * Copyright 2015-2016 - CNRS (Centre National de Recherche Scientifique)
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 *******************************************************************************/

package fr.univnantes.termsuite.engines.splitter;

import static java.util.stream.Collectors.toList;

import java.util.List;

import org.slf4j.Logger;

import fr.univnantes.termsuite.engines.SimpleEngine;
import fr.univnantes.termsuite.framework.InjectLogger;
import fr.univnantes.termsuite.framework.Resource;
import fr.univnantes.termsuite.framework.service.RelationService;
import fr.univnantes.termsuite.framework.service.TermService;
import fr.univnantes.termsuite.model.Relation;
import fr.univnantes.termsuite.model.RelationType;
import fr.univnantes.termsuite.model.Term;
import fr.univnantes.termsuite.model.Word;
import fr.univnantes.termsuite.uima.ResourceType;
import fr.univnantes.termsuite.uima.resources.preproc.ManualSegmentationResource;

public class ManualPrefixSetter extends SimpleEngine {
	@InjectLogger Logger logger;

	@Resource(type=ResourceType.PREFIX_EXCEPTIONS)
	private ManualSegmentationResource prefixExceptions;

	
	@Override
	public void execute()  {
		Segmentation segmentation;
		for(TermService swt:terminology.getTerms()) {
			if(!swt.isSingleWord())
				continue;
			Word word = swt.getWords().get(0).getWord();
			segmentation = prefixExceptions.getSegmentation(word.getLemma());
			if(segmentation != null) 
				if(segmentation.size() <= 1) {
					List<RelationService> outboundRels = swt.outboundRelations(RelationType.IS_PREFIX_OF).collect(toList());
					for(RelationService tv:outboundRels) {
						terminology.removeRelation(tv);
						watch(swt.getTerm(), tv.getRelation());
					}
				} else {
					logger.warn("Ignoring prefix exception {}->{} since non-expty prefix exceptions are not allowed.",
							word.getLemma(),
							segmentation);
				}
		}
	}

	private void watch(Term swt, Relation tv) {
		if(history.isPresent()) {
			if(history.get().isTermWatched(swt))
				history.get().saveEvent(
						swt, 
						this.getClass(), 
						"Prefix variation of term " + tv.getTo() + " removed");
			if(history.get().isTermWatched(tv.getTo()))
				history.get().saveEvent(
						tv.getTo(), 
						this.getClass(), 
						"Prefix variation of term " + swt + " removed");
		}
	}
}
