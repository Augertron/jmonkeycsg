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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.blender.math.Vector3d;


/** Constructive Solid Geometry (CSG)
	
	A CSG Vertex is a point in space, know by its position, its normal, and its texture coordinate
	This is the DOUBLE variant based on Vector3d

	NOTE
		that a vertex is expected to be immutable with no internally moving parts.  Once created, you
		cannot alter its attributes.  
		THEREFORE, be careful with Vertex internal Vectors used in Mesh construction which could be 
		adjusted/altered. .
  */
public class CSGVertexDbl 
	extends CSGVertex<Vector3d>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGVertexDblRevision="$Rev$";
	public static final String sCSGVertexDblDate="$Date$";

	
	/** Factory level service routine that squeezes out any Vertex from a list that is not
	 	a 'significant' distance from other vertices in the list. The vertices are assumed
	 	to be in order, so that 1::2, 2::3, ... N-1::N, N::1
	 	
	 	You can also force all the vertices to be explicitly 'projected' onto a given optional plane
	 	
	 	@return - the 'eccentricity' of the related vertices, which represents the ratio 
	 			  of the longest distance between points and the shortest.  So a very large
	 			  eccentricity represents a rather wrapped set of vertices.
	 */
	public static double compressVertices(
		List<CSGVertex>		pVertices
	,	CSGPlaneDbl 		pPlane
	,	CSGEnvironment		pEnvironment
	) {
		CSGVertex rejectedVertex = null;
		double minDistance = Double.MAX_VALUE, maxDistance = 0.0;
		
		// Check each in the list against its neighbor
		int lastIndex = pVertices.size() -1;
		if ( lastIndex <= 0 ) {
			// Nothing interesting in the list
			return( 0.0f );
		}
		for( int i = 0, j = 1; i <= lastIndex; i += 1 ) {
			if ( j > lastIndex ) j = 0;		// Loop around to compare the last to the first
			
			CSGVertexDbl aVertex = (CSGVertexDbl)pVertices.get( i );
			if ( aVertex != null ) {
				CSGVertexDbl otherVertex = (CSGVertexDbl)pVertices.get( j );
				double aDistance = aVertex.distance( otherVertex );
				if ( (aDistance >= pEnvironment.mEpsilonBetweenPoints) 
				&& (aDistance <= pEnvironment.mEpsilonMaxBetweenPoints) ) {
					// NOTE that by Java spec definition, NaN always returns false in any comparison, so 
					// 		structure your bound checks accordingly
					if ( aDistance < minDistance ) minDistance = aDistance;
					if ( aDistance > maxDistance ) maxDistance = aDistance;
					
					if ( pPlane != null ) {
						// Ensure the given point is actually on the plane
						Vector3d aPoint = aVertex.getPosition();
						aDistance = pPlane.pointDistance( aPoint );
						if ( (aDistance < -pEnvironment.mEpsilonNearZero) 
						|| (aDistance > pEnvironment.mEpsilonNearZero) ) {
							if ( pEnvironment.mPolygonPlaneMode == CSGPolygonPlaneMode.FORCE_TO_PLANE ) {
								// Resolve back to the corresponding point on the given plane
								Vector3d newPoint = pPlane.pointProjection( aPoint, null );
								if ( DEBUG ) {
									// Debug check to ensure the new point is really on the plane
									aDistance = pPlane.pointDistance( newPoint );
								}
								// Assume the normal from the original is close enough...
								aVertex = new CSGVertexDbl( newPoint
														, aVertex.getNormal()
														, aVertex.getTextureCoordinate()
														, null );
								pVertices.set( i, aVertex );
							} else {
								// This point not really on the plane.  Keep it, but track it for debug
								rejectedVertex = aVertex;
							}
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
		double eccentricity = maxDistance / minDistance;
		return( eccentricity );
	}

	/** Standard null constructor */
	public CSGVertexDbl(
	) {
		this( Vector3d.ZERO, Vector3d.ZERO, Vector2f.ZERO, null );
	}
	
	/** Constructor based on the given components */
	public CSGVertexDbl(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	) {
		this( pPosition, pNormal, pTextureCoordinate, CSGEnvironment.sStandardEnvironment );
	}
	public CSGVertexDbl(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	,	CSGEnvironment	pEnvironment
	) {
		// Use what was given
		if ( (pEnvironment != null) && pEnvironment.mStructuralDebug ) {
			// NOTE use of negative boolean logic to accommodate NaN and Infinity always producing
			//		false comparisons
			if ( !(
			   (Math.abs( pPosition.x ) < pEnvironment.mEpsilonMaxBetweenPoints ) 
			&& (Math.abs( pPosition.y ) < pEnvironment.mEpsilonMaxBetweenPoints ) 
			&& (Math.abs( pPosition.z ) < pEnvironment.mEpsilonMaxBetweenPoints )
			) ) {
				ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Vertex: " + pPosition );
			}
			// Upon further research, I am not seeing a requirement for the normal to be a unit vector
			double normalLength = pNormal.length();
			if ( !(normalLength != 0.0f) ) {
				ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Normal: " + pNormal + ", " + pNormal.length() );
			}
			if ( !(
			   (Math.abs( pTextureCoordinate.x ) <= pEnvironment.mEpsilonMaxBetweenPoints ) 
			&& (Math.abs( pTextureCoordinate.y ) <= pEnvironment.mEpsilonMaxBetweenPoints )
			) ) {
				ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Tex: " + pTextureCoordinate );
			}
		}
		mPosition = pPosition;
		mNormal = pNormal;
		mTextureCoordinate = pTextureCoordinate;
	}
	
	/** Make a copy */
	@Override
	public CSGVertexDbl clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Make a flipped copy (invert the normal)
			return( new CSGVertexDbl( mPosition.clone(), mNormal.negate(), mTextureCoordinate.clone(), null ));
		} else {
			// Standard copy, which is currently this same immutable instance
			return( this ); // new CSGVertex( mPosition.clone(), mNormal.clone(), mTextureCoordinate.clone() ));
		}
	}
	public CSGVertexDbl clone(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	) {
		return( new CSGVertexDbl( pPosition, pNormal, pTextureCoordinate, null ) );
	}
		
	
	/** Access as Floats */
	@Override
	public Vector3f getPositionFlt(
	) { 
		return new Vector3f( (float)mPosition.x, (float)mPosition.y, (float)mPosition.z ); 
	}
	@Override
	public Vector3f getNormalFlt(
	) { 
		return new Vector3f( (float)mNormal.x, (float)mNormal.y, (float)mNormal.z ); 
	}

	/** Interpolate between this vertex and another */
	public CSGVertexDbl interpolate(
		CSGVertexDbl 	pOther
	, 	double			pPercentage
	,	Vector3d		pNewPosition
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGVertexDbl aVertex = null;
		
		// NOTE that by Java spec definition, NaN always returns false in any comparison, so 
		// 		structure your bound checks accordingly
		if ( (pPercentage < 0.0) || (pPercentage > 1.0) ) {
			// Not sure what to make of this....
		
		// Once upon a time, I had tolerance checks here to check for near zero and near
		// one, then using the appropriate Vertex.  But that resulted in duplicate points
		// which means we could not compute plane.  So punt that and always use the percentage.
		} else if ( Double.isFinite( pPercentage ) ){
			// What is its normal?
			Vector3d newNormal 
				= this.mNormal.add( 
					pTempVars.vectd1.set( pOther.getNormal() )
						.subtractLocal( this.mNormal ).multLocal( pPercentage ) ).normalizeLocal();
			
			// What is its texture?
			Vector2f newTextureCoordinate 
				= this.mTextureCoordinate.add( 
					pTempVars.vect2d.set( pOther.getTextureCoordinate() )
						.subtractLocal( this.mTextureCoordinate ).multLocal( (float)pPercentage ) );
			
			aVertex = this.clone( pNewPosition, newNormal, newTextureCoordinate );
		}
		if ( aVertex == null ) {
			// Not a percentage we can deal with
			ConstructiveSolidGeometry.sLogger.log( Level.SEVERE
			, pEnvironment.mShapeName + "unexpected percentage: " + pPercentage );
		}
		return( aVertex );
	}
	
	/** Calculate the distance of this vertex to another */
	public double distance(
		CSGVertexDbl	pOtherVertex
	) {
		double aDistance = this.mPosition.distance( pOtherVertex.mPosition );
		return( aDistance );
	}
	public double distanceSquared(
		CSGVertexDbl	pOtherVertex
	) {
		double aDistance = this.mPosition.distanceSquared( pOtherVertex.mPosition );
		return( aDistance );
	}

	/** Make a Vertex savable */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule(this);
		aCapsule.write( mPosition, "position", Vector3d.ZERO );
		aCapsule.write( mNormal, "normal", Vector3d.ZERO );
		super.write( pExporter );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mPosition = (Vector3d)aCapsule.readSavable( "position", Vector3d.ZERO );
		mNormal = (Vector3d)aCapsule.readSavable( "normal", Vector3d.ZERO );
		super.read( pImporter );
	}
	
	/** Enhanced equality check to see if within a tolerance */
	@Override
	public boolean equals(
		CSGVertex		pOther
	,	CSGEnvironment	pEnvironment
	) {
		if ( pOther instanceof CSGVertexDbl ) {
			CSGVertexDbl other = (CSGVertexDbl)pOther;
			if ( ConstructiveSolidGeometry.equalVector3d( this.getPosition()
															, other.getPosition()
															, pEnvironment.mEpsilonBetweenPoints ) ) {
				if ( ConstructiveSolidGeometry.equalVector3d( this.getNormal()
															, other.getNormal()
															, pEnvironment.mEpsilonBetweenPoints ) ) {
					if ( ConstructiveSolidGeometry.equalVector2f( this.getTextureCoordinate()
															, pOther.getTextureCoordinate()
															, pEnvironment.mEpsilonBetweenPoints ) ) {
						return( true );
					}
				}
			}
		}
		return( false );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGVertexDblRevision
													, sCSGVertexDblDate
													, pBuffer ) );
	}

}
