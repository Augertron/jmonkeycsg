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
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

/** Constructive Solid Geometry (CSG)
	
	A CSG Vertex is a point in space, know by its position, its normal, and its texture coordinate

	NOTE
		that a vertex is expected to be immutable with no internally moving parts.  Once created, you
		cannot alter its attributes.  
		HOWEVER, the Vertex internal Vectors are used in Mesh construction and could therefore be 
		adjusted/altered. So even though the vertex itself is expected to be immutable, we must 
		anticipate its parts being consumed.  Therefore every clone must be a true copy.
  */
public class CSGVertex 
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGVertexRevision="$Rev$";
	public static final String sCSGVertexDate="$Date$";
	
	/** Factory level service routine that squeezes out an Vertex from a list that is not
	 	a 'significant' distance from other vertices in the list
	 */
	public static List<CSGVertex> compressVertices(
		List<CSGVertex>		pVertices
	,	int					pMinimumCount
	) {
		if ( pVertices.size() < pMinimumCount ) {
			return( Collections.EMPTY_LIST );
		}
		List<CSGVertex> compressedList = new ArrayList( pVertices.size() );
		float minDistance = Float.MAX_VALUE, maxDistance = 0.0f;
		
		// Check each in the list against all others
		int lastIndex = pVertices.size() -1;
		for( int i = 0; i < lastIndex; i += 1 ) {
			CSGVertex aVertex = pVertices.get( i );
			for( int k = i + 1, m = pVertices.size(); k < m; k += 1 ) {
				CSGVertex otherVertex = pVertices.get( k );
				float aDistance = aVertex.distanceSquared( otherVertex );
				if ( (aDistance >= EPSILON_SQUARED) && (aDistance <= EPSILON_MAX_SQUARED) ) {
					// NOTE that by Java spec definition, NaN always returns false in any comparison, so 
					// 		structure your bound checks accordingly
					if ( aDistance < minDistance ) minDistance = aDistance;
					if ( aDistance > maxDistance ) maxDistance = aDistance;
				} else {
					// The two vertices are too close (or super far apart) to be significant
					aVertex = null;
					break;
				}
			}
			if ( aVertex != null ) {
				compressedList.add( aVertex );
			}
		}
		// Look for a shape totally out of bounds
		float eccentricity = maxDistance / minDistance;
		if ( eccentricity > 100000000.0f ) {
			// Any shape corresponding to these vertices is totally stretched out of whack
			return( Collections.EMPTY_LIST );
		}
		// By definition, the last vertex is always included
		compressedList.add( pVertices.get( lastIndex ) );
		return( compressedList );
	}

	/** Where is this vertex */
	protected Vector3f	mPosition;
	/** What is its normal */
	protected Vector3f 	mNormal;
	/** What is the texture coordinate */
	protected Vector2f	mTextureCoordinate;
	
	/** Standard null constructor */
	public CSGVertex(
	) {
		this( Vector3f.ZERO, Vector3f.ZERO, Vector2f.ZERO );
	}
	
	/** Constructor based on the given components */
	public CSGVertex(
		Vector3f		pPosition
	,	Vector3f		pNormal
	,	Vector2f		pTextureCoordinate
	) {
		this( pPosition, pNormal, pTextureCoordinate, null );
	}
	public CSGVertex(
		Vector3f		pPosition
	,	Vector3f		pNormal
	,	Vector2f		pTextureCoordinate
	,	Transform		pTransform
	) {
		if ( pTransform != null ) {
			// Adjust the position
			mPosition = pTransform.transformVector( pPosition, pPosition );
			// Only rotation affects the surface normal
			mNormal = pTransform.getRotation().multLocal( pNormal );
			// The texture does not budge
			mTextureCoordinate = pTextureCoordinate;
		} else {
			// Use what was given
			// Think about using Assert for the following
			if ( true ) {
				if ( !(
				   (Math.abs( pPosition.x ) < EPSILON_MAX) 
				&& (Math.abs( pPosition.y ) < EPSILON_MAX) 
				&& (Math.abs( pPosition.z ) < EPSILON_MAX)
				) ) {
					ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Vertex: " + pPosition );
				}
				float normalLength = pNormal.length();
				if ( !(
				   (Math.abs( pNormal.x ) <= 1.0f) 
				&& (Math.abs( pNormal.y ) <= 1.0f) 
				&& (Math.abs( pNormal.z ) <= 1.0f) 
				&& (normalLength <= 1.0f + EPSILON) && (normalLength > 1.0f - EPSILON)
				) ) {
					ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Normal: " + pNormal + ", " + pNormal.length() );
				}
				if ( !(
				   (Math.abs( pTextureCoordinate.x ) <= EPSILON_MAX) 
				&& (Math.abs( pTextureCoordinate.y ) <= EPSILON_MAX)
				) ) {
					ConstructiveSolidGeometry.sLogger.log( Level.SEVERE, "Bogus Tex: " + pTextureCoordinate );
				}
			}
			mPosition = pPosition;
			mNormal = pNormal;
			mTextureCoordinate = pTextureCoordinate;
		}
	}
	
	/** Make a copy */
	public CSGVertex clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Make a flipped copy (invert the normal)
			return( new CSGVertex( mPosition.clone(), mNormal.negate(), mTextureCoordinate.clone() ));
		} else {
			// Standard copy
			return( new CSGVertex( mPosition.clone(), mNormal.clone(), mTextureCoordinate.clone() ));
		}
	}
	
	/** Accessor to the position */
	public Vector3f getPosition() { return mPosition; }
	
	/** Accessor to the normal */
	public Vector3f getNormal() { return mNormal; }
	
	/** Accessor to the texture coordinate */
	public Vector2f getTextureCoordinate() { return mTextureCoordinate; }
	
	/** Interpolate between this vertex and another */
	public CSGVertex interpolate(
		CSGVertex 	pOther
	, 	float		pPercentage
	,	Vector3f	pTemp3f
	,	Vector2f	pTemp2f
	) {
		// NOTE that by Java spec definition, NaN always returns false in any comparison, so 
		// 		structure your bound checks accordingly
		if ( (pPercentage >= EPSILON) && (pPercentage <= (1.0f - EPSILON)) ) {
			// Where is this new vertex?
			Vector3f newPosition 
				= this.mPosition.add(
					pTemp3f.set( pOther.getPosition() )
						.subtractLocal( this.mPosition ).multLocal( pPercentage ) );
			
			// Confirm a reasonable distance
			float aDistance = this.mPosition.distanceSquared( newPosition );
			float bDistance = pOther.mPosition.distanceSquared( newPosition );
			if ( (aDistance >= EPSILON_SQUARED) && (bDistance >= EPSILON_SQUARED) 
			&& (aDistance <= EPSILON_MAX_SQUARED) && (bDistance <= EPSILON_MAX_SQUARED) ) {
				// What is its normal?
				Vector3f newNormal 
					= this.mNormal.add( 
						pTemp3f.set( pOther.getNormal() )
							.subtractLocal( this.mNormal ).multLocal( pPercentage ) ).normalizeLocal();
				
				// What is its texture?
				Vector2f newTextureCoordinate 
					= this.mTextureCoordinate.add( 
						pTemp2f.set( pOther.getTextureCoordinate() )
							.subtractLocal( this.mTextureCoordinate ).multLocal( pPercentage ) );
				
				return new CSGVertex( newPosition, newNormal, newTextureCoordinate );
			}
		}
		// Not a valid interpolation
		return( null );
	}
	
	/** Calculate the distance of this vertex to another */
	public float distance(
		CSGVertex	pOtherVertex
	) {
		float aDistance = this.mPosition.distance( pOtherVertex.mPosition );
		return( aDistance );
	}
	public float distanceSquared(
		CSGVertex	pOtherVertex
	) {
		float aDistance = this.mPosition.distanceSquared( pOtherVertex.mPosition );
		return( aDistance );
	}

	/** Make a Vertex savable */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule(this);
		aCapsule.write( mPosition, "position", Vector3f.ZERO );
		aCapsule.write( mNormal, "normal", Vector3f.ZERO );
		aCapsule.write( mTextureCoordinate, "texCoord", Vector2f.ZERO );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mPosition = (Vector3f)aCapsule.readSavable( "position", Vector3f.ZERO );
		mNormal = (Vector3f)aCapsule.readSavable( "normal", Vector3f.ZERO );
		mTextureCoordinate = (Vector2f)aCapsule.readSavable( "texCoord", Vector2f.ZERO );
	}
	
	/** For Debug */
	@Override
	public String toString(
	) {
		return( super.toString() + " - " + mPosition );
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGVertexRevision
													, sCSGVertexDate
													, pBuffer ) );
	}

}
