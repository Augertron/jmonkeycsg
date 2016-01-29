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

import java.util.logging.Level;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.blender.math.Vector3d;
import com.jme3.util.TempVars;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGPlaneDbl;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
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
	
	//private static final double TOL = 1e-10f;
	
	/** Intersection of two lines (if any)
	 	 	In general you can find the two points, A0 and B0, on the respective lines A1A2 and B1B2 which 
	 	 	are closest together and determine if they coincide or else see how far apart they lie.

  			The following is a specific formula using matlab for determining these closest points A0 and B0 
  			in terms of vector cross products and dot products. Assume that all four points are represented 
  			by three-element column vectors or by three-element row vectors. Do the following:

 			nA = dot(cross(B2-B1,A1-B1),cross(A2-A1,B2-B1));
 			nB = dot(cross(A2-A1,A1-B1),cross(A2-A1,B2-B1));
 			d = dot(cross(A2-A1,B2-B1),cross(A2-A1,B2-B1));
 			A0 = A1 + (nA/d)*(A2-A1);
 			B0 = B1 + (nB/d)*(B2-B1);
 			
 			@return - the intersection point, or NULL if the two lines do not cross
 			
 		****** TempVars used:  vectd1, vectd2, vectd3, vectd4, vectd5
	 */
	public static Vector3d lineIntersection(
		Vector3d		pLineAPoint1
	,	Vector3d		pLineAPoint2
	,	Vector3d		pLineBPoint1
	,	Vector3d		pLineBPoint2
	,	Vector3d		pResult
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		Vector3d vA2minusA1 = pLineAPoint2.subtract( pLineAPoint1, pTempVars.vectd1 );
		Vector3d vB2minusB1 = pLineBPoint2.subtract( pLineBPoint1, pTempVars.vectd2 );
		Vector3d vA1minusB1 = pLineAPoint1.subtract( pLineBPoint1, pTempVars.vectd3 );
		
		// Where on line A?
		Vector3d nCrossAB = vA2minusA1.cross( vB2minusB1, pTempVars.vectd4 );
		Vector3d nACrossBA = vB2minusB1.cross( vA1minusB1, pTempVars.vectd5 );
		double nA = nACrossBA.dot( nCrossAB );
		
		Vector3d dCrossAB = vA2minusA1.cross( vB2minusB1, pTempVars.vectd5 );
		double d = dCrossAB.dot( dCrossAB );
		double multiplierA = nA / d;
		if ( Double.isNaN( multiplierA ) || Double.isInfinite( multiplierA ) ) {
			return( null );		// No intersection
		}
		pResult = vA2minusA1.mult( multiplierA, pResult );
		pResult.addLocal( pLineAPoint1 );
		
		// Where on line B?
		Vector3d nBCrossBA = vA2minusA1.cross( vA1minusB1, pTempVars.vectd5 );
		double nB = nBCrossBA.dot( nCrossAB );
			
		double multiplierB = nB / d;
		if ( Double.isNaN( multiplierB ) || Double.isInfinite( multiplierB ) ) {
			return( null );		// No Intersection
		}	
		Vector3d bZero = vB2minusB1.multLocal( multiplierB );
		bZero.addLocal( pLineBPoint1 );
		
		// Confirm the intersection
		if ( !CSGEnvironment.equalVector3d( pResult, bZero, pEnvironment.mEpsilonBetweenPointsDbl ) ) {
			return( null );		// No Intersection
		}
		if ( pEnvironment.mRationalizeValues ) {
			// Confirm that the magnitudes of the resultant point are rational
			CSGEnvironment.rationalizeVector( pResult, pEnvironment.mEpsilonMagnitudeRange );
			CSGEnvironment.rationalizeVector( bZero, pEnvironment.mEpsilonMagnitudeRange );
		}
		return( pResult );
	}

	/** The origin */
	protected Vector3d	mOrigin;
	/** The direction */
	protected Vector3d 	mDirection;
	
	/** Constructor based on a direction and origin */
	public CSGRay(
		Vector3d 	pDirection
	,	Vector3d	pOrigin
	) {
		this.mDirection = pDirection.normalize();;
		this.mOrigin = (Vector3d)pOrigin.clone();
	}
	
	/** Constructor based on the intersection of two faces 
	 	My incredibly incomplete understanding of this is that the ray represents the 
	 	infinite line that is the intersection of the infinite planes.  The actual bounds
	 	imposed by a finite 'face' do not come into play at all.
	 	
	 	So while the ray is defined by the intersection of the two faces, it is
	 	quite possible that the ray does not really fall within the constraints of 
	 	either face.
	 	
	 	@see CSGSegment for a ray bound to the limits of a given face
	 */
	public CSGRay(
		CSGFace 		pFace1
	, 	CSGFace 		pFace2
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL;
		CSGPlaneDbl planeFace1 = pFace1.getPlane();
		CSGPlaneDbl planeFace2 = pFace2.getPlane();
		Vector3d normalFace1 = planeFace1.getNormal();
		Vector3d normalFace2 = planeFace2.getNormal();
		
		// Direction: the cross product of the normals from the faces
		mDirection = normalFace1.cross( normalFace2 );
				
		// Check if direction length is not zero (the planes aren't parallel )...
		if ( mDirection.length() > tolerance ) {
			// Getting a line point, zero is set to a coordinate whose direction 
			// component isn't zero (line intersecting its origin plan)
//			Vector3d position1 = pFace1.v1().getPosition();
//			Vector3d position2 = pFace2.v1().getPosition();
//			double d1 = -(normalFace1.x*position1.x + normalFace1.y*position1.y + normalFace1.z*position1.z);
//			double d2 = -(normalFace2.x*position2.x + normalFace2.y*position2.y + normalFace2.z*position2.z);
			
			double d1 = -planeFace1.getDot();
			double d2 = -planeFace2.getDot();

			mOrigin = new Vector3d();
			if ( Math.abs(mDirection.x) > tolerance ) {
				mOrigin.x = 0;
				mOrigin.y = (d2*normalFace1.z - d1*normalFace2.z)/mDirection.x;
				mOrigin.z = (d1*normalFace2.y - d2*normalFace1.y)/mDirection.x;
			} else if ( Math.abs(mDirection.y) > tolerance ) {
				mOrigin.x = (d1*normalFace2.z - d2*normalFace1.z)/mDirection.y;
				mOrigin.y = 0;
				mOrigin.z = (d2*normalFace1.x - d1*normalFace2.x)/mDirection.y;
			} else {
				mOrigin.x = (d2*normalFace1.y - d1*normalFace2.y)/mDirection.z;
				mOrigin.y = (d1*normalFace2.x - d2*normalFace1.x)/mDirection.z;
				mOrigin.z = 0;
			}
			if ( pEnvironment.mRationalizeValues ) {
				// Confirm that the magnitudes of the resultant point are rational
				CSGEnvironment.rationalizeVector( mOrigin, pEnvironment.mEpsilonMagnitudeRange );
			}
		} else {
			throw new IllegalArgumentException( "Ray built from parallel faces" );
		}
		// NOTE NOTE NOTE
		//		If rational values are active, the following seems to screw things up
		//		unless the magnitude range is very very large, so I am disabling it
//		if ( pEnvironment.mRationalizeValues ) {
//			CSGEnvironment.rationalizeVector( mDirection, pEnvironment.mEpsilonMagnitudeRange );
//		}
		mDirection.normalizeLocal();
	}

	
	/** Accessor to the direction */
	public Vector3d getDirection() { return mDirection; }
	
	/** Accessor to the origin */
	public Vector3d getOrigin() { return mOrigin; }
	
	
	/** Computes the distance from this ray's origin to another point

	  	@return - the distance. If the point submitted is 'behind' the direction, then 
	 		      the distance is negative 
	 		      
 		****** TempVars used:  vectd6
	 */
	public double computePointToPointDistance(
		Vector3d 		pOtherPoint
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		// Absolute distance
		double distance = pOtherPoint.distance( mOrigin );
		
		Vector3d vec = pOtherPoint.subtract( mOrigin, pTempVars.vectd6 );
		vec.normalizeLocal();
		if ( vec.dot( mDirection ) < 0 ) {
			return( -distance );			
		} else {
			return( distance );
		}
	}
	
	/** Computes the vertex resulting from the intersection with another line 
	 	I found the following on the web which may shed some light on the code 
	 	
--------------------------------------------------------------------------------------------
The ray is basically defined by the part of the line when the parameter (t or s) is greater 
than or equal to 0 (assuming p0 and q0 are the starting points of the rays). Unless the 
lines are perfectly parallel, they will intersect at some point. This is the point at 
which the X and Y values of both lines correspond with one another. So we rewrite them 
as equalities:

p0.x + t * pd.x = q0.x + s * qd.x
p0.y + t * pd.y = q0.y + s * qd.y

We can now algebraically isolate t and s:

t = (qd.x*(q0.y - p0.y) + qd.y*(p0.x - q0.x)) / (pd.y*qd.x - pd.x*qd.y)
s = (pd.x*(q0.y - p0.y) + pd.y*(p0.x - q0.x)) / (pd.y*qd.x - pd.x*qd.y)

Since both have the same divisor, we can make this a little easier on the computer by calculating it first:

div = pd.y*qd.x - pd.x*qd.y

(If div = 0, you can stop here: the lines/rays are perfectly parallel and they will never intersect.)

Now:

t = (qd.x*(q0.y - p0.y) + qd.y*(p0.x - q0.x)) / div
s = (pd.x*(q0.y - p0.y) + pd.y*(p0.x - q0.x)) / div

t is the point on the first line at which the intersection occurs, and s is the point of intersection 
on the second line.

We must determine if the intersection occurs in the rays. If t >= 0 and s >= 0, then the rays 
intersect. If one or both are < 0, the rays do not intersect, and the intersection occurs at 
some point before the start of the ray (on the line that the ray defines).

If t and s meet the proper conditions, you can now plug t or s into one of the original equations 
to find the exact point of intersection, which you also might need for your program.
-----------------------------------------------------------------------------------------------------------
 
	*/
	public Vector3d computeLineIntersection(
		CSGRay			pOtherLine
	,	Vector3d		pResult
	,	double			pTolerance
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		//x = x1 + a1*t = x2 + b1*s
		//y = y1 + a2*t = y2 + b2*s
		//z = z1 + a3*t = z2 + b3*s
		Vector3d lineOrigin = pOtherLine.getOrigin(); 
		Vector3d lineDirection = pOtherLine.getDirection();
				
		double t;
		if ( Math.abs( mDirection.y*lineDirection.x - mDirection.x*lineDirection.y ) > pTolerance ) {
			t = (-mOrigin.y*lineDirection.x
					+ lineOrigin.y*lineDirection.x
					+ lineDirection.y*mOrigin.x
					- lineDirection.y*lineOrigin.x) 
				/ (mDirection.y*lineDirection.x - mDirection.x*lineDirection.y);
			
		} else if ( Math.abs( -mDirection.x*lineDirection.z + mDirection.z*lineDirection.x) > pTolerance ) {
			t = -(-lineDirection.z*mOrigin.x
					+ lineDirection.z*lineOrigin.x
					+ lineDirection.x*mOrigin.z
					- lineDirection.x*lineOrigin.z)
				/ (-mDirection.x*lineDirection.z + mDirection.z*lineDirection.x);
			
		} else if ( Math.abs( -mDirection.z*lineDirection.y + mDirection.y*lineDirection.z) > pTolerance ) {
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
		pResult = mDirection.mult( t, pResult );
		pResult.addLocal( mOrigin );
		
		if ( pEnvironment.mRationalizeValues ) {
			// Confirm that the magnitudes of the resultant point are rational
			CSGEnvironment.rationalizeVector( pResult, pEnvironment.mEpsilonMagnitudeRange );
		}
		return( pResult );
	}
	public Vector3d computeLineIntersection(
		Vector3d		pOtherPoint1
	,	Vector3d		pOtherPoint2
	,	Vector3d		pResult
	,	double			pTolerance
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		Vector3d thisPoint2 = pTempVars.vectd6.set( this.mOrigin ).addLocal( this.mDirection );
		pResult = lineIntersection( this.mOrigin
									,	thisPoint2
									,	pOtherPoint1
									,	pOtherPoint2
									,	pResult
									,	pTempVars
									,	pEnvironment );
		return( pResult );
	}
	

	/** Compute the point resulting from the intersection of this ray with a plane
	 
	 	@return - intersection point. If they don't intersect, return null
	 */
	public Vector3d computePlaneIntersection(
		Vector3d		pPlaneNormal
	,	double			pPlaneDot
	,	Vector3d		pResult
	,	CSGTempVars		pTempVars
	,	CSGEnvironment	pEnvironment
	) {
		//Ax + By + Cz + D = 0
		//x = x0 + t(x1 – x0)
		//y = y0 + t(y1 – y0)
		//z = z0 + t(z1 – z0)
		//(x1 - x0) = dx, (y1 - y0) = dy, (z1 - z0) = dz
		//t = -(A*x0 + B*y0 + C*z0 )/(A*dx + B*dy + C*dz)
		
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL;
		
		double A = pPlaneNormal.x;
		double B = pPlaneNormal.y;
		double C = pPlaneNormal.z;
		double D = -pPlaneDot;
			
		double numerator = A*mOrigin.x + B*mOrigin.y + C*mOrigin.z + D;
		double denominator = A*mDirection.x + B*mDirection.y + C*mDirection.z;
				
		if ( Math.abs( denominator ) < tolerance ) {
			// Line is parallel to the plane
			if( Math.abs( numerator ) < tolerance ) {
				// Line contained within the plane
				if ( pResult == null ) {
					return( mOrigin.clone() );
				} else {
					return( pResult.set( mOrigin ) );
				}
			} else {
				// No intersection
				return( null );
			}
		} else {
			// Line intersects the plane
			double t = -numerator/denominator;
			pResult = mDirection.mult( t, pResult );
			pResult.addLocal( mOrigin );
			
			if ( pEnvironment.mRationalizeValues ) {
				// Confirm that the magnitudes of the resultant point are rational
				CSGEnvironment.rationalizeVector( pResult, pEnvironment.mEpsilonMagnitudeRange );
			}
			return pResult;
		}
	}

    /** Compute if/where this ray intersects the given triangle */
    public boolean intersectsTriangle(
    	Vector3d 	pV0
    , 	Vector3d 	pV1
    , 	Vector3d 	pV2
    ,	Vector3d 	pResult
    , 	boolean 	pResultsAsPoint
    ,	CSGTempVars	pVars
    ,	double		pTolerance
    ) {
        Vector3d tempVa = pVars.vectd1,
                tempVb = pVars.vectd2,
                tempVc = pVars.vectd3,
        		tempVd = pVars.vectd4;

        Vector3d diff = mOrigin.subtract( pV0, tempVa );
        Vector3d edge1 = pV1.subtract( pV0, tempVb );
        Vector3d edge2 = pV2.subtract( pV0, tempVc );
        Vector3d norm = edge1.cross( edge2, tempVd );

        double dirDotNorm = mDirection.dot( norm );
        double sign;
        if ( dirDotNorm > pTolerance ) {
            sign = 1;
        } else if (dirDotNorm < -pTolerance ) {
            sign = -1;
            dirDotNorm = -dirDotNorm;
        } else {
            // Ray and triangle are parallel
            return false;
        }
        double dirDotEdge1xDiff, dirDotDiffxEdge2, diffDotNorm;
        dirDotDiffxEdge2 = sign * mDirection.dot( diff.cross( edge2, edge2 ) );
        if ( dirDotDiffxEdge2 >= 0.0 ) {
            dirDotEdge1xDiff = sign * mDirection.dot( edge1.crossLocal( diff ) );

            if ( dirDotEdge1xDiff >= 0.0 ) {
                if ( dirDotDiffxEdge2 + dirDotEdge1xDiff <= dirDotNorm ) {
                    diffDotNorm = -sign * diff.dot( norm );
                    if ( diffDotNorm >= 0.0 ) {
                        // Ray intersects triangle
                        if ( pResult != null) {
	                        double inv = 1 / dirDotNorm;
	                        double t = diffDotNorm * inv;
	                        if ( pResultsAsPoint ) {
	                        	// Where is the intersection
	                        	pResult.set( mOrigin )
	                            	.addLocal( mDirection.x * t, mDirection.y * t, mDirection.z * t );
	                        } else {
	                            // These weights can be used to determine
	                            // interpolated values, such as texture coord.
	                            // eg. texcoord s,t at intersection point:
	                            // s = w0*s0 + w1*s1 + w2*s2;
	                            // t = w0*t0 + w1*t1 + w2*t2;
	                            double w1 = dirDotDiffxEdge2 * inv;
	                            double w2 = dirDotEdge1xDiff * inv;
	                            
	                            //float w0 = 1.0f - w1 - w2;
	                            pResult.set(t, w1, w2);
	                        }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }
    public boolean intersectsTriangle(
    	Vector3d 	pV0
    , 	Vector3d 	pV1
    , 	Vector3d 	pV2
    ,	double		pTolerance
    ) {
        double edge1X = pV1.x - pV0.x;
        double edge1Y = pV1.y - pV0.y;
        double edge1Z = pV1.z - pV0.z;

        double edge2X = pV2.x - pV0.x;
        double edge2Y = pV2.y - pV0.y;
        double edge2Z = pV2.z - pV0.z;

        double normX = ((edge1Y * edge2Z) - (edge1Z * edge2Y));
        double normY = ((edge1Z * edge2X) - (edge1X * edge2Z));
        double normZ = ((edge1X * edge2Y) - (edge1Y * edge2X));

        double dirDotNorm = mDirection.x * normX + mDirection.y * normY + mDirection.z * normZ;

        double diffX = mOrigin.x - pV0.x;
        double diffY = mOrigin.y - pV0.y;
        double diffZ = mOrigin.z - pV0.z;

        double sign;
        if ( dirDotNorm > pTolerance ) {
            sign = 1;
        } else if ( dirDotNorm < -pTolerance ) {
            sign = -1f;
            dirDotNorm = -dirDotNorm;
        } else {
            // ray and triangle/quad are parallel
            return false;
        }
        double diffEdge2X = ((diffY * edge2Z) - (diffZ * edge2Y));
        double diffEdge2Y = ((diffZ * edge2X) - (diffX * edge2Z));
        double diffEdge2Z = ((diffX * edge2Y) - (diffY * edge2X));

        double dirDotDiffxEdge2 = sign * (mDirection.x * diffEdge2X
							                + mDirection.y * diffEdge2Y
							                + mDirection.z * diffEdge2Z);

        if ( dirDotDiffxEdge2 >= 0.0 ) {
            diffEdge2X = ((edge1Y * diffZ) - (edge1Z * diffY));
            diffEdge2Y = ((edge1Z * diffX) - (edge1X * diffZ));
            diffEdge2Z = ((edge1X * diffY) - (edge1Y * diffX));

            double dirDotEdge1xDiff = sign * (mDirection.x * diffEdge2X
							                    + mDirection.y * diffEdge2Y
							                    + mDirection.z * diffEdge2Z);

            if ( dirDotEdge1xDiff >= 0.0 ) {
                if ( dirDotDiffxEdge2 + dirDotEdge1xDiff <= dirDotNorm ) {
                    double diffDotNorm = -sign * (diffX * normX + diffY * normY + diffZ * normZ);
                    if ( diffDotNorm >= 0.0 ) {
                        // ray intersects triangle
                        return true;
                    }
                }
            }
        }
        return false;
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
		return( "Ray: " + mDirection + ", Origin: " + mOrigin );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGRay3dRevision
													, sCSGRay3dDate
													, pBuffer ) );
	}

}
