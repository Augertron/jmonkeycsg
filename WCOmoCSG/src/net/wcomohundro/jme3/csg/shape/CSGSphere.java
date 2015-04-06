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
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.scene.shape.Sphere.TextureMode;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

import static com.jme3.util.BufferUtils.*;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGCylinder.Face;


/** Specialization/Copy of standard JME3 Sphere for unification with other CSG shapes
 	Like CSGCylinder (and the original Sphere) we render slices of the sphere along the
 	z-axis.  AxisSamples determines how many slices to produce, and RadialSamples 
 	determines how many vertices to produce around the circle for each slice.
 	
 	I have altered the how textures are applied to the poles to (ZAXIS, PROJECTED) so that
 	the swirlly radial distortion is eliminated.
 	 
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
    
	/** For possible future enhances to texture scaling  */
	public enum Face {
		BACK, CRUST, FRONT, NONE;
		
		private int mask;
		Face() {
			mask = (this.name().equals("NONE")) ? 0 : (1 << this.ordinal());
		}
		public int getMask() { return mask; }
	}

    /** When generating the slices along the z-axis, evenly space them (else create more near the extremities) */
    protected boolean 		mEvenSlices;
	/** Marker to enforce 'uniform' texture (once around the cylinder matches once across the end cap) */
	protected TextureMode	mTextureMode;
	
	public CSGSphere(
	) {
		this( 32, 32, 1, false, false, TextureMode.ZAXIS );
	}
	
    public CSGSphere(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadius
    , 	boolean 	pUseEvenSlices
    , 	boolean 	pInverted
    ,	TextureMode	pTextureMode
    ) {
    	super( pAxisSamples, pRadialSamples, pRadius, true, pInverted, false );
        mEvenSlices = pUseEvenSlices;
        mTextureMode = pTextureMode;
    }

    /** Configuration accessors */
    public boolean hasEvenSlices() { return mEvenSlices; }
    public void setEvenSlices( boolean pFlag ) { mEvenSlices = pFlag; }
    
    public TextureMode getTextureMode() { return mTextureMode; }
    public void setTextureMode( TextureMode pTextureMode ) { mTextureMode = pTextureMode; }

    
    /** Rebuilds the sphere based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
    	if ( true ) { 
            // Allocate buffers for position/normals/texture
        	CSGRadialContext aContext = getContext();
        	
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
            return;
    	}
/********
        // Allocate buffers for position/normals/texture
    	// We generate an extra vertex around the radial sample where the last
    	// vertex overlays the first.  And even though the north/south poles are
    	// a single point, we need to generate different textures coordinates for
    	// the pole for each different radial so we need a full set of vertices
        int aSliceCount = (mClosed) ? mAxisSamples + 2 : mAxisSamples;
        int vertCount = aSliceCount  * (mRadialSamples + 1);

        FloatBuffer posBuf = BufferUtils.createVector3Buffer(vertCount);
        FloatBuffer normBuf = BufferUtils.createVector3Buffer(vertCount);
        FloatBuffer texBuf = BufferUtils.createVector2Buffer(vertCount);

        setBuffer( Type.Position, 3, posBuf );
        setBuffer( Type.Normal, 3, normBuf );
        setBuffer( Type.TexCoord, 2, texBuf );

        // Percentage of the whole circle that each radial sample takes
        float inverseRadialSamples = 1.0f / mRadialSamples;
        
        // Percentage of the full zExtent (unit of -1 to +1) that each slice takes
        float zAxisFactor = 2.0f / (aSliceCount - 1);

        // Generate points on the unit circle to be used in computing the mesh
        // points on a sphere slice.
        CSGRadialCoord[] coordList = getRadialCoordinates( mRadialSamples, mFirstRadial );

        // Avoid some object churn by using the thread-specific 'temp' variables
        TempVars vars = TempVars.get();
	    int index = 0;
        try {
	        // Generate the sphere itself
	        float rSquared = mRadius * mRadius;

	        for( int iZ = 1; iZ < (aSliceCount - 1); iZ += 1 ) {
	        	// Angle based on where we are along the zAxis
	            float angleFraction = FastMath.HALF_PI * (-1.0f + (zAxisFactor * iZ)); // in (-pi/2, pi/2)
                float polarFraction = (FastMath.HALF_PI - FastMath.abs( angleFraction )) / FastMath.PI;

	            // Position along zAxis as a +/- percentage
	            float zAxisFraction;
	            if ( mEvenSlices ) {
	            	// Spread the slices evenly along the zAxis
	            	zAxisFraction = -1.0f + zAxisFactor * iZ; // in (-1, 1)
	            } else {
	            	// Generate more slices where the values change the quickest
	            	zAxisFraction = FastMath.sin( angleFraction ); // in (-1,1)
	            }
	            // Absolute position along the zAxis
	            float zAxis = mExtentZ * zAxisFraction;
	
	            // compute center of slice
	            Vector3f sliceCenter = vars.vect2.set( Vector3f.ZERO );
	            sliceCenter.z += zAxis;
	
	            // compute radius of slice
	            // For a circle, where zAxis is the distance along the axis from the center, simple
	            // aSquared + bSquared = cSquared, means that zAxisSquared + sliceRadiusSquared = rSquared
	            // 		sliceRadius == SquareRoot( rSquared - zAxisSquared)
	            float sliceRadius 
	            	= FastMath.sqrt( FastMath.abs( (rSquared - (zAxis * zAxis))) );
	
	            // Compute slice vertices with duplication at end point
	            int iSave = index;
                float textureX = 0, textureY = 0;
                switch( mTextureMode ) {
                case ZAXIS:
                    textureY = 0.5f * (zAxisFraction + 1.0f);
                    break;
                case PROJECTED:
                	textureY = FastMath.INV_PI * (FastMath.HALF_PI + FastMath.asin( zAxisFraction ));
                    break;
                }
	            for( int iRadial = 0; iRadial < mRadialSamples; iRadial += 1 ) {
	            	// Get the vector on the surface for this radial position
	            	CSGRadialCoord aCoord = coordList[ iRadial ];
	                Vector3f radialVector = vars.vect3.set( aCoord.mCosine, aCoord.mSine, 0 );
	                
	                // Account for the actual radius
	                Vector3f posVector = radialVector.mult( sliceRadius, vars.vect1 );
	                
	                // Account for the center
	                posVector.addLocal( sliceCenter );
	                posBuf.put( posVector.x ).put( posVector.y ).put( posVector.z );
	
	                // What is the normal?
	                Vector3f normVector = posVector.normalizeLocal();
	                if ( !mInverted ) {
	                    normBuf.put( normVector.x ).put( normVector.y ).put( normVector.z );
	                } else {
	                    normBuf.put( -normVector.x ).put( -normVector.y ).put( -normVector.z );
	                }
	                // Where are we radially along the surface?
	                float radialFraction = iRadial * inverseRadialSamples; // in [0,1)
	                switch( mTextureMode ) {
	                case ZAXIS:
	                case PROJECTED:
	                	textureX = radialFraction;
	                    break;
	                case POLAR:
	                    textureX = polarFraction * aCoord.mCosine + 0.5f;
	                    textureY = polarFraction * aCoord.mSine + 0.5f;
	                    break;
	                }
	                texBuf.put( textureX ).put( textureY );
	                index += 1;
	            }
	            // Copy the first radial vertex to the end
	            BufferUtils.copyInternalVector3( posBuf, iSave, index );
	            BufferUtils.copyInternalVector3( normBuf, iSave, index );
	
	            switch( mTextureMode ) {
	            case ZAXIS:
	            case PROJECTED:
	            	textureX = 1.0f;
	                break;
	            case POLAR:
	                textureX = polarFraction + 0.5f;
	                textureY = 0.5f;
	                break;
	            }
                texBuf.put( textureX ).put( textureY );
	            
	            index += 1;
	        }
        } finally {
        	// Return the borrowed vectors
        	vars.release();
        }
        // South pole (at the back, along zAxis, facing backwards <unless inverted>)
        int southPoleIndex = index;
        for( int iRadial = 0; iRadial < mRadialSamples; iRadial += 1 ) {
	        posBuf.put( 0 ).put( 0 ).put( -mRadius );
	        normBuf.put( 0 ).put( 0 ).put( (mInverted) ? 1 : -1 );

	        if ( mTextureMode == TextureMode.POLAR ) {
	        	// Spreading the texture across the pole, so the pole itself is dead center
	            texBuf.put( 0.5f ).put( 0.5f );
	        } else {
	        	// x is following the radial, and y is following the zAxis
                float radialFraction = iRadial * inverseRadialSamples; // in [0,1)
	            texBuf.put( radialFraction ).put( 0.0f );
	        }
	        index += 1;
        }
        // North pole (at the front, along zAxis, facing forwards <unless inverted>)
        int northPoleIndex = index;
        for( int iRadial = 0; iRadial < mRadialSamples; iRadial += 1 ) {
	        posBuf.put( 0 ).put( 0 ).put( mRadius );
	        normBuf.put( 0 ).put( 0 ).put( (mInverted) ? -1 : 1 );
	
	        if ( mTextureMode == TextureMode.POLAR ) {
	        	// Spreading the texture across the pole, so the pole itself is dead center
	            texBuf.put( 0.5f ).put( 0.5f );
	        } else {
	        	// x is following the radial, and y is following the zAxis
                float radialFraction = iRadial * inverseRadialSamples; // in [0,1)
	            texBuf.put( radialFraction ).put( 1.0f );
	        }
        }
        // Allocate the indices
        int triCount = 2 * (aSliceCount - 2) * mRadialSamples;
        ShortBuffer idxBuf = BufferUtils.createShortBuffer( 3 * triCount );
        setBuffer( Type.Index, 3, idxBuf );

        for( int iZ = 0, iZStart = 0; iZ < (aSliceCount - 3); iZ++ ) {
            int i0 = iZStart;
            int i1 = i0 + 1;
            iZStart += (mRadialSamples + 1);
            int i2 = iZStart;
            int i3 = i2 + 1;
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
        // South pole triangles
        for( int i = 0; i < mRadialSamples; i += 1 ) {
            if ( !mInverted ) {
                idxBuf.put( (short) i );
                idxBuf.put( (short) southPoleIndex++ );
                idxBuf.put( (short) (i + 1) );
            } else { // inside view
                idxBuf.put( (short) i );
                idxBuf.put( (short) (i + 1) );
                idxBuf.put( (short) southPoleIndex++ );
            }
        }
        // North pole triangles
        int iOffset = (aSliceCount - 3) * (mRadialSamples + 1);
        for( int i = 0; i < mRadialSamples; i += 1 ) {
            if ( !mInverted ) {
                idxBuf.put( (short) (i + iOffset) );
                idxBuf.put( (short) (i + 1 + iOffset) );
                idxBuf.put( (short) northPoleIndex++ );
            } else { // inside view
                idxBuf.put( (short) (i + iOffset) );
                idxBuf.put( (short) northPoleIndex++ );
                idxBuf.put( (short) (i + 1 + iOffset) );
            }
        }
        // Establish the bounds
        updateBound();
        setStatic();
*******/
    }
    
    /** FOR SUBCLASS OVERRIDE: allocate the context */
    @Override
    protected CSGRadialContext getContext(
    ) {
    	// Allocate buffers for position/normals/texture
    	// We generate an extra vertex around the radial sample where the last
    	// vertex overlays the first.  
		// And even though the north/south poles are a single point, we need to 
		// generate different texture coordinates for the pole for each different 
		// radial, so we need a full set of vertices
    	int sliceCount = (mClosed) ? mAxisSamples + 2 : mAxisSamples;
   	 	CSGSphereContext aContext = new CSGSphereContext( sliceCount, mRadialSamples );
    	 
    	// The rSquared is constant for a given radius and can be calculated here
    	aContext.mRadiusSquared = mRadius * mRadius;
    	return( aContext );
    }
    
    /** FOR SUBCLASS OVERRIDE: fractional speaking, where are we along the z axis */
    @Override
    protected float getZAxisFraction( 
    	CSGRadialContext 	pContext
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
    
    /** FOR SUBCLASS OVERRIDE: where is the center of the given slice */
    @Override
    protected Vector3f getSliceCenter(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// By default, the center is ON the zAxis at the given absolute z position 
    	return( super.getSliceCenter( pUseVector, pContext, pSurface ) );
    }
    
    /** FOR SUBCLASS OVERRIDE: what is the radius of the Radial at this slice */
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
    
    /** FOR SUBCLASS OVERRIDE: prepare the texture for the entire slice */
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
    		// Special backface/processing processing, using the -1/+1 of the surface
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
        
        mEvenSlices = inCapsule.readBoolean( "useEvenSlices", false );
        mTextureMode = inCapsule.readEnum( "textureMode", TextureMode.class, TextureMode.ZAXIS );

        // Super processing of zExtent reads to zero to allow us to apply an appropriate default here
        if ( mExtentZ == 0 ) {
        	// For a cylinder the default is to match the radius
        	mExtentZ = mRadius;
        } else if ( mRadius == 1 ) {
        	// An explicit Z extent overrides a default radius
        	mRadius = mExtentZ;
        } else if ( mExtentZ != mRadius ) {
        	// So what does this mean?
        	throw new IOException( "CSGSphere radius/zExtent mismatch" );
        }
        // Standard trigger of updateGeometry() to build the shape 
        this.updateGeometry();
    }
    
    /** Apply texture coordinate scaling to selected 'faces' of the sphere */
    @Override
    public void scaleFaceTextureCoordinates(
    	float		pX
    ,	float		pY
    ,	int			pFaceMask
    ) {
        VertexBuffer tc = getBuffer(Type.TexCoord);
        if ( tc == null ) {
            throw new IllegalStateException("The mesh has no texture coordinates");
        }
        if ( tc.getFormat() != VertexBuffer.Format.Float ) {
            throw new UnsupportedOperationException("Only float texture coord format is supported");
        }
        if ( tc.getNumComponents() != 2 ) {
            throw new UnsupportedOperationException("Only 2D texture coords are supported");
        }
        FloatBuffer aBuffer = (FloatBuffer)tc.getData();
        aBuffer.clear();
        
        // Nothing really custom yet
        boolean doBack = (pFaceMask & Face.BACK.getMask()) != 0;
        boolean doFront = (pFaceMask & Face.FRONT.getMask()) != 0;
        boolean doCrust = (pFaceMask & Face.CRUST.getMask()) != 0;

        if ( doCrust ) {
	        for( int i = 0, j = aBuffer.limit() / 2; i < j; i += 1 ) {
	            float x = aBuffer.get();
	            float y = aBuffer.get();
	            aBuffer.position( aBuffer.position()-2 );
	            x *= pX;
	            y *= pY;
	            aBuffer.put(x).put(y);
	        }
	        aBuffer.clear();
	        tc.updateData( aBuffer );
        }
    }
    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		if ( pBuffer.length() > 0 ) pBuffer.append( "\n" );
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGSphereRevision
													, sCSGSphereDate
													, pBuffer ) );
	}

}
/** Helper class for use during the geometry calculations */
class CSGSphereContext
	extends CSGRadialContext
{
	float	mRadiusSquared;
	
    /** Initialize the context */
    CSGSphereContext(
    	int		pSliceCount
    ,	int		pRadialSamples
    ) {	
    	// The sphere has RadialSamples (+1 for the overlapping end point) times the number of slices
    	super( pSliceCount, pRadialSamples, pSliceCount * (pRadialSamples + 1) );
    }

}