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

import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.OutputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.JmeExporter;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
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


/** Specialization of a radial CSG shape that has:
 	1)	Optional, flat end caps
 		(if no end caps are produced, ie shape not closed, then you can see inside it)
 	2)	Uniformly varying slice radius (ie, front/back radii that apply to the end caps)
 	
 	Subclasses are expected to determine the placement of the slices
 	
 	 Configuration Settings:
 		radius2 - 		radius that applies to the back endcap
 		
 		textureMode -	how to apply the texture to the surfaces

 */
public abstract class CSGRadialCapped 
	extends CSGRadial
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGRadialCappedRevision="$Rev$";
	public static final String sCSGRadialCappedDate="$Date$";

	/** Special TextureModes that can be applied */
    public enum TextureMode {
        FLAT			// The texture of the end caps is simply flat
    ,   UNIFORM			// Texture of end caps is flat and uniform in scale to the curved surface
    ,	FLAT_LINEAR		// Flat end caps, curved surface is linear front-to-back
    ,	UNIFORM_LINEAR	// Flat, uniform end caps, curved surface is linear front-to-back
    }
    
    /** The radius of the back cap */
    protected float 		mRadiusBack;
	/** Marker to enforce 'uniform' texture (once around the cylinder matches once across the end cap) */
	protected TextureMode	mTextureMode;
	
	/** For subclass use */
    protected CSGRadialCapped(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadiusFront
    ,	float		pRadiusBack
    , 	float 		pZExtent
    , 	boolean 	pClosed
    , 	boolean 	pInverted
    ,	TextureMode	pTextureMode
    ) {
    	// By definition, a capped radial has flat ends
    	super( pAxisSamples, pRadialSamples, pRadiusFront, pClosed, pInverted, true );
    	
        mRadiusBack = pRadiusBack;
        mExtentZ = pZExtent;			// This will override the default setting from the radius
        mTextureMode = pTextureMode;
    }

    /** Configuration accessors */
    public float getRadiusFront() { return mRadius; }
    public void setRadiusFront( float pRadius ) { mRadius = pRadius; }
    
    public float getRadiusBack() { return mRadiusBack; }
    public void setRadiusBack( float pRadius ) { mRadiusBack = pRadius; }
    
    public TextureMode getTextureMode() { return mTextureMode; }
    public void setTextureMode( TextureMode pTextureMode ) { mTextureMode = pTextureMode; }

	/** Accessor to the material that applies to the given surface */
    @Override
	public Material getMaterial(
		int					pFaceIndex
	) {
    	if ( mFaceProperties != null ) {
			// Determine the face
	    	Face aFace = Face.SIDES;
	    	if ( mClosed ) {
	    		// If the shape is closed, then we have front and back
	    		if ( pFaceIndex < mRadialSamples ) {
	    			// Looks like the SouthPole
	    			aFace = Face.BACK;
	    		} else if ( pFaceIndex >= mFirstFrontTriangle ) {
	    			// Looks like the NorthPole
	    			aFace= Face.FRONT;
	    		}
	    	}
	    	Material aMaterial = resolveFaceMaterial( aFace.getMask() );
			return( aMaterial );
    	} else {
    		// No custom textures
    		return( null );
    	}
	}
    
	/** Accessor to the physics that applies to the given surface */
    @Override
	public PhysicsControl getPhysics(
		int					pFaceIndex
	) {
    	if ( mFaceProperties != null ) {
			// Determine the face
	    	Face aFace = Face.SIDES;
	    	if ( mClosed ) {
	    		// If the shape is closed, then we have front and back
	    		if ( pFaceIndex < mRadialSamples ) {
	    			// Looks like the SouthPole
	    			aFace = Face.BACK;
	    		} else if ( pFaceIndex >= mFirstFrontTriangle ) {
	    			// Looks like the NorthPole
	    			aFace= Face.FRONT;
	    		}
	    	}
	    	PhysicsControl aPhysics = resolveFacePhysics( aFace.getMask() );
			return( aPhysics );
    	} else {
    		// No custom textures
    		return( null );
    	}
	}


    /** Rebuilds the radial based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
        // Allocate buffers for position/normals/texture
    	CSGRadialContext aContext = getContext();
    	
    	// Create all the vertices info
    	int southPoleIndex = -1, northPoleIndex = -1;
    	createGeometry( aContext );
    	
        if ( mClosed ) { TempVars vars = TempVars.get(); try {
        	// Two extra vertices for the centers of the end caps
        	southPoleIndex = aContext.mIndex++;
        	//aContext.mPosBuf.put( 0 ).put( 0 ).put( -mExtentZ ); // bottom center
        	//aContext.mNormBuf.put( 0 ).put( 0 ).put( -1 * (mInverted ? -1 : 1) );
        	
        	aContext.mSliceCenter = getSliceCenter( vars.vect2, aContext, -1 );
        	aContext.mPosVector = getCenterPosition( vars.vect1, aContext, -1 );
            aContext.mPosBuf.put( aContext.mPosVector.x )
            				.put( aContext.mPosVector.y )
            				.put( aContext.mPosVector.z );

        	aContext.mNormVector = getCenterNormal( vars.vect4, aContext, -1 );
            aContext.mNormBuf.put( aContext.mNormVector.x )
            				.put( aContext.mNormVector.y )
            				.put( aContext.mNormVector.z );

            aContext.mTexBuf.put( 0.5f ).put( 0.5f );
            
        	northPoleIndex = aContext.mIndex++;
            //aContext.mPosBuf.put( 0).put( 0 ).put( mExtentZ); // top center
            //aContext.mNormBuf.put( 0).put( 0 ).put( 1 * (mInverted ? -1 : 1) );
        	
        	aContext.mSliceCenter = getSliceCenter( vars.vect2, aContext, 1 );
        	aContext.mPosVector = getCenterPosition( vars.vect1, aContext, 1 );
            aContext.mPosBuf.put( aContext.mPosVector.x )
            				.put( aContext.mPosVector.y )
            				.put( aContext.mPosVector.z );

        	aContext.mNormVector = getCenterNormal( vars.vect4, aContext, 1 );
            aContext.mNormBuf.put( aContext.mNormVector.x )
            				.put( aContext.mNormVector.y )
            				.put( aContext.mNormVector.z );

            aContext.mTexBuf.put( 0.5f ).put( 0.5f );
        } finally {
        	// Return the borrowed vectors
        	vars.release();
        }}
        // The poles are represented by a single point
        VertexBuffer idxBuffer 
        	= createIndices( aContext, 0.0f, southPoleIndex, northPoleIndex, true );
        setBuffer( idxBuffer );
        
        if ( mLODFactors != null ) {
        	// Various Levels of Detail, with the zero slot being the master
        	VertexBuffer[] levels = new VertexBuffer[ mLODFactors.length + 1 ];
        	levels[0] = idxBuffer;
        	
        	// Generate each level
        	for( int i = 0, j = mLODFactors.length; i < j; i += 1 ) {
        		idxBuffer 
            		= createIndices( aContext, mLODFactors[i], southPoleIndex, northPoleIndex, true );
        		levels[ i + 1 ] = idxBuffer;
        	}
        	this.setLodLevels( levels );
        }
        // Establish the bounds
        updateBound();
        setStatic();
    }
    
    /** SUBCLASS MUST PROVIDE: allocate the context */
//	protected CSGRadialContext getContext();

    /** SUBCLASS MUST PROVIDE: fractional speaking, where are we along the z axis */
//  protected float getZAxisFraction( 
//  	CSGRadialContext 	pContext
//  ,	int					pSurface
//  )
    
    /** SUBCLASS MUST PROVIDE: where is the center of the given slice */
//  protected Vector3f getSliceCenter(
//    	Vector3f			pUseVector
//  ,	CSGRadialContext 	pContext
//  ,	int					pSurface
//  )
    
    /** FOR SUBCLASS OVERRIDE: what is the radius of the Radial at this slice */
    @Override
    protected float getSliceRadius(
        CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Account for differing radii, spread evenly along the z
    	float aRadius = ((mRadius - mRadiusBack) * pContext.mZAxisFraction) + mRadiusBack;
    	return( aRadius );
    }
    
    /** FOR SUBCLASS OVERRIDE: prepare the texture for the entire slice */
    @Override
    protected Vector2f getSliceTexture(
    	Vector2f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Nothing special at the slice
    	pUseVector.set( Vector2f.ZERO );
    	return( pUseVector );
    }
    
    /** SUBCLASS MUST PROVIDE: compute the normal of a given radial vertex */
//  protected Vector3f getRadialNormal(
// 		Vector3f			pUseVector
//  ,	CSGRadialContext 	pContext
//  ,	int					pSurface
//  )

    /** FOR SUBCLASS OVERRIDE: compute the texture of a given radial vertex */
    @Override
    protected Vector2f getRadialTexture(
    	Vector2f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	CSGRadialCappedContext myContext = (CSGRadialCappedContext)pContext;

        // Apply scaling ONLY to the end caps so that we retain the cookie-cutout style
    	// of texture without screwing up the curved crust.  Scaling is reflected in the
    	// EndcapTextureBias
    	if ( pSurface < 0 ) {
        	// Map the texture to the bottom cap (facing the other way, right to left)
    		// NOTE no scaling of the end caps
        	CSGRadialCoord aCoord = pContext.mCoordList[ pContext.mRadialIndex ];
    		pUseVector.set(
    			0.5f - ((aCoord.mCosine / 2.0f) * myContext.mEndcapTextureBiasX)
			,	0.5f + ((aCoord.mSine / 2.0f) * myContext.mEndcapTextureBiasY) );
    		
    	} else if ( pSurface > 0 ) {
        	// Map the texture to the top cap (simple left to right)
    		// NOTE no scaling of the end caps
        	CSGRadialCoord aCoord = pContext.mCoordList[ pContext.mRadialIndex ];
    		pUseVector.set(
        		0.5f + ((aCoord.mCosine / 2.0f) * myContext.mEndcapTextureBiasX)
    		,	0.5f + ((aCoord.mSine / 2.0f) * myContext.mEndcapTextureBiasY) );
    	
    	} else {
        	// Map the texture along the curved crust/surface
    		float radialFraction = myContext.mRadialFraction;
    		if ( myContext.mRadialIndex == 0 ) {
    			// Start at the very beginning
    			radialFraction = 0.0f;
    		
    		} if ( myContext.mRadialIndex >= this.mRadialSamples ) {
    			// Loop around back to the beginning
    			radialFraction = 1.0f;
    		}
            switch( mTextureMode ) {
            case FLAT:
            case UNIFORM:
            	// Run the texture around the circle
            	pUseVector.set(
            		(mInverted) ? 1.0f - radialFraction : radialFraction
            	,	(myContext.mZAxisUniformPercent * (float)myContext.mZOffset) / 2.0f );                    	
            	break;
            default:
            	// Run the texture linearly from front to back
            	pUseVector.set(
            		(myContext.mZAxisUniformPercent * (float)myContext.mZOffset) / 2.0f
            	,	(mInverted) ? radialFraction : 1.0f - radialFraction );	
            	break;
            }
    	}

    	return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: what is the center of the endcap */
    protected Vector3f getCenterPosition(
     	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// By default, the center is in the center on the zAxis
    	pUseVector.set( pContext.mSliceCenter );
    	return( pUseVector );
    }

    /** FOR SUBCLASS OVERRIDE: what is the center normal of the endcap */
    protected Vector3f getCenterNormal(
     	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// By default, the center is in the center on the zAxis
    	pUseVector.set( 0, 0, pSurface * (mInverted ? -1 : 1));
    	return( pUseVector );
    }
    
    /** Service routine to precalculate a set of normals for the various radial points */
    protected Vector3f[] calculateRadialNormals(
    	CSGRadialContext 	pContext
    ,	float				pRadiusFront
    ,	float				pRadiusBack
    ,	Vector3f			pTempVector
    ) {
    	// Precalculate the normals we will need
        Vector3f[] radialNormals = new Vector3f[ mRadialSamples + 1 ];
        	
        // If the radii differ, then we will need a slant in Z.  We assume that
        // the slant is spread evenly across the entire z extent
        Vector3f vHeight = (pRadiusFront == pRadiusBack) ? null : Vector3f.UNIT_Z.mult( getHeight() );

        // Walk around all the radial points
        for( int radialCount = 0; radialCount < mRadialSamples + 1; radialCount += 1 ) {
        	// Perpendicular to the tangent of the circle at this radial point
        	// within the x/y plane (z = 0)
        	CSGRadialCoord aCoord = pContext.mCoordList[ radialCount ];
        	pTempVector.set( aCoord.mCosine, aCoord.mSine, 0 );
            
            if ( vHeight != null ) {
            	// If the radii are different, then the surface slants in the z Plane
            	Vector3f vRadiusFront = pTempVector.mult( pRadiusFront );
            	Vector3f vRadiusBack = pTempVector.mult( pRadiusFront );
            	
            	// Account for how the height differs across the entire extent
            	Vector3f vHeightDifference = vRadiusBack.subtractLocal( vRadiusFront );
            	Vector3f vMantle = vHeight.subtract( vHeightDifference );
            	
            	Vector3f vTangent = pTempVector.cross( Vector3f.UNIT_Z );
            	radialNormals[ radialCount ] = vMantle.crossLocal( vTangent ).normalizeLocal();
            } else {
            	// Keep a copy of this normal for future fast access
            	radialNormals[ radialCount ] = pTempVector.clone();
            }
        }
        return( radialNormals );
	};


    /** Support capped radial specific configuration parameters */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    	
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.write( mRadiusBack, "radius2", mRadius );
        outCapsule.write( mTextureMode, "textureMode", TextureMode.FLAT );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing
        super.read( pImporter );

        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        // The super will have read 'radius'
        mRadiusBack = inCapsule.readFloat( "radius2", mRadius );
        mTextureMode = inCapsule.readEnum( "textureMode", TextureMode.class, TextureMode.FLAT );

        // Super processing of zExtent reads to zero to allow us to apply an appropriate default here
        if ( mExtentZ == 0 ) {
        	// For a cylinder the default is 1
        	mExtentZ = 1;
        }
    }
    
    /** Apply texture coordinate scaling to selected 'faces' of the shape */
    @Override
    public void scaleFaceTextureCoordinates(
    	Vector2f	pScaleTexture
    ,	int			pFaceMask
    ) {
    	// Operate on the TextureCoordinate buffer
        VertexBuffer tc = getBuffer(Type.TexCoord);
        if (tc == null)
            throw new IllegalStateException("The mesh has no texture coordinates");

        if (tc.getFormat() != VertexBuffer.Format.Float)
            throw new UnsupportedOperationException("Only float texture coord format is supported");

        if (tc.getNumComponents() != 2)
            throw new UnsupportedOperationException("Only 2D texture coords are supported");

        FloatBuffer aBuffer = (FloatBuffer)tc.getData();
        aBuffer.clear();
        
        // What surfaces are we dealing with?
        boolean doBack = (pFaceMask & Face.BACK.getMask()) != 0;
        boolean doFront = (pFaceMask & Face.FRONT.getMask()) != 0;
        boolean doSides = (pFaceMask & Face.SIDES.getMask()) != 0;
        
        // If closed, we will generate 2 extra slices, one for top and one for bottom
        // Vertices are generated in a standard way for the these special slices, but there
        // is no puck with an edge.  Instead, the triangles describe the flat closed face.
        // For that, we will have 2 extra vertices that describe the center of the face.
        int index = 0;
        int aSliceCount = (mClosed) ? mAxisSamples + 2 : mAxisSamples;
        int aRadialCount = mRadialSamples + 1;
        for( int axisCount = 0; axisCount < aSliceCount; axisCount += 1 ) {
            Face whichFace = Face.NONE;
            if ( mClosed ) {
            	// The first slice is the closed bottom, and the last slice is the closed top
                if (axisCount == 0) {
                	if ( doBack ) {
                		// Actively processing the back
                		whichFace = Face.BACK;
                	}
                } else if (axisCount == aSliceCount - 1) {
                	if ( doFront ) {
                		// Actively processing the front
                		whichFace = Face.FRONT;
                	}
                } else {
                	if ( doSides ) {
                		// Actively processing the sides
                		whichFace = Face.SIDES;
                	}
                }
            } else {
            	if ( doSides ) {
            		// Actively processing the sides
            		whichFace = Face.SIDES;
            	}
            }
            if ( whichFace == Face.NONE ) {
            	// Nothing is this slice to process
            	index += (2 * aRadialCount);
            } else {
            	for( int i = 0; i < aRadialCount; i += 1 ) {
            		// Scale these points
            		index = applyScale( index, aBuffer, pScaleTexture.x, pScaleTexture.y, true );
            	}
            }
    	}
        if ( mClosed ) {
        	// Two extra vertices for the centers of the end caps
        	index = applyScale( index, aBuffer, pScaleTexture.x, pScaleTexture.y, doBack );
        	index = applyScale( index, aBuffer, pScaleTexture.x, pScaleTexture.y, doFront );
        }
    	aBuffer.clear();
        tc.updateData( aBuffer );
    }
    /** Service routine to apply scaling within the texture buffer */
    protected int applyScale(
    	int			pIndex
    ,	FloatBuffer pBuffer
	,	float		pX
	,	float		pY
    ,	boolean		pApplyScale
    ) {
    	if ( pApplyScale ) {
			float x = pBuffer.get( pIndex  );
			float y = pBuffer.get( pIndex + 1 );

            x *= pX;
            y *= pY;
            pBuffer.put( pIndex++, x ).put( pIndex++, y );        		
    	} else {
    		pIndex += 2;
    	}
    	return( pIndex );
    }
    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( CSGRadialCapped.class
													, sCSGRadialCappedRevision
													, sCSGRadialCappedDate
													, pBuffer ) );
	}
}
/** Helper class for use during the geometry calculations */
class CSGRadialCappedContext
	extends CSGRadialContext
{
    Vector3f[] 	mRadialNormals;			// Helper for the normals on each slice
    float		mEndcapTextureBiasX;
    float		mEndcapTextureBiasY;
	
    /** Initialize the context */
    CSGRadialCappedContext(
    	int							pAxisSamples
    ,	int							pRadialSamples
    ,	boolean						pClosed
    ,	CSGRadialCapped.TextureMode	pTextureMode
    ,	Vector2f					pScaleSlice
    ) {	
    	initializeContext( pAxisSamples, pRadialSamples, pClosed );
    	
    	// Account for the span of the texture on the end caps
    	switch( pTextureMode ) {
	    case UNIFORM:
	    case UNIFORM_LINEAR:
	    	// UNIFORM end caps are inherently scaled so match the texture scaling of the curved surface
	    	mEndcapTextureBiasX = mEndcapTextureBiasY = FastMath.INV_PI;
	    	break;
	    default:
	    	// Simple FLAT end caps act like a circular cookie-cutter applied on the texture
	    	mEndcapTextureBiasX = mEndcapTextureBiasY = 1.0f;
	    	break;
	    }
    	if ( pScaleSlice != null ) {
    		mEndcapTextureBiasX *= pScaleSlice.x;
    		mEndcapTextureBiasY *= pScaleSlice.y;
    	}

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

    /** How many vertices are produced? */
    @Override
    protected int resolveVetexCount(
    	int			pSliceCount
    ,	int			pRadialSamples
    ,	boolean		pIsClosed
    ) {
    	// The cylinder has RadialSamples (+1 for the overlapping end point) times the number of slices
    	// plus the 2 polar center points if closed
    	int vertCount = (pSliceCount * (pRadialSamples + 1)) + (pIsClosed ? 2 : 0);
    	return( vertCount );
    }

}
