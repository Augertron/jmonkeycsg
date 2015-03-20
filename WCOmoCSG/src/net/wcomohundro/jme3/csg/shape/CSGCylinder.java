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
import net.wcomohundro.jme3.csg.shape.CSGSphere.TextureMode;


/** Specialization/Copy of standard JME3 Cylinder that applies a standard texture to its end caps
 */
public class CSGCylinder 
	extends CSGMesh
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGCylinderRevision="$Rev$";
	public static final String sCSGCylinderDate="$Date$";

    public enum TextureMode {
        FLAT			// The texture of the end caps is simply flat
    ,   UNIFORM			// Texture of end caps is flat and uniform in scale to the curved surface
    }
	/** Identify the 3 faces of the cylinder  */
	public enum Face {
		BACK, SIDES, FRONT, NONE;
		
		private int mask;
		Face() {
			mask = (this.name().equals("NONE")) ? 0 : (1 << this.ordinal());
		}
		public int getMask() { return mask; }
	}

	/** How many sample circles to generate along the axis */
    protected int 			mAxisSamples;
    /** How many point around the circle to generate
     		The more points, the smoother the circular surface.
     		The fewer points, and it looks like a triangle/cube/pentagon/...
     */
    protected int 			mRadialSamples;

    /** The radius of the front cap */
    protected float 		mRadiusFront;
    /** The radius of the back cap */
    protected float 		mRadiusBack;

    /** How tall is the cylinder along the z axis (where the 'extent' is half the total height) */
    protected float 		mExtentZ;
    /** If closed, then the front and end caps are produced */
    protected boolean 		mClosed;
    /** If inverted, then the cylinder is intended to be viewed from the inside */
    protected boolean 		mInverted;
	/** Marker to enforce 'uniform' texture (once around the cylinder matches once across the end cap) */
	protected TextureMode	mTextureMode;
	
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
        mAxisSamples = pAxisSamples;
        mRadialSamples = pRadialSamples;
        mRadiusFront = pRadiusFront;
        mRadiusBack = pRadiusBack;
        mExtentZ = pZExtent;
        mClosed = pClosed;
        mInverted = pInverted;
        mTextureMode = pTextureMode;
    }

    /** Configuration accessors */
    public int getAxisSamples() { return mAxisSamples; }
    public float getZExtent() { return mExtentZ; }
    public float getHeight() { return mExtentZ * 2; }
    public int getRadialSamples() { return mRadialSamples; }
    public float getRadiusFront() { return mRadiusFront; }
    public float getRadiusBack() { return mRadiusBack; }
    public boolean isClosed() { return mClosed; }
    public boolean isInverted() { return mInverted; }
    public TextureMode getTextureMode() { return mTextureMode; }

    
    /** Rebuilds the cylinder based on the current set of configuration parameters */
    @Override
    public void updateGeometry(
    ) {
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

        // generate geometry
        float inverseRadial = 1.0f / mRadialSamples;
        float inverseAxisLess = 1.0f / (mAxisSamples - 1);
        float inverseAxisLessTexture = 1.0f / (aSliceCount - 1);
        float fullHeight = mExtentZ * 2;
        float textureBias = (mTextureMode == TextureMode.UNIFORM) ? FastMath.INV_PI : 1.0f;

        // Generate points on the unit circle to be used in computing the mesh
        // points on a cylinder slice. 
        float[] sin = new float[ mRadialSamples ];
        float[] cos = new float[ mRadialSamples ];

        for (int radialCount = 0; radialCount < mRadialSamples; radialCount += 1 ) {
            float angle = FastMath.TWO_PI * inverseRadial * radialCount;
            cos[ radialCount ] = FastMath.cos( angle );
            sin[ radialCount ] = FastMath.sin( angle );
        }
        // calculate normals
        Vector3f[] vNormals = null;
        Vector3f vNormal = Vector3f.UNIT_Z;

        if ((fullHeight > 0.0f) && ( mRadiusFront != mRadiusBack)) {
        	// Account for the slant of the normal when the radii differ
            vNormals = new Vector3f[ mRadialSamples ];
            Vector3f vHeight = Vector3f.UNIT_Z.mult( fullHeight );
            Vector3f vRadial = new Vector3f();

            for (int radialCount = 0; radialCount < mRadialSamples; radialCount++) {
                vRadial.set(cos[radialCount], sin[radialCount], 0.0f);
                Vector3f vRadius = vRadial.mult( mRadiusFront );
                Vector3f vRadius2 = vRadial.mult( mRadiusBack );
                Vector3f vMantle = vHeight.subtract(vRadius2.subtract(vRadius));
                Vector3f vTangent = vRadial.cross(Vector3f.UNIT_Z);
                vNormals[radialCount] = vMantle.cross(vTangent).normalize();
            }
        }
        // Generate the cylinder itself, working up the axis, generating vertices at each slice
        Vector3f tempNormal = new Vector3f();
        for (int axisCount = 0, i = 0; axisCount < aSliceCount; axisCount++, i++) {
        	// Percent of distance along the axis
            float axisFraction;
            float axisFractionTexture;
            int topBottom = 0;
            if ( !mClosed ) {
            	// Each slice is evenly spaced
                axisFraction = axisCount * inverseAxisLess;
                axisFractionTexture = axisFraction;
            } else {
            	// The first slice is the closed bottom, and the last slice is the closed top
                if (axisCount == 0) {
                    topBottom = -1; // bottom
                    axisFraction = 0;
                    axisFractionTexture = inverseAxisLessTexture;
                } else if (axisCount == aSliceCount - 1) {
                    topBottom = 1; // top
                    axisFraction = 1;
                    axisFractionTexture = 1 - inverseAxisLessTexture;
                } else {
                    axisFraction = (axisCount - 1) * inverseAxisLess;
                    axisFractionTexture = axisCount * inverseAxisLessTexture;
                }
            }
            // compute center of slice
            float z = -mExtentZ + fullHeight * axisFraction;
            Vector3f sliceCenter = new Vector3f(0, 0, z);

            // Compute slice vertices, with duplication at the end point
            int save = i;
            for (int radialCount = 0; radialCount < mRadialSamples; radialCount++, i++) {
            	// How far around the circle are we?
                float radialFraction = radialCount * inverseRadial;
                
                // And what is the normal to this portion of the arc?
                tempNormal.set(cos[radialCount], sin[radialCount], 0.0f);

                if (vNormals != null) {
                	// Use the slant
                    vNormal = vNormals[radialCount];
                } else if ( mRadiusFront == mRadiusBack ) {
                	// Use the standard perpendicular (with z of zero)
                    vNormal = tempNormal;
                }
                if (topBottom == 0) {
                	// NOT at a closed end cap, so just just handle the inversion
                    if ( !mInverted ) {
                        nb.put(vNormal.x).put(vNormal.y).put(vNormal.z);
                    } else {
                        nb.put(-vNormal.x).put(-vNormal.y).put(-vNormal.z);
                    }
                } else {
                	// Account for top/bottom, where the normal runs along the z axis
                    nb.put(0).put(0).put( topBottom * (mInverted ? -1 : 1) );
                }
                // What texture applies?
                if ( topBottom == 0 ) {
                	// Map the texture along the curved surface
                	tb.put((mInverted ? 1 - radialFraction : radialFraction))
                        	.put(axisFractionTexture);
                } else if ( topBottom < 0 ) {
                	// Map the texture to the bottom cap (facing the other way, right to left)
                	tb.put( 0.5f - ((tempNormal.x / 2.0f) * textureBias))
        					.put( 0.5f + ((tempNormal.y / 2.0f) * textureBias) );
                } else {
                	// Map the texture to the top cap (simple left to right)
                	tb.put( 0.5f + ((tempNormal.x / 2.0f) * textureBias))
        					.put( 0.5f + ((tempNormal.y / 2.0f) * textureBias) );
                }
                // Where is the point along the circle ?
                tempNormal.multLocal((mRadiusFront - mRadiusBack) * axisFraction + mRadiusBack )
                        .addLocal(sliceCenter);
                pb.put(tempNormal.x).put(tempNormal.y).put(tempNormal.z);
            }
            // Copy the first generated point of the trip around the circle as the last
            BufferUtils.copyInternalVector3( pb, save, i );
            BufferUtils.copyInternalVector3( nb, save, i );
            if ( topBottom == 0 ) {
            	// Full circle, so select the extremity of the texture
            	tb.put((mInverted ? 0.0f : 1.0f)).put(axisFractionTexture);
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
    }
    
    /** Support texture scaling */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    	
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.write( mAxisSamples, "axisSamples", 32 );
        outCapsule.write( mRadialSamples, "radialSamples", 32 );
        outCapsule.write( mRadiusFront, "radius", 1 );
        outCapsule.write( mRadiusBack, "radius2", mRadiusFront );
        outCapsule.write( mExtentZ, "zExtent", 1 );
        outCapsule.write( mClosed, "closed", true );
        outCapsule.write( mInverted, "inverted", false );
        outCapsule.write( mTextureMode, "textureMode", TextureMode.FLAT );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mAxisSamples = inCapsule.readInt( "axisSamples", 32 );
        mRadialSamples = inCapsule.readInt( "radialSamples", 32 );
        mRadiusFront = inCapsule.readFloat( "radius", 1 );
        mRadiusBack = inCapsule.readFloat( "radius2", mRadiusFront );
        mExtentZ = inCapsule.readFloat( "zExtent", 0 );
        if ( mExtentZ == 0 ) {
        	float aHeight = inCapsule.readFloat( "height", 0 );
        	mExtentZ = (aHeight > 0) ? (aHeight / 2.0f) : 1;
        }
        mClosed = inCapsule.readBoolean( "closed", true );
        mInverted = inCapsule.readBoolean( "inverted", false );
        mTextureMode = inCapsule.readEnum( "textureMode", TextureMode.class, TextureMode.FLAT );

        // Let the super do its thing (which will updateGeometry as needed)
        super.read( pImporter );
    }
    
    /** Apply texture coordinate scaling to selected 'faces' of the cylinder */
    @Override
    public void scaleFaceTextureCoordinates(
    	float		pX
    ,	float		pY
    ,	int			pFaceMask
    ) {
        VertexBuffer tc = getBuffer(Type.TexCoord);
        if (tc == null)
            throw new IllegalStateException("The mesh has no texture coordinates");

        if (tc.getFormat() != VertexBuffer.Format.Float)
            throw new UnsupportedOperationException("Only float texture coord format is supported");

        if (tc.getNumComponents() != 2)
            throw new UnsupportedOperationException("Only 2D texture coords are supported");

        FloatBuffer aBuffer = (FloatBuffer)tc.getData();
        aBuffer.clear();
        
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
                	if ( (pFaceMask & Face.BACK.getMask()) != 0 ) {
                		// Actively processing the back
                		whichFace = Face.BACK;
                	}
                } else if (axisCount == aSliceCount - 1) {
                	if ( (pFaceMask & Face.FRONT.getMask()) != 0 ) {
                		// Actively processing the front
                		whichFace = Face.FRONT;
                	}
                } else {
                	if ( (pFaceMask & Face.SIDES.getMask()) != 0 ) {
                		// Actively processing the sides
                		whichFace = Face.SIDES;
                	}
                }
            } else {
            	if ( (pFaceMask & Face.SIDES.getMask()) != 0 ) {
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
	    			float x = aBuffer.get( index  );
	    			float y = aBuffer.get( index + 1 );
	
	                x *= pX;
	                y *= pY;
	                aBuffer.put( index++, x ).put( index++, y);
            	}
            }
    	}
    	aBuffer.clear();
        tc.updateData( aBuffer );
    }
    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGCylinderRevision
													, sCSGCylinderDate
													, pBuffer ) );
	}

}
