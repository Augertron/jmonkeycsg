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
import java.util.List;

import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGRadialCapped.TextureMode;


/** A CSG Cylinder is a 'capped' radial with standard slices that are simply perpendicular to the
 	zAxis.
 */
public class CSGCylinder 
	extends CSGRadialCapped
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGCylinderRevision="$Rev$";
	public static final String sCSGCylinderDate="$Date$";

	
	/** Standard null constructor */
	public CSGCylinder(
	) {
		this( 32, 32, 1, 1, 1, true, false, TextureMode.FLAT, false );
	}
    public CSGCylinder(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadiusFront
    , 	float 		pZExtent
    ) {
		this( pAxisSamples, pRadialSamples, pRadiusFront, pRadiusFront, pZExtent, true, false, TextureMode.FLAT, true );
	}
	
    public CSGCylinder(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadiusFront
    ,	float		pRadiusBack
    , 	float 		pZExtent
    , 	boolean 	pClosed
    , 	boolean 	pInverted
    ,	TextureMode	pTextureMode
    ,	boolean		pReadyGeometry
    ) {
    	super( pAxisSamples, pRadialSamples, pRadiusFront, pRadiusBack, pZExtent, pClosed, pInverted, pTextureMode );

    	if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
    }

    /** Rebuilds the cylinder based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
    	if ( true ) { 
            // Standard 'capped' construction driven by the callbacks
    		super.updateGeometryProlog();
            return;
    	}
    }
    
    /** OVERRIDE: allocate the context */
    @Override
    protected CSGRadialContext getContext(
    	boolean		pAllocateBuffers
    ) {
        // Allocate buffers for position/normals/texture
    	// We generate an extra vertex around the radial sample where the last
    	// vertex overlays the first.  
		// And even though the north/south poles are a single point, we need to 
		// generate different textures and normals for the two EXTRA end slices if closed
    	CSGCylinderContext aContext 
    		= new CSGCylinderContext( mAxisSamples, mRadialSamples, mGeneratedFacesMask, mTextureMode, mScaleSlice, pAllocateBuffers );

    	return( aContext );
    }

    /** OVERRIDE: fractional speaking, where are we along the z axis */
    @Override
    protected float getZAxisFraction( 
    	CSGAxialContext 	pContext
    ,	int					pSurface
    ) {
    	// Standard even slices
    	return( super.getZAxisFraction( pContext, pSurface ) );
    }
    
    /** OVERRIDE: where is the center of the given slice */
    @Override
    protected Vector3f getSliceCenter(
    	CSGAxialContext 	pContext
    ,	int					pSurface
    ,	Vector3f			pUseVector
    ,	TempVars			pTempVars
    ) {
    	// By default, the center is ON the zAxis at the given absolute z position
    	return( super.getSliceCenter( pContext, pSurface, pUseVector, pTempVars ) );
    }
      
    /** OVERRIDE: compute the position of a given radial vertex */
    @Override
    protected Vector3f getRadialPosition(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
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
        // Apply rotation
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
    	CSGCylinderContext myContext = (CSGCylinderContext)pContext;
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
    	return( pUseVector );
    }
    
    /** Support cylinder specific configuration parameters */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );

        // Let the super do its thing
        super.read( pImporter );

        // Standard trigger of updateGeometry() to build the shape 
        this.updateGeometry();
        
        // Any special color processing?
        List<ColorRGBA> colors = inCapsule.readSavableArrayList( "colorGradient", null );
        if ( colors != null ) {
	        // First is the north pole, second is the equator, optional third is the south pole
	        applyGradient( colors );
        }
    }

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( this.getClass()
													, sCSGCylinderRevision
													, sCSGCylinderDate
													, pBuffer ) );
	}
}
/** Helper class for use during the geometry calculations */
class CSGCylinderContext
	extends CSGRadialCappedContext
{
    /** Initialize the context */
    CSGCylinderContext(
    	int							pAxisSamples
    ,	int							pRadialSamples
    ,	int							pGeneratedFacesMask
    ,	CSGRadialCapped.TextureMode	pTextureMode
    ,	Vector2f					pScaleSlice
    ,	boolean						pAllocateBuffers
    ) {	
    	super( pAxisSamples, pRadialSamples, pGeneratedFacesMask, pTextureMode, pScaleSlice, pAllocateBuffers );
    }

}
