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
import java.util.List;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGPolygon.CSGPolygonPlaneMode;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector2f;
import com.jme3.scene.plugins.blender.math.Vector3d;

/**  Constructive Solid Geometry (CSG)
 
  	A CSGPlaneDbl is the DOUBLE variant of CSGPlane
 */
public class CSGPlaneDbl 
	extends CSGPlane<Vector3d,CSGVertexDbl>
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPlaneDblRevision="$Rev$";
	public static final String sCSGPlaneDblDate="$Date$";

	/** Factory method to produce a plane from a minimal set of points 
	 * 
	 	TempVars Usage:
	 		vect4
	 		vect5
	 */
	public static CSGPlaneDbl fromPoints(
		Vector3d		pA
	, 	Vector3d 		pB
	, 	Vector3d 		pC
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Compute the normal vector
		Vector3d temp1 = pB.subtract( pA, pTempVars.vectd4 );
		Vector3d temp2 = pC.subtract( pA, pTempVars.vectd5 );
		Vector3d aNormal = temp1.cross( temp2 ).normalizeLocal();
		//Vector3d aNormal = pB.subtract( pA ).cross( pC.subtract( pA ) ).normalizeLocal();
		if ( pEnvironment.mRationalizeValues ) {
			pEnvironment.rationalizeVector( aNormal, pEnvironment.mEpsilonMagnitudeRange );
		}
		// I am defintely NOT understanding something here...
		// I had thought that a normalDot of zero was indicating congruent points.  But
		// apparently, the pattern (x, y, 0) (-x, y, 0) (0, 0, z) produces a valid normal
		// but with a normalDot of 0.  So check for a zero normal vector instead, which
		// indicates all points on a straight line
		if ( aNormal.equals( Vector3d.ZERO ) ) {
			// Not a valid normal
			return( null );
		}
		double normalDot = aNormal.dot( pA );
		CSGPlaneDbl aPlane = new CSGPlaneDbl( aNormal, pA, normalDot, -1, pEnvironment );
		if ( pEnvironment.mStructuralDebug ) {
			// NOTE that the rationalization of the normal could make the points look off the plane
			//		(I wonder if I should compute normalDot before rationalizeVector???)
			double aDistance = aPlane.pointDistance( pA );
			double bDistance = aPlane.pointDistance( pB );
			double cDistance = aPlane.pointDistance( pC );
			if ( aDistance + bDistance + cDistance > pEnvironment.mEpsilonOnPlaneDbl ) {
				pEnvironment.log( Level.SEVERE, "Points NOT on plane: " + aPlane );				
			}
		}
		return( aPlane );
	}
	/** Factory method to produce a plane from a set of vertices */
	public static CSGPlaneDbl fromVertices(
		List<CSGVertex>		pVertices
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( pVertices.size() >= 3 ) {
			// Use the position of the first 3 vertices to define the plane
			Vector3d aVector = ((CSGVertexDbl)pVertices.get(0)).getPosition();
			Vector3d bVector = ((CSGVertexDbl)pVertices.get(1)).getPosition();
			Vector3d cVector = ((CSGVertexDbl)pVertices.get(2)).getPosition();
			return( fromPoints( aVector, bVector, cVector, pTempVars, pEnvironment ) );
		} else {
			// Not enough info to define a plane
			return( null );
		}
	}
	public static CSGPlaneDbl fromVertices(
		CSGVertex[]			pVertices
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		if ( pVertices.length >= 3 ) {
			// Use the position of the first 3 vertices to define the plane
			Vector3d aVector = ((CSGVertexDbl)pVertices[ 0 ]).getPosition();
			Vector3d bVector = ((CSGVertexDbl)pVertices[ 1 ]).getPosition();
			Vector3d cVector = ((CSGVertexDbl)pVertices[ 2 ]).getPosition();
			return( fromPoints( aVector, bVector, cVector, pTempVars, pEnvironment ) );
		} else {
			// Not enough info to define a plane
			return( null );
		}
	}
	
	/** Quick access to its DOT for comparison purposes */
	protected double	mDot;

	/** Standard null constructor */
	public CSGPlaneDbl(
	) {
		mSurfaceNormal = Vector3d.ZERO;
		mPointOnPlane = Vector3d.ZERO;
		mDot = Double.NaN;
		mMark = -1;
	}
	/** Constructor based on a given normal and point on the plane */
	public CSGPlaneDbl(
		Vector3d	pNormal
	,	Vector3d	pPointOnPlane
	) {
		// From the BSP FAQ paper, the 'D' value is calculated from the normal and a point on the plane.
		this( pNormal, pPointOnPlane, pNormal.dot( pPointOnPlane ), -1, CSGEnvironment.sStandardEnvironment );
	}
	/** Internal constructor for a given normal and dot */
	protected CSGPlaneDbl(
		Vector3d		pNormal
	,	Vector3d		pPointOnPlane
	,	double			pDot
	,	int				pMarkValue
	,	CSGEnvironment	pEnvironment
	) {
		if ( (pEnvironment != null) && pEnvironment.mStructuralDebug ) {
			// Remember that NaN always returns false for any comparison, so structure the logic accordingly
			double normalLength = pNormal.length();
			if ( !(
			   (Math.abs( pNormal.x ) <= 1) && (Math.abs( pNormal.y ) <= 1) && (Math.abs( pNormal.z ) <= 1) 
			&& (normalLength < 1.0f + pEnvironment.mEpsilonNearZeroDbl) 
			&& (normalLength > 1.0f - pEnvironment.mEpsilonNearZeroDbl)
			&& CSGEnvironment.isFinite( pDot )
			) ) {
				pEnvironment.log( Level.SEVERE, "Bogus Plane: " + pNormal + ", " + normalLength + ", " + pDot );
				pDot =  Double.NaN;
			}
		}
		mSurfaceNormal = pNormal;
		mPointOnPlane = pPointOnPlane;
		mDot = pDot;
		mMark = pMarkValue;
	}
	
	/** Return a copy */
	public CSGPlaneDbl clone(
		boolean		pFlipIt
	) {
		if ( pFlipIt ) {
			// Flipped copy
			return( new CSGPlaneDbl( mSurfaceNormal.negate(), mPointOnPlane, -mDot, -1, null ) );
		} else {
			// Standard use of this immutable copy
			return( this );
		}
	}
	
	/** DOT value for this plane */
	public double getDot() { return( mDot ); }
	
	/** Ensure we have something valid */
	@Override
	public boolean isValid() { return( CSGEnvironment.isFinite( mDot ) ); }
	
	/** Check if a given point is in 'front' or 'behind' this plane  */
	public double pointDistance(
		Vector3d	pPoint
	) {
		// How far away is the given point
		double distanceToPlane = mSurfaceNormal.dot( pPoint ) - (double)mDot;
		return( distanceToPlane );
	}
	
	/** Check if a given point is in 'front' or 'behind' this plane.
	 	@return -  -1 if behind
	 				0 if on the plane
	 			   +1 if in front
	 */
	public int pointPosition(
		Vector3d	pPoint
	,	double		pTolerance
	) {
		// How far away is the given point
		double distanceToPlane = mSurfaceNormal.dot( pPoint ) - mDot;
		
		// If within a given tolerance, it is the same plane
		int aPosition = (distanceToPlane < -pTolerance) ? -1 : (distanceToPlane > pTolerance) ? 1 : 0;
		return( aPosition );
	}
	public int pointPosition(
		CSGVertex		pVertex
	,	CSGEnvironment	pEnvironment
	) {
		return( pointPosition( (Vector3d)pVertex.getPosition(), pEnvironment.mEpsilonOnPlaneDbl ) );
	}
	
	/** Find the projection of a given point onto this plane */
	public Vector3d pointProjection(
		Vector3d	pPoint
	,	Vector3d	pPointStore
	) {
		// Digging around the web, I found the following:
		//		q_proj = q - dot(q - p, n) * n
		// And
		//		a = point_x*normal_dx + point_y*normal_dy + point_z*normal_dz - c;
		//		planar_x = point_x - a*normal_dx;
		//		planar_y = point_y - a*normal_dy;
		//		planar_z = point_z - a*normal_dz;
		double aFactor = pPoint.dot( mSurfaceNormal ) - mDot;
		pPointStore = mSurfaceNormal.mult( aFactor, pPointStore );
		pPointStore.set( pPoint.x - pPointStore.x, pPoint.y - pPointStore.y, pPoint.z - pPointStore.z );
		return( pPointStore );
	}
	

	/** Intersection of a line and this plane 
	 	Based on code fragment from: 
	 		http://math.stackexchange.com/questions/83990/line-and-plane-intersection-in-3d
	 */
	public Vector3d intersectLine(
		Vector3d		pPointA
	,	Vector3d		pPointB
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Use the temp
		Vector3d lineFrag = pPointB.subtract( pPointA, pTempVars.vectd1 );
		double nDotA = mSurfaceNormal.dot( pPointA );
		double nDotFrag = mSurfaceNormal.dot( lineFrag );
		
		double distance = (mDot - nDotA) / nDotFrag;
		lineFrag.multLocal( distance );
		
		// Produce a new vector for subsequent use
		Vector3d intersection = pPointA.add( lineFrag );
		
		if ( pEnvironment.mStructuralDebug ) {
			double confirmDistance = pointDistance( intersection );
			if ( confirmDistance > pEnvironment.mEpsilonNearZeroDbl ) {
				// Try to force back onto the plane
				Vector3d pointOnPlane = this.pointProjection( intersection, null );
				intersection = pointOnPlane;
				
				pEnvironment.log( Level.WARNING, "Line intersect failed: "+ confirmDistance );
			}
		}
		return( intersection );
	}

	/** Make the Plane 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.write( mSurfaceNormal, "Normal", Vector3d.ZERO );
		aCapsule.write( mDot, "Dot", 0 );
	}
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule( this );
		mSurfaceNormal = (Vector3d)aCapsule.readSavable( "Normal", Vector3d.ZERO );
		mDot = aCapsule.readDouble( "Dot", 0 );
	}
	
	/** Treat two planes as equal if they happen to be close */
	public boolean equals(
		Object		pOther
	,	double		pTolerance
	) {
		if ( pOther == this ) {
			// By definition, if the plane is the same
			return true;
		} else if ( pOther instanceof CSGPlaneDbl ) {
			// Two planes that are close are equal
			if ( this.mDot != ((CSGPlaneDbl)pOther).mDot ) {
				return( false );
			} else if ( CSGEnvironment.equalVector3d( this.mSurfaceNormal
														, ((CSGPlaneDbl)pOther).mSurfaceNormal
														, pTolerance ) ) {
				return( true );
			} else {
				return( false );
			}
		} else {
			// Let the super handle the error case
			return( super.equals( pOther ) );
		}
	}
	
	/** For DEBUG */
	@Override
	public String toString(
	) {
		return( toString( null ).toString() );
	}
	public StringBuilder toString(
		StringBuilder	pBuffer
	) {
		if ( pBuffer == null ) pBuffer = new StringBuilder( 128 );
		pBuffer.append( "Plane: " )
				.append( mSurfaceNormal.toString() )
				.append( " (" )
				.append( mDot )
				.append( ")" );
		return( pBuffer );
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGPlaneDblRevision
													, sCSGPlaneDblDate
													, pBuffer ) );
	}

}
