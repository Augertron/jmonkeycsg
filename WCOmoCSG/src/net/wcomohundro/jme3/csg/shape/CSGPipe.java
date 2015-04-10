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
import static com.jme3.util.BufferUtils.*;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.ArrayList;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGRadialCapped.TextureMode;


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
	protected Spline	mSlicePath;
	
	
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
    public Spline getSlicePath() { return mSlicePath; }
    public void setSlicePath( Spline pCurve ) { mSlicePath = pCurve; }
    
	/** FOR POSSIBLE SUBCLASS OVERRIDE: Resolve this mesh, with possible 'debug' delegate representation */
	public Mesh resolveMesh(
		boolean		pDebug
	) {
		if ( pDebug ) {
			// Display the spline
			Curve aCurve = new Curve( mSlicePath, mAxisSamples / mSlicePath.getControlPoints().size() );
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
    		mSlicePath = new Spline( SplineType.Linear, endPoints, 0, false );
    	} else {
    		// Use the length of the spline as the z extent
    		mExtentZ = mSlicePath.getTotalLength() / 2.0f;
    	}
        // Standard 'capped' construction
    	super.updateGeometryProlog();   	
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
    	int sliceCount = (mClosed) ? mAxisSamples + 2 : mAxisSamples;
    	CSGPipeContext aContext 
    		= new CSGPipeContext( sliceCount, mRadialSamples, mClosed, mTextureMode, mScaleSlice );

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
        
        // Account for the center
        pUseVector.addLocal( pContext.mSliceCenter );

        // Apply scaling
        if ( mScaleSlice != null ) {
        	pUseVector.multLocal( mScaleSlice.x, mScaleSlice.y, 1.0f );
        }
        // Apply individual slice rotation around the zAxis
    	if ( pContext.mSliceRotator != null ) {
    		pContext.mSliceRotator.multLocal( pUseVector );
    	}
    	// Apply any rotation required by the underlying spline
    	if ( myContext.mSliceSplineRotation != null ) {
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
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

    	// Start with the center
    	pUseVector.set( pContext.mSliceCenter );
    	
    	// Apply any rotation required by the underlying spline
    	if ( myContext.mSliceSplineRotation != null ) {
    		myContext.mSliceSplineRotation.multLocal( pUseVector );
    	}
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
    	List<Vector3f> centerList = new ArrayList( this.mAxisSamples );
    	
    	// We will try to assign center points evenly along the curve, but we
    	// will base it on the lengths of the various segments
    	int sampleCount = 0;
    	float fullLength = mSlicePath.getTotalLength();
    	List<Float> allSegmentLengths = mSlicePath.getSegmentsLength();
    	int segmentCount = allSegmentLengths.size() -1;
    	
    	// We assume that there is one more 'control point' than there are segments,
    	// so the last center point is always special
    	for( int i = 0; i <= segmentCount; i += 1 ) {
    		float thisSegmentPortion = allSegmentLengths.get( i ).floatValue() / fullLength;
    		int thisSegmentSamples = (int)(this.mAxisSamples * thisSegmentPortion);
    		if ( thisSegmentSamples == 0 ) {
    			// Every segment gets at least one sample
    			thisSegmentSamples = 1;
    		}
    		// Accommodate the 'slop' in the last segment, always leaving out the last
    		// @todo - think about spreading the slop around
    		if ( i == segmentCount ) {
    			// Adjust the samples we take in the last segment
    			// Since we are truncating, and not rounding, we should not have to worry
    			// about having too many samples, only too few
    			thisSegmentSamples = this.mAxisSamples - sampleCount -1;
    		}
    		sampleCount += thisSegmentSamples;

    		// Generate samples within this segment
    		for( int j = 0; j < thisSegmentSamples; j += 1 ) {
    			float percentWithinSegment = 0.0f;
    			if ( j > 0 ) percentWithinSegment = (float)j / (float)thisSegmentSamples;
    			Vector3f centerPoint = mSlicePath.interpolate( percentWithinSegment, i, null );
    			centerList.add( centerPoint );
    		}
    		if ( i == segmentCount ) {
    	    	// The last sample comes from the final control point (100% from the current to the next)
    			Vector3f centerPoint = mSlicePath.interpolate( 1.0f, i, null );
    			centerList.add( centerPoint );   			
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
    ) {
    	int lastIndex = pContext.mCenterList.size() -1;
		Vector3f thisCenter, priorCenter = null, nextCenter = null;
		Quaternion sliceRotation = null;
		
		int centerIndex;
    	if ( pSurface < 0 ) {
    		// Back face, which shares the last point
    		centerIndex = lastIndex;
    		//thisCenter =  pContext.mCenterList.get( lastIndex );
    		//priorCenter =  pContext.mCenterList.get( lastIndex -1 );
    	} else if ( pSurface > 0 ) {
    		// Front face, which shares the first point
    		centerIndex = 0;
    		//thisCenter = pContext.mCenterList.get( 0 );
    		//nextCenter = pContext.mCenterList.get( 1 );
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
    		pSliceNormal.set( thisCenter ).subtractLocal( nextCenter ).normalizeLocal();
       	} else if ( nextCenter == null ) {
    		// Must be at the back, so track from THIS back to PRIOR
    		pSliceNormal.set( priorCenter ).subtractLocal( thisCenter ).normalizeLocal();
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
    	return( sliceRotation );
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
        	CSGSplineGenerator aPath = new CSGSplineGenerator( mSlicePath );
        	outCapsule.write( aPath, "slicePath", null );
        }
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing
        super.read( pImporter );

        InputCapsule inCapsule = pImporter.getCapsule( this );
        CSGSplineGenerator aPath = (CSGSplineGenerator)inCapsule.readSavable( "slicePath", null );
        if ( aPath != null ) {
        	mSlicePath = aPath.getSpline();
        }
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
	/** Rotation to apply to the slice to match the spline */
	Quaternion			mSliceSplineRotation;
	
	
    /** Initialize the context */
    CSGPipeContext(
    	int							pSliceCount
    ,	int							pRadialSamples
    ,	boolean						pClosed
    ,	CSGRadialCapped.TextureMode	pTextureMode
    ,	Vector2f					pScaleSlice
    ) {	
    	super( pSliceCount, pRadialSamples, pClosed, pTextureMode, pScaleSlice );
    }

}

