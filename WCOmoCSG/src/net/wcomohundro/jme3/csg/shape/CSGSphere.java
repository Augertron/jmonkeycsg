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
import com.jme3.math.ColorRGBA;
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
import java.nio.ShortBuffer;
import java.util.List;

import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;

/** Specialization/Copy of standard JME3 Sphere for unification with other CSG shapes
 	Like CSGCylinder (and the original Sphere) we render slices of the sphere along the
 	z-axis.  AxisSamples determines how many slices to produce, and RadialSamples 
 	determines how many vertices to produce around the circle for each slice.
 	
 	The basic idea is to alter the active radius of the radial circle at each slice
 	point along the zAxis.  If you alter this value to match the appropriate sine/cosine
 	then the result is a sphere.  You can set the generation to produce even slices 
 	along the entire range of z, or you can vary the z slice width to produce more slices
 	where the values change most rapidly.  This gives you a smoother looking sphere.
 	
 	I have altered how textures are applied to the poles to (ZAXIS, PROJECTED) so that
 	the radial distortion in the base jme3 class is eliminated.
 	 
 	NOTE that for a while, I was going to support 'eccentricity' in the generation of the
 		sphere to provide oblate/prolate spheroids.  But then I can came across a telling
 		note that scaling gives you the same effect, with more flexibility and less 
 		complexity.
*/
public class CSGSphere 
	extends CSGRadial
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSphereRevision="$Rev$";
	public static final String sCSGSphereDate="$Date$";

	/** Special texture modes that can be applied */
    public enum TextureMode {
        ZAXIS			// Wrap texture radially and along the z-axis
    ,   PROJECTED		// Wrap texture radially, but spherically project along the z-axis
    ,   POLAR			// Apply texture to each pole.  Eliminates polar distortion,
        				// but mirrors the texture across the equator
    }
    
    /** When generating the slices along the z-axis, evenly space them (else create more near the extremities) */
    protected boolean 		mEvenSlices;
	/** How to apply the texture */
	protected TextureMode	mTextureMode;
	
	public CSGSphere(
	) {
		this( 32, 32, 1, false, false, TextureMode.ZAXIS, false );
	}
    public CSGSphere(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadius
    ) {
		this( pAxisSamples, pRadialSamples, pRadius, false, false, TextureMode.ZAXIS, true );
	}
    public CSGSphere(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadius
    , 	boolean 	pUseEvenSlices
    , 	boolean 	pInverted
    ,	TextureMode	pTextureMode
    ,  	boolean		pReadyGeometry
    ) {
    	super( pAxisSamples, pRadialSamples, pRadius, true, pInverted, false );
        mEvenSlices = pUseEvenSlices;
        mTextureMode = pTextureMode;
        
        if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
    }
    
	/** Accessor to the full range of faces supported by this mesh */
	@Override
	public int getSupportedFacesMask(
	) {
		return( Face.FRONT_BACK.getMask() | Face.SURFACE.getMask() );
	}

    /** By definition for a sphere, the zExtent matches the radius */
    @Override
    public void setRadius( float pRadius ) { mExtentZ = mRadius = pRadius; }

    /** Configuration accessors */
    public boolean hasEvenSlices() { return mEvenSlices; }
    public void setEvenSlices( boolean pFlag ) { mEvenSlices = pFlag; }
    
    public TextureMode getTextureMode() { return mTextureMode; }
    public void setTextureMode( TextureMode pTextureMode ) { mTextureMode = pTextureMode; }
    
    /** Apply gradient vertex colors:
     		0 - North Pole
     		1 - Equator
     		2 - South Pole
     */
    @Override
    public void applyGradient(
    	List<ColorRGBA>	pColorList
    ) {
    	float[] northPoleColors = null, southPoleColors = null, equatorColors = null;
    	
    	if ( (pColorList.size() > 0) && (pColorList.get(0) != null) ) {
    		northPoleColors = pColorList.get(0).getColorArray( new float[4] );
    	}
    	if ( (pColorList.size() > 1) && (pColorList.get(1) != null) ) {
    		equatorColors = pColorList.get(1).getColorArray( new float[4] );
    	}
    	if ( (pColorList.size() > 2) && (pColorList.get(2) != null) ) {
    		southPoleColors = pColorList.get(2).getColorArray( new float[4] );
    	}
    	if ( southPoleColors == null ) southPoleColors = northPoleColors;
    	if ( northPoleColors == null ) northPoleColors = southPoleColors;
    	if ( equatorColors == null ) {
    		equatorColors = new float[4];
    		for( int i = 0; i < 4; i += 1 ) {
    			equatorColors[i] = (southPoleColors[i] + northPoleColors[i]) / 2f;
    		}
    	}
    	// Follow the texture buffer
        VertexBuffer vtxBuffer = getBuffer( Type.TexCoord );
        FloatBuffer tcBuffer = resolveTexCoordBuffer( vtxBuffer );
        
        // Prep the range of colors
        float[] northSpan = new float[4], southSpan = new float[4];
        for( int i = 0; i < 4; i +=1 ) {
        	northSpan[i] = equatorColors[i] - northPoleColors[i];
        	southSpan[i] = equatorColors[i] - southPoleColors[i];
        }
        // Build the color buffer
        int vertexCount = tcBuffer.limit() / 2;			// x and y per vertex
        FloatBuffer colorBuf = BufferUtils.createFloatBuffer( 4 * vertexCount );
        
        // Generate a color point for each vertex
        float[] calcColors = new float[4];
	    for( int i = 0; i < vertexCount; i += 1 ) {
            float x = tcBuffer.get();
            float y = tcBuffer.get();
            
            float[] useColors = calcColors;
            
            switch( mTextureMode ) {
            case POLAR:
            	// I am not quite sure if the following applies to polar????
            	// (but is better than nothing at this time)
            	
            case ZAXIS:
            case PROJECTED:
            	// y varies by slice, x by radial point, so only y is of interest
            	// y ranges from 0 (north) to 1 (south), with 0.5 at the equator
            	if ( y <= 0.0f ) {
            		// NorthPole
            		useColors = northPoleColors;
            		
            	} else if ( y >= 1.0f ) {
            		// SouthPole
            		useColors = southPoleColors; 
          		
            	} else if ( y == 0.5f ) {
            		useColors = equatorColors;
            		
            	} else if ( y < 0.5f ) {
            		// Northern hemisphere
            		for( int j = 0; j < 4; j += 1 ) {
            			calcColors[j] = northPoleColors[j] + (northSpan[j] * (y * 2.0f));
            		}
            	} else {
            		// Southern hemisphere
            		for( int j = 0; j < 4; j += 1 ) {
            			calcColors[j] = southPoleColors[j] + (southSpan[j] * ((1.0f - y) * 2.0f));
            		}
            	}
            }
            // Set the color: red / green / blue / alpha
            colorBuf.put( useColors[0] ).put( useColors[1] ).put( useColors[2] ).put( useColors[3] );
	    }
	    // Define the standard color buffer
	    this.setBuffer( Type.Color, 4, colorBuf );
    }

    
    /** Rebuilds the sphere based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
        // Allocate buffers for position/normals/texture
    	CSGRadialContext aContext = getContext( true );
    	
    	// Create all the vertices info
    	int southPoleIndex = 0;
    	int northPoleIndex = createGeometry( aContext );

        // Multiple, unique poles are provided
        VertexBuffer idxBuffer 
        	= createIndices( aContext, 0.0f, southPoleIndex, northPoleIndex, false );
        setBuffer( idxBuffer );
        
        if ( mLODFactors != null ) {
        	// Various Levels of Detail, with the zero slot being the master
        	VertexBuffer[] levels = new VertexBuffer[ mLODFactors.length + 1 ];
        	levels[0] = idxBuffer;
        	
        	// Generate each level
        	for( int i = 0, j = mLODFactors.length; i < j; i += 1 ) {
        		idxBuffer 
            		= createIndices( aContext, mLODFactors[i], southPoleIndex, northPoleIndex, false );
        		levels[ i + 1 ] = idxBuffer;
        	}
        	this.setLodLevels( levels );
        }
        // Establish the bounds
        updateBound();
        setStatic();
    }
    
    /** FOR SUBCLASS OVERRIDE: allocate the context */
    @Override
    protected CSGRadialContext getContext(
    	boolean		pAllocateBuffers
    ) {
    	// Allocate buffers for position/normals/texture
    	// We generate an extra vertex around the radial sample where the last
    	// vertex overlays the first.  
		// And even though the north/south poles are a single point, we need to 
		// generate different texture coordinates for the pole for each different 
		// radial, so we need a full set of vertices
    	if ( (mTextureMode == CSGSphere.TextureMode.POLAR) && ((mAxisSamples & 1) == 0) ) {
    		// Polar mode works much better with an odd count of samples,
    		// which eliminates the duplication across the equator
    		mAxisSamples += 1;
    	}
   	 	CSGSphereContext aContext 
   	 		= new CSGSphereContext( mAxisSamples, mRadialSamples, mGeneratedFacesMask, mRadius, mTextureMode, pAllocateBuffers );
    	 
    	return( aContext );
    }
    
    /** FOR SUBCLASS OVERRIDE: fractional speaking, where are we along the z axis */
    @Override
    protected float getZAxisFraction( 
    	CSGAxialContext 	pContext
    ,	int					pSurface
    ) {
    	float axisFraction;
    	if ( pSurface != 0 ) {
    		// At the end caps
    		axisFraction = (float)pSurface;
    		
    	} else if ( mEvenSlices ) {
        	// Spread the slices evenly along the zAxis
        	axisFraction = (pContext.mZAxisUniformPercent * (float)pContext.mZOffset) - 1.0f; // in (-1, 1)
        	
        } else {
        	// Generate more slices where the values change the quickest
        	axisFraction = FastMath.sin( pContext.mAngleFraction ); // in (-1,1)
        }
    	return( axisFraction );
    }
    
    /** OVERRIDE: where is the center of the given slice */
    @Override
    protected Vector3f getSliceCenter(
    	CSGAxialContext 	pContext
    ,	int					pSurface
	,	Vector3f			pUseVector
	,	CSGTempVars			pTempVars
    ) {
    	// By default, the center is ON the zAxis at the given absolute z position 
    	return( super.getSliceCenter(  pContext, pSurface, pUseVector, pTempVars ) );
    }
    
    /** OVERRIDE: what is the radius of the Radial at this slice */
    @Override
    protected float getSliceRadius(
        CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
        // For a circle, where zAxis is the distance along the axis from the center, simple
        // aSquared + bSquared = cSquared, means that zAxisSquared + sliceRadiusSquared = rSquared
        // 		sliceRadius == SquareRoot( rSquared - zAxisSquared)
        float sliceRadius 
        	= FastMath.sqrt( FastMath.abs( (((CSGSphereContext)pContext).mRadiusSquared
        										- (pContext.mZAxisAbsolute * pContext.mZAxisAbsolute))) );
        return( sliceRadius );
    }
    
    /** OVERRIDE: prepare the texture for the entire slice */
    @Override
    protected Vector2f getSliceTexture(
    	Vector2f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// The texture y will vary by the slice
    	pUseVector.set( Vector2f.ZERO );
        switch( mTextureMode ) {
        case ZAXIS:
        	pUseVector.y = 0.5f * (pContext.mZAxisFraction + 1.0f);
            break;
        case PROJECTED:
        	pUseVector.y = FastMath.INV_PI * (FastMath.HALF_PI + FastMath.asin( pContext.mZAxisFraction ));
            break;
        }
    	return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: compute the position of a given radial vertex */
    @Override
    protected Vector3f getRadialPosition(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	if ( pSurface != 0 ) {
    		// Special backface/frontface processing, using the -1/+1 of the surface
    		pUseVector.set( 0, 0, mRadius * (float)pSurface);
    		
    	} else {
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
    	}
        return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: compute the normal of a given radial vertex */
    @Override
    protected Vector3f getRadialNormal(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Since we are dealing with a sphere, the normal just needs to point
    	// back to the origin
    	if ( pSurface != 0 ) {
    		// Special backface/frontface processing, using the -1/+1 of the surface
    		pUseVector.set( 0, 0, (float)pSurface );

    	} else {
    		// Normal surface/crust processing
    		pUseVector.set( pContext.mPosVector );
    		pUseVector.normalizeLocal();
    		
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
    	return( pUseVector );
    }
    
    /** FOR SUBCLASS OVERRIDE: compute the texture of a given radial vertex */
    @Override
    protected Vector2f getRadialTexture(
    	Vector2f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Start with the slice previously set
    	pUseVector.set( pContext.mSliceTexture );
    	
    	if ( pSurface != 0 ) {
    		// Working the poles
    		switch( mTextureMode ) {
    		case POLAR:
            	// Spreading the texture across the pole, so the pole itself is dead center
    			pUseVector.set( 0.5f, 0.5f );
                break;
    		default:
            	// x is following the radial, and y is following the zAxis, at its extremities
    			pUseVector.set( pContext.mRadialFraction, (pSurface < 0) ? 0 : 1 );
    			break;
    		}
    	} else if ( pContext.mRadialIndex == mRadialSamples ) {
    		// Once around the radial and back to the beginning
            switch( mTextureMode ) {
            case ZAXIS:
            case PROJECTED:
            	pUseVector.x = 1.0f;
                break;
            case POLAR:
            	pUseVector.x = pContext.mPolarFraction + 0.5f;
            	pUseVector.y = 0.5f;
                break;
            }
    	} else {
    		// Working the surface
    		switch( mTextureMode ) {
	        case ZAXIS:
	        case PROJECTED:
	        	pUseVector.x = pContext.mRadialFraction;
	            break;
	        case POLAR:
	        	CSGRadialCoord aCoord = pContext.mCoordList[ pContext.mRadialIndex ];
	        	pUseVector.x = pContext.mPolarFraction * aCoord.mCosine + 0.5f;
	        	pUseVector.y = pContext.mPolarFraction * aCoord.mSine + 0.5f;
	            break;
	        }
    	}
    	// NOTE that we do not apply the slice scaling to the texture... We must still
    	//		map once around the globe as a single texture iteration
    	return( pUseVector );
    }
    

    /** Support sphere specific configuration settings */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    	
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.write( mEvenSlices, "useEvenSlices", false );
        outCapsule.write( mTextureMode, "textureMode", TextureMode.ZAXIS );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing
        super.read( pImporter );

        InputCapsule inCapsule = pImporter.getCapsule( this );
        if ( mGeneratedFacesMask == 0 ) {
        	// Default to full faces with possible explicit open/closed
        	mGeneratedFacesMask = this.getSupportedFacesMask();
        	setClosed( inCapsule.readBoolean( "closed", true ) );
        }        
        mEvenSlices = inCapsule.readBoolean( "useEvenSlices", false );
        mTextureMode = inCapsule.readEnum( "textureMode", TextureMode.class, TextureMode.ZAXIS );

        // Super processing of zExtent reads to zero to allow us to apply an appropriate default here
        if ( mExtentZ == 0 ) {
        	// For a sphere the default is to match the radius
        	mExtentZ = mRadius;
        } else if ( mRadius == 1 ) {
        	// An explicit Z extent overrides a default radius
        	mRadius = mExtentZ;
        } else if ( mExtentZ != mRadius ) {
        	// So what does this mean?
        	throw new IOException( "CSGSphere radius/zExtent mismatch" );
        }
        // Standard trigger of updateGeometry() to build the shape, unless the radius tells us to defer
        if ( mRadius > 0 ) {
        	this.updateGeometry();
        }
        // Any special color processing?
        List<ColorRGBA> colors = inCapsule.readSavableArrayList( "colorGradient", null );
        if ( colors != null ) {
	        // First is the north pole, second is the equator, optional third is the south pole
	        applyGradient( colors );
        }
    }
    
    /** Apply texture coordinate scaling to selected 'faces' of the sphere */
    @Override
    public void scaleFaceTextureCoordinates(
    	Vector2f	pScaleTexture
    ,	int			pFaceMask
    ) {
        VertexBuffer vtxBuffer = getBuffer( Type.TexCoord );
        FloatBuffer aBuffer = resolveTexCoordBuffer( vtxBuffer );
        
        // Nothing really custom yet
        boolean doBack = (pFaceMask & Face.BACK.getMask()) != 0;
        boolean doFront = (pFaceMask & Face.FRONT.getMask()) != 0;
        boolean doCrust = (pFaceMask & Face.SURFACE.getMask()) != 0;

        if ( doCrust ) {
	        for( int i = 0, j = aBuffer.limit() / 2; i < j; i += 1 ) {
	            float x = aBuffer.get();
	            float y = aBuffer.get();
	            aBuffer.position( aBuffer.position()-2 );
	            x *= pScaleTexture.x;
	            y *= pScaleTexture.y;
	            aBuffer.put(x).put(y);
	        }
	        aBuffer.clear();
	        vtxBuffer.updateData( aBuffer );
        }
    }
    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( this.getClass()
													, sCSGSphereRevision
													, sCSGSphereDate
													, pBuffer ) );
	}

}
/** Helper class for use during the geometry calculations */
class CSGSphereContext
	extends CSGRadialContext
{
	/** Fast access to r squared */
	float						mRadiusSquared;
	
	/** Uninitialized context */
	CSGSphereContext(
	) {
	}
	
    /** Initialize the context */
    CSGSphereContext(
    	int						pAxisSamples
    ,	int						pRadialSamples
    ,	int						pGeneratedFacesMask
    ,	float					pRadius
    ,	CSGSphere.TextureMode	pTextureMode
    ,	boolean					pAllocateBuffers
    ) {	
    	initializeContext( pAxisSamples, pRadialSamples, pGeneratedFacesMask, pAllocateBuffers );	
   	
    	// The rSquared is constant for a given radius and can be calculated here
    	mRadiusSquared = pRadius * pRadius;
    }
    
    /** How many slices are needed? */
    @Override
    protected int resolveSliceCount(
    	int			pAxisSamples
    ,	int			pGeneratedFacesMask
    ) {
		// Even though the north/south poles are a single point, we need to 
		// generate different textures and normals for the two EXTRA end slices if closed
    	int sliceCount = pAxisSamples;
    	if ( Face.BACK.maskIncludesFace( pGeneratedFacesMask ) ) {
    		sliceCount += 1;
    	}
    	if ( Face.FRONT.maskIncludesFace( pGeneratedFacesMask ) ) {
    		sliceCount += 1;
    	}
    	return( sliceCount );
    }

    /** How many vertices are produced? */
    @Override
    protected int resolveVetexCount(
    	int			pSliceCount
    ,	int			pRadialSamples
    ,	int			mGeneratedFacesMask
    ) {
    	// The sphere has RadialSamples (+1 for the overlapping end point) times the number of slices
    	int vertCount = pSliceCount * (pRadialSamples + 1);
    	return( vertCount );
    }

}