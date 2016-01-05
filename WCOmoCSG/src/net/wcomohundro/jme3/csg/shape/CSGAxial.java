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
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector2f;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

import static com.jme3.util.BufferUtils.*;

import java.io.IOException;
import java.nio.FloatBuffer;

import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;



/** Abstract base class for those 'shapes' that produce their meshes by processing
 	a set of slices along the zAxis.  The span of the processing is determined by
 	the zExtent.  The shape runs from -zExtent to +zExtent, so the effective 'height'
 	of the shape is 2*zExtent.
 	
 	@see CSGMesh for a discussion of the 'faces' of a shape.  For CSGAxial, 
 	'front' and 'back' are the common elements.  The front face is known as the
 	northpole, and the back face is known as the southpole.
 	
 	An axial is considered to be 'closed' if front/back endcaps are generated 
 	for the shape.  The end caps are either 'flat' (they have zero z extent) or not
 	(they have a given z extent), as determined by the appropriate subclass.
 	
 	The structure CSGAxialContext holds all the dynamic elements computed during 
 	the iterations along Z, and callback methods determine the details of the shape 
 	being generated.  In particular, there are various ways of measuring how far along Z
 	the process has gone.
 */
public abstract class CSGAxial 
	extends CSGMesh
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGAxialRevision="$Rev$";
	public static final String sCSGAxialDate="$Date$";


	/** How many sample slices to generate along the axis */
    protected int 			mAxisSamples;
    /** How tall is the shape along the z axis (where the 'extent' is half the total height) */
    protected float 		mExtentZ;
    /** If closed, do the endcaps bulge out or are they flat? (subclass setting only, no accessors) */
    protected boolean		mFlatEnds;


	protected CSGAxial(
	) {
		this( 32, 1, true, false );
	}
    protected CSGAxial(
    	int 		pAxisSamples
    , 	float 		pZExtent
    ,	boolean		pClosed
    ,	boolean		pInverted
    ) {
        mAxisSamples = pAxisSamples;
        mExtentZ = pZExtent;
        setClosed( pClosed );
        setInverted( pInverted );
    }

    /** Configuration accessors */
    public int getAxisSamples() { return mAxisSamples; }
    public void setAxisSamples( int pSampleCount ) { mAxisSamples = pSampleCount; }
    
    public float getZExtent() { return mExtentZ; }
    public void setZExtent( float pZExtent ) { mExtentZ = pZExtent; }
    
    public float getHeight() { return mExtentZ * 2; }
    public void setHeight( float pHeight ) { mExtentZ = pHeight / 2.0f; }
    
    /** An Axial is considered 'closed' if the front/back faces are generated */
    public boolean isClosed(
    ) { 
    	return( (mGeneratedFacesMask & Face.FRONT_BACK.getMask()) == Face.FRONT_BACK.getMask() ); 
    }
    public void setClosed( 
    	boolean pIsClosed 
    ) { 
    	if ( pIsClosed ) {
    		mGeneratedFacesMask |= Face.FRONT_BACK.getMask();
    	} else {
    		mGeneratedFacesMask &= ~Face.FRONT_BACK.getMask();
    	}
    }
    
    /** Rebuilds the axial shape based on the current set of configuration parameters
    
****    	THIS IMPLEMENTATION PROVIDES A SAMPLE TO COPY AND APPLY SPECIALIZATIONS IN ANY GIVEN SUBCLASS ****
****    	DO NOT EXPECT THIS IMPLEMENTATION TO PRODUCE AN APPROPRITE SHAPE							  ****
     */
    @Override
    protected void updateGeometryProlog(
    ) {
/*****
    	CSGAxialContext aContext = getContext( true );
    	
    	int southPoleIndex = 0;
    	int northPoleIndex = createGeometry( aContext );

    	// Establish the IndexBuffer which maps the vertices to use
        VertexBuffer idxBuffer 
        	= createIndices( aContext, 0.0f, southPoleIndex, northPoleIndex, mFlatEnds );
        setBuffer( idxBuffer );
        
        if ( mLODFactors != null ) {
        	// Various Levels of Detail, with the zero slot being the master
        	VertexBuffer[] levels = new VertexBuffer[ mLODFactors.length + 1 ];
        	levels[0] = idxBuffer;
        	
        	// Generate each level
        	for( int i = 0, j = mLODFactors.length; i < j; i += 1 ) {
        		idxBuffer 
            		= createIndices( aContext, mLODFactors[i], southPoleIndex, northPoleIndex, mFlatEnds );
        		levels[ i + 1 ] = idxBuffer;
        	}
        	this.setLodLevels( levels );
        }
        // Establish the bounds
        updateBound();
        setStatic();
*****/
	}
    
        
    /** Generic driver that rebuilds the shape from the current set of configuration parameters.
	 	As an Axial, we are walking along the zAxis, generating a set of vertices at each 
	 	Axial slice, including the front/back end caps as needed.
	 	
	 	"flat" endcaps do not contribute to the height.  "non-flat" endcaps influence the
	 	overall height processing.
	 */
	protected int createGeometry(
		CSGAxialContext	pContext
	) {
		// Establish the buffers
	    setBuffer( Type.Position, 3, pContext.mPosBuf );
	    if ( pContext.mNormBuf != null ) setBuffer( Type.Normal, 3, pContext.mNormBuf );
	    if ( pContext.mTexBuf != null ) setBuffer( Type.TexCoord, 2, pContext.mTexBuf );
	
	    // Compute the percentage of each 'uniform' slice down the zAxis.
	    // Since we are ranging from -zExtent to +zExtent, we consider the unit
	    // length to be 2.
	    // If the end caps are flat, then only the actual samples consume space
	    // along the zAxis.  But if the end caps are not flat, then they consume
	    // zAxis space and must be figured in.
	    pContext.mZAxisUniformPercent = 2.0f;
	    pContext.mIndex = 0;
		pContext.mZOffset = 0;
		
	    if ( mFlatEnds ) {
	    	// Only samples take up space, not the flat end caps
	    	pContext.mZAxisUniformPercent /= (mAxisSamples -1);
	    } else {
	    	// Every slice, including the non-flat end caps consume space if they
	    	// are in use
	    	int countBias = 1;
	    	if ( Face.BACK.maskIncludesFace( mGeneratedFacesMask ) ) {
	    		// Closed at the back, so account for space along z
	    		countBias -= 1;
	    	} else {
	    		// No back face, so skip the first Z offset
	    		pContext.mZOffset = 1;
	    	}
	    	if ( Face.FRONT.maskIncludesFace( mGeneratedFacesMask ) ) {
	    		// Closed at the front, so account for space along z
	    		countBias -= 1;
	    	}
	    	// How far along Z we take with each step
	    	pContext.mZAxisUniformPercent /= (pContext.mSliceCount + countBias );
	    }
	    // Pregenerate any points that can be used repeatedly in the process.
	    readyCoordinates( pContext );
	
	    // Avoid some object churn by using the thread-specific 'temp' variables
	    TempVars vars = TempVars.get();
	    int southPoleIndex = -1, northPoleIndex = -1;
	    try {
	        // Iterate down the zAxis
	        for( int zIndex = 0; zIndex < pContext.mSliceCount; zIndex += 1 ) {
	        	// If closed, zIndex == 0 is the backface (-1), zIndex == sliceCount-1 is the frontface (1)
	        	int aSurface = 0;					// On the curve/crust
	        	int zOffsetAdjust = 1;				// Account for change of z offset
	        	if ( Face.BACK.maskIncludesFace( mGeneratedFacesMask ) && (zIndex == 0) ) {
	        		// Southpole/Backface
	        		aSurface = -1;
	        		southPoleIndex = pContext.mIndex;
	        		
	        		if ( mFlatEnds ) zOffsetAdjust = 0;
	        		
	        	} else if ( Face.FRONT.maskIncludesFace( mGeneratedFacesMask ) && (zIndex == pContext.mSliceCount -1) ) {
	        		// Northpole/Frontface
	        		aSurface = 1;
	        		northPoleIndex = pContext.mIndex;
	        		
	        		if ( mFlatEnds ) zOffsetAdjust = 0;
	        	}
	        	// Angle based on where we are along the zAxis
	        	pContext.mAngleFraction
	        		= FastMath.HALF_PI * ((pContext.mZAxisUniformPercent * (float)pContext.mZOffset) - 1.0f); // in (-pi/2, pi/2)
	        	pContext.mPolarFraction 
	        		= (FastMath.HALF_PI - FastMath.abs( pContext.mAngleFraction )) / FastMath.PI;
	
	            // Position along zAxis as a +/- percentage  (-1 / +1)
	        	// NOTE that the implementation of getZAxisFraction is free to apply non-uniform
	        	//		spacing between the slices.
	        	pContext.mZAxisFraction = getZAxisFraction( pContext, aSurface );
	            // Absolute position along the zAxis
	        	pContext.mZAxisAbsolute = mExtentZ * pContext.mZAxisFraction;
	
	            // Where is the slice
	        	pContext.mSliceCenter = getSliceCenter( pContext, aSurface, vars.vect2, vars );
	            	        	
	        	// Process all the points on the slice
	        	createSlice( pContext, aSurface, vars );
	        	
	            pContext.mIndex += 1;
	            pContext.mZOffset += zOffsetAdjust;
	        }
	        // Apply any smoothing that may be needed
	        smoothSurface( pContext, vars );
	        
	    } finally {
	    	// Return the borrowed vectors
	    	vars.release();
	    }
	    return( northPoleIndex );
	}
	
    /** FOR SUBCLASS OVERRIDE: allocate the context */
    protected abstract CSGAxialContext getContext(
    	boolean		pAllocateBuffers
    );
    
    /** FOR SUBCLASS OVERRIDE: ready axial coordinates */
    protected void readyCoordinates(
    	CSGAxialContext		pContext
    ) {
    }
    
    /** FOR SUBCLASS OVERRIDE: fractional speaking, where are we along the z axis (+/-)*/
    protected float getZAxisFraction( 
    	CSGAxialContext 	pContext
    ,	int					pSurface
    ) {
    	// By default, work even slices
    	if ( pSurface != 0 ) {
    		// On an endcap (either +/- 1.0)
    		return( (float)pSurface );
    	} else {
    		// How far along the total length are we?
    		float zAxisFraction = (pContext.mZAxisUniformPercent * (float)pContext.mZOffset) - 1.0f; // in (-1, 1)
    		return( zAxisFraction );
    	}
    }
    
    /** FOR SUBCLASS OVERRIDE: where is the center of the given slice */
    protected Vector3f getSliceCenter(
    	CSGAxialContext 	pContext
    ,	int					pSurface
	,	Vector3f			pUseVector
    ,	TempVars 			pTempVars
    ) {
    	// By default, the center is ON the zAxis at the given absolute z position 
    	if ( pSurface != 0 ) {
    		// Sitting on an end cap
    		pUseVector.set( 0, 0, mExtentZ * (float)pSurface );
    	} else {
    		// Follow along on the zAxis
    		pUseVector.set( 0, 0, pContext.mZAxisAbsolute );
    	}
    	return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: create the elements of a given slice */
    protected abstract void createSlice(
        CSGAxialContext 	pContext
    ,	int					pSurface
    ,	TempVars			pTempVars
    );

    
    /** FOR SUBCLASS OVERRIDE: apply any 'smoothing' needed on the surface */
    protected void smoothSurface(
    	CSGAxialContext		pContext
    ,	TempVars			pTempVars
    ) {
    }
    
    
    /** Support texture scaling */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    	
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.write( mAxisSamples, "axisSamples", 32 );
        outCapsule.write( mExtentZ, "zExtent", 1 );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing
        super.read( pImporter );
        
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mAxisSamples = inCapsule.readInt( "axisSamples", mAxisSamples );
        
        // Let each shape apply its own zExtent default
        mExtentZ = inCapsule.readFloat( "zExtent", 0 );
        if ( mExtentZ == 0 ) {
        	float aHeight = inCapsule.readFloat( "height", 0 );
        	if ( aHeight > 0 ) {
        		// An explicit height sets the zExtent
        		mExtentZ = aHeight / 2.0f;
        	}
        }
    }
        
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( CSGAxial.class
													, sCSGAxialRevision
													, sCSGAxialDate
													, pBuffer ) );
	}

}
/** Helper class for use during the geometry calculations */
abstract class CSGAxialContext
{
	int 				mSliceCount;			// How many slices are being processed
	int					mSliceReductionFactor;	// For LOD - multiplier to skip slices
    int 				mVertCount;				// How many vertices are being generated
    int					mIndex;					// The current index within the buffers
    
    FloatBuffer 		mPosBuf;;				// Position points (3f)
    FloatBuffer 		mNormBuf;				// Normal points (3f)
    FloatBuffer 		mTexBuf;				// Texture points (2f)
    
    int					mZOffset;				// Counter running along the zAxis
    float 				mZAxisUniformPercent;	// Percentage of each slice along z (evenly spaced)
    float 				mAngleFraction;			// Distance along z as an angle  (-pi/2 : +pi/2)
    float 				mPolarFraction;			// Polar representation of z
    float 				mZAxisFraction;			// Percentage of actual current z (-1.0 : +1.0)
    float 				mZAxisAbsolute;			// Actual absolute z point
    
    Vector3f 			mSliceCenter;			// Center point of the active slice
    Vector2f			mSliceTexture;			// Texture base for entire slice
    
    Vector3f			mPosVector;				// The position vector of the current radial point
    Vector3f			mNormVector;			// The normal vector of the current radial point
    Vector2f			mTexVector;				// The texture vector of the current radial point
    
    /** Initialize the context */
    protected void initializeContext(
    	int		pAxisSamples
    ,	int		pRadialSamples
    ,	int		pGeneratedFacesMask
    ,	boolean	pAllocateBuffers
    ) {	
    	mSliceCount = resolveSliceCount( pAxisSamples, pGeneratedFacesMask );
    	mVertCount = resolveVetexCount( mSliceCount, pRadialSamples, pGeneratedFacesMask );

    	if ( pAllocateBuffers ) {
	    	mPosBuf = BufferUtils.createVector3Buffer( mVertCount );
	        mNormBuf = BufferUtils.createVector3Buffer( mVertCount );
	        mTexBuf = BufferUtils.createVector2Buffer( mVertCount );
    	}
    }
    
    /** How many slices are needed? */
    protected abstract int resolveSliceCount(
    	int			pAxisSamples
    ,	int			pGeneratedFacesMask
    );
    
    /** How many vertices are produced? */
    protected abstract int resolveVetexCount(
    	int			pSliceCount
    ,	int			pRadialSamples
    ,	int			pGeneratedFacesMask
    );
    
    /** Establish the reduction factors for LOD processing, returning the count of triangles 
     	produced at this level of reduction
     */
    int setReductionFactors(
    	float		pLODFactor
    ,	int			pAxisSamples
    ,	int			pRadialSamples
    ,	int			pGeneratedFacesMask
    ) {
    	// Account for reduction to slices and radial points
    	mSliceReductionFactor = 0;
    	
    	// There are 2 triangles for every radial point on every puck,
        // and there are AxisSamples -1 pucks (1 puck defined by 2 slices)
    	int useAxisSamples = pAxisSamples;
    	
    	if ( pLODFactor > 0.0f ) {
    		// Reduce the number of slices by 'skipping' past selected slices (every second, every third...)
    		// while we produce the Index buffer
    		if ( pLODFactor > 0.5f ) {
    			// Minimal 'skipping' is every other, so if you want some special LOD, then you get
    			// a 50% reduction no matter what
    			pLODFactor = 0.5f;
    		}
    		float reducedSliceCount = pAxisSamples * pLODFactor;
    		mSliceReductionFactor = (int)(pAxisSamples / reducedSliceCount);
    		
    		if ( mSliceReductionFactor > 1 ) {
	    		// Account for final slice
	    		useAxisSamples = (pAxisSamples / mSliceReductionFactor) + 1;
	    		if ( (pAxisSamples % mSliceReductionFactor) > 0 ) {
	    			// Not an even multiple of the 'factor' so we will add an extra short slice to the end
	    			useAxisSamples += 1;
	    		}
    		}
    	}
        int triCount = 2 * (useAxisSamples -1) * pRadialSamples;
        if ( Face.FRONT.maskIncludesFace( pGeneratedFacesMask ) ) {
        	// If the shape is closed, then there is one triangle for every radial point,
        	triCount += pRadialSamples;
        }
        if ( Face.BACK.maskIncludesFace( pGeneratedFacesMask ) ) {
        	// If the shape is closed, then there is one triangle for every radial point,
        	triCount += pRadialSamples;
        }
        return( triCount );
    }
    
    /** For a given slice, how many slices to skip to during LOD processing */
    int nextSliceFactor(
    	int		pZIndex
    ,	int		pGeneratedFacesMask
    ) {
    	// If closed, zIndex == 0 is the backface (-1), zIndex == sliceCount-2 is the frontface (1)
    	if ( Face.BACK.maskIncludesFace( pGeneratedFacesMask ) && (pZIndex == 0) ) {
    		// Southpole/Backface does not reduce
    		return( 1 );
    		
    	} else if ( Face.FRONT.maskIncludesFace( pGeneratedFacesMask ) && (pZIndex == mSliceCount -2) ) {
    		// Northpole/Frontface does not reduce
    		return( 1 );
    	}
    	if ( mSliceReductionFactor > 1 ) {
    		// We cannot skip past the very last slice
    		int closedBias = (Face.FRONT.maskIncludesFace( pGeneratedFacesMask )) ? 2 : 1;
    		if ( pZIndex + mSliceReductionFactor > mSliceCount - closedBias ) {
    			// Count of slices left to the last one
    			return( mSliceCount - pZIndex - closedBias );
    			
    		} else {
    			// Skip some slices
    			return( mSliceReductionFactor );
    		}
    	} else {
    		// No slices are being ignored
    		return( 1 );
    	}
    }
}

