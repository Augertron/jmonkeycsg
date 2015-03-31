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
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGSphere.TextureMode;


/** Extend on the idea of producing a shape from a set of slices taken along the zAxis
 	by assuming some number of 'radial' points around the surface of each slice.
 	The basic assumption is that every slice is a circular disk of a given radius.
 	
 	Basic circular slices can be readily created by knowing the sine/cosine of each
 	radial sample point.  We maintain a cache of such values based on the count of 
 	radial points desired.  The assumption is that a given application is very likely to settle
 	on an appropriate count of radial samples that will be used over and over.
 	
 	.updateGeometry() provides a basic framework of iterating along the zAxis to 
 	produce a center point for each slice, and then iterating radially along the 
 	circle's surface to produce the vertices.
 	The structure CSGRadialContext holds all the dynamic elements computed during 
 	these iterations, and callback methods determine the details of the shape 
 	being generated.
 	
 	Subclasses are expected to override the callback processing routines.  Sample
 	defaults are provided just to get you started.  Subclasses may also extend the
 	context element to provide their own contextual definitions.
 	
 	Configuration Settings:
 	 	radialSamples - the count of sampling points around the circle.
 	 					= 3 produces a triangular shape
 	 					= 4 produces a square shape
 	 					= 5 produces a pentagonal shape
 	 					higher numbers look more and more circular
 	 					
        firstRadial - 	radian number of degrees of where the first point around the 
        			 	circle is started.  0 means East, PI/4 means NorthEast,
        			 	PI/2 means North, etc
        			 	
        radius -		the size of the radial circle
        
        scaleSliceX -	special size scaling to apply to every individual slice.
        scaleSliceY		(overall scaling of the Geometry that contains this shape
        				 does produce an elliptical rather than circular radial.
        				 However, the texture applied to each slice is then scaled as well.
        				 Applying scale to the individual slice preserves the original
        				 texture mapping.  IE - check out the endcaps of a cylinder)
        				 
        twist -			total amount of angular twist from the back surface to the
        				front surface, with an appropriate fractional amount applied
        				to each slice
 		
 */
public abstract class CSGRadial 
	extends CSGAxial
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGRadialRevision="$Rev$";
	public static final String sCSGRadialDate="$Date$";
	
	/** Cache of radial coordinates based on a radial count and starting angle.
	 	The key is a simple blend of the count and an integer representation of the angle 
	 */
	protected static Map<Integer,WeakReference<CSGRadialCoord[]>> sRadialCoordCache = new HashMap( 17 );
	protected static CSGRadialCoord[] getRadialCoordinates(
		int		pRadialSamples
	,	float	pFirstRadial
	) {
		// Look in the cache
		int aHash = (int)((100 * pFirstRadial) / FastMath.PI);
		Integer coordKey = new Integer( pRadialSamples + (aHash << 16) );
		WeakReference<CSGRadialCoord[]> aReference = sRadialCoordCache.get( coordKey );
		CSGRadialCoord[] coordList = (aReference == null) ? null : aReference.get();
		if ( coordList == null ) synchronized( sRadialCoordCache ) {
			// Not in the cache, so create it now 
			// (and it is not worth the effort to check the cache again once synchronized)
	        float inverseRadialSamples = 1.0f / pRadialSamples;
			coordList = new CSGRadialCoord[ pRadialSamples + 1 ];
			
			// Generate the coordinate values for each radial point
	        for( int iRadial = 0; iRadial < pRadialSamples; iRadial += 1 ) {
	            float anAngle = (FastMath.TWO_PI * inverseRadialSamples * iRadial) + pFirstRadial;
	            if ( anAngle > FastMath.TWO_PI ) anAngle -= FastMath.TWO_PI;
	            coordList[ iRadial ] = new CSGRadialCoord( anAngle );
	        }
	        // Include an extra point at the end that matches the starting point
	        coordList[ pRadialSamples ] = coordList[ 0 ];

	        // NOTE that we are willing to allow the key/weakref mapping to remain even
	        //		after the given coordinate list is reaped.  The assumption is that
	        //		once a list is in the cache, we are likely to use it again, and that
	        //		the overhead of a few extra Map.Entry is not worth the effort to 
	        //		monitor the reference queue or to run a harvester thread.
			sRadialCoordCache.put( coordKey, new WeakReference( coordList ) );
		}
		return( coordList );
	}

	/** How many samples to take take around the surface of each slice */
    protected int 			mRadialSamples;
    /** Which radial angle to start with */
    protected float			mFirstRadial;

    /** The base radius of the surface (which may well match the zExtent) */
    protected float 		mRadius;
    /** If closed, do the endcaps bulge out or are they flat? (subclass setting only, no accessors) */
    protected boolean		mFlatEnds;
    /** Per-slice scaling **/
    protected Vector2f		mScaleSlice;
    /** Per-slice across all the slices (the total angle spread across all the slices) */
    protected float		 	mRotateSlices;
    
	
    /** As abstract, it only makes sense to have protected constructors */
	public CSGRadial(
	) {
		this( 32, 32, 1, true, false, false );
	}
    protected CSGRadial(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadius
    ,	boolean		pClosed
    ,	boolean		pInverted
    ,	boolean		pFlatEnds
    ) {
    	// By default, treat the radius and zExtent the same
    	super( pAxisSamples, pRadius, pClosed, pInverted );
        mRadialSamples = pRadialSamples;
        mRadius = pRadius;
        mFlatEnds = pFlatEnds;
    }

    /** Configuration accessors */
    public int getRadialSamples() { return mRadialSamples; }
    public void setRadialSamples( int pRadialSamples ) { mRadialSamples = pRadialSamples; }
    
    public float getFirstRadial() { return mFirstRadial; }
    public void setFirstRadial( float pFirstRadial ) { mFirstRadial = pFirstRadial; }
    
    public float getRadius() { return mRadius; }
    public void setRadius( float pRadius ) { mRadius = pRadius; }
    
    public Vector2f getSliceScale() { return mScaleSlice; }
    public void setSliceScale( Vector2f pScaling ) { mScaleSlice = pScaling; }
    
    public float getSliceRotation() { return mRotateSlices; }
    public void setSliceRotation( float pTotalRotation ) { mRotateSlices = pTotalRotation; }
    
    
    /** Rebuilds the sphere based on the current set of configuration parameters
     
    	THIS IMPLEMENTATION PROVIDES A SAMPLE TO COPY AND APPLY SPECIALIZATIONS IN ANY GIVEN SUBCLASS
    	DO NOT EXPECT THIS IMPLEMENTATION TO PRODUCE AN APPROPRITE SHAPE
     */
    @Override
    public void updateGeometry(
    ) {
        // Allocate buffers for position/normals/texture
    	// We generate an extra vertex around the radial sample where the last
    	// vertex overlays the first.  
		// And even though the north/south poles are a single point, we need to 
		// generate different texture coordinates for the pole for each different 
		// radial, so we need a full set of vertices
    	int sliceCount = (mClosed) ? mAxisSamples + 2 : mAxisSamples;
    	CSGRadialContext aContext = getContext( sliceCount );
    	
    	int southPoleIndex = 0;
    	int northPoleIndex = createGeometry( aContext );

    	// There are 2 triangles for every radial point on every puck,
        // and there are AxisSamples -1 pucks
        int triCount = 2 * (mAxisSamples -1) * mRadialSamples;
        if ( mClosed ) {
        	// If the shape is closed, then there is one triangle for every radial point,
        	// but there are two ends
        	triCount += 2 * mRadialSamples;
        }
        createIndices( aContext, triCount, southPoleIndex, northPoleIndex, mFlatEnds );
        
        // Establish the bounds
        updateBound();
        setStatic();
	}

    /** Generic driver that rebuilds the shape from the current set of configuration parameters.
     	As a Radial/Axial combination, we are walking along the zAxis, generating a Radial 
     	set of vertices at each Axial slice, including the front/back end caps as needed.
     	
     	"flat" endcaps do not contribute to the height.  "non-flat" endcaps influence the
     	overall height processing.
     */
    protected int createGeometry(
    	CSGRadialContext	pContext
    ) {
    	// Establish the buffers
        setBuffer( Type.Position, 3, pContext.mPosBuf );
        setBuffer( Type.Normal, 3, pContext.mNormBuf );
        setBuffer( Type.TexCoord, 2, pContext.mTexBuf );

        // The percentage of each radial sample around the circle
        pContext.mInverseRadialSamples = 1.0f / mRadialSamples;
        
        // Compute the percentage of each 'uniform' slice down the zAxis.
        // Since we are ranging from -zExtent to +zExtent, we consider the unit
        // length to be 2.
        // If the end caps are flat, then only the actual samples consume space
        // along the zAxis.  But if the end caps are not flat, then they consume
        // zAxis space and must be figured in.
        pContext.mZAxisUniformPercent = 2.0f;
        if ( mFlatEnds ) {
        	// Only samples take up space, not the flat end caps
        	pContext.mZAxisUniformPercent /= (mAxisSamples -1);
        } else {
        	// Every slice, including the non-flat end caps consume space
        	pContext.mZAxisUniformPercent /= (pContext.mSliceCount - 1);
        }
        // Generate points on the unit circle to be used in computing the mesh
        // points on a sphere slice.
        pContext.mCoordList = getRadialCoordinates( mRadialSamples, mFirstRadial );

        // Avoid some object churn by using the thread-specific 'temp' variables
        TempVars vars = TempVars.get();
        pContext.mIndex = 0;
        int southPoleIndex = -1, northPoleIndex = -1;
        try {
	        // Iterate down the zAxis
        	pContext.mZOffset = 0;
	        for( int zIndex = 0; zIndex < pContext.mSliceCount; zIndex += 1 ) {
	        	// If closed, zIndex == 0 is the backface (-1), zIndex == sliceCount-1 is the frontface (1)
	        	int aSurface = 0;				// On the curve/crust
	        	if ( mClosed && (zIndex == 0) ) {
	        		// Southpole/Backface
	        		aSurface = -1;
	        		southPoleIndex = pContext.mIndex;
	        		
	        	} else if ( mClosed && (zIndex == pContext.mSliceCount -1) ) {
	        		// Northpole/Frontface
	        		aSurface = 1;
	        		northPoleIndex = pContext.mIndex;
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
	        	pContext.mSliceCenter = getSliceCenter( vars.vect2, pContext, aSurface );
	        	pContext.mSliceRadius = getSliceRadius( pContext, aSurface );
	            
	            // Ready the standard texture for this slice 
	        	// (this may let you optimize some texture calculations once for the slice
	        	//  with minor adjustments at each radial point on the slice)
	        	pContext.mSliceTexture = getSliceTexture( vars.vect2d, pContext, aSurface );
	        	
	        	// Any per-slice rotation?
	        	pContext.mSliceRotator = null;
	        	if ( mRotateSlices != 0 ) {
	        		// What rotation should be applied to this position along the z
	        		float rotateAngle = ((pContext.mZAxisFraction - 1.0f) / -2.0f) * mRotateSlices;
	        		pContext.mSliceRotator = (new Quaternion()).fromAngleNormalAxis( rotateAngle, Vector3f.UNIT_Z );
	        	}
	            // Compute slice vertices with duplication at end point
	            int iSave = pContext.mIndex;
	            for( pContext.mRadialIndex = 0; pContext.mRadialIndex < mRadialSamples; pContext.mRadialIndex += 1 ) {
	                // Where are we radially along the surface?
	            	pContext.mRadialFraction 
	            		= pContext.mRadialIndex * pContext.mInverseRadialSamples; // in [0,1)

	                // Where is this vertex?
	            	pContext.mPosVector = getRadialPosition( vars.vect1, pContext, aSurface );
	                pContext.mPosBuf.put( pContext.mPosVector.x )
	                				.put( pContext.mPosVector.y )
	                				.put( pContext.mPosVector.z );
	
	                // What is the normal for this position?
	                pContext.mNormVector = getRadialNormal( vars.vect4, pContext, aSurface );
	                pContext.mNormBuf.put( pContext.mNormVector.x )
	                				.put( pContext.mNormVector.y )
	                				.put( pContext.mNormVector.z );

	                // What texture applies?
	                pContext.mTexVector = getRadialTexture( vars.vect2d2, pContext, aSurface );            					
	                pContext.mTexBuf.put( pContext.mTexVector.x )
	                				.put( pContext.mTexVector.y );
	                pContext.mIndex += 1;
	            }
	            // Copy the first radial vertex to the end
	            BufferUtils.copyInternalVector3( pContext.mPosBuf, iSave, pContext.mIndex );
	            BufferUtils.copyInternalVector3( pContext.mNormBuf, iSave, pContext.mIndex );
	            
	            // Special texture processing at the end where pContext.mRadialIndex == mRadialSamples
                pContext.mTexVector = getRadialTexture( vars.vect2d2, pContext, aSurface );            					
                pContext.mTexBuf.put( pContext.mTexVector.x )
            					.put( pContext.mTexVector.y );

	            pContext.mIndex += 1;
	            if ( !mFlatEnds || (aSurface == 0) ) {
	            	// We have consumed z space
	            	pContext.mZOffset += 1;
	            }
	        }
        } finally {
        	// Return the borrowed vectors
        	vars.release();
        }
        return( northPoleIndex );
    }
    /** Service routine to allocate and set the index buffer */
    protected void createIndices(
    	CSGRadialContext	pContext
    ,	int					pTriangleCount
    ,	int					pSouthPoleIndex
    ,	int					pNorthPoleIndex
    ,	boolean				pSinglePoleVertex
    ) {
        // Allocate the indices, 3 points on every triangle
        ShortBuffer idxBuf = BufferUtils.createShortBuffer( 3 * pTriangleCount );
        setBuffer( Type.Index, 3, idxBuf );

        // Process along the axis, mapping the vertices of one slice to the slice in front of it
        for( int iZ = 0, iZStart = 0; iZ < pContext.mSliceCount -1; iZ++ ) {
            int i0 = iZStart;
            int i1 = i0 + 1;
            iZStart += (mRadialSamples + 1);
            int i2 = iZStart;
            int i3 = i2 + 1;
            
        	if ( mClosed && (iZ == 0) ) {
                // BackFace/SouthPole triangles
                for( int i = 0; i < mRadialSamples; i += 1 ) {
                	if ( pSinglePoleVertex ) {
                		// The single vertex at the pole is used over and over
                		// for the set of radial edge points
	                    if ( !mInverted ) {
	                        idxBuf.put( (short) i0++ );
	                        idxBuf.put( (short) pSouthPoleIndex );
	                        idxBuf.put( (short) i1++ );
	                    } else { // inside view
	                        idxBuf.put( (short) i0++ );
	                        idxBuf.put( (short) i1++ );
	                        idxBuf.put( (short) pSouthPoleIndex );
	                    }              		
                	} else {
                		// Multiple vertices are provided for the pole
            			// Remember that first cluster is the pole itself, and the next
            			// cluster is start of real data, so utilize i2/i3
	                    if ( !mInverted ) {
	                        idxBuf.put( (short) i2++ );
	                        idxBuf.put( (short) pSouthPoleIndex++ );
	                        idxBuf.put( (short) i3++ );
	                    } else { // inside view
	                        idxBuf.put( (short) i2++ );
	                        idxBuf.put( (short) i3++ );
	                        idxBuf.put( (short) pSouthPoleIndex++ );
	                    }
                	}
                }
        	} else if ( mClosed && (iZ == pContext.mSliceCount -2) ) {
                // FrontFace/NorthPole triangles
                for( int i = 0; i < mRadialSamples; i += 1 ) {
                	if ( pSinglePoleVertex ) {
                		// The single vertex at the pole is used over and over
                		// for the set of radial edge points
	                    if ( !mInverted ) {
	                        idxBuf.put( (short) i2++ );
	                        idxBuf.put( (short) i3++ );
	                        idxBuf.put( (short) pNorthPoleIndex );
	                    } else { // inside view
	                        idxBuf.put( (short) i2++ );
	                        idxBuf.put( (short) pNorthPoleIndex );
	                        idxBuf.put( (short) i3++ );
	                    }
                	} else {
                		// Multiple vertices are provided for the pole.
            			// Remember that the very last cluster is the pole itself, and the next
            			// to last is the final set of real data, so utilize i0/i1
	                    if ( !mInverted ) {
	                        idxBuf.put( (short) i0++ );
	                        idxBuf.put( (short) i1++ );
	                        idxBuf.put( (short) pNorthPoleIndex++ );
	                    } else { // inside view
	                        idxBuf.put( (short) i0++ );
	                        idxBuf.put( (short) pNorthPoleIndex++ );
	                        idxBuf.put( (short) i1++ );
	                    }
                	}
                }
        	} else {
        		// On the surface/crust
	            for( int i = 0; i < mRadialSamples; i++ ) {
	                if ( !mInverted ) {
	                    idxBuf.put( (short) i0++);
	                    idxBuf.put( (short) i1);
	                    idxBuf.put( (short) i2);
	                    
	                    idxBuf.put( (short) i1++);
	                    idxBuf.put( (short) i3++);
	                    idxBuf.put( (short) i2++);
	                    
	                } else { // inside view
	                    idxBuf.put( (short) i0++);
	                    idxBuf.put( (short) i2);
	                    idxBuf.put( (short) i1);
	                    
	                    idxBuf.put( (short) i1++);
	                    idxBuf.put( (short) i2++);
	                    idxBuf.put( (short) i3++);
	                }
	            }
        	}
        }
    }
    
    /** FOR SUBCLASS OVERRIDE: allocate the context */
    protected abstract CSGRadialContext getContext(
    	int		pSliceCount
    );
    
    /** FOR SUBCLASS OVERRIDE: fractional speaking, where are we along the z axis (+/-)*/
    protected float getZAxisFraction( 
    	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// By default, work even slices
    	if ( pSurface != 0 ) {
    		// On an endcap (either +/1 1.0)
    		return( (float)pSurface );
    	} else {
    		// How far along the total length are we?
    		float zAxisFraction = (pContext.mZAxisUniformPercent * (float)pContext.mZOffset) - 1.0f; // in (-1, 1)
    		return( zAxisFraction );
    	}
    }
    
    /** FOR SUBCLASS OVERRIDE: where is the center of the given slice */
    protected Vector3f getSliceCenter(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
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
    
    /** FOR SUBCLASS OVERRIDE: what is the radius of the Radial at this slice */
    protected float getSliceRadius(
        CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Since it is simplest, by default, we assume a constant radius (like a cylinder)
    	return( mRadius );
    }
    
    /** FOR SUBCLASS OVERRIDE: prepare the texture for the entire slice */
    protected Vector2f getSliceTexture(
    	Vector2f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// No reasonable default
    	pUseVector.set( Vector2f.ZERO );
    	return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: compute the position of a given radial vertex */
    protected Vector3f getRadialPosition(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Get the vector on the surface for this radial position
    	// By default, just operate on the unit circle
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
        // Apply rotation
    	if ( pContext.mSliceRotator != null ) {
    		pContext.mSliceRotator.multLocal( pUseVector );
    	}
        return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: compute the normal of a given radial vertex */
    protected Vector3f getRadialNormal(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// By default, just normalize the given position (which is the normal of a sphere)
    	pUseVector.set( pContext.mPosVector );
    	pUseVector.normalizeLocal();
    	
        // Apply scaling
        if ( mScaleSlice != null ) {
        	pUseVector.multLocal( mScaleSlice.x, mScaleSlice.y, 1.0f );
        }
        // Invert as needed
    	if ( mInverted ) {
    		// Invert the normal
    		pUseVector.multLocal( -1 );
    	}
    	return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: compute the texture of a given radial vertex */
    protected Vector2f getRadialTexture(
    	Vector2f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// No real default, just copy the slice
    	pUseVector.set( pContext.mSliceTexture );
    	
        // Apply scaling
        if ( mScaleSlice != null ) {
        	pUseVector.multLocal( mScaleSlice );
        }
    	return( pUseVector );
    }
    
    
    /** Support radial configuration parameters */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    	
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.write( mRadialSamples, "radialSamples", 32 );
        outCapsule.write( mFirstRadial, "firstRadial", 0 );
        outCapsule.write( mRadius, "radius", 1 );
        if ( mScaleSlice != null ) {
        	outCapsule.write( mScaleSlice.x, "scaleSliceX", 1.0f );
        	outCapsule.write( mScaleSlice.y, "scaleSliceY", 1.0f );
        }
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing (which will updateGeometry as needed)
        super.read( pImporter );
        
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mRadialSamples = inCapsule.readInt( "radialSamples", mRadialSamples );
        mFirstRadial = readPiValue( inCapsule, "firstRadial", 0 );
        mRadius = inCapsule.readFloat( "radius", mRadius );
        
        float scaleX = readPiValue( inCapsule, "scaleSliceX", 1.0f );
        float scaleY = readPiValue( inCapsule, "scaleSliceY", 1.0f );
        if ( (scaleX != 1.0f) || (scaleY != 1.0f) ) {
        	mScaleSlice = new Vector2f( scaleX, scaleY );
        }
        
        mRotateSlices = readPiValue( inCapsule, "twist", 0 );
    }
    
    /** Service routine to interpret a Savable.read() float value that can take the form
     		xxxPI/yyy
     	that supports fractional values of PI
     */
    protected float readPiValue(
    	InputCapsule	pCapsule
    ,	String			pValueName
    ,	float			pDefaultValue
    ) throws IOException {
    	float aFloatValue = pDefaultValue;
    	
    	// Read the value as a string so we can test for "PI"
        String piText = pCapsule.readString( pValueName, null );
        if ( piText != null ) {
        	// Something explicitly given, figure out what
        	piText = piText.toUpperCase();
        	int index = piText.indexOf( "PI" );
        	if ( index >= 0 ) {
        		// Decipher things like 3PI/4
        		int numenator = 1;
        		int denominator = 1;
        		if ( index > 0 ) {
        			// Some integer multiplier of PI
        			numenator = Integer.parseInt( piText.substring( 0, index ) );
        		}
        		index += 2;
        		if ( (index < piText.length() -1) 
        		&& (piText.charAt( index++ ) == '/') ) {
        			// Some integer divisor of PI
        			denominator = Integer.parseInt( piText.substring( index ) );
        		}
        		aFloatValue = ((float)numenator * FastMath.PI) / (float)denominator;
        	} else {
        		// If not PI, then its just a float
        		aFloatValue = Float.parseFloat( piText );
        	}
        }
    	return( aFloatValue );
    }
       
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		if ( pBuffer.length() > 0 ) pBuffer.append( "\n" );
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGRadialRevision
													, sCSGRadialDate
													, pBuffer ) );
	}
}

/** Helper class for sine/cosine values of a unit circle for a given 'radial' point */
class CSGRadialCoord
{
	/** Basic values */
	float		mSine;
	float		mCosine;
	
	/** Standard constructor */
	CSGRadialCoord(
		float	pSine
	,	float	pCosine
	) {
		mSine = pSine;
		mCosine = pCosine;
	}
	/** Construct based on an angle */
	CSGRadialCoord(
		float	pAngle
	) {
		mCosine = FastMath.cos( pAngle );
        mSine = FastMath.sin( pAngle );
	}
}

/** Helper class for use during the geometry calculations */
class CSGRadialContext
{
	int 				mSliceCount;			// How many slices are being processed
    int 				mVertCount;				// How many vertices are being generated
    int					mIndex;					// The current index within the buffers
    
    FloatBuffer 		mPosBuf;;				// Position points (3f)
    FloatBuffer 		mNormBuf;				// Normal points (3f)
    FloatBuffer 		mTexBuf;				// Texture points (2f)
    
    float 				mInverseRadialSamples;	// Percentage of each radial
    float 				mZAxisUniformPercent;	// Percentage of each slice along z (evenly spaced)
    
    CSGRadialCoord[]	mCoordList;				// Sine/Cosine coordinates based on count of radials
    
    float 				mAngleFraction;			// Distance along z as an angle  (-pi/2 : +pi/2)
    float 				mPolarFraction;			// Polar representation of z
    float 				mZAxisFraction;			// Percentage of actual current z (-1.0 : +1.0)
    float 				mZAxisAbsolute;			// Actual absolute z point
    
    Vector3f 			mSliceCenter;			// Center point of the active slice
    float 				mSliceRadius;			// Radius of this slice
    Vector2f			mSliceTexture;			// Texture base for entire slice
    Quaternion 			mSliceRotator;			// Rotation to be applied to this slice
    
    int					mZOffset;				// Counter running along the zAxis
    int					mRadialIndex;			// Counter running along the circular radial points
    float				mRadialFraction;		// Percentage of distance along the radial surface (0.0 : +1.0)
    
    Vector3f			mPosVector;				// The position vector of the current radial point
    Vector3f			mNormVector;			// The normal vector of the current radial point
    Vector2f			mTexVector;				// The texture vector of the current radial point
    
    /** Initialize the context */
    CSGRadialContext(
    	int		pSliceCount
    ,	int		pRadialSamples
    ,	int		pVertexCount
    ) {	
    	mSliceCount = pSliceCount;
    	mVertCount = pVertexCount;

    	mPosBuf = BufferUtils.createVector3Buffer( mVertCount );
        mNormBuf = BufferUtils.createVector3Buffer( mVertCount );
        mTexBuf = BufferUtils.createVector2Buffer( mVertCount );
    }
}
