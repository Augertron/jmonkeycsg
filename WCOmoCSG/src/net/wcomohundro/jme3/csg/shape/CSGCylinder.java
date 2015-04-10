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
import com.jme3.util.BufferUtils;

import static com.jme3.util.BufferUtils.*;

import java.io.IOException;
import java.nio.FloatBuffer;

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
		this( 32, 32, 1, 1, 1, true, false, TextureMode.FLAT );
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
    ) {
    	super( pAxisSamples, pRadialSamples, pRadiusFront, pRadiusBack, pZExtent, pClosed, pInverted, pTextureMode );
    }

    /** Rebuilds the cylinder based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
    	if ( true ) { 
            // Standard 'capped' construction
    		super.updateGeometryProlog();
            return;
    	}
/*********
    	// The cylinder is built from a series of 'pucks' as determined by the 
    	// axisSample count. Each puck is based on two slices (front and back) where a 
    	// vertex is generated at each radialSample point around the circle. So for 
    	// an axisSample of 2, you build one puck, while 3 = 2 pucks, 4 = 3 pucks ...
    	
    	// For ease of construction, an extra vertex is created for the (radialSample + 1) which
    	// is the same as the (0) point.
    	// We then create two triangles that lay on the edge of the puck for each radialSample.
        
        // If closed, we will generate 2 extra slices, one for top and one for bottom
        // Vertices are generated in a standard way for the these special slices, but there
        // is no puck with an edge.  Instead, the triangles describe the flat closed face.
        // For that, we will have 2 extra vertices that describe the center of the face.
        int aSliceCount = (mClosed) ? mAxisSamples + 2 : mAxisSamples;

        // Vertices = ((radialSamples + 1) * number of slices) plus 2 center points if closed
        int vertCount = (aSliceCount * (mRadialSamples + 1)) + (mClosed ? 2 : 0);
        setBuffer( Type.Position, 3, createVector3Buffer( getFloatBuffer(Type.Position), vertCount) );

        // Normals
        setBuffer( Type.Normal, 3, createVector3Buffer( getFloatBuffer(Type.Normal), vertCount) );

        // Texture co-ordinates
        setBuffer( Type.TexCoord, 2, createVector2Buffer(vertCount) );

        // Indexes based on the triangle count, where there are 2 triangles per radial sample
        // per puck. If closed, then we have radialSample triangles on top plus radialSample 
        // triangles on bottom, so the times 2 works out fine
        int aTriangleCount = (2 * mRadialSamples * (aSliceCount - 1));
        setBuffer( Type.Index, 3, createShortBuffer(getShortBuffer(Type.Index), 3 * aTriangleCount) );
        
        FloatBuffer nb = getFloatBuffer(Type.Normal);
        FloatBuffer pb = getFloatBuffer(Type.Position);
        FloatBuffer tb = getFloatBuffer(Type.TexCoord);

        // Generate geometry
        float inverseRadial = 1.0f / mRadialSamples;
        float inverseAxis = 1.0f / (mAxisSamples - 1);
        float fullHeight = mExtentZ * 2;
        
        float endcapTextureBias;
        switch( mTextureMode ) {
        case UNIFORM:
        case UNIFORM_LINEAR:
        	// UNIFORM end caps are inherently scaled so match the texture scaling of the curved surface
        	endcapTextureBias = FastMath.INV_PI;
        	break;
        default:
        	// Simple FLAT end caps act like a circular cookie-cutter applied on the texture
        	endcapTextureBias = 1.0f;
        	break;
        }
        // Generate points on the unit circle to be used in computing the mesh
        // points on a cylinder slice. 
        CSGRadialCoord[] coordList = getRadialCoordinates( mRadialSamples, mFirstRadial );

        // Calculate normals
        Vector3f[] vNormals = null;
        Vector3f vNormal = Vector3f.UNIT_Z;

        if ( (fullHeight > 0.0f) && (mRadius != mRadiusBack) ) {
        	// Account for the slant of the normal when the radii differ
            vNormals = new Vector3f[ mRadialSamples ];
            Vector3f vHeight = Vector3f.UNIT_Z.mult( fullHeight );
            Vector3f vRadial = new Vector3f();

            for( int radialCount = 0; radialCount < mRadialSamples; radialCount += 1 ) {
            	CSGRadialCoord aCoord = coordList[ radialCount ];
                vRadial.set( aCoord.mCosine, aCoord.mSine, 0 );
                
                Vector3f vRadius = vRadial.mult( mRadius );
                Vector3f vRadius2 = vRadial.mult( mRadiusBack );
                Vector3f vMantle = vHeight.subtract( vRadius2.subtract(vRadius) );
                Vector3f vTangent = vRadial.cross( Vector3f.UNIT_Z );
                vNormals[radialCount] = vMantle.cross( vTangent ).normalize();
            }
        }
        // Generate the cylinder itself, working up the axis, generating vertices at each slice
        Vector3f tempNormal = new Vector3f();
        for( int axisCount = 0, i = 0; axisCount < aSliceCount; axisCount++, i += 1 ) {
        	// Percent of distance along the axis
            float axisFraction;
            float axisFractionTexture = Float.NaN;
            int topBottom = 0;
            if ( !mClosed ) {
            	// Each slice is evenly spaced
                axisFraction = axisCount * inverseAxis;
                axisFractionTexture = axisFraction;
            } else {
            	// The first slice is the closed bottom, and the last slice is the closed top
                if (axisCount == 0) {
                    topBottom = -1; // bottom
                    axisFraction = 0;
                } else if (axisCount == aSliceCount - 1) {
                    topBottom = 1; // top
                    axisFraction = 1;
                } else {
                    axisFraction = (axisCount - 1) * inverseAxis;
                    axisFractionTexture = (axisCount - 1) * inverseAxis;
                }
            }
            // compute center of slice
            float z = -mExtentZ + fullHeight * axisFraction;
            Vector3f sliceCenter = new Vector3f(0, 0, z);

            // Compute slice vertices, with duplication at the end point
            int save = i;
            for( int radialCount = 0; radialCount < mRadialSamples; radialCount += 1, i += 1 ) {
            	// How far around the circle are we?
                float radialFraction = radialCount * inverseRadial;
                
                // And what is the normal to this portion of the arc?
               	CSGRadialCoord aCoord = coordList[ radialCount ];
                tempNormal.set( aCoord.mCosine, aCoord.mSine, 0 );

                if ( vNormals != null ) {
                	// Use the slant
                    vNormal = vNormals[radialCount];
                } else if ( mRadius == mRadiusBack ) {
                	// Use the standard perpendicular (with z of zero)
                    vNormal = tempNormal;
                }
                if ( topBottom == 0 ) {
                	// NOT at a closed end cap, so just just handle the inversion
                    if ( !mInverted ) {
                        nb.put( vNormal.x ).put( vNormal.y ).put( vNormal.z );
                    } else {
                        nb.put( -vNormal.x ).put( -vNormal.y ).put( -vNormal.z );
                    }
                } else {
                	// Account for top/bottom, where the normal runs along the z axis
                    nb.put( 0 ).put( 0 ).put( topBottom * (mInverted ? -1 : 1) );
                }
                // What texture applies?
                if ( topBottom == 0 ) {
                	// Map the texture along the curved surface
                    switch( mTextureMode ) {
                    case FLAT:
                    case UNIFORM:
                    	// Run the texture around the circle
                    	tb.put( (mInverted ? 1 - radialFraction : radialFraction) )
                    		.put( axisFractionTexture );                    	
                    	break;
                    default:
                    	// Run the texture linearly from front to back
                    	tb.put( axisFractionTexture )
                    		.put( mInverted ? radialFraction : 1 - radialFraction ); 	
                    	break;
                    }
                } else if ( topBottom < 0 ) {
                	// Map the texture to the bottom cap (facing the other way, right to left)
                	tb.put( 0.5f - ((tempNormal.x / 2.0f) * endcapTextureBias))
        					.put( 0.5f + ((tempNormal.y / 2.0f) * endcapTextureBias) );
                } else {
                	// Map the texture to the top cap (simple left to right)
                	tb.put( 0.5f + ((tempNormal.x / 2.0f) * endcapTextureBias))
        					.put( 0.5f + ((tempNormal.y / 2.0f) * endcapTextureBias) );
                }
                // Where is the point along the circle ?
                tempNormal.multLocal((mRadius - mRadiusBack) * axisFraction + mRadiusBack )
                        .addLocal(sliceCenter);
                
                pb.put( tempNormal.x ).put( tempNormal.y ).put( tempNormal.z );
            }
            // Copy the first generated point of the trip around the circle as the last
            BufferUtils.copyInternalVector3( pb, save, i );
            BufferUtils.copyInternalVector3( nb, save, i );
            if ( topBottom == 0 ) {
            	// Full circle, so select the extremity of the texture
            	switch( mTextureMode ) {
                case FLAT:
                case UNIFORM:
                	tb.put((mInverted ? 0.0f : 1.0f)).put( axisFractionTexture );
                	break;
                default:
                	tb.put( axisFractionTexture ).put( mInverted ? 1.0f : 0.0f );
                	break;
            	}
            } else {
            	// Back to the beginning of the end cap
            	BufferUtils.copyInternalVector2( tb, save, i );
            }
        }
        if ( mClosed ) {
        	// Two extra vertices for the centers of the end caps
            pb.put( 0 ).put( 0 ).put( -mExtentZ ); // bottom center
            nb.put( 0 ).put( 0 ).put( -1 * (mInverted ? -1 : 1) );
            tb.put( 0.5f ).put( 0.5f );
            
            pb.put( 0).put( 0 ).put( mExtentZ); // top center
            nb.put( 0).put( 0 ).put( 1 * (mInverted ? -1 : 1) );
            tb.put( 0.5f ).put( 0.5f );
        }
        // Map the generated set of vertices above into the appropriate triangles
        IndexBuffer ib = getIndexBuffer();
        int index = 0;
        // Process along the axis, mapping the vertices of one slice to the slice behind it
        for (int axisCount = 0, axisStart = 0; axisCount < aSliceCount - 1; axisCount++) {
        	// i0 and i1 are on this slice
            int i0 = axisStart;
            int i1 = i0 + 1;
            
            // i2 and i3 are on the slice behind
            axisStart += mRadialSamples + 1;
            int i2 = axisStart;
            int i3 = i2 + 1;
            for (int i = 0; i < mRadialSamples; i++) {
            	// Loop around the circle
                if ( mClosed && (axisCount == 0) ) {
                	// This is the bottom
                    ib.put(index++, i0++);
                    ib.put(index++, mInverted ? i1++ : vertCount - 2);
                    ib.put(index++, mInverted ? vertCount - 2 : i1++ );
                } else if (mClosed && (axisCount == aSliceCount - 2) ) {
                	// This is the top
                    ib.put(index++, i2++);
                    ib.put(index++, mInverted ? vertCount - 1 : i3++);
                    ib.put(index++, mInverted ? i3++ : vertCount - 1);
                } else {
                	// This is a standard slice composed of two triangles
                    ib.put(index++, i0++);
                    ib.put(index++, mInverted ? i2 : i1);
                    ib.put(index++, mInverted ? i1 : i2);
                    
                    ib.put(index++, i1++);
                    ib.put(index++, mInverted ? i2++ : i3++);
                    ib.put(index++, mInverted ? i3++ : i2++);
                }
            }
        }
        updateBound();
*********/
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
    	CSGCylinderContext aContext 
    		= new CSGCylinderContext( sliceCount, mRadialSamples, mClosed, mTextureMode, mScaleSlice );

    	return( aContext );
    }

    /** OVERRIDE: fractional speaking, where are we along the z axis */
    @Override
    protected float getZAxisFraction( 
    	CSGRadialContext 	pContext
    ,	int					pSurface
    ) {
    	// Standard even slices
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
    	CSGCylinderContext myContext = (CSGCylinderContext)pContext;
		if ( myContext.mRadialNormals == null ) {
    		// Precalculate and cache the normals we will need
        	myContext.mRadialNormals = new Vector3f[ mRadialSamples + 1 ];
            Vector3f vHeight = Vector3f.UNIT_Z.mult( getHeight() );
            Vector3f vRadial = new Vector3f();

            for( int radialCount = 0; radialCount < mRadialSamples + 1; radialCount += 1 ) {
            	// Perpendicular to the tangent of the circle at this radial point
            	// within the x/y plane (z = 0)
            	CSGRadialCoord aCoord = myContext.mCoordList[ radialCount ];
                vRadial.set( aCoord.mCosine, aCoord.mSine, 0 );
                
                if ( mRadius != mRadiusBack ) {
                	// If the radii are different, then the surface slants in the
                	// z Plane
                	Vector3f vRadius = vRadial.mult( mRadius );
                	Vector3f vRadius2 = vRadial.mult( mRadiusBack );
                	Vector3f vMantle = vHeight.subtract( vRadius2.subtract( vRadius ) );
                	Vector3f vTangent = vRadial.cross( Vector3f.UNIT_Z );
                	myContext.mRadialNormals[ radialCount ] = vMantle.cross( vTangent ).normalize();
                } else {
                	// Keep a copy of this normal for future fast access
                	myContext.mRadialNormals[ radialCount ] = vRadial.clone();
                }
            }
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
        // Let the super do its thing
        super.read( pImporter );

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
													, sCSGCylinderRevision
													, sCSGCylinderDate
													, pBuffer ) );
	}
}
/** Helper class for use during the geometry calculations */
class CSGCylinderContext
	extends CSGRadialCappedContext
{
    Vector3f[] 	mRadialNormals;		// Helper for the normals on each slice
	
    /** Initialize the context */
    CSGCylinderContext(
    	int							pSliceCount
    ,	int							pRadialSamples
    ,	boolean						pClosed
    ,	CSGRadialCapped.TextureMode	pTextureMode
    ,	Vector2f					pScaleSlice
    ) {	
    	super( pSliceCount, pRadialSamples, pClosed, pTextureMode, pScaleSlice );
    }

}
