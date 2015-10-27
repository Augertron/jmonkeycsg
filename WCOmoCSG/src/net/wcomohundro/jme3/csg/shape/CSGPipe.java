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

import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGPlane;
import net.wcomohundro.jme3.csg.CSGPlaneFlt;


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
 	slices can produce pipes of reasonable appearance. The smoothSurface option
 	attempts to smooth out such crumples, but it can only do so much (at a significant
 	processing cost when generating the mesh)
 	
 	Configuration Settings:
 		slicePath - 		an instance of CSGSplineGenerator that defines the path to follow.
 		
 		pipeEnds - 			adjust the slice-normal orientation of the end slices:
 							STANDARD - normal orientation based on the last point of the curve
 							PERPENDICULAR - force parallel/perpendicular to the xyz axes
 							PERPENDICULAR45 - force parallel/perpendicular/45deg to the xyz axes
 							CROPPED - ignore the curve end points EXCEPT to influence the slice-normal
 							
 		smoothSurface -		true/false if smoothing should be applied to the final surface, 
 							eliminating slices that may poke through other slices

 */
public class CSGPipe 
	extends CSGRadialCapped
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPipeRevision="$Rev$";
	public static final String sCSGPipeDate="$Date$";

	/** Variations of how the pipe ends are treated */
	public enum PipeEnds { 
		STANDARD			// End slice is generated 'normally', perpendicular to the last point of the curve
	, 	PERPENDICULAR		// End slice is generated perpendicular to the x/y/z axes
	,	PERPENDICULAR45		// End slice is generated perpendicular/40degree to the x/y/z axes
	, 	CROPPED 			// Curve end points do NOT produce a slice, they only influence the last slice normal
	}

	
	/** The splice that determines the positioning of the various slices */
	protected CSGSplineGenerator	mSlicePath;
	/** Control flag to force special processing on the ending slices */
	protected PipeEnds				mPipeEnds;
	/** Control flag to apply smoothing */
	protected boolean				mSmoothSurface;
	
	
	/** Standard null constructor */
	public CSGPipe(
	) {
		this( 32, 32, 1, 1, null, true, false, TextureMode.FLAT, false );
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
    ,	boolean		pReadyGeometry
    ) {
    	super( pAxisSamples, pRadialSamples, pRadiusFront, pRadiusBack, 0.0f, pClosed, pInverted, pTextureMode );
    	setSlicePath( pSlicePath );

    	if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
    }
    
    /** Accessor to the curve that provides the path of the slices */
    public Spline getSlicePath() { return (mSlicePath == null) ? null : mSlicePath.getSpline(); }
    public void setSlicePath( Spline pCurve ) { if ( mSlicePath != null ) mSlicePath.setSpline( pCurve ); }
    
    /** Accessor to the 'smooth surface' control flag */
    public boolean hasSmoothSurface() { return mSmoothSurface; }
    public void setSmoothSurface( boolean pFlag ) { mSmoothSurface = pFlag; }
    
    /** Accessor to the how the pipe ends are processed */
    public PipeEnds getPipeEnds() { return mPipeEnds; }
    public void setPipeEnds( PipeEnds pEnds ) { mPipeEnds = pEnds; }
    
    
	/** SUBCLASS OVERRIDE: Resolve this mesh, with possible 'debug' delegate representation */
    @Override
	public Mesh resolveMesh(
		boolean		pDebug
	) {
		if ( pDebug ) {
			// Display the spline in debug mode
			List<Vector3f> centerList = computeCenters();
			Spline aLine = new Spline( SplineType.Linear, centerList, 0, false );
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
    
    /** OVERRIDE: apply any 'smoothing' needed on the surface, trying to eliminate crumples.
     		We do this by walking through all the 'position' points on every slice. We look
     		to see if any given point is on the wrong side of the 'plane' of the previous
     		slice.  This means that the point is poking through the previous slice, which
     		then looks like crumple on the surface.
     		
     		If we find such a point, then we do not use that point, but rather the
     		corresponding point on the prior slice.  This ensures that nothing sticks through.
     		
     		@todo - look into a better solution than just using the point from the prior slice,
     				since this is likely to give us some flat triangles.  I have experimented
     				with using the point on the prior slice that is a projection of the real
     				point on the prior slice plane, but either my projection calculation is
     				incorrect, or it just doesn't look that good.
      */
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
    	CSGPlaneFlt basePlane = pContext.mSlicePlanes.get( pBaseIndex );
    	CSGPlaneFlt thisPlane = pContext.mSlicePlanes.get( pBaseIndex + pScanDirection );
    	
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
	    	// @todo - I think there may be a problem with the 'planes' if the slices are rotated.....
	    	CSGPlaneFlt overlappedPlane
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
     	
     	@return - null if there is no overlap, otherwise return the plane that has
     			  the overlap (typically pBasePlane, but possibly something deeper)
     */
    protected CSGPlaneFlt checkPointOverlap(
        CSGPipeContext 	pContext
    ,	CSGPlaneFlt		pThisPlane
    ,	Vector3f		pPoint
    ,	CSGPlaneFlt		pBasePlane
    ,	int				pBaseIndex
    ,	int				pScanDirection
    ) {
    	// Where is this point in relationship to the plane of the base slice?
    	int pointPlaneRelationship = pBasePlane.pointPosition( pPoint, EPSILON_ONPLANE_FLT );

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
        	CSGPlaneFlt priorPlane = pContext.mSlicePlanes.get( priorPlaneIndex );
        	CSGPlaneFlt overlappedPlane
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
    	
    	// And remember the curve itself could require more AxisSamples than what was requested.
     	// As a side effect of .computeCenters(), the mAxisSamples count may be adjusted.
		List<Vector3f> centerList = computeCenters();
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
    
    /** OVERRIDE: where is the center of the given slice
     		mCenterList is expected to have been prebuilt as part of the construction 
     		processing of CSGPipeContext.
     */
    @Override
    protected Vector3f getSliceCenter(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	CSGPipeContext myContext = (CSGPipeContext)pContext;
    	
    	// Return the center point as defined on the spline
    	// NOTE that mZOffset runs from back to front, but we computed the centers front to back
    	int firstIndex = 0;
    	int lastIndex = myContext.mCenterList.size() -1;
    	if ( this.mPipeEnds == PipeEnds.CROPPED ) {
    		// The end points of the curve are cropped off and are NOT true centers
    		firstIndex += 1;
    		lastIndex -= 1;
    	}
    	if ( pSurface < 0 ) {
    		// Back face, which shares the last point
    		pUseVector.set( myContext.mCenterList.get( lastIndex ) );
    	} else if ( pSurface > 0 ) {
    		// Front face, which shares the first point
    		pUseVector.set( myContext.mCenterList.get( firstIndex ) );
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
    		myContext.mSliceSplineRotation 
    			= computeSliceNormal( myContext.mSliceNormal, myContext, pSurface, pUseVector );
    	}
    	// By default, just operate on the unit circle (even on the endcaps)
    	CSGRadialCoord aCoord = pContext.mCoordList[ pContext.mRadialIndex ];
        pUseVector.set( aCoord.mCosine, aCoord.mSine, 0 );
        
        // Account for the actual radius
        pUseVector.multLocal( pContext.mSliceRadius );
        
        // Apply scaling
        if ( mScaleSlice != null ) {
        	pUseVector.multLocal( mScaleSlice.x, mScaleSlice.y, 1.0f );
        }
        // Apply individual slice rotation around the zAxis
    	if ( pContext.mSliceRotator != null ) {
    		pContext.mSliceRotator.multLocal( pUseVector );
    	}
    	// Apply any rotation required by the underlying spline
		// Remember that we are building the unit circle around the center point, 
		// so apply the 'curve' before we adjust the center, but after any scaling
    	if ( myContext.mSliceSplineRotation != null ) {
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
    	}
        // Account for the actual center
        pUseVector.addLocal( pContext.mSliceCenter );

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
		myContext.mSliceSplineRotation 
			= computeSliceNormal( myContext.mSliceNormal, myContext, pSurface, pUseVector );

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
    	pUseVector = super.getCenterNormal( pUseVector, pContext, pSurface );
    	
    	// Apply any rotation required by the underlying spline
    	if ( myContext.mSliceSplineRotation != null ) {
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
    	}
    	return( pUseVector );
    }

    /** Service routine that computes the center points based on the given spline 
     	NOTE that as a side effect, the mAxisSamples count may be adjusted.
     */
    protected List<Vector3f> computeCenters(
    ) {
    	// We need a center point for every sample taken along the zAxis
    	// If the ends are CROPPED, then we need 2 extra points
    	int sampleCount = this.mAxisSamples;
    	if ( this.mPipeEnds == PipeEnds.CROPPED ) {
    		// We will NOT be generating a slice for these extra points, but they will
    		// influence the sliceNormal of the end caps
    		sampleCount += 2;
    	}
    	List<Vector3f> centerList = mSlicePath.interpolate( sampleCount, 0 ); // Limit not active: , this.mRadius );

    	// Account for possibility that the curve (based on its count of segments) requires 
    	// more samples than we asked for
    	if ( centerList.size() > sampleCount ) {
			mAxisSamples = centerList.size();
			if ( this.mPipeEnds == PipeEnds.CROPPED ) {
				// The CROPPED end caps do not get a sample
				mAxisSamples -= 2;
			}
		}
    	return( centerList );
    }
    
    /** Service routine that computes the normal to the surface of the given slice,
     	and returns the quaternion to apply to the needed to rotate the slice.
      */
    protected Quaternion computeSliceNormal(
        Vector3f			pSliceNormal
    ,	CSGPipeContext 		pContext
    ,	int					pSurface
    ,	Vector3f			pTempVector
    ) {
    	int firstIndex = 0;
    	int lastIndex = pContext.mCenterList.size() -1;
    	if ( this.mPipeEnds == PipeEnds.CROPPED ) {
    		// The ends of the curve only influence the sliceNormal, they do not produce slices
    		firstIndex += 1;
    		lastIndex -= 1;
    	}
		Vector3f thisCenter, priorCenter = null, nextCenter = null;
		Quaternion sliceRotation;
		
		int centerIndex;
    	if ( pSurface < 0 ) {
    		// Back face, which shares the last point
    		centerIndex = lastIndex;
    	} else if ( pSurface > 0 ) {
    		// Front face, which shares the first point
    		centerIndex = firstIndex;
    	} else {
    		// Along the surface of the pipe 
    		// (where mZOffset acts like a simple counter, but from back to front)
    		centerIndex = lastIndex - pContext.mZOffset;
    	}
    	thisCenter = pContext.mCenterList.get( centerIndex );
    	if ( centerIndex > 0 ) {
        	priorCenter =  pContext.mCenterList.get( centerIndex -1 );        			
    	}
    	if ( centerIndex < pContext.mCenterList.size() -1 ) {
        	nextCenter = pContext.mCenterList.get( centerIndex + 1 );	
    	}
    	if ( priorCenter == null ) {
    		// Must be at the front, so track from NEXT back to THIS
    		normalizeEndSlice( pSliceNormal.set( thisCenter ).subtractLocal( nextCenter ) );
       	} else if ( nextCenter == null ) {
    		// Must be at the back, so track from THIS back to PRIOR
       		normalizeEndSlice( pSliceNormal.set( priorCenter ).subtractLocal( thisCenter ) );
    	} else {
    		// We may need a blend across the given 3 points
    		pSliceNormal.set( priorCenter ).subtractLocal( thisCenter ).normalizeLocal();
    		Vector3f otherNormal = nextCenter.clone().subtractLocal( thisCenter ).normalizeLocal();
    		
    		// Aligned normals will be 180 degrees / PI radians apart
    		float angleBetween = pSliceNormal.angleBetween( otherNormal );
    		angleBetween -= FastMath.PI;
    		if ( (angleBetween > 0.0001f) || (angleBetween < -0.0001f) ) {
    			// The normals are not in line, so we need some kind of blend
    			pSliceNormal.subtractLocal( otherNormal ).normalizeLocal();
    		}
    	}
    	// The generating radials are built in the x/y plane with z at zero, which
    	// means that the slice normal of the radial shape is, by defintion, the UNIT_Z axis.
    	// The tilt of the actual slice normal controls how the x/y slice must be rotated to match.
    	// So a change in z is reflected as a rotation around the y axis.
    	// A change in center height (y), is reflected as a rotation around the x axis.
    	// Since we build the radials in the x/y plane, then no rotation in z is ever needed.
    	float[] axisAngles = new float[3];

		pTempVector.set( pSliceNormal.x, 0, pSliceNormal.z ).normalizeLocal();
		if ( pTempVector.z == 1.0f ) {
    		// The slice is perpendicular to z, so no rotation is needed around y
    		axisAngles[1] = 0;
    		
    	} else if ( pTempVector.z == -1.0f ) {
    		// The slice is perpendicular to z, but 180deg around y
    		axisAngles[1] = FastMath.PI;
    		
    	} else {
    		// z represents the cosine of desired angle around y
    		axisAngles[1] = FastMath.acos( pTempVector.z );
    		if ( pSliceNormal.x < 0 ) {
    			// Negative x means we are 180deg off
    			axisAngles[1] = FastMath.TWO_PI - axisAngles[1];
    		}
    	}
    	if ( pSliceNormal.y != 0 ) {
    		// Any change of y represents a rotation around x
    		axisAngles[0] = FastMath.asin( -pSliceNormal.y );
    	} else {
    		axisAngles[0] = 0;
    	}
		sliceRotation = new Quaternion( axisAngles );

    	if ( mSmoothSurface ) {
    		// If we plan on smoothing the resultant surface, we need to keep track of
    		// the 'plane' that defines every slice
    		// NOTE that we use a null plane as a placeholder in the list for the endcaps
    		CSGPlaneFlt slicePlane = (pSurface == 0) ? new CSGPlaneFlt( pSliceNormal, thisCenter ) : null;
    		
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
    	
    	switch( mPipeEnds ) {
    		case PERPENDICULAR:
	    		// Force this normal to the nearest perpendicular
	    		pEndCapNormal.set( (float)Math.round( pEndCapNormal.x )
	    						,	(float)Math.round( pEndCapNormal.y )
	    						,	(float)Math.round( pEndCapNormal.z ) );
	    		break;
    		case PERPENDICULAR45:
    			float x = pEndCapNormal.x, y = pEndCapNormal.y, z = pEndCapNormal.z;
    			
    			if ( (x > 0.25) && (x < 0.75) ) { x = 0.5f;
    			} else if ( (x < -0.25) && (x > -0.75) ) { x = -0.5f;
    			} else { x = (float)Math.round( x ); }
    			
    			if ( (y > 0.25) && (y < 0.75) ) { y = 0.5f;
    			} else if ( (y < -0.25) && (y > -0.75) ) { y = -0.5f;
    			} else { y = (float)Math.round( y ); }
    			
    			if ( (z > 0.25) && (z < 0.75) ) { z = 0.5f;
    			} else if ( (z < -0.25) && (z > -0.75) ) { z = -0.5f;
    			} else { z = (float)Math.round( z ); }
    			
    			pEndCapNormal.set( x, y, z ).normalizeLocal();
    			break;
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
        outCapsule.write( mPipeEnds, "pipeEnds", PipeEnds.STANDARD );
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
        mPipeEnds = inCapsule.readEnum( "pipeEnds", PipeEnds.class, PipeEnds.STANDARD );
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
		return( CSGVersion.getVersion( this.getClass()
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
	List<CSGPlaneFlt>	mSlicePlanes;
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

