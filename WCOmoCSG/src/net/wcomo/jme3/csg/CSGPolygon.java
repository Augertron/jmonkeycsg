/** Copyright (c) 2011 Evan Wallace (http://madebyevan.com/)
 	Copyright (c) 2003-2014 jMonkeyEngine
	Copyright (c) 2015, WCOmohundro
	All rights reserved.

	Redistribution and use in source and binary forms, with or without modification, are permitted 
	provided that the following conditions are met:

	1. 	Redistributions of source code must retain the above copyright notice, this list of conditions 
		and the following disclaimer.

	2. 	Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
		and the following disclaimer in the documentation and/or other materials provided with the distribution.
	
	3. 	Neither the name of the copyright holder nor the names of its contributors may be used to endorse 
		or promote products derived from this software without specific prior written permission.

	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
	WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
	PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR 
	ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
	LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
	INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
	OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN 
	IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
	
	Logic and Inspiration taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomo.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;

/**  Constructive Solid Geometry (CSG)

  	ACSGPolygon represents a flat geometric shape defined by a set of vertices in a given plane.
  	
 	A CSGPolygon is assumed to be immutable once created, which means that its internal
 	structure is never changed.  This means that a clone of a polygon just uses the
 	original instance.
 */
public class CSGPolygon 
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPolygonRevision="$Rev$";
	public static final String sCSGPolygonDate="$Date$";

	/** Static empty list of vertices */
	protected static final List<CSGVertex> sEmptyVertices = new ArrayList<CSGVertex>(0);
	
	/** The vertices that define this shape */
	protected List<CSGVertex>	mVertices;
	/** The plane that contains this shape */
	protected CSGPlane 			mPlane;
	/** The custom material that applies to this shape */
	protected int				mMaterialIndex;

	/** Standard null constructor */
	public CSGPolygon(
	) {
		this( sEmptyVertices, 0 );
	}
	
	/** Constructor based on a set of Vertices (minimum 3 expected) */
	public CSGPolygon(
		List<CSGVertex>		pVertices
	,	int					pMaterialIndex
	) {
		this( pVertices, CSGPlane.fromVertices( pVertices ), pMaterialIndex );
	}
	
	/** Internal constructor based on given vertices and plane */
	protected CSGPolygon(
		List<CSGVertex>		pVertices
	,	CSGPlane			pPlane
	,	int					pMaterialIndex
	) {
		mVertices = pVertices;
		mPlane = pPlane;
		mMaterialIndex = pMaterialIndex;
	}
	
	/** Make a copy */
	public CSGPolygon clone(
		boolean 	pFlipIt
	) {
		if ( pFlipIt ) {
			// Flip all the components
			List<CSGVertex> newVertices = new ArrayList<CSGVertex>( mVertices.size() );
			for( CSGVertex aVertex : mVertices ) {
				CSGVertex bVertex = aVertex.clone( pFlipIt );
				
				// Flip the order of the vertices as we go
				newVertices.add( 0, bVertex );
			}
			return( new CSGPolygon( newVertices, mPlane.clone( pFlipIt ), mMaterialIndex ) );
		} else {
			// The polygon is immutable, so its clone is just itself
			return( this );
		}
	}
	
	/** Accessor to the vertices */
	public List<CSGVertex> getVertices() { return mVertices; }
	
	/** Accessor to the plane */
	public CSGPlane getPlane() { return mPlane; }
	
	/** Accessor to the custom material index */
	public int getMaterialIndex() { return mMaterialIndex; }
	

	/** Make it 'savable' */
	@Override
	public void write(
		JmeExporter 	pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule (this );	
		
		// NOTE a deficiency in the OutputCapsule API which should operate on a List,
		//		but instead requires an ArrayList
		aCapsule.writeSavableArrayList( (ArrayList<CSGVertex>)mVertices
										, "Vertices"
										, (ArrayList<CSGVertex>)sEmptyVertices );
		aCapsule.write( mPlane, "Plane", null );
	}
	@Override
	public void read(
		JmeImporter 	pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mVertices = (List<CSGVertex>)aCapsule.readSavableArrayList( "Vertices"
																	, (ArrayList<CSGVertex>)sEmptyVertices );
		mPlane = (CSGPlane)aCapsule.readSavable( "Plane", null );
	}
	
	/** For DEBUG */
	@Override
	public String toString(
	) {
		return( super.toString() + " - " + mVertices.size() + "(" + mPlane + ")" );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGPolygonRevision
													, sCSGPolygonDate
													, pBuffer ) );
	}

}
