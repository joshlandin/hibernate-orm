/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal.source.hbm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.MappingException;
import org.hibernate.jaxb.spi.hbm.EntityElement;
import org.hibernate.jaxb.spi.hbm.JaxbClassElement;
import org.hibernate.jaxb.spi.hbm.JaxbJoinedSubclassElement;
import org.hibernate.jaxb.spi.hbm.JaxbSubclassElement;
import org.hibernate.jaxb.spi.hbm.JaxbUnionSubclassElement;
import org.hibernate.jaxb.spi.hbm.SubEntityElement;
import org.hibernate.metamodel.spi.source.EntitySource;
import org.hibernate.metamodel.spi.source.SubclassEntityContainer;
import org.hibernate.metamodel.spi.source.SubclassEntitySource;

/**
 * @author Steve Ebersole
 */
public class HierarchyBuilder {
	private final List<EntityHierarchyImpl> entityHierarchies = new ArrayList<EntityHierarchyImpl>();

	// process state
	private final Map<String,SubclassEntityContainer> subEntityContainerMap = new HashMap<String, SubclassEntityContainer>();
	private final List<ExtendsQueueEntry> extendsQueue = new ArrayList<ExtendsQueueEntry>();

	// mapping file specific state
	private MappingDocument currentMappingDocument;

	public void processMappingDocument(MappingDocument mappingDocument) {
		this.currentMappingDocument = mappingDocument;
		try {
			processCurrentMappingDocument();
		}
		finally {
			this.currentMappingDocument = null;
		}
	}

	private void processCurrentMappingDocument() {
		for(final JaxbClassElement jaxbClass : currentMappingDocument.getMappingRoot().getClazz()){
			// we can immediately handle <class/> elements in terms of creating the hierarchy entry
			final RootEntitySourceImpl rootEntitySource = new RootEntitySourceImpl( currentMappingDocument,
					jaxbClass
			);
			final EntityHierarchyImpl hierarchy = new EntityHierarchyImpl( rootEntitySource );

			entityHierarchies.add( hierarchy );
			subEntityContainerMap.put( rootEntitySource.getEntityName(), rootEntitySource );

			processSubElements( jaxbClass, rootEntitySource );
		}
		for(final JaxbJoinedSubclassElement element : currentMappingDocument.getMappingRoot().getJoinedSubclass()){
			processSubclassElement( element );

		}
		for(final JaxbUnionSubclassElement element : currentMappingDocument.getMappingRoot().getUnionSubclass()){
			processSubclassElement( element );
		}
		for(final JaxbSubclassElement element : currentMappingDocument.getMappingRoot().getSubclass()){
			processSubclassElement( element );
		}

	}

	private void processSubclassElement(SubEntityElement element) {
		// we have to see if this things super-type has been found yet, and if not add it to the
		// extends queue
		final String entityItExtends = currentMappingDocument.getMappingLocalBindingContext()
				.qualifyClassName( element.getExtends() );
		final SubclassEntityContainer container = subEntityContainerMap.get( entityItExtends );
		final SubclassEntitySourceImpl subClassEntitySource = new SubclassEntitySourceImpl( currentMappingDocument, element, (EntitySource) container );
		final String entityName = subClassEntitySource.getEntityName();
		subEntityContainerMap.put( entityName, subClassEntitySource );
		processSubElements( element, subClassEntitySource );
		if ( container != null ) {
			// we already have this entity's super, attach it and continue
			container.add( subClassEntitySource );
		}
		else {
			// we do not yet have the super and have to wait, so add it fto the extends queue
			extendsQueue.add( new ExtendsQueueEntry( subClassEntitySource, entityItExtends ) );
		}
	}

	public List<EntityHierarchyImpl> groupEntityHierarchies() {
		while ( ! extendsQueue.isEmpty() ) {
			// set up a pass over the queue
			int numberOfMappingsProcessed = 0;
			Iterator<ExtendsQueueEntry> iterator = extendsQueue.iterator();
			while ( iterator.hasNext() ) {
				final ExtendsQueueEntry entry = iterator.next();
				final SubclassEntityContainer container = subEntityContainerMap.get( entry.entityItExtends );
				if ( container != null ) {
					// we now have this entity's super, attach it and remove entry from extends queue
					container.add( entry.subClassEntitySource );
					iterator.remove();
					numberOfMappingsProcessed++;
				}
			}

			if ( numberOfMappingsProcessed == 0 ) {
				// todo : we could log the waiting dependencies...
				throw new MappingException( "Unable to process extends dependencies in hbm files" );
			}
		}

		return entityHierarchies;
	}

	private void processSubElements(EntityElement entityElement, SubclassEntityContainer container) {
		if ( JaxbClassElement.class.isInstance( entityElement ) ) {
			final JaxbClassElement jaxbClass = (JaxbClassElement) entityElement;
			processElements( jaxbClass.getJoinedSubclass(), container );
			processElements( jaxbClass.getSubclass(), container );
			processElements( jaxbClass.getUnionSubclass(), container );
		}
		else if ( JaxbSubclassElement.class.isInstance( entityElement ) ) {
			final JaxbSubclassElement jaxbSubclass = (JaxbSubclassElement) entityElement;
			processElements( jaxbSubclass.getSubclass(), container );
		}
		else if ( JaxbJoinedSubclassElement.class.isInstance( entityElement ) ) {
			final JaxbJoinedSubclassElement jaxbJoinedSubclass = (JaxbJoinedSubclassElement) entityElement;
			processElements( jaxbJoinedSubclass.getJoinedSubclass(), container );
		}
		else if ( JaxbUnionSubclassElement.class.isInstance( entityElement ) ) {
			final JaxbUnionSubclassElement jaxbUnionSubclass = (JaxbUnionSubclassElement) entityElement;
			processElements( jaxbUnionSubclass.getUnionSubclass(), container );
		}
	}

	private void processElements(List subElements, SubclassEntityContainer container) {
		for ( Object subElementO : subElements ) {
			final SubEntityElement subElement = (SubEntityElement) subElementO;
			final SubclassEntitySourceImpl subclassEntitySource = new SubclassEntitySourceImpl( currentMappingDocument, subElement, ( EntitySource ) container );
			container.add( subclassEntitySource );
			final String subEntityName = subclassEntitySource.getEntityName();
			subEntityContainerMap.put( subEntityName, subclassEntitySource );
		}
	}

	private static class ExtendsQueueEntry {
		private final SubclassEntitySource subClassEntitySource;
		private final String entityItExtends;

		private ExtendsQueueEntry(SubclassEntitySource subClassEntitySource, String entityItExtends) {
			this.subClassEntitySource = subClassEntitySource;
			this.entityItExtends = entityItExtends;
		}
	}
}
