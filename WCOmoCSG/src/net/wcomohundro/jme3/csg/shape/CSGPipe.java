/** Copyright (c) 2015, WCOmohundro
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
**/
package net.wcomohundro.jme3.csg.shape;

import com.jme3.export.InputCapsule;
import com.jme3.export.OutputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.JmeExporter;
import com.jme3.export.Savable;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector2f;
import com.jme3.math.Spline;
import com.jme3.math.Spline.SplineType;
import com.jme3.math.Quaternion;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.scene.shape.Curve;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.ArrayList;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGPlane;


/** A CSG Pipe is a 'capped' radial whose slices follow a given curve.
 	If the curve happens to be a straight line, then you have generated a cylinder.
 	
 	Otherwise, we allocate the 'slices' to appropriate points along the given spline.
 	Each point defines the center of the radial circle used to generate the vertices.
 	The vectors between each point and its adjacent, neighbor points determines the
 	surface normal of the slice.  This surface normal then defines a rotation to apply
 	to all the generated vertices.
 	
 	The result is a set of slices where each slice is perpendicular to the curve at
 	its center point.
 	Of course, if you twist the curve too tightly, then the slices can bunch up and 
 	give you an unpleasant shape.  But gentle curves with an appropriate number of 
 	slices can produce pipes of reasonable appearance.
 */
public class CSGPipe 
	extends CSGRadialCapped
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPipeRevision="$Rev$";
	public static final String sCSGPipeDate="$Date$";

	/** The splice that determines the positioning of the various slices */
	protected CSGSplineGenerator	mSlicePath;
	/** Control flag to force 'perpendicular' endcaps */
	protected boolean				mPerpendicularEnds;
	/** Control flag to apply smoothing */
	protected boolean				mSmoothSurface;
	
	
	/** Standard null constructor */
	public CSGPipe(
	) {
		this( 32, 32, 1, 1, null, true, false, TextureMode.FLAT );
	}
	
    public CSGPipe(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadiusFront
    ,	float		pRadiusBack
    ,	Spline		pSlicePath
    , 	boolean 	pClosed
    , 	boolean 	pInverted
    ,	TextureMode	pTextureMode
    ) {
    	super( pAxisSamples, pRadialSamples, pRadiusFront, pRadiusBack, 0.0f, pClosed, pInverted, pTextureMode );
    	setSlicePath( pSlicePath );
    }
    
    /** Accessor to the curve that provides the path of the slices */
    public Spline getSlicePath() { return (mSlicePath == null) ? null : mSlicePath.getSpline(); }
    public void setSlicePath( Spline pCurve ) { if ( mSlicePath != null ) mSlicePath.setSpline( pCurve ); }
    
	/** SUBCLASS OVERRIDE: Resolve this mesh, with possible 'debug' delegate representation */
    @Override
	public Mesh resolveMesh(
		boolean		pDebug
	) {
		if ( pDebug ) {
			// Display the spline in debug mode
			List<Vector3f> centerList = computeCenters();
			Spline aLine = new Spline( SplineType.Linear, centerList, 0, false );
			//Spline aLine = mSlicePath.getSpline();
			Curve aCurve = new Curve( aLine, this.mAxisSamples / aLine.getSegmentsLength().size() );
			return( aCurve );
		} else {
			// Display the pipe
			return( this );
		}
	}
	
    /** Rebuilds the cylinder based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
    	// Ensure we have a Spline
    	if ( mSlicePath == null ) {
    		// No explicit curve, so just follow the zAxis
    		if ( mExtentZ == 0 ) mExtentZ = 1.0f;
    		Vector3f[] endPoints 
    			= new Vector3f[] { new Vector3f( 0, 0, mExtentZ ), new Vector3f( 0, 0, -mExtentZ ) };
    		mSlicePath = new CSGSplineGenerator( endPoints );
    	} else {
    		// Use the length of the spline as the z extent
    		mExtentZ = mSlicePath.getTotalLength() / 2.0f;
    	}
        // Standard 'capped' construction
    	super.updateGeometryProlog();   	
    }
    
    /** OVERRIDE: apply any 'smoothing' needed on the surface, trying to eliminate crumples */
    @Override
    protected void smoothSurface(
        CSGRadialContext	pContext
    ,	TempVars			pTempVars
    ) {
    	if ( mSmoothSurface ) {
    		// Scan the surface, looking for crumple points where radial points on one slice
    		// pushes through/overlaps the equivalent point on a different slice.
        	CSGPipeContext myContext = (CSGPipeContext)pContext;
   		
    		// NOTE that we treat the endcaps as inviolate, so we work from the ends to 
    		//		the middle
        	int startPoint = (this.mClosed) ? 1 : 0;
        	int endPoint = (this.mClosed) ? pContext.mSliceCount -2 : pContext.mSliceCount -1;
    		int midPoint = pContext.mSliceCount / 2;
    		
    		// Every slice has mRadialSamples +1 entries
    		int slice3fBias = (mRadialSamples + 1) * 3;
    		int slice2fBias = (mRadialSamples + 1) * 2;
    		
    		for( int i = startPoint; i < midPoint; i += 1 ) {
    			smoothSlice( myContext, i, +1, slice3fBias, slice2fBias, pTempVars );
    		}
    		for( int i = endPoint; i > midPoint; i -= 1 ) {
    			smoothSlice( myContext, i, -1, slice3fBias, slice2fBias, pTempVars );
    		}
    	}
    }
    /** Service routine that checks the points on one slice for overlap with another */
    protected void smoothSlice(
    	CSGPipeContext 	pContext
    ,	int				pBaseIndex
    ,	int				pScanDirection
    ,	int				pOffset3f
    ,	int				pOffset2f
    ,	TempVars		pTempVars
    ) {
    	Vector3f checkPosition = pTempVars.vect1;
    	Vector3f basePosition = pTempVars.vect2;
    	Vector3f baseNormal = pTempVars.vect3;
    	Vector2f baseTexture = pTempVars.vect2d;

    	// We check all points against the Plane of the base
    	CSGPlane basePlane = pContext.mSlicePlanes.get( pBaseIndex );
    	CSGPlane thisPlane = pContext.mSlicePlanes.get( pBaseIndex + pScanDirection );
    	
    	// Locate each radial point in question
    	int floatIdx = (pBaseIndex + pScanDirection) * pOffset3f;
    	int texIdx = (pBaseIndex + pScanDirection) * pOffset2f;
    	
    	for( int i = 0; i <= mRadialSamples; i += 1, texIdx += 2 ) {
    		// We are interested in the position
    		int floatIdx0 = floatIdx++;
    		int floatIdx1 = floatIdx++;
    		int floatIdx2 = floatIdx++;
	    	checkPosition.set( pContext.mPosBuf.get( floatIdx0 )
	    					,	pContext.mPosBuf.get( floatIdx1 )
	    					,	pContext.mPosBuf.get( floatIdx2 ) );
	    	
	    	// Where is this point in relationship to the plane of the base slice?
	    	// @todo - I think there is a problem with the 'planes' if the slices are rotated.....
	    	CSGPlane overlappedPlane
	    		= checkPointOverlap( pContext, thisPlane, checkPosition, basePlane, pBaseIndex, pScanDirection );
	    	if ( overlappedPlane != null ) {
	    		// This given point is on the wrong side of the overlapped plane
if ( false ) {
				// Experiment with the projection of the given point onto the overlapped plane
				// Just using a copy of the base (see below) seems to give a better shape
		    	overlappedPlane.pointProjection( checkPosition, basePosition );
		    	pContext.mPosBuf.put( floatIdx0, basePosition.x )
								.put( floatIdx1, basePosition.y )
								.put( floatIdx2, basePosition.z );
} 
if ( true ) {
				// Use the corresponding point from the base
				int baseIdx0 = floatIdx0 - (pOffset3f * pScanDirection);
				int baseIdx1 = baseIdx0 + 1;
				int baseIdx2 = baseIdx0 + 2;
				
				basePosition.set( pContext.mPosBuf.get( baseIdx0 )
								,	pContext.mPosBuf.get( baseIdx1 )
								,	pContext.mPosBuf.get( baseIdx2 ) );
		    	baseNormal.set( pContext.mNormBuf.get( baseIdx0 )
    						,	pContext.mNormBuf.get( baseIdx1 )
    						,	pContext.mNormBuf.get( baseIdx2 ) );
		    	
		    	int baseTexIdx = texIdx - (pOffset2f * pScanDirection);
		    	baseTexture.set( pContext.mTexBuf.get( baseTexIdx )
								,	pContext.mTexBuf.get( baseTexIdx + 1 ) );
		    	
		    	// Overwrite the point on the wrong side of the base slice with info
		    	// from the base slice itself
		    	pContext.mPosBuf.put( floatIdx0, basePosition.x )
		    					.put( floatIdx1, basePosition.y )
		    					.put( floatIdx2, basePosition.z );
		    	pContext.mNormBuf.put( floatIdx0, baseNormal.x )
								.put( floatIdx1, baseNormal.y )
								.put( floatIdx2, baseNormal.z );
		    	
		    	// Using the original texture seems to give us a better effect
		    	//pContext.mTexBuf.put( texIdx, baseTexture.x )
		    	//				.put( texIdx + 1, baseTexture.y );
}
	    	}
    	}
    }
    /** Service routine to check if a given point is on the proper side of a
     	a plane that defines a 'base' slice.  If the point is on the proper side, then 
     	null is returned.  If the point overlaps the slice, then the base plane that is
     	being overlapped is returned.
     	
     	We keep track of any slice whose points were adjusted due to overlap.
     	This is done by 'marking' that slice with the index of the plane it collided with.
     	That way, if B overlaps with A, a check of C against B will know to also check
     	C against A.
     */
    protected CSGPlane checkPointOverlap(
        CSGPipeContext 	pContext
    ,	CSGPlane		pThisPlane
    ,	Vector3f		pPoint
    ,	CSGPlane		pBasePlane
    ,	int				pBaseIndex
    ,	int				pScanDirection
    ) {
    	// Where is this point in relationship to the plane of the base slice?
    	int pointPlaneRelationship = pBasePlane.pointPosition( pPoint );

    	if ( ((pointPlaneRelationship < 0) && (pScanDirection > 0))
    	|| ((pointPlaneRelationship > 0) && (pScanDirection < 0)) ) {
    		// This given point is on the wrong side of the base plane.
    		if ( pThisPlane.getMark() < 0 ) {
    			// Mark this plane to indicate that an overlap was detected
    			pThisPlane.setMark( pBaseIndex );
    		}
    		return( pBasePlane );
    	}
    	// No direct problems with the base BUT it is
    	// possible that the 'base' slice we are checking against had
    	// its own problems with a prior slice
    	int priorPlaneIndex = pBasePlane.getMark();
    	while( priorPlaneIndex >= 0 ) {
    		// Confirm against the prior plane
        	CSGPlane priorPlane = pContext.mSlicePlanes.get( priorPlaneIndex );
        	CSGPlane overlappedPlane
        		= checkPointOverlap( pContext, pThisPlane, pPoint, priorPlane, priorPlaneIndex, pScanDirection );
        	if ( overlappedPlane != null ) {
        		// This given point is on the wrong side of some prior plane
        		return( overlappedPlane );
        	}
        	// Keep looking for problems with prior planes
        	priorPlaneIndex = priorPlane.getMark();
    	}
    	// We have checked against all possible prior planes that we could overlap with
    	// and everything looks good for this point
    	return( null );
    }
    
    /** OVERRIDE: allocate the context */
    @Override
    protected CSGRadialContext getContext(
    ) {
        // Allocate buffers for position/normals/texture
    	// We generate an extra vertex around the radial sample where the last
    	// vertex overlays the first.  
		// And even though the north/south poles are a single point, we need to 
		// generate different textures and normals for the two EXTRA end slices if closed
    	
    	// And remember the curve itself could require more AxisSamples than what was requested
		List<Vector3f> centerList = computeCenters();
		if ( centerList.size() > mAxisSamples ) {
			mAxisSamples = centerList.size();
		}
    	CSGPipeContext aContext 
    		= new CSGPipeContext( mAxisSamples, mRadialSamples, mClosed, mTextureMode, mScaleSlice );
    	aContext.mCenterList = centerList;
    	return( aContext );
    }

    /** OVERRIDE: fractional speaking, where are we along the z axis */
    @Override
    protected float getZAxisFraction( 
    	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Even though we are following a curve, we treat each slice as if evenly spread
    	// across the full range of z (-1 : +1)
    	return( super.getZAxisFraction( pContext, pSurface ) );
    }
    
    /** OVERRIDE: where is the center of the given slice */
    @Override
    protected Vector3f getSliceCenter(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	CSGPipeContext myContext = (CSGPipeContext)pContext;
    	
    	if ( myContext.mCenterList == null ) {
    		// Compute the centers on the curve
    		myContext.mCenterList = computeCenters();
    	}
    	// Return the center point as defined on the spline
    	// NOTE that mZOffset runs from back to front, but we computed the centers front to back
    	int lastIndex = myContext.mCenterList.size() -1;
    	if ( pSurface < 0 ) {
    		// Back face, which shares the last point
    		pUseVector.set( myContext.mCenterList.get( lastIndex ) );
    	} else if ( pSurface > 0 ) {
    		// Front face, which shares the first point
    		pUseVector.set( myContext.mCenterList.get( 0 ) );
    	} else {
    		// Along the surface of the pipe (where mZOffset acts like a simple counter)
    		pUseVector.set( myContext.mCenterList.get( lastIndex - pContext.mZOffset ) );
    	}
    	return( pUseVector );
    }
      
    /** OVERRIDE: compute the position of a given radial vertex */
    @Override
    protected Vector3f getRadialPosition(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	CSGPipeContext myContext = (CSGPipeContext)pContext;

    	// Look for any twist introduced by the spline
    	if ( pContext.mRadialIndex == 0 ) {
    		// The twist is the same for all radial vertexes on a given slice, and it is
    		// based on the surface normal of the slice, which is determined by the
    		// current active point and the ones on either side
    		myContext.mSliceNormal = new Vector3f();
    		myContext.mSliceSplineRotation = computeSliceNormal( myContext.mSliceNormal, myContext, pSurface );
    	}
    	// By default, just operate on the unit circle (even on the endcaps)
    	CSGRadialCoord aCoord = pContext.mCoordList[ pContext.mRadialIndex ];
        pUseVector.set( aCoord.mCosine, aCoord.mSine, 0 );
        
        // Account for the actual radius
        pUseVector.multLocal( pContext.mSliceRadius );
        
    	// Apply any rotation required by the underlying spline
    	if ( myContext.mSliceSplineRotation != null ) {
    		// Remember that we are building the unit circle around the center point, 
    		// so apply the 'curve' before we adjust the center
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
    	}
        // Account for the actual center
        pUseVector.addLocal( pContext.mSliceCenter );

        // Apply scaling
        if ( mScaleSlice != null ) {
        	pUseVector.multLocal( mScaleSlice.x, mScaleSlice.y, 1.0f );
        }
        // Apply individual slice rotation around the zAxis
    	if ( pContext.mSliceRotator != null ) {
    		pContext.mSliceRotator.multLocal( pUseVector );
    	}
	    return( pUseVector );
    }
    
    /** OVERRIDE: compute the normal of a given radial vertex */
    @Override
    protected Vector3f getRadialNormal(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	CSGPipeContext myContext = (CSGPipeContext)pContext;
		if ( myContext.mRadialNormals == null ) {
    		// Precalculate and cache the normals we will need
        	myContext.mRadialNormals = calculateRadialNormals( pContext, mRadius, mRadiusBack, pUseVector );
		}
    	if ( pSurface != 0 ) {
    		// Backface/Frontface processing perpendicular to x/y plane, using the -1/+1 of the surface
    		pUseVector.set( 0, 0, (float)pSurface );
    	} else {
    		// Use the prebuilt, cached normal
    		pUseVector.set( myContext.mRadialNormals[ myContext.mRadialIndex ] ); 
    		
            // Apply scaling
            if ( mScaleSlice != null ) {
            	pUseVector.multLocal( mScaleSlice.x, mScaleSlice.y, 1.0f );
            }
            // Apply rotation
        	if ( pContext.mSliceRotator != null ) {
        		pContext.mSliceRotator.multLocal( pUseVector );
        	}
    	}
    	if ( mInverted ) {
    		// Invert the normal
    		pUseVector.multLocal( -1 );
    	}
    	// Apply any rotation required by the underlying spline
    	if ( myContext.mSliceSplineRotation != null ) {
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
    	}
    	return( pUseVector );
    }
    
    /** OVERRIDE: what is the center of the endcap */
    @Override
    protected Vector3f getCenterPosition(
     	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	CSGPipeContext myContext = (CSGPipeContext)pContext;
    	
    	// Figure out the adjustments needed for the end cap
		myContext.mSliceNormal = new Vector3f();
		myContext.mSliceSplineRotation = computeSliceNormal( myContext.mSliceNormal, myContext, pSurface );

		// Start with the unit circle center
		pUseVector.set( 0, 0, 0 );
		
    	// Apply any rotation required by the underlying spline
    	if ( myContext.mSliceSplineRotation != null ) {
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
    	}
    	// Account for the actual center
    	pUseVector.addLocal( pContext.mSliceCenter );
    	
    	return( pUseVector );
    }

    /** OVERRIDE: what is the center normal of the endcap */
    @Override
    protected Vector3f getCenterNormal(
     	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	CSGPipeContext myContext = (CSGPipeContext)pContext;

    	// Start with the center with the z normalized to match the surface
    	pUseVector.set( pContext.mSliceCenter.x, pContext.mSliceCenter.y, pSurface );
    	if ( mInverted ) {
    		// Invert the normal
    		pUseVector.multLocal( -1 );
    	}
    	// Apply any rotation required by the underlying spline
    	if ( myContext.mSliceSplineRotation != null ) {
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
    	}
    	return( pUseVector );
    }

    /** Service routine that computes the center points based on the given spline */
    protected List<Vector3f> computeCenters(
    ) {
    	// We need a center point for every sample taken along the zAxis
    	List<Vector3f> centerList = mSlicePath.interpolate( this.mAxisSamples );
    	return( centerList );
    }
    
    /** Service routine that computes the normal to the surface of the given slice,
     	and returns the quaternion to apply to the needed to rotate the slice.
      */
    protected Quaternion computeSliceNormal(
        Vector3f			pSliceNormal
    ,	CSGPipeContext 		pContext
    ,	int					pSurface
    ) {
    	int lastIndex = pContext.mCenterList.size() -1;
		Vector3f thisCenter, priorCenter = null, nextCenter = null;
		Quaternion sliceRotation = null;
		
		int centerIndex;
    	if ( pSurface < 0 ) {
    		// Back face, which shares the last point
    		centerIndex = lastIndex;
    	} else if ( pSurface > 0 ) {
    		// Front face, which shares the first point
    		centerIndex = 0;
    	} else {
    		// Along the surface of the pipe 
    		// (where mZOffset acts like a simple counter, but from back to front)
    		centerIndex = lastIndex - pContext.mZOffset;
    	}
    	thisCenter = pContext.mCenterList.get( centerIndex );
    	if ( centerIndex > 0 ) {
        	priorCenter =  pContext.mCenterList.get( centerIndex -1 );        			
    	}
    	if ( centerIndex < lastIndex ) {
        	nextCenter = pContext.mCenterList.get( centerIndex + 1 );	
    	}
    	if ( priorCenter == null ) {
    		// Must be at the front, so track from NEXT back to THIS
    		normalizeEndSlice( pSliceNormal.set( thisCenter ).subtractLocal( nextCenter ) );
       	} else if ( nextCenter == null ) {
    		// Must be at the back, so track from THIS back to PRIOR
       		normalizeEndSlice( pSliceNormal.set( priorCenter ).subtractLocal( thisCenter ) );
    	} else {
    		// We may need a blend 
    		pSliceNormal.set( priorCenter ).subtractLocal( thisCenter ).normalizeLocal();
    		Vector3f otherNormal = nextCenter.clone().subtractLocal( thisCenter ).normalizeLocal();
    		
    		// Aligned normals will be 180 degrees / PI radians apart
    		float angleBetween = pSliceNormal.angleBetween( otherNormal );
    		angleBetween -= FastMath.PI;
    		if ( (angleBetween > 0.001f) || (angleBetween < -0.001f) ) {
    			// The normals are not in line, so we need some kind of blend
    			pSliceNormal.subtractLocal( otherNormal ).normalizeLocal();
    		}
    	}
    	// Each slice is defined in the x/y plane.  See if it has stayed there
    	if ( !Vector3f.UNIT_Z.equals( pSliceNormal ) ) {
    		// We must apply a rotation to the slice to match the spline
if ( false ) {
    	    float cos_theta = Vector3f.UNIT_Z.dot( pSliceNormal );
    	    float angle = FastMath.acos( cos_theta );
    		Vector3f anAxis = Vector3f.UNIT_Z.cross( pSliceNormal ).normalizeLocal();
    		sliceRotation = new Quaternion();
    		sliceRotation.fromAngleNormalAxis( angle, anAxis );
}   	    
/** FROM   http://lolengine.net/blog/2013/09/18/beautiful-maths-quaternion-from-vectors
    float cos_theta = dot(normalize(u), normalize(v));
    float angle = acos(cos_theta);
    vec3 w = normalize(cross(u, v));
    return quat::fromaxisangle(angle, w);
 */
    	    
if ( true ) {	// Looks like the following gets you the same results as above with fewer calculations....
    		float real_part = 1.0f + Vector3f.UNIT_Z.dot( pSliceNormal );
    		Vector3f aVector = Vector3f.UNIT_Z.cross( pSliceNormal );
    		sliceRotation = new Quaternion( aVector.x, aVector.y, aVector.z, real_part );
    		sliceRotation.normalizeLocal();
}
/** FROM   http://lolengine.net/blog/2014/02/24/quaternion-from-two-vectors-finalqa
 	// If you know you are exclusively dealing with unit vectors, you can replace all 
 	// occurrences of norm_u_norm_v with the value 1.0f in order to avoid a useless square root.
    float norm_u_norm_v = sqrt(dot(u, u) * dot(v, v));
    float real_part = norm_u_norm_v + dot(u, v);
    vec3 w;

    if (real_part < 1.e-6f * norm_u_norm_v)
    {
        // If u and v are exactly opposite, rotate 180 degrees
        // around an arbitrary orthogonal axis. Axis normalisation
        // can happen later, when we normalise the quaternion.
        real_part = 0.0f;
        w = abs(u.x) > abs(u.z) ? vec3(-u.y, u.x, 0.f)
                                : vec3(0.f, -u.z, u.y);
    }
    else
    {
        // Otherwise, build quaternion the standard way.
        w = cross(u, v);
    }
    return normalize(quat(real_part, w.x, w.y, w.z));
**/
    	}
    	if ( mSmoothSurface ) {
    		// If we plan on smoothing the resultant surface, we need to keep track of
    		// the 'plane' that defines every slice
    		// NOTE that we use a null plane as a placeholder in the list for the endcaps
    		CSGPlane slicePlane = (pSurface == 0) ? new CSGPlane( pSliceNormal, thisCenter ) : null;
    		
    		if ( pContext.mSlicePlanes == null ) pContext.mSlicePlanes = new ArrayList( pContext.mSliceCount );
    		pContext.mSlicePlanes.add( slicePlane );
    	}
    	return( sliceRotation );
    }
    
    /** Service routine to normalize and force an end slice normal to be 'perpendicular' */
    protected Vector3f normalizeEndSlice(
    	Vector3f		pEndCapNormal
    ) {
    	pEndCapNormal.normalizeLocal();
    	
    	if ( mPerpendicularEnds ) {
    		// Force this normal to the nearest perpendicular
    		pEndCapNormal.set( (float)Math.round( pEndCapNormal.x )
    						,	(float)Math.round( pEndCapNormal.y )
    						,	(float)Math.round( pEndCapNormal.z ) );
    	}
    	return( pEndCapNormal );
    }
    
    /** Support Pipe specific configuration parameters */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    	
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        if ( mSlicePath != null ) {
        	// Save it as a generated element
        	outCapsule.write( mSlicePath, "slicePath", null );
        }
        outCapsule.write( mPerpendicularEnds, "perpendicularEnds", false );
        outCapsule.write( mSmoothSurface, "smoothSurface", false );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing
        super.read( pImporter );

        InputCapsule inCapsule = pImporter.getCapsule( this );
        mSlicePath = (CSGSplineGenerator)inCapsule.readSavable( "slicePath", null );
        mPerpendicularEnds = inCapsule.readBoolean( "perpendicularEnds", false );
        mSmoothSurface = inCapsule.readBoolean( "smoothSurface",  false );

        // Standard trigger of updateGeometry() to build the shape 
        this.updateGeometry();
    }

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		if ( pBuffer.length() > 0 ) pBuffer.append( "\n" );
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGPipeRevision
													, sCSGPipeDate
													, pBuffer ) );
	}
}
/** Helper class for use during the geometry calculations */
class CSGPipeContext
	extends CSGRadialCappedContext
{
	/** List of points on the curve that define the centers */
	List<Vector3f>		mCenterList;
	/** Surface normal to the current slice */
	Vector3f			mSliceNormal;
	/** Planes associated with every slice */
	List<CSGPlane>		mSlicePlanes;
	/** Rotation to apply to the slice to match the spline */
	Quaternion			mSliceSplineRotation;
	
	
    /** Initialize the context */
    CSGPipeContext(
    	int							pAxisSamples
    ,	int							pRadialSamples
    ,	boolean						pClosed
    ,	CSGRadialCapped.TextureMode	pTextureMode
    ,	Vector2f					pScaleSlice
    ) {	
    	super( pAxisSamples, pRadialSamples, pClosed, pTextureMode, pScaleSlice );
    }
    
    /** How many slices are needed? */
    @Override
    protected int resolveSliceCount(
    	int			pAxisSamples
    ,	boolean		pIsClosed
    ) {
		// Even though the north/south poles are a single point, we need to 
		// generate different textures and normals for the two EXTRA end slices if closed
    	int sliceCount = (pIsClosed) ? pAxisSamples + 2 : pAxisSamples;
    	return( sliceCount );
    }

}

