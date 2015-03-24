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


/** Specialization/Copy of standard JME3 Sphere for unification with other CSG shapes
 	Like CSGCylinder (and the original Sphere) we render slices of the sphere along the
 	z-axis.  AxisSamples determines how many slices to produce, and RadialSamples 
 	determines how many vertices to produce around the circle for each slice.
 	
 	The concept of 'eccentricity' is supported to produce oblate/prolate spheroids.  
 	For e == 0 --> follow a circle and produce a regular sphere
 	For e > 0 and e < 1 --> follow an ellipse where the major axis is the radius (prolate)
 	For e >= 1 --> follow an ellipse where the minor axis is the radius (oblate)
 	
 	In all cases, every slice is circular.  The eccentricity only determines how the
 	radius of the slice is determined.  Again, in all cases, the zExtent matches the radius.
 	For e == 0 --> the xExtent and yExtent match the radius.
 	For e > 0 and e < 1 --> the xExtent and yExtent will be smaller than the radius
 	For e > 1 --> the xExtent and yExtent will be greater than the radius
 */
public class CSGSphere 
	extends CSGRadial
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSphereRevision="$Rev$";
	public static final String sCSGSphereDate="$Date$";

    public enum TextureMode {
        ZAXIS			// Wrap texture radially and along the z-axis
    ,   PROJECTED		// Wrap texture radially, but spherically project along the z-axis
    ,   POLAR			// Apply texture to each pole.  Eliminates polar distortion,
        				// but mirrors the texture across the equator
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
    	super( pAxisSamples, pRadialSamples, pRadius, pInverted );
        mEvenSlices = pUseEvenSlices;
        mTextureMode = pTextureMode;
    }

    /** Configuration accessors */
    public boolean hasEvenSlices() { return mEvenSlices; }
    public TextureMode getTextureMode() { return mTextureMode; }

    
    /** Rebuilds the cylinder based on the current set of configuration parameters */
    @Override
    public void updateGeometry(
    ) {
        // Allocate buffers for position/normals/texture
        int vertCount = (mAxisSamples - 2) * (mRadialSamples + 1) + 2;

        FloatBuffer posBuf = BufferUtils.createVector3Buffer(vertCount);
        FloatBuffer normBuf = BufferUtils.createVector3Buffer(vertCount);
        FloatBuffer texBuf = BufferUtils.createVector2Buffer(vertCount);

        setBuffer(Type.Position, 3, posBuf);
        setBuffer(Type.Normal, 3, normBuf);
        setBuffer(Type.TexCoord, 2, texBuf);

        // generate geometry
        float fInvRS = 1.0f / mRadialSamples;
        float fZFactor = 2.0f / (mAxisSamples - 1);

        // Generate points on the unit circle to be used in computing the mesh
        // points on a sphere slice.
        CSGRadialCoord[] coordList = getRadialCoordinates( mRadialSamples );

        TempVars vars = TempVars.get();
        Vector3f tempVa = vars.vect1;
        Vector3f tempVb = vars.vect2;
        Vector3f tempVc = vars.vect3;

        // Generate the sphere itself
        int index = 0;
        float rSquared = mRadius * mRadius;
        float eSquared = mEccentricity * mEccentricity;
        float oneMinusESquared = (mEccentricity >= 1)
        							?	eSquared
        							:	((mEccentricity > 0) ? (1 - eSquared) : 1);
        
        for( int iZ = 1; iZ < (mAxisSamples - 1); iZ += 1 ) {
            float fAFraction = FastMath.HALF_PI * (-1.0f + fZFactor * iZ); // in (-pi/2, pi/2)
            float fZFraction;
            if ( mEvenSlices ) {
                fZFraction = -1.0f + fZFactor * iZ; // in (-1, 1)
            } else {
                fZFraction = FastMath.sin(fAFraction); // in (-1,1)
            }
            float fZ = mRadius * fZFraction;

            // compute center of slice
            Vector3f kSliceCenter = tempVb.set(Vector3f.ZERO);
            kSliceCenter.z += fZ;

            // compute radius of slice
            // For a circle, where fZ is the distance along the axis from the center, simple
            // aSquared + bSquared = cSquared, means that fZSquared + sliceRadiusSquared = rSquared
            // 		sliceRadius == SquareRoot( rSquared - fZSquared)
            // For an ellipse, we can include the eccentricity where:
            //		y = SquareRoot( (aSquared - xSquared) * (1 - eSquared) )
            // For us, the radius is really 'a' (the major radius), and we are treating the distance
            // along the zAxis as 'x'...
            //  	sliceRadius == SquareRoot( (rSquared - fZSquared) * (1 - eSquared) )
            // which is reasonable, since a circle has eccentricity of 0, so the equations are the same
            float fSliceRadius 
            	= FastMath.sqrt(FastMath.abs( (rSquared - (fZ * fZ))) * oneMinusESquared );

            // compute slice vertices with duplication at end point
            Vector3f kNormal;
            int iSave = index;
            for (int iRadial = 0; iRadial < mRadialSamples; iRadial += 1 ) {
            	CSGRadialCoord aCoord = coordList[ iRadial ];
                float fRadialFraction = iRadial * fInvRS; // in [0,1)
                Vector3f kRadial = tempVc.set( aCoord.mCosine, aCoord.mSine, 0 );
                kRadial.mult( fSliceRadius, tempVa );
                posBuf.put( kSliceCenter.x + tempVa.x )
                		.put( kSliceCenter.y + tempVa.y )
                		.put( kSliceCenter.z + tempVa.z );

                BufferUtils.populateFromBuffer( tempVa, posBuf, index );
                kNormal = tempVa;
                kNormal.normalizeLocal();
                if ( !mInverted ) {
                    normBuf.put( kNormal.x ).put( kNormal.y ).put( kNormal.z );
                } else {
                    normBuf.put( -kNormal.x ).put( -kNormal.y ).put( -kNormal.z );
                }
                switch( mTextureMode ) {
                case ZAXIS:
                    texBuf.put(fRadialFraction).put(
                            0.5f * (fZFraction + 1.0f));
                    break;
                case PROJECTED:
                    texBuf.put(fRadialFraction).put(
                            FastMath.INV_PI
                            * (FastMath.HALF_PI + FastMath.asin(fZFraction)));
                    break;
                case POLAR:
                    float r = (FastMath.HALF_PI - FastMath.abs(fAFraction)) / FastMath.PI;
                    float u = r * aCoord.mCosine + 0.5f;
                    float v = r * aCoord.mSine + 0.5f;
                    texBuf.put(u).put(v);
                    break;
                }
                index += 1;
            }
            // Copy the first radial vertex to the end
            BufferUtils.copyInternalVector3( posBuf, iSave, index );
            BufferUtils.copyInternalVector3( normBuf, iSave, index );

            switch( mTextureMode ) {
            case ZAXIS:
                texBuf.put(1.0f).put( 0.5f * (fZFraction + 1.0f) );
                break;
            case PROJECTED:
                texBuf.put(1.0f)
                	.put( FastMath.INV_PI * (FastMath.HALF_PI + FastMath.asin(fZFraction)));
                break;
            case POLAR:
                float r = (FastMath.HALF_PI - FastMath.abs(fAFraction)) / FastMath.PI;
                texBuf.put(r + 0.5f).put(0.5f);
                break;
            }
            index += 1;
        }
        vars.release();

        // South pole
        posBuf.position( index * 3);
        posBuf.put(0f).put(0f).put( -mRadius );

        normBuf.position( index * 3);
        if ( !mInverted ) {
            normBuf.put(0).put(0).put(-1); // allow for inner
        } else {
            normBuf.put(0).put(0).put(1);
        }
        texBuf.position( index * 2 );

        if ( mTextureMode == TextureMode.POLAR ) {
            texBuf.put(0.5f).put(0.5f);
        } else {
            texBuf.put(0.5f).put(0.0f);
        }
        // North pole
        posBuf.put(0).put(0).put( mRadius );
        if ( !mInverted ) {
            normBuf.put(0).put(0).put(1);
        } else {
            normBuf.put(0).put(0).put(-1);
        }
        if ( mTextureMode == TextureMode.POLAR ) {
            texBuf.put(0.5f).put(0.5f);
        } else {
            texBuf.put(0.5f).put(1.0f);
        }
        // Allocate the indices
        int triCount = 2 * (mAxisSamples - 2) * mRadialSamples;
        ShortBuffer idxBuf = BufferUtils.createShortBuffer( 3 * triCount );
        setBuffer( Type.Index, 3, idxBuf );

        index = 0;
        for( int iZ = 0, iZStart = 0; iZ < (mAxisSamples - 3); iZ++ ) {
            int i0 = iZStart;
            int i1 = i0 + 1;
            iZStart += (mRadialSamples + 1);
            int i2 = iZStart;
            int i3 = i2 + 1;
            for( int i = 0; i < mRadialSamples; i++, index += 6) {
                if ( !mInverted ) {
                    idxBuf.put((short) i0++);
                    idxBuf.put((short) i1);
                    idxBuf.put((short) i2);
                    idxBuf.put((short) i1++);
                    idxBuf.put((short) i3++);
                    idxBuf.put((short) i2++);
                } else { // inside view
                    idxBuf.put((short) i0++);
                    idxBuf.put((short) i2);
                    idxBuf.put((short) i1);
                    idxBuf.put((short) i1++);
                    idxBuf.put((short) i2++);
                    idxBuf.put((short) i3++);
                }
            }
        }
        // South pole triangles
        for( int i = 0; i < mRadialSamples; i++, index += 3 ) {
            if ( !mInverted ) {
                idxBuf.put((short) i);
                idxBuf.put((short) (vertCount - 2));
                idxBuf.put((short) (i + 1));
            } else { // inside view
                idxBuf.put((short) i);
                idxBuf.put((short) (i + 1));
                idxBuf.put((short) (vertCount - 2));
            }
        }
        // North pole triangles
        int iOffset = (mAxisSamples - 3) * (mRadialSamples + 1);
        for( int i = 0; i < mRadialSamples; i++, index += 3 ) {
            if ( !mInverted ) {
                idxBuf.put((short) (i + iOffset));
                idxBuf.put((short) (i + 1 + iOffset));
                idxBuf.put((short) (vertCount - 1));
            } else { // inside view
                idxBuf.put((short) (i + iOffset));
                idxBuf.put((short) (vertCount - 1));
                idxBuf.put((short) (i + 1 + iOffset));
            }
        }
        // Establish the bounds
        updateBound();
        setStatic();
    }

    /** Support texture scaling */
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
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mEvenSlices = inCapsule.readBoolean( "useEvenSlices", false );
        mTextureMode = inCapsule.readEnum( "textureMode", TextureMode.class, TextureMode.ZAXIS );

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
        

    	aBuffer.clear();
        tc.updateData( aBuffer );
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
