/** Copyright (c) ????, Danilo Balby Silva Castanheira (danbalby@yahoo.com)
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
	
	Logic and Inspiration taken from:
		D. H. Laidlaw, W. B. Trumbore, and J. F. Hughes.  
 		"Constructive Solid Geometry for Polyhedral Objects" 
 		SIGGRAPH Proceedings, 1986, p.161. 
**/
package net.wcomohundro.jme3.csg.iob;

import java.util.ArrayList;
import java.util.List;

import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.blender.math.Vector3d;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.CSGVertex;
import net.wcomohundro.jme3.csg.CSGVertexDbl;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

/** A Vertex in the IOB processing realm is assigned to a face, and is dynamically marked as its
 	IN/OUT/BOUNDARY condition is determined.
 */
public class CSGVertexIOB 
	extends CSGVertexDbl 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGVertexIOBRevision="$Rev$";
	public static final String sCSGVertexIOBDate="$Date$";
	
	//private static final double TOL = 1e-5f;

	
	/** Status of an individual vertex */
	public static enum CSGVertexStatus {
		UNKNOWN			// Not Yet classified
	,	INSIDE			// Inside a solid
	,	OUTSIDE			// Outside a solid
	,	BOUNDARY		// On the boundary of the solid
	}

	/** Factory construction of appropriate vertex */
	public static CSGVertexIOB makeVertex(
		Vector3f			pPosition
	,	Vector3f			pNormal
	,	Vector2f			pTextureCoordinate
	,	Transform			pTransform
	,	CSGEnvironmentIOB	pEnvironment
	) {
		CSGVertexIOB aVertex;
		
		if ( pTransform != null ) {
			// Adjust the position
			pPosition = pTransform.transformVector( pPosition, pPosition );
			// Only rotation affects the surface normal
			pNormal = pTransform.getRotation().multLocal( pNormal );
			// The texture does not budge
			pTextureCoordinate = pTextureCoordinate;
		}
		// NOTE that we are starting from 'float' precision, but working in 'double' hereafter
		Vector3d aPosition = new Vector3d( pPosition.x, pPosition.y, pPosition.z );
		Vector3d aNormal = new Vector3d( pNormal.x, pNormal.y, pNormal.z );
		aVertex = new CSGVertexIOB( aPosition, aNormal, pTextureCoordinate, CSGVertexStatus.UNKNOWN, pEnvironment );

		return( aVertex );
	}
	
	/** Status - computed relative to some other object */
	protected CSGVertexStatus		mStatus;
	/** List of other vertices that have the same Position 
	 	(which means their 'status' should be the same as this one */
	protected List<CSGVertexIOB>	mSamePosition;
	
	
	/** Constructor based on the given components */
	public CSGVertexIOB(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	,	CSGVertexStatus	pStatus
	) {
		this( pPosition, pNormal, pTextureCoordinate, pStatus, CSGShapeIOB.sDefaultEnvironment );
	}
	public CSGVertexIOB(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	,	CSGVertexStatus	pStatus
	,	CSGEnvironment	pEnvironment
	) {
		super( pPosition, pNormal, pTextureCoordinate, pEnvironment );
		mStatus = pStatus;
		
		if ( pEnvironment.mRationalizeValues ) {
			// NOTE if you do NOT rationalize the position vector, you can get some
			//		strange artifacts (probably due to 'near zero' processing)
			CSGEnvironment.rationalizeVector( mPosition, pEnvironment.mEpsilonMagnitudeRange );
		}
	}
	/** Constructor based on a new position along a line between two given vertices */
	public CSGVertexIOB(
		CSGVertexIOB	pVertexA
	,	CSGVertexIOB	pVertexB
	,	Vector3d		pNewPosition
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		super( pNewPosition, new Vector3d(), new Vector2f(), false );
		mStatus = CSGVertexStatus.UNKNOWN;
		
		if ( pEnvironment.mRationalizeValues ) {
			// NOTE if you do NOT rationalize the position vector, you can get some
			//		strange artifacts (probably due to 'near zero' processing)
			CSGEnvironment.rationalizeVector( mPosition, pEnvironment.mEpsilonMagnitudeRange );
		}
		double d1 = pVertexA.getPosition().distance( pNewPosition );
		double d2 = pVertexB.getPosition().distance( pNewPosition );
		double percent = d1 / (d1 + d2);
		
		if ( (pEnvironment != null) && pEnvironment.mStructuralDebug ) {
			// Confirm what we were given
			double d3 = pVertexA.getPosition().distance( pVertexB.getPosition() );
			d3 -= (d1 + d2);
			if ( d3 > pEnvironment.mEpsilonNearZeroDbl ) {
				throw new IllegalArgumentException( "New Vertex not aligned with given points: " + d3 );
			}
		}
		// What is its normal?
		mNormal.set( pVertexA.getNormal() );
		
		Vector3d otherNormal = pTempVars.vectd1.set( pVertexB.getNormal() );
		mNormal.addLocal( 
			otherNormal.subtractLocal( pVertexA.getNormal() ).multLocal( percent ) ).normalizeLocal();
		
		// What is its texture?
		mTextureCoordinate.set( pVertexA.getTextureCoordinate() );
		
		Vector2f otherTexCoord = pTempVars.vect2d1.set( pVertexB.getTextureCoordinate() );
		mTextureCoordinate.addLocal( 
			otherTexCoord.subtractLocal( pVertexA.getTextureCoordinate() ).multLocal( (float)percent ) );
	}
	
	
	/** OVERRIDE: to always produce a copy, since the status is dynamically modified */
	@Override
	public CSGVertexDbl clone(
		boolean		pFlipIt
	) {
		CSGVertexIOB aCopy;
		if ( pFlipIt ) {
			// Make a flipped copy (invert the normal)
			aCopy = new CSGVertexIOB( mPosition.clone(), mNormal.negate(), mTextureCoordinate.clone(), null );
		} else {
			// Standard copy
			aCopy = new CSGVertexIOB( mPosition.clone(), mNormal.clone(), mTextureCoordinate.clone(), null );
		}
		// Retain the status
		aCopy.mStatus = this.mStatus;
		
		// By definition, the clone has the same position as this one
		this.samePosition( aCopy );
		return( aCopy );
	}
	@Override
	public CSGVertexDbl sibling(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	,	CSGEnvironment	pEnvironment
	) {
		if ( !pEnvironment.mDoublePrecision ) {
			// IOB processing is inherently 'double', but if we mark it 'float', then 
			// adjust the given values to match single precision only
			float xValue = (float)pPosition.x;
			float yValue = (float)pPosition.y;
			float zValue = (float)pPosition.z;
			pPosition.set( xValue, yValue, zValue );
			
			xValue = (float)pNormal.x;
			yValue = (float)pNormal.y;
			zValue = (float)pNormal.z;
			pNormal.set( xValue, yValue, zValue );
		}
		return( new CSGVertexIOB( pPosition, pNormal, pTextureCoordinate, null ) );
	}

	/** Accessor to the status */
	public CSGVertexStatus getStatus() { return mStatus; }
	public void setStatus(
		CSGVertexStatus		pStatus
	) {
		// Reset the status to whatever is given
		mStatus = pStatus;
	}
	
	/** If we detect another vertex with the same position as this one, then track it */
	public void samePosition(
		CSGVertexIOB	pOtherVertex
	) {
		// Work on the assumption that the 'other' vertex is new
		if ( pOtherVertex.mSamePosition != null ) {
			throw new IllegalArgumentException( "samePosition() invalid OTHER vertex" );
		}
		if ( this.mSamePosition == null ) {
			this.mSamePosition = new ArrayList<CSGVertexIOB>( 3 );
			this.mSamePosition.add( this );
		}
		// The list itself can be shared across all the Vertex instances
		pOtherVertex.mSamePosition = this.mSamePosition;
		pOtherVertex.mSamePosition.add( pOtherVertex );
	}

	/** Sets the vertex status, as needed */
	public void mark(
		CSGFace.CSGFaceStatus 	pStatus
	) {
		// Mark this vertex, if not already marked
		if ( this.mStatus == CSGVertexStatus.UNKNOWN ) {
			switch( pStatus ) {
			case INSIDE:
				this.mStatus = CSGVertexStatus.INSIDE;
				break;
			case OUTSIDE:
				this.mStatus = CSGVertexStatus.OUTSIDE;
				break;
			case SAME:
			case OPPOSITE:
				this.mStatus = CSGVertexStatus.BOUNDARY;
				break;
			}
			if ( this.mSamePosition != null ) {
				// Any other vertices that share this same position have the same status
				for( CSGVertexIOB otherVertex : mSamePosition ) {
					otherVertex.mark( pStatus );
				}
			}
		}
	}

	/** FOR DEBUG: Makes a string definition for the Vertex object */
	@Override
	public String toString()
	{
		return "Vtx(" + mPosition.x + ", " + mPosition.y + ", " + mPosition.z + ")";
	}
	
	/** A vertex is equal if the position, normal, and texcoord match within a given
	 	tolerance
	 */
	@Override
	public boolean equals(
		Object		pOther
	) {
		if ( this == pOther ) {
			return( true );
			
		} else if ( pOther instanceof CSGVertexIOB ) {
			CSGVertexIOB aVertex = (CSGVertexIOB)pOther;
			double tolerance = CSGShapeIOB.sDefaultEnvironment.mEpsilonBetweenPointsDbl;
			
			if ( CSGEnvironment.equalVector3d( this.getPosition()
														, aVertex.getPosition()
														, tolerance ) ) {
				if ( CSGEnvironment.equalVector3d( this.getNormal()
															, aVertex.getNormal()
															, tolerance ) ) {
					if ( CSGEnvironment.equalVector2f( this.getTextureCoordinate()
																, aVertex.getTextureCoordinate()
																, tolerance ) ) {
						return( true );
					}
				}
			}
			return( false );
			
		} else {
			return( super.equals( pOther ) );
		}
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGVertexIOBRevision
													, sCSGVertexIOBDate
													, pBuffer ) );
	}


}
