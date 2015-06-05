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
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;

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
	/** Quick access to a NULL in a list of Vertices */
	protected static final List<CSGVertex> sNullVertexList = Collections.singletonList( null );

	
	/** Factory level service routine that squeezes out any Vertex from a list that is not
	 	a 'significant' distance from other vertices in the list. The vertices are assumed
	 	to be in order, so that 1::2, 2::3, ... N-1::N, N::1
	 	
	 	You can also force all the vertices to be explicitly 'projected' onto a given optional plane
	 	
	 	@return - the 'eccentricity' of the related vertices, which represents the ratio 
	 			  of the longest distance between points and the shortest.  So a very large
	 			  eccentricity represents a rather wrapped set of vertices.
	 */
	public static float compressVertices(
		List<CSGVertex>		pVertices
	,	CSGPlane			pPlane
	,	float				pMinimalBetween
	,	float				pMaximalBetween
	) {
		CSGVertex rejectedVertex = null;
		float minDistance = Float.MAX_VALUE, maxDistance = 0.0f;
		
		// Check each in the list against its neighbor
		int lastIndex = pVertices.size() -1;
		if ( lastIndex <= 0 ) {
			// Nothing interesting in the list
			return( 0.0f );
		}
		for( int i = 0, j = 1; i <= lastIndex; i += 1 ) {
			if ( j > lastIndex ) j = 0;
			
			CSGVertex aVertex = pVertices.get( i );
			if ( aVertex != null ) {
				CSGVertex otherVertex = pVertices.get( j );
				float aDistance = aVertex.distance( otherVertex );
				if ( (aDistance >= pMinimalBetween) && (aDistance <= pMaximalBetween) ) {
					// NOTE that by Java spec definition, NaN always returns false in any comparison, so 
					// 		structure your bound checks accordingly
					if ( aDistance < minDistance ) minDistance = aDistance;
					if ( aDistance > maxDistance ) maxDistance = aDistance;
					
					if ( pPlane != null ) {
						// Ensure the given point is actually on the plane
						Vector3f aPoint = aVertex.getPosition();
						aDistance = pPlane.pointDistance( aPoint );
						if ( (aDistance < -EPSILON_NEAR_ZERO) || (aDistance > EPSILON_NEAR_ZERO) ) {
							// Resolve back to the corresponding point on the given plane
							Vector3f newPoint = pPlane.pointProjection( aPoint, null );
							aVertex = new CSGVertex( newPoint, aVertex.getNormal(), aVertex.getTextureCoordinate() );
							pVertices.set( i, aVertex );
						}
					}
				} else {
					// The two vertices are too close (or super far apart) to be significant
					rejectedVertex = aVertex;
					pVertices.set( j, null );
					
					// NOTE that we work with the same i again with the next j
					i -= 1;
				}
				j += 1;
			}
		}
		if ( rejectedVertex != null ) {
			// We have punted something, so compress out all nulls
			pVertices.removeAll( sNullVertexList );
		}
		// Look for a shape totally out of bounds
		float eccentricity = maxDistance / minDistance;
		return( eccentricity );
	}
	

	/** Factory level service routine to create an appropriate polygon, compressing vertices
	 	and deciding if the polygon is 'worth' constructing.
	 	
	 	NOTE
	 		that the given list of vertices is 'compressed' as a side effect
	 	
	 	@return - a CSGPolygon or null if no polygon was constructed
	 */
	public static CSGPolygon createPolygon(
		List<CSGVertex>		pVertices
	,	int					pMaterialIndex
	) {
		CSGPlane aPlane = CSGPlane.fromVertices( pVertices );
		return( createPolygon( pVertices, aPlane, pMaterialIndex ) );
	}
	public static CSGPolygon createPolygon(
		List<CSGVertex>		pVertices
	,	CSGPlane			pPlane
	,	int					pMaterialIndex
	) {
		if ( (pPlane != null) && pPlane.isValid() ) {
			// NOTE that compressVertices operates directly on the given list
			//		and the current thinking is to preserve the original vertices, and NOT force them
			//		onto the given plane.
			float eccentricity 
				= compressVertices( pVertices, null /*pPlane*/, EPSILON_BETWEEN_POINTS, EPSILON_BETWEEN_POINTS_MAX );
			if ( pVertices.size() >= 3 ) {
				// We have enough vertices for a shape
				// NOTE when debugging, it can be useful to look for odd eccentricty values here....
				CSGPolygon aPolygon = new CSGPolygon( pVertices, pMaterialIndex );
				return( aPolygon );
			}
		} else {
			throw new IllegalArgumentException( "Incomplete Polygon" );
		}
		// We did NOT build anything of value
		return( null );
	}
	
	/** Factory level service routine that combines polygons that happen to lie in the same plane
	 	@todo - look into doing something real with this if possible
	 */
	public static List<CSGPolygon> compressPolygons(
		List<CSGPolygon>	pPolygons
	) {
		if ( true ) { // pPolygons.size() < 2 ) {
			// Nothing to compress
			return( pPolygons );
		}
		List<CSGPolygon> compressedList = null;
		
		// Check each in the list against all others
		int lastIndex = pPolygons.size() -1;
		for( int i = 0; i < lastIndex; i += 1 ) {
			CSGPolygon aPolygon = pPolygons.get( i );
			CSGPlane aPlane = aPolygon.getPlane();
			
			for( int k = i + 1, m = pPolygons.size(); k < m; k += 1 ) {
				CSGPolygon otherPolygon = pPolygons.get( k );
				CSGPlane otherPlane = otherPolygon.getPlane();
				
				if ( aPlane.equals( otherPlane ) ) {
					// Two polygons in the same plane, so look for similar vertices
					compressedList = new ArrayList( pPolygons.size() );
					compressedList.addAll( pPolygons );
				}
			}
		}
		return( (compressedList == null) ? pPolygons : compressedList );
	}
	

	/** The vertices that define this shape */
	protected List<CSGVertex>	mVertices;
	/** The plane that contains this shape */
	protected CSGPlane 			mPlane;
	/** The custom material that applies to this shape */
	protected int				mMaterialIndex;

	/** Standard null constructor */
	public CSGPolygon(
	) {
		mVertices = sEmptyVertices;
		mPlane = null;
		mMaterialIndex = 0;
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
		if ( (pPlane != null) && pPlane.isValid() ) {
			mPlane = pPlane;
		}
		mMaterialIndex = pMaterialIndex;
	}
	
	/** Ensure we are working with something valid */
	public boolean isValid() { return( mPlane != null ); }
	
	/** Make a copy */
	public CSGPolygon clone(
		boolean 	pFlipIt
	) {
		if ( pFlipIt ) {
			// Flip all the components
			List<CSGVertex> newVertices = new ArrayList<CSGVertex>( mVertices.size() );
			for( CSGVertex aVertex : mVertices ) {
				CSGVertex bVertex = aVertex.clone( pFlipIt );
				newVertices.add( bVertex );
			}
			// Flip the order of the vertices as well
			// NOTE that we are assuming that .reverse() is more efficient than repeatedly inserting
			//		new items at the start of the list (which forces things to copy and slide)
			Collections.reverse( newVertices );
			return( new CSGPolygon( newVertices, mPlane.clone( pFlipIt ), mMaterialIndex ) );
		} else {
			// The polygon is immutable, so its clone is just itself
			return( this );
		}
	}
	
	/** Accessor to the vertices */
	public List<CSGVertex> getVertices() { return( this.isValid() ? mVertices : Collections.EMPTY_LIST ); }
	
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
										, "vertices"
										, (ArrayList<CSGVertex>)sEmptyVertices );
		aCapsule.write( mPlane, "plane", null );
	}
	@Override
	public void read(
		JmeImporter 	pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mVertices = (List<CSGVertex>)aCapsule.readSavableArrayList( "vertices"
																	, (ArrayList<CSGVertex>)sEmptyVertices );
		mPlane = (CSGPlane)aCapsule.readSavable( "plane", null );
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
