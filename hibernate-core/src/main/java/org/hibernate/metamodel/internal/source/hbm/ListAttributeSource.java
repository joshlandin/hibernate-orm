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

import org.hibernate.jaxb.spi.hbm.JaxbListElement;
import org.hibernate.jaxb.spi.hbm.JaxbListIndexElement;
import org.hibernate.metamodel.spi.source.AttributeSourceContainer;
import org.hibernate.metamodel.spi.source.IndexedPluralAttributeSource;
import org.hibernate.metamodel.spi.source.PluralAttributeNature;

/**
 *
 */
public class ListAttributeSource extends AbstractPluralAttributeSourceImpl implements IndexedPluralAttributeSource {

	private final ListAttributeIndexSource indexSource;

	/**
	 * @param sourceMappingDocument
	 * @param listElement
	 * @param container
	 */
	public ListAttributeSource(
			MappingDocument sourceMappingDocument,
			JaxbListElement listElement,
			AttributeSourceContainer container ) {
		super( sourceMappingDocument, listElement, container );
		JaxbListIndexElement listIndexElement = listElement.getListIndex();
		if ( listIndexElement == null ) {
			this.indexSource = new ListAttributeIndexSource( sourceMappingDocument(), listElement.getIndex() );
		} else {
			this.indexSource = new ListAttributeIndexSource( sourceMappingDocument(), listIndexElement );
		}
	}

	@Override
	public ListAttributeIndexSource getIndexSource() {
		return indexSource;
	}

	@Override
	public JaxbListElement getPluralAttributeElement() {
		return ( JaxbListElement ) super.getPluralAttributeElement();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.hibernate.metamodel.spi.source.PluralAttributeSource#getPluralAttributeNature()
	 */
	@Override
	public PluralAttributeNature getPluralAttributeNature() {
		return PluralAttributeNature.LIST;
	}
}
