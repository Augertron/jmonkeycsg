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
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;

import static com.jme3.util.BufferUtils.*;

import java.io.IOException;
import java.nio.FloatBuffer;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;


/** A CSG Pipe is a 'capped' radial whose slices follow a given curve
 */
public class CSGPipe 
	extends CSGRadialCapped
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGPipeRevision="$Rev: 39 $";
	public static final String sCSGPipeDate="$Date: 2015-04-06 13:21:03 -0500 (Mon, 06 Apr 2015) $";

	/** The splice that determines the positioning of the various slices */
	protected Spline	mSlicePath;
	
	
	/** Standard null constructor */
	public CSGPipe(
	) {
		this( 32, 32, 1, 1, 1, null, true, false, TextureMode.FLAT );
	}
	
    public CSGPipe(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadiusFront
    ,	float		pRadiusBack
    , 	float 		pZExtent
    ,	Spline		pSlicePath
    , 	boolean 	pClosed
    , 	boolean 	pInverted
    ,	TextureMode	pTextureMode
    ) {
    	super( pAxisSamples, pRadialSamples, pRadiusFront, pRadiusBack, pZExtent, pClosed, pInverted, pTextureMode );
    	setSlicePath( pSlicePath );
    }
    
    /** Accessor to the curve that provides the path of the slices */
    public Spline getSlicePath() { return mSlicePath; }
    public void setSlicePath( 
    	Spline pCurve 
    ) { 
    	if ( pCurve == null ) {
    		// No explicit curve, so just follow the zAxis
    		Vector3f[] endPoints 
    			= new Vector3f[] { new Vector3f( 0, 0, mExtentZ ), new Vector3f( 0, 0, -mExtentZ ) };
    		mSlicePath = new Spline( SplineType.Linear, endPoints, 0, false );
    	} else {
    		// Follow the path given
    		mSlicePath = pCurve; 
    	}
    }
    

    /** Rebuilds the cylinder based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
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
    	// across the full range of z
    	return( super.getZAxisFraction( pContext, pSurface ) );
    }
    
    /** OVERRIDE: where is the center of the given slice */
    @Override
    protected Vector3f getSliceCenter(
    	Vector3f			pUseVector
    ,	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// By default, the center is ON the zAxis at the given absolute z position
    	return( super.getSliceCenter( pUseVector, pContext, pSurface ) );
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
    	CSGPipeContext myContext = (CSGPipeContext)pContext;
    	if ( pSurface != 0 ) {
    		// Backface/Frontface processing perpendicular to x/y plane, using the -1/+1 of the surface
    		pUseVector.set( 0, 0, (float)pSurface );
    	} else {
    		// Use the prebuilt, cached normal
    		pUseVector.set( 0, 0, 0 ); 
    		
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

