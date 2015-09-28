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

import com.jme3.scene.plugins.blender.math.Vector3d;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

/** This class is a variant of the basic jme3 Ray, but relying on 'double' coordinates rather than floats.
 	A ray is a 3D construct represented by a direction and an origin.
 */
public class CSGRay 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGRay3dRevision="$Rev$";
	public static final String sCSGRay3dDate="$Date$";
	
	private static final double TOL = 1e-10f;

	/** The origin */
	protected Vector3d	mOrigin;
	/** The direction */
	protected Vector3d 	mDirection;
	
	/** Constructor based on a direction and origin */
	public CSGRay(
		Vector3d 	pDirection
	,	Vector3d	pOrigin
	)
	{
		this.mDirection = pDirection.normalize();;
		this.mOrigin = (Vector3d)pOrigin.clone();
	}
	
	/** Constructor based on the intersection of two faces */
	public CSGRay(
		CSGFace 		pFace1
	, 	CSGFace 		pFace2
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = TOL; // pEnvironment.mEpsilonNearZero
		Vector3d normalFace1 = pFace1.getNormal();
		Vector3d normalFace2 = pFace2.getNormal();
		
		// Direction: the cross product of the normals from the faces
		mDirection = normalFace1.cross( normalFace2 );
				
		// Check if direction length is not zero (the planes aren't parallel )...
		if ( mDirection.length() > TOL ) {
			//getting a line point, zero is set to a coordinate whose direction 
			//component isn't zero (line intersecting its origin plan)
			Vector3d position1 = pFace1.v1().getPosition();
			Vector3d position2 = pFace2.v1().getPosition();
			double d1 = -(normalFace1.x*position1.x + normalFace1.y*position1.y + normalFace1.z*position1.z);
			double d2 = -(normalFace2.x*position2.x + normalFace2.y*position2.y + normalFace2.z*position2.z);

			mOrigin = new Vector3d();
			if ( Math.abs(mDirection.x) > TOL ) {
				mOrigin.x = 0;
				mOrigin.y = (d2*normalFace1.z - d1*normalFace2.z)/mDirection.x;
				mOrigin.z = (d1*normalFace2.y - d2*normalFace1.y)/mDirection.x;
			} else if ( Math.abs(mDirection.y) > TOL ) {
				mOrigin.x = (d1*normalFace2.z - d2*normalFace1.z)/mDirection.y;
				mOrigin.y = 0;
				mOrigin.z = (d2*normalFace1.x - d1*normalFace2.x)/mDirection.y;
			} else {
				mOrigin.x = (d2*normalFace1.y - d1*normalFace2.y)/mDirection.z;
				mOrigin.y = (d1*normalFace2.x - d2*normalFace1.x)/mDirection.z;
				mOrigin.z = 0;
			}
		}
		mDirection.normalizeLocal();
	}

	
	/** Accessor to the direction */
	public Vector3d getDirection() { return mDirection; }
	
	/** Accessor to the origin */
	public Vector3d getOrigin() { return mOrigin; }
	
	
	/** Computes the distance from this ray's origin to another point

		@param otherPoint - the point to compute the distance from the origin. The point 
	 						is supposed to be on the same line.
	  	@return - the distance. If the point submitted is behind the direction, the 
	 						distance is negative 
	 */
	public double computePointToPointDistance(
		Vector3d 		pOtherPoint
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Absolute distance
		double distance = pOtherPoint.distance( mOrigin );
		
		Vector3d vec = pOtherPoint.subtract( mOrigin, pTempVars.vectd4 );
		vec.normalizeLocal();
		if ( vec.dot( mDirection ) < 0) {
			return -distance;			
		} else {
			return distance;
		}
	}
	
	/** Computes the vertex resulting from the intersection with another line

	   @param otherLine - the other line to apply the intersection. The lines are supposed
   						  to intersect
	 */
	public Vector3d computeLineIntersection(
		CSGRay			pOtherLine
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		//x = x1 + a1*t = x2 + b1*s
		//y = y1 + a2*t = y2 + b2*s
		//z = z1 + a3*t = z2 + b3*s
		
		double tolerance = TOL; // pEnvironment.mEpsilonNearZero;
		Vector3d lineOrigin = pOtherLine.getOrigin(); 
		Vector3d lineDirection = pOtherLine.getDirection();
				
		double t;
		if ( Math.abs( mDirection.y*lineDirection.x - mDirection.x*lineDirection.y ) > tolerance ) {
			t = (-mOrigin.y*lineDirection.x
					+ lineOrigin.y*lineDirection.x
					+ lineDirection.y*mOrigin.x
					- lineDirection.y*lineOrigin.x) 
				/ (mDirection.y*lineDirection.x - mDirection.x*lineDirection.y);
			
		} else if ( Math.abs( -mDirection.x*lineDirection.z + mDirection.z*lineDirection.x) > tolerance ) {
			t = -(-lineDirection.z*mOrigin.x
					+ lineDirection.z*lineOrigin.x
					+ lineDirection.x*mOrigin.z
					- lineDirection.x*lineOrigin.z)
				/ (-mDirection.x*lineDirection.z + mDirection.z*lineDirection.x);
			
		} else if ( Math.abs( -mDirection.z*lineDirection.y + mDirection.y*lineDirection.z) > tolerance ) {
			t = (mOrigin.z*lineDirection.y
					- lineOrigin.z*lineDirection.y
					- lineDirection.z*mOrigin.y
					+ lineDirection.z*lineOrigin.y)
				/ (-mDirection.z*lineDirection.y + mDirection.y*lineDirection.z);
			
		} else {
			// Nothing we can figure out
			return null;
		}
		// Construct a new position based on what we know 
		double x = mOrigin.x + mDirection.x*t;
		double y = mOrigin.y + mDirection.y*t;
		double z = mOrigin.z + mDirection.z*t;
		Vector3d newPosition = new Vector3d( x, y, z );
		return( newPosition );
	}
	

	/** Compute the point resulting from the intersection of this ray with a plane
	 	@return - intersection point. If they don't intersect, return null
	 */
	public Vector3d computePlaneIntersection(
		Vector3d		pPlaneNormal
	, 	Vector3d		pPlanePoint
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		//Ax + By + Cz + D = 0
		//x = x0 + t(x1 – x0)
		//y = y0 + t(y1 – y0)
		//z = z0 + t(z1 – z0)
		//(x1 - x0) = dx, (y1 - y0) = dy, (z1 - z0) = dz
		//t = -(A*x0 + B*y0 + C*z0 )/(A*dx + B*dy + C*dz)
		
		double tolerance = TOL; // pEnvironment.mEpsilonNearZero;
		
		double A = pPlaneNormal.x;
		double B = pPlaneNormal.y;
		double C = pPlaneNormal.z;
		double D = -(pPlaneNormal.x * pPlanePoint.x
					 + pPlaneNormal.y * pPlanePoint.y
					 + pPlaneNormal.z * pPlanePoint.z);
			
		double numerator = A*mOrigin.x + B*mOrigin.y + C*mOrigin.z + D;
		double denominator = A*mDirection.x + B*mDirection.y + C*mDirection.z;
				
		if ( Math.abs( denominator ) < tolerance ) {
			// Line is parallel to the plane
			if( Math.abs( numerator ) < tolerance ) {
				// Line contained within the plane
				return( mOrigin.clone() );
			} else {
				// No intersection
				return( null );
			}
		} else {
			// Line intersects the plane
			double t = -numerator/denominator;
			Vector3d resultPoint = new Vector3d();
			resultPoint.x = mOrigin.x + t*mDirection.x; 
			resultPoint.y = mOrigin.y + t*mDirection.y;
			resultPoint.z = mOrigin.z + t*mDirection.z;
			
			return resultPoint;
		}
	}

	/** Randomly alter the direction of this line */
	public void perturbDirection(
	) {
		mDirection.x += 1e-5*Math.random();			
		mDirection.y += 1e-5*Math.random();
		mDirection.z += 1e-5*Math.random();
	}

	/** OVERRIDE for debug report */
	@Override
	public String toString(
	) {
		return( "Direction: " + mDirection + ", Origiin: " + mOrigin );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGRay3dRevision
													, sCSGRay3dDate
													, pBuffer ) );
	}

}
