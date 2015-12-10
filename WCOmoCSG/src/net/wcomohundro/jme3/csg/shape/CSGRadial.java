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
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;
import net.wcomohundro.jme3.csg.shape.CSGSphere.TextureMode;


/** Extend on the idea of producing a shape from a set of slices taken along the zAxis
 	by assuming some number of 'radial' points around the surface of each slice.
 	The basic assumption is that every slice is a circular disk of a given radius.
 	
 	Basic circular slices can be readily created by knowing the sine/cosine of each
 	radial sample point.  We maintain a cache of such values based on the count of 
 	radial points desired.  The assumption is that a given application is very likely to settle
 	on an appropriate count of radial samples that will be used over and over.
 	
 	.updateGeometryProlog() provides a basic framework of iterating along the zAxis to 
 	produce a center point for each slice, and then iterating radially along the 
 	circle's surface to produce the vertices.
 	The structure CSGRadialContext holds all the dynamic elements computed during 
 	these iterations, and callback methods determine the details of the shape 
 	being generated.
 	
 	Subclasses are expected to override the callback processing routines.  Sample
 	defaults are provided just to get you started.  Subclasses may also extend the
 	context element to provide their own contextual definitions.
 	
 	Interesting effects are produced by 'scaling' each individual slice.
 	Interesting effects are produced by 'rotating' each individual slice.
 	
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
    /** Per-slice scaling **/
    protected Vector2f		mScaleSlice;
    /** Per-slice rotation across all the slices (the total angle spread across all the slices) */
    protected float		 	mRotateSlices;
    /** Track which triangle is the first of the front face
     	NOTE  that zero is the first of the back face, and mRadialSamples is the first of the 
     		  side faces if closed.  */
    protected int			mFirstFrontTriangle;
    
	
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
    
    
    /** OVERRIDE: ready the coordinates */
    @Override
    protected void readyCoordinates(
    	CSGAxialContext		pContext
    ) {
    	CSGRadialContext aContext = (CSGRadialContext)pContext;

    	// The percentage of each radial sample around the circle
        aContext.mInverseRadialSamples = 1.0f / mRadialSamples;

        // Generate points on the unit circle to be used in computing the mesh
        // points on a sphere slice.
        aContext.mCoordList = getRadialCoordinates( mRadialSamples, mFirstRadial );
    }
    
    /** OVERRIDE: where is the center of the given slice */
    @Override
    protected Vector3f getSliceCenter(
    	CSGAxialContext 	pContext
    ,	int					pSurface
    ,	Vector3f			pUseVector
    ,	TempVars			pTempVars
    ) {
    	CSGRadialContext aContext = (CSGRadialContext)pContext;
    	
    	// Standard calculation
    	Vector3f aCenter = super.getSliceCenter( pContext, pSurface, pUseVector, pTempVars );
    	
    	// Take this opportunity to compute the radius as well
    	aContext.mSliceRadius = getSliceRadius( (CSGRadialContext)pContext, pSurface );
    	
        // Ready the standard texture for this slice 
    	// (this may let you optimize some texture calculations once for the slice
    	//  with minor adjustments at each radial point on the slice)
    	aContext.mSliceTexture = getSliceTexture( pTempVars.vect2d, aContext, pSurface );

    	return( aCenter );
    }

    /** OVERRIDE: create the elements of a given slice */
    @Override
    protected void createSlice(
        CSGAxialContext 	pContext
    ,	int					pSurface
    ,	TempVars			pTempVars
    ) {
    	CSGRadialContext aContext = (CSGRadialContext)pContext;
    	
    	// Any per-slice rotation?
    	aContext.mSliceRotator = null;
    	if ( mRotateSlices != 0 ) {
    		// What rotation should be applied to this position along the z
    		float rotateAngle = ((pContext.mZAxisFraction - 1.0f) / -2.0f) * mRotateSlices;
    		aContext.mSliceRotator = (new Quaternion()).fromAngleNormalAxis( rotateAngle, Vector3f.UNIT_Z );
    	}
        // Compute slice vertices with duplication at end point
        int iSave = pContext.mIndex;
        for( aContext.mRadialIndex = 0; aContext.mRadialIndex < mRadialSamples; aContext.mRadialIndex += 1 ) {
            // Where are we radially along the surface?
        	aContext.mRadialFraction 
        		= aContext.mRadialIndex * aContext.mInverseRadialSamples; // in [0,1)

            // Where is this vertex?
        	pContext.mPosVector = getRadialPosition( pTempVars.vect1, aContext, pSurface );
            pContext.mPosBuf.put( pContext.mPosVector.x )
            				.put( pContext.mPosVector.y )
            				.put( pContext.mPosVector.z );

            // What is the normal for this position?
            if ( pContext.mNormBuf != null ) {
                pContext.mNormVector = getRadialNormal( pTempVars.vect4, aContext, pSurface );
                pContext.mNormBuf.put( pContext.mNormVector.x )
                				.put( pContext.mNormVector.y )
                				.put( pContext.mNormVector.z );
            }
            // What texture applies?
            if ( pContext.mTexBuf != null ) {
                pContext.mTexVector = getRadialTexture( pTempVars.vect2d2, aContext, pSurface );            					
                pContext.mTexBuf.put( pContext.mTexVector.x )
                				.put( pContext.mTexVector.y );
            }
            pContext.mIndex += 1;
        }
        // Copy the first radial vertex to the end
        BufferUtils.copyInternalVector3( pContext.mPosBuf, iSave, pContext.mIndex );
        if ( pContext.mNormBuf != null ) {
        	BufferUtils.copyInternalVector3( pContext.mNormBuf, iSave, pContext.mIndex );
        }
        // Special texture processing at the end where pContext.mRadialIndex == mRadialSamples
        if ( pContext.mTexBuf != null ) {
            pContext.mTexVector = getRadialTexture( pTempVars.vect2d2, aContext, pSurface );            					
            pContext.mTexBuf.put( pContext.mTexVector.x )
        					.put( pContext.mTexVector.y );
        }
    }

    /** Service routine to allocate and fill an index buffer */
    protected VertexBuffer createIndices(
    	CSGRadialContext	pContext
    ,	float				pLODFactor
    ,	int					pSouthPoleIndex
    ,	int					pNorthPoleIndex
    ,	boolean				pSinglePoleVertex
    ) {
    	// Accommodate the LOD factor
    	int triangleCount 
    		= pContext.setReductionFactors( pLODFactor, mAxisSamples, mRadialSamples, mGeneratedFacesMask );
    	
        // Allocate the indices, 3 points on every triangle
        ShortBuffer idxBuf = BufferUtils.createShortBuffer( 3 * triangleCount );

        // Process along the axis, mapping the vertices of one slice to the slice in front of it
        int triangleCounter = 0;
        for( int iZStart = 0, zIndex = 0, zFactor; zIndex < pContext.mSliceCount -1; zIndex += zFactor ) {
        	// Account for the starting points
            int i0 = iZStart;
            int i1 = i0 + 1;
            
        	zFactor = pContext.nextSliceFactor( zIndex, mGeneratedFacesMask );
            iZStart += (mRadialSamples + 1) * zFactor;
            int i2 = iZStart;
            int i3 = i2 + 1;
            
        	if ( Face.BACK.maskIncludesFace( mGeneratedFacesMask ) && (zIndex == 0) ) {
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
                triangleCounter += mRadialSamples;
                
        	} else if ( Face.FRONT.maskIncludesFace( mGeneratedFacesMask ) && (zIndex == pContext.mSliceCount -2) ) {
                // FrontFace/NorthPole triangles
        		mFirstFrontTriangle = triangleCounter;
        		
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
                triangleCounter += mRadialSamples;
                
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
	            triangleCounter += mRadialSamples * 2;
        	}
        }
        // Wrap as an Index buffer
    	VertexBuffer vtxBuffer = new VertexBuffer( Type.Index );
    	vtxBuffer.setupData( Usage.Dynamic, 3, Format.UnsignedShort, idxBuf );
        return( vtxBuffer );
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
        
        // Apply scaling
        if ( mScaleSlice != null ) {
        	pUseVector.multLocal( mScaleSlice.x, mScaleSlice.y, 1.0f );
        }
        // Apply rotation
    	if ( pContext.mSliceRotator != null ) {
    		pContext.mSliceRotator.multLocal( pUseVector );
    	}
        // Account for the center
        pUseVector.addLocal( pContext.mSliceCenter );
        
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
        // Let the super do its thing
        super.read( pImporter );
        
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mRadialSamples = inCapsule.readInt( "radialSamples", mRadialSamples );
        mFirstRadial = CSGEnvironment.readPiValue( inCapsule, "firstRadial", 0 );
        mRadius = inCapsule.readFloat( "radius", mRadius );
        
        float scaleX = CSGEnvironment.readPiValue( inCapsule, "scaleSliceX", 1.0f );
        float scaleY = CSGEnvironment.readPiValue( inCapsule, "scaleSliceY", 1.0f );
        if ( (scaleX != 1.0f) || (scaleY != 1.0f) ) {
        	mScaleSlice = new Vector2f( scaleX, scaleY );
        }
        mRotateSlices = CSGEnvironment.readPiValue( inCapsule, "twist", 0 );
    }
    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( CSGRadial.class
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
abstract class CSGRadialContext
	extends CSGAxialContext
{
    float 				mInverseRadialSamples;	// Percentage of each radial
    
    CSGRadialCoord[]	mCoordList;				// Sine/Cosine coordinates based on count of radials
    
    float 				mSliceRadius;			// Radius of this slice
    Quaternion 			mSliceRotator;			// Rotation to be applied to this slice
    
    int					mRadialIndex;			// Counter running along the circular radial points
    float				mRadialFraction;		// Percentage of distance along the radial surface (0.0 : +1.0) 
}
