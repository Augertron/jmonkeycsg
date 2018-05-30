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

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI.CSGErrorCode;
import net.wcomohundro.jme3.csg.math.CSGVertex;
import net.wcomohundro.jme3.csg.math.CSGVertexDbl;
import net.wcomohundro.jme3.math.Vector3d;


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

	
	/** Status of an individual vertex  - used to optimize face classification.
	 		The BOUNDARY state can be set early on during CSGSegment processing if a vertex
	 		is known to be an end point of the segment.  Being on a boundary means we 
	 		cannot know anything more about the face containing this vertex.  A boundary
	 		could be part of either an inside or outside face.
	 		
	 		The INSIDE and OUTSIDE states can be set when a given face is determined to
	 		be either in or out.  Since classification occurs after all face overlap has 
	 		been eliminated, it means that all vertices in the same face share the 
	 		same status (but a BOUNDARY is never overridden)  Any vertex that shares
	 		the same position as one marked INSIDE/OUTSIDE is, by definition, also
	 		INSIDE/OUTSIDE.  This is where the optimization occurs.  Determining (via
	 		standard raytrace processing) that a face is in/out can then affect the
	 		status of any other face that shares the same position.
	 		
	 		INSIDE/OUTSIDE must be reset and redetermined on a face-by-face basis.
	 		But once a BOUNDARY, always a BOUNDARY.
	 */
	public static enum CSGVertexStatus {
		UNKNOWN			// Not Yet classified
	,	INSIDE			// Inside a solid
	,	OUTSIDE			// Outside a solid
	,	BOUNDARY		// On the boundary of the solid
	}

	/** Factory construction of appropriate vertex */
	public static CSGVertexIOB makeVertex(
		CSGFace				pPriorFace
	,	Vector3f			pPosition
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
		aVertex = new CSGVertexIOB( aPosition, aNormal, pTextureCoordinate, pEnvironment );

		// Quick check against a 'prior' face to see if any vertices overlap
		if ( pPriorFace != null ) {
			pPriorFace.matchVertex( aVertex, pEnvironment );
		}
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
	) {
		this( pPosition, pNormal, pTextureCoordinate, CSGEnvironment.resolveEnvironment() );
	}
	public CSGVertexIOB(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	,	CSGEnvironment	pEnvironment
	) {
		super( pPosition, pNormal, pTextureCoordinate, pEnvironment );
		mStatus = CSGVertexStatus.UNKNOWN;
		
		if ( pEnvironment.mRationalizeValues ) {
			CSGEnvironment.rationalizeVector( mPosition, pEnvironment.mEpsilonMagnitudeRange );
		}
	}
	/** Constructor based on a new position along a line between two given vertices */
	public CSGVertexIOB(
		CSGVertexIOB	pVertexA
	,	CSGVertexIOB	pVertexB
	,	Vector3d		pNewPosition
	,	CSGVertexStatus	pNewStatus
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) throws CSGConstructionException {
		// Suppress the vertex check
		super( pNewPosition, new Vector3d(), new Vector2f(), null );
		
		// If the new position knows that it is on a boundary, retain that knowledge
		mStatus = (pNewStatus == CSGVertexStatus.BOUNDARY) 
					?	CSGVertexStatus.BOUNDARY
					:	CSGVertexStatus.UNKNOWN;
		
		if ( pEnvironment.mRationalizeValues ) {
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
				throw new CSGConstructionException( CSGErrorCode.INVALID_VERTEX
												,	"CSGVertexIOB not aligned with given points: " + d3 );
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
		return( clone( pFlipIt, CSGEnvironment.resolveEnvironment() ) );
	}
	public CSGVertexIOB clone(
		boolean			pFlipIt
	,	CSGEnvironment	pEnvironment
	) {
		CSGVertexIOB aCopy;
		if ( pFlipIt ) {
			// Make a flipped copy (invert the normal)
			aCopy = new CSGVertexIOB( mPosition.clone(), mNormal.negate(), mTextureCoordinate.clone(), pEnvironment );
		} else {
			// Standard copy
			aCopy = new CSGVertexIOB( mPosition.clone(), mNormal.clone(), mTextureCoordinate.clone(), pEnvironment );
			// By definition, the clone has the same position as this one
			this.samePosition( aCopy );
		}
		// NOTE that the status cannot be cloned, since it is likely we are cloning a vertex
		//		to reside in a different face, which may well have a different status. BUT
		//		a vertex on a BOUNDARY remains on a boundary, not matter what, even if inverted.
		if ( this.mStatus == CSGVertexStatus.BOUNDARY ) {
			aCopy.mStatus = CSGVertexStatus.BOUNDARY;
		}
		return( aCopy );
	}
	@Override
	public CSGVertexDbl sibling(
		Vector3d		pPosition
	,	Vector3d		pNormal
	,	Vector2f		pTextureCoordinate
	,	CSGEnvironment	pEnvironment
	) {
		return( new CSGVertexIOB( pPosition, pNormal, pTextureCoordinate, pEnvironment ) );
	}
	
	/** Apply a transform to this vertex to produce another */
	public CSGVertexIOB applyTransform(
		Transform		pTransform
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		Vector3d dPosition, dNormal;
		
		// Adjust the position
		Vector3f aPosition = pTempVars.vect1.set( (float)mPosition.x, (float)mPosition.y, (float)mPosition.z );
		aPosition = pTransform.transformVector( aPosition, aPosition );
		dPosition = new Vector3d( aPosition.x, aPosition.y, aPosition.z );

		// Only rotation affects the surface normal
		Quaternion aRotation = pTransform.getRotation();
		if ( !Quaternion.IDENTITY.equals( aRotation ) ) {
			// Apply the rotation
			Vector3f aNormal = pTempVars.vect2.set( (float)mNormal.x, (float)mNormal.y, (float)mNormal.z );
			aNormal = aRotation.multLocal( aNormal );
			dNormal = new Vector3d( aNormal.x, aNormal.y, aNormal.z );
		} else {
			// Use the unrotated normal
			dNormal = mNormal;
		}
		CSGVertexIOB aVertex = new CSGVertexIOB( dPosition, dNormal, mTextureCoordinate, pEnvironment );
		return( aVertex );
	}

	/** Accessor to the status */
	public CSGVertexStatus getStatus() { return mStatus; }
	public void setStatus(
		CSGVertexStatus		pStatus
	,	boolean				pAffectSame
	) {
		// Reset the status to whatever is given
		mStatus = pStatus;
		
		if ( pAffectSame && (mSamePosition != null) ) {
			for( CSGVertexIOB otherVertex : mSamePosition ) {
				if ( otherVertex != this ) {
					otherVertex.setStatus( pStatus, false );
				}	
			}
		}
	}
	
	/** If we detect another vertex with the same position as this one, then link them
	 	together to optimize the IOB processing.  Each vertex so linked will share the 
	 	same vertex status, but retains its own normal/texture.
	 */
	public boolean hasSame() { return( this.mSamePosition != null ); }
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
		mSamePosition.add( pOtherVertex );
		
		// The list itself can be shared across all the Vertex instances
		pOtherVertex.mSamePosition = this.mSamePosition;
	}
	
	/** Look if this vertex matches a given position */
	public boolean matchPosition(
		Vector3d		pPosition
	,	CSGEnvironment	pEnvironment
	) {
		return( CSGEnvironment.equalVector3d( 	this.getPosition()
											,	pPosition
											,	pEnvironment.mEpsilonBetweenPointsDbl ) );
	}

	/** Resets the vertex status */
	public void mark(
		CSGFace 	pForFace
	,	CSGEnvironmentIOB	pEnvironment
	) {
		mark( pForFace.getStatus(), this.mSamePosition, pEnvironment );
	}	
	protected void mark(
		CSGFace.CSGFaceStatus 	pStatus
	,	List<CSGVertexIOB>		pSamePosition
	,	CSGEnvironmentIOB		pEnvironment
	) {
		// A BOUNDARY vacillates between inside/outside and is not reset
		// due to what any face says. The vertex itself just stays on the boundary.
		// LIKEWISE, if on the boundary, we cannot affect others at the same position
		if ( this.mStatus != CSGVertexStatus.BOUNDARY ) {
			// Mark this vertex according to the face just given
			switch( pStatus ) {
			case INSIDE:
				this.mStatus = CSGVertexStatus.INSIDE;
				break;
			case OUTSIDE:
				this.mStatus = CSGVertexStatus.OUTSIDE;
				break;
			case SAME:
			case OPPOSITE:
				// Once we set it on the boundary, it remains on the boundary and
				// is not part of any quick face status check
				this.mStatus = CSGVertexStatus.BOUNDARY;
				break;
			case UNKNOWN:
				// Reset the status 
				this.mStatus = CSGVertexStatus.UNKNOWN;
				break;
			}
			if ( pSamePosition != null ) {
				// Here is what I think is happening.
				//	At this point in the process, all the faces have been split so that no two
				//	faces from the different solids overlap.  That means every face is either
				//	entirely inside, outside, or coincident with the other solid.  If the 
				//  status of the entire face is known,  then by definition, each of its vertices 
				//  is known as well.
				//	So any OTHER vertex at the exact same position must likewise have the 
				//  same status.  This provides a bit of a short cut for the same positioned
				//	vertex in two different faces. 
				for( CSGVertexIOB otherVertex : pSamePosition ) {
					if ( otherVertex != this ) {
						otherVertex.mark( pStatus, null, pEnvironment );
					}
				}
			}
		}
	}

	/** FOR DEBUG: Makes a string definition for the Vertex object */
	@Override
	public String toString()
	{
		return "Vtx(" + mPosition.x + ", " + mPosition.y + ", " + mPosition.z + ") : " + mStatus ;
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
