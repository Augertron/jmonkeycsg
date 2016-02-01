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

/** Constructive Solid Geometry (CSG)
	
	A CSG Vertex is a point in space, know by its position, its normal, and its texture coordinate
	This is the FLOAT based variant that is based on Vector3f

	NOTE
		that a vertex is expected to be immutable with no internally moving parts.  Once created, you
		cannot alter its attributes.  
		THEREFORE, be careful with Vertex internal Vectors used in Mesh construction which could be 
		adjusted/altered. .
  */
public class CSGVertexFlt 
	extends CSGVertex<Vector3f>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGVertexFltRevision="$Rev$";
	public static final String sCSGVertexFltDate="$Date$";
		
	
	/** Standard null constructor */
	public CSGVertexFlt(
	) {
		this( Vector3f.ZERO, Vector3f.ZERO, Vector2f.ZERO, null );
	}
	
	/** Constructor based on the given components */
	public CSGVertexFlt(
		Vector3f		pPosition
	,	Vector3f		pNormal
	,	Vector2f		pTextureCoordinate
	) {
		this( pPosition, pNormal, pTextureCoordinate, CSGEnvironment.resolveEnvironment() );
	}
	public CSGVertexFlt(
		Vector3f		pPosition
	,	Vector3f		pNormal
	,	Vector2f		pTextureCoordinate
	,	CSGEnvironment	pEnvironment
	) {
		// Use what was given
		mPosition = pPosition;
		mNormal = pNormal;
		mTextureCoordinate = pTextureCoordinate;
		
		if ( (pEnvironment != null) && pEnvironment.mStructuralDebug ) {
			CSGEnvironment.confirmVertex( this, pEnvironment );
		}
	}
	
	/** Make a copy */
	public CSGVertexFlt clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Make a flipped copy (invert the normal)
			return( new CSGVertexFlt( mPosition.clone(), mNormal.negate(), mTextureCoordinate.clone(), null ));
		} else {
			// Standard copy, which is currently this same immutable instance
			return( this ); // new CSGVertex( mPosition.clone(), mNormal.clone(), mTextureCoordinate.clone() ));
		}
	}
	
	/** Access as Floats */
	@Override
	public Vector3f getPositionFlt() { return mPosition; }
	@Override
	public Vector3f getNormalFlt() { return mNormal; }

	/** Interpolate between this vertex and another */
	public CSGVertexFlt interpolate(
		CSGVertexFlt 	pOther
	, 	float			pPercentage
	,	Vector3f		pNewPosition
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		CSGVertexFlt aVertex = null;
		
		// NOTE that by Java spec definition, NaN always returns false in any comparison, so 
		// 		structure your bound checks accordingly
		if ( (pPercentage < 0.0f) || (pPercentage > 1.0f) ) {
			// Not sure what to make of this....
		
		// Once upon a time, I had tolerance checks here to check for near zero and near
		// one, then using the appropriate Vertex.  But that resulted in duplicate points
		// which means we could not compute plane.  So punt that and always use the percentage.
		} else if ( CSGEnvironment.isFinite( pPercentage ) ){
			// What is its normal?
			Vector3f newNormal 
				= this.mNormal.add( 
					pTempVars.vect1.set( pOther.getNormal() )
						.subtractLocal( this.mNormal ).multLocal( pPercentage ) ).normalizeLocal();
			
			// What is its texture?
			Vector2f newTextureCoordinate 
				= this.mTextureCoordinate.add( 
					pTempVars.vect2d1.set( pOther.getTextureCoordinate() )
						.subtractLocal( this.mTextureCoordinate ).multLocal( pPercentage ) );
			
			aVertex = new CSGVertexFlt( pNewPosition, newNormal, newTextureCoordinate );
		}
		if ( aVertex == null ) {
			// Not a percentage we can deal with
			pEnvironment.log( Level.SEVERE, "Unexpected interpolate %: " + pPercentage );
		}
		return( aVertex );
	}
	
	/** Calculate the distance of this vertex to another */
	public float distance(
		CSGVertexFlt	pOtherVertex
	) {
		float aDistance = this.mPosition.distance( pOtherVertex.mPosition );
		return( aDistance );
	}
	public float distanceSquared(
		CSGVertexFlt	pOtherVertex
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
		super.write( pExporter );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		mPosition = (Vector3f)aCapsule.readSavable( "position", Vector3f.ZERO );
		mNormal = (Vector3f)aCapsule.readSavable( "normal", Vector3f.ZERO );
		super.read( pImporter );
	}
	
	/** Enhanced equality check to see if within a tolerance */
	@Override
	public boolean equals(
		CSGVertex		pOther
	,	CSGEnvironment	pEnvironment
	) {
		if ( pOther instanceof CSGVertexFlt ) {
			CSGVertexFlt other = (CSGVertexFlt)pOther;
			if ( CSGEnvironment.equalVector3f( this.getPosition()
															, other.getPosition()
															, pEnvironment.mEpsilonBetweenPointsFlt ) ) {
				if ( CSGEnvironment.equalVector3f( this.getNormal()
															, other.getNormal()
															, pEnvironment.mEpsilonBetweenPointsFlt ) ) {
					if ( CSGEnvironment.equalVector2f( this.getTextureCoordinate()
															, pOther.getTextureCoordinate()
															, pEnvironment.mEpsilonBetweenPointsFlt ) ) {
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
		return( CSGVersion.getVersion( this.getClass()
													, sCSGVertexFltRevision
													, sCSGVertexFltDate
													, pBuffer ) );
	}

}
