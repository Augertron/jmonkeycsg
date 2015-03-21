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
	extends CSGMesh
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

	/** How many sample circles to generate along the z axis */
    protected int 			mAxisSamples;
    /** How many point around the circle to generate
     		The more points, the smoother the circular surface.
     		The fewer points, and it looks like a triangle/cube/pentagon/...
     */
    protected int 			mRadialSamples;

    /** The radius of the sphere */
    protected float 		mRadius;
    /** The eccentricity (where 0 is a circle) */
    protected float			mEccentricity;

    /** When generating the slices along the z-axis, evenly space them (else create more near the extremities) */
    protected boolean 		mEvenSlices;
    /** If inverted, then the cylinder is intended to be viewed from the inside */
    protected boolean 		mInverted;
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
        mAxisSamples = pAxisSamples;
        mRadialSamples = pRadialSamples;
        mRadius = pRadius;
        mEvenSlices = pUseEvenSlices;
        mInverted = pInverted;
        mTextureMode = pTextureMode;
    }

    /** Configuration accessors */
    public int getAxisSamples() { return mAxisSamples; }
    public int getRadialSamples() { return mRadialSamples; }
    public float getRadius() { return mRadius; }
    public boolean hasEvenSlices() { return mEvenSlices; }
    public boolean isInverted() { return mInverted; }
    public TextureMode getTextureMode() { return mTextureMode; }

    
    /** Rebuilds the cylinder based on the current set of configuration parameters */
    @Override
    public void updateGeometry(
    ) {
    	int vertexCount = setGeometryData();
    	setIndexData( vertexCount );
    }
    /** builds the vertices based on the radius, radial and zSamples. */
    protected int setGeometryData(
    ) {
        // allocate vertices
        int vertCount = (mAxisSamples - 2) * (mRadialSamples + 1) + 2;

        FloatBuffer posBuf = BufferUtils.createVector3Buffer(vertCount);

        // allocate normals if requested
        FloatBuffer normBuf = BufferUtils.createVector3Buffer(vertCount);

        // allocate texture coordinates
        FloatBuffer texBuf = BufferUtils.createVector2Buffer(vertCount);

        setBuffer(Type.Position, 3, posBuf);
        setBuffer(Type.Normal, 3, normBuf);
        setBuffer(Type.TexCoord, 2, texBuf);

        // generate geometry
        float fInvRS = 1.0f / mRadialSamples;
        float fZFactor = 2.0f / (mAxisSamples - 1);

        // Generate points on the unit circle to be used in computing the mesh
        // points on a sphere slice.
        float[] afSin = new float[(mRadialSamples + 1)];
        float[] afCos = new float[(mRadialSamples + 1)];
        for (int iR = 0; iR < mRadialSamples; iR++) {
            float fAngle = FastMath.TWO_PI * fInvRS * iR;
            afCos[iR] = FastMath.cos(fAngle);
            afSin[iR] = FastMath.sin(fAngle);
        }
        afSin[mRadialSamples] = afSin[0];
        afCos[mRadialSamples] = afCos[0];

        TempVars vars = TempVars.get();
        Vector3f tempVa = vars.vect1;
        Vector3f tempVb = vars.vect2;
        Vector3f tempVc = vars.vect3;

        // generate the sphere itself
        int i = 0;
        float rSquared = mRadius * mRadius;
        float eSquared = mEccentricity * mEccentricity;
        float oneMinusESquared = (mEccentricity >= 1)
        							?	eSquared
        							:	((mEccentricity > 0) ? (1 - eSquared) : 1);
        
        for (int iZ = 1; iZ < (mAxisSamples - 1); iZ++) {
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
            int iSave = i;
            for (int iR = 0; iR < mRadialSamples; iR++) {
                float fRadialFraction = iR * fInvRS; // in [0,1)
                Vector3f kRadial = tempVc.set(afCos[iR], afSin[iR], 0);
                kRadial.mult(fSliceRadius, tempVa);
                posBuf.put( kSliceCenter.x + tempVa.x )
                		.put( kSliceCenter.y + tempVa.y )
                		.put( kSliceCenter.z + tempVa.z );

                BufferUtils.populateFromBuffer(tempVa, posBuf, i);
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
                    float u = r * afCos[iR] + 0.5f;
                    float v = r * afSin[iR] + 0.5f;
                    texBuf.put(u).put(v);
                    break;
                }
                i += 1;
            }

            BufferUtils.copyInternalVector3(posBuf, iSave, i);
            BufferUtils.copyInternalVector3(normBuf, iSave, i);

            switch( mTextureMode ) {
            case ZAXIS:
                texBuf.put(1.0f)
                	.put( 0.5f * (fZFraction + 1.0f));
                break;
            case PROJECTED:
                texBuf.put(1.0f)
                	.put(
                        FastMath.INV_PI
                        * (FastMath.HALF_PI + FastMath.asin(fZFraction)));
                break;
            case POLAR:
                float r = (FastMath.HALF_PI - FastMath.abs(fAFraction)) / FastMath.PI;
                texBuf.put(r + 0.5f).put(0.5f);
                break;
            }
            i += 1;
        }

        vars.release();

        // south pole
        posBuf.position(i * 3);
        posBuf.put(0f).put(0f).put( -mRadius );

        normBuf.position(i * 3);
        if ( !mInverted ) {
            normBuf.put(0).put(0).put(-1); // allow for inner
        } // texture orientation
        // later.
        else {
            normBuf.put(0).put(0).put(1);
        }

        texBuf.position(i * 2);

        if ( mTextureMode == TextureMode.POLAR ) {
            texBuf.put(0.5f).put(0.5f);
        } else {
            texBuf.put(0.5f).put(0.0f);
        }

        i++;

        // north pole
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

        updateBound();
        setStatic();
        
        return( vertCount );
    }

    /**
     * sets the indices for rendering the sphere.
     */
    private void setIndexData(
    	int 	pVertexCount
    ) {
        // allocate connectivity
        int triCount = 2 * (mAxisSamples - 2) * mRadialSamples;
        ShortBuffer idxBuf = BufferUtils.createShortBuffer(3 * triCount);
        setBuffer(Type.Index, 3, idxBuf);

        // generate connectivity
        int index = 0;
        for (int iZ = 0, iZStart = 0; iZ < (mAxisSamples - 3); iZ++) {
            int i0 = iZStart;
            int i1 = i0 + 1;
            iZStart += (mRadialSamples + 1);
            int i2 = iZStart;
            int i3 = i2 + 1;
            for (int i = 0; i < mRadialSamples; i++, index += 6) {
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

        // south pole triangles
        for (int i = 0; i < mRadialSamples; i++, index += 3) {
            if ( !mInverted ) {
                idxBuf.put((short) i);
                idxBuf.put((short) (pVertexCount - 2));
                idxBuf.put((short) (i + 1));
            } else { // inside view
                idxBuf.put((short) i);
                idxBuf.put((short) (i + 1));
                idxBuf.put((short) (pVertexCount - 2));
            }
        }

        // north pole triangles
        int iOffset = (mAxisSamples - 3) * (mRadialSamples + 1);
        for (int i = 0; i < mRadialSamples; i++, index += 3) {
            if ( !mInverted ) {
                idxBuf.put((short) (i + iOffset));
                idxBuf.put((short) (i + 1 + iOffset));
                idxBuf.put((short) (pVertexCount - 1));
            } else { // inside view
                idxBuf.put((short) (i + iOffset));
                idxBuf.put((short) (pVertexCount - 1));
                idxBuf.put((short) (i + 1 + iOffset));
            }
        }
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
        outCapsule.write( mRadius, "radius", 1 );
        outCapsule.write( mEccentricity, "eccentricity", 0 );
        outCapsule.write( mEvenSlices, "useEvenSlices", false );
        outCapsule.write( mInverted, "inverted", false );
        outCapsule.write( mTextureMode, "textureMode", TextureMode.ZAXIS );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mAxisSamples = inCapsule.readInt( "axisSamples", 32 );
        mRadialSamples = inCapsule.readInt( "radialSamples", 32 );
        mRadius = inCapsule.readFloat( "radius", 1 );
        mEccentricity = inCapsule.readFloat( "eccentricity", 0 );
        mEvenSlices = inCapsule.readBoolean( "useEvenSlices", false );
        mInverted = inCapsule.readBoolean( "inverted", false );
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
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGSphereRevision
													, sCSGSphereDate
													, pBuffer ) );
	}

}
