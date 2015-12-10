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

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;

import net.wcomohundro.jme3.csg.CSGMeshManager;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;

import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

/** Extensions/customizations of a Box for CSG support.
 
 	In particular, retain some knowledge of the various 'faces' of the box.
 	
 	NOTE
 		that we extend CSGMesh, but liberally steal code from Box/AbstractBox
 */
public class CSGBox 
	extends CSGMesh
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGBoxRevision="$Rev$";
	public static final String sCSGBoxDate="$Date$";

	/** Standard center */
	protected final Vector3f sCenter = new Vector3f( 0f, 0f, 0f );
	
    protected static final short[] GEOMETRY_INDICES_DATA = {
        2,  1,  0,    3,  2,  0, // front
        6,  5,  4,    7,  6,  4, // back
        
       10,  9,  8,   11, 10,  8, // left
       14, 13, 12,   15, 14, 12, // right

       18, 17, 16,   19, 18, 16, // top
       22, 21, 20,   23, 22, 20  // bottom
    };

    protected static final float[] GEOMETRY_NORMALS_DATA = {
       0,  0,  1,    0,  0,  1,    0,  0,  1,    0,  0,  1, // front
       0,  0, -1,    0,  0, -1,    0,  0, -1,    0,  0, -1, // back
       
      -1,  0,  0,   -1,  0,  0,   -1,  0,  0,   -1,  0,  0, // left
       1,  0,  0,    1,  0,  0,    1,  0,  0,    1,  0,  0, // right
       
       0,  1,  0,    0,  1,  0,    0,  1,  0,    0,  1,  0, // top
       0, -1,  0,    0, -1,  0,    0, -1,  0,    0, -1,  0  // bottom
   	};

   	protected static final float[] GEOMETRY_TEXTURE_DATA = {
       1, 0,   0, 0,   0, 1,   1, 1, // front
       1, 0,   0, 0,   0, 1,   1, 1, // back
       
       1, 0,   0, 0,   0, 1,   1, 1, // left
       1, 0,   0, 0,   0, 1,   1, 1, // right
       
       1, 0,   0, 0,   0, 1,   1, 1, // top
       1, 0,   0, 0,   0, 1,   1, 1  // bottom
   	};

	/** The size of the box (remember to *2 for full width/height/depth) */
    protected float mExtentX, mExtentY, mExtentZ;

    
	/** Standard null constructor */
	public CSGBox(
	) {
		this( 1, 1, 1, false );
	}
	/** Constructor based on the given extents */
	public CSGBox(
		float	pExtentX
	,	float	pExtentY
	,	float	pExtentZ
	) {
		this( pExtentX, pExtentY, pExtentZ, true );
	}
	public CSGBox(
		float	pExtentX
	,	float	pExtentY
	,	float	pExtentZ
	,	boolean	pReadyGeometry
	) {
		mExtentX = pExtentX;
		mExtentY = pExtentY;
		mExtentZ = pExtentZ;
		
        if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
	}
	
	/** Accessors to the extents */
    public float getXExtent() { return mExtentX; }
    public void setXExtent( float pExtent ) { mExtentX = pExtent; }
    
    public float getYExtent() { return mExtentY; }
    public void setYExtent( float pExtent ) { mExtentY = pExtent; }

    public float getZExtent() { return mExtentZ; }
    public void setZExtent( float pExtent ) { mExtentZ = pExtent; }
    
    /** Apply gradient vertex colors */
    @Override
    public void applyGradient(
    	List<ColorRGBA>	pColors
    ) {
    }

    /** Apply the change of geometry */
    @Override
    protected void updateGeometryProlog(
    ) {
    	// Shape specific generation
        duUpdateGeometryVertices();
        duUpdateGeometryNormals();
        duUpdateGeometryTextures();
        duUpdateGeometryIndices();
    }

    /** Read the fundamental configuration parameters */
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );

        // Let the super do its thing
        super.read( pImporter );

        // Basic configuration
        mExtentX = inCapsule.readFloat( "xExtent", 1 );
        mExtentY = inCapsule.readFloat( "yExtent", 1 );
        mExtentZ = inCapsule.readFloat( "zExtent", 1 );

        // Standard trigger of updateGeometry() to build the shape 
        this.updateGeometry();
    }
    
    /** Preserve this shape */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule outCapsule = pExporter.getCapsule(this);
        
    	// Let the super do its thing
        super.write( pExporter );
        
        outCapsule.write( mExtentX, "xExtent", 1 );
        outCapsule.write( mExtentY, "yExtent", 1 );
        outCapsule.write( mExtentZ, "zExtent", 1 );
    }


    /** Apply texture coordinate scaling to selected 'faces' of the box */
    @Override
    public void scaleFaceTextureCoordinates(
    	Vector2f	pScaleTexture
    ,	int			pFaceMask
    ) {
        VertexBuffer tc = getBuffer( Type.TexCoord );
        if ( tc == null ) {
            throw new IllegalStateException( "CSGBox: no texture coordinates" );
        }
        if ( tc.getFormat() != VertexBuffer.Format.Float ) {
            throw new UnsupportedOperationException( "CSGBox: only float texture coord format is supported" );
    	}
        if ( tc.getNumComponents() != 2 ) {
            throw new UnsupportedOperationException( "CSGBox: only 2D texture coords are supported" );
        }
        FloatBuffer aBuffer = (FloatBuffer)tc.getData();
        aBuffer.clear();
        
    	// Process over the faces selected in the mask 
    	for( int aFace = 0, aMask = pFaceMask & 0x3f; aMask != 0; aFace += 1, aMask >>= 1 ) {
    		if ( (aMask & 0x01) == 0 ) continue;	// Skip this face
    		
    		// Select the points where to apply the texture
    		// Each face is defined by 4 x/y points, in the order of the Face values
    		for( int i = 0, index = (aFace * 8); i < 4; i += 1 ) {
    			float x = aBuffer.get( index  );
    			float y = aBuffer.get( index + 1 );

                x *= pScaleTexture.x;
                y *= pScaleTexture.y;
                aBuffer.put( index++, x ).put( index++, y);
            }
    	}
    	aBuffer.clear();
        tc.updateData( aBuffer );
    }
    
	/** Accessor to the material that applies to the given surface */
    @Override
	public Material getMaterial(
		int					pFaceIndex
	) {
		// Determine the face, which we know is in the order
    	//	Front/Back/Left/Right/Top/Bottom, with 2 triangles per face, which matches
    	//  the bits in Face
    	int faceMask = 1 << (pFaceIndex / 2);
    	Material aMaterial = resolveFaceMaterial( faceMask );
		return( aMaterial );
	}
    
	/** Accessor to the physics that applies to the given surface */
    @Override
	public PhysicsControl getPhysics(
		int					pFaceIndex
	) {
		// Subclasses must override if they support custom material per surface
		// Determine the face, which we know is in the order
    	//	Front/Back/Left/Right/Top/Bottom, with 2 triangles per face, which matches
    	//  the bits in Face
    	int faceMask = 1 << (pFaceIndex / 2);
    	PhysicsControl aPhysics = resolveFacePhysics( faceMask );
		return( aPhysics );
	}
    
    /** A bit different from standard Box, in order to run the texture along appropriate x /y 
     		(For top/bottom, the super class applies the x texture along the z axis, and the y texture
     		 along the x axis.  The configuration below applies the x texture along the x axis, and the
     		 y texture along the z axis.  Then scaling applied to x and y is much more uniform for 
     		 top/bottom in comparison to front/back
     */
    protected void duUpdateGeometryVertices(
    ) {
        FloatBuffer fpb = BufferUtils.createVector3Buffer(24);
        Vector3f[] v = computeVertices();
        fpb.put(new float[] {
        /** This is from the original jme3 Box, which applies the x texture along the z axis and the
            y texture along the x axis for top/bottom
                v[0].x, v[0].y, v[0].z, v[1].x, v[1].y, v[1].z, v[2].x, v[2].y, v[2].z, v[3].x, v[3].y, v[3].z, // back
                v[1].x, v[1].y, v[1].z, v[4].x, v[4].y, v[4].z, v[6].x, v[6].y, v[6].z, v[2].x, v[2].y, v[2].z, // right
                v[4].x, v[4].y, v[4].z, v[5].x, v[5].y, v[5].z, v[7].x, v[7].y, v[7].z, v[6].x, v[6].y, v[6].z, // front
                v[5].x, v[5].y, v[5].z, v[0].x, v[0].y, v[0].z, v[3].x, v[3].y, v[3].z, v[7].x, v[7].y, v[7].z, // left
                v[2].x, v[2].y, v[2].z, v[6].x, v[6].y, v[6].z, v[7].x, v[7].y, v[7].z, v[3].x, v[3].y, v[3].z, // top
                v[0].x, v[0].y, v[0].z, v[5].x, v[5].y, v[5].z, v[4].x, v[4].y, v[4].z, v[1].x, v[1].y, v[1].z  // bottom
        **/
        /** This keeps the x texture along the x axis, and runs the y texture along the z axis for
         	top/bottom */
                v[4].x, v[4].y, v[4].z,   v[5].x, v[5].y, v[5].z,   v[7].x, v[7].y, v[7].z,   v[6].x, v[6].y, v[6].z, // front
                v[0].x, v[0].y, v[0].z,   v[1].x, v[1].y, v[1].z,   v[2].x, v[2].y, v[2].z,   v[3].x, v[3].y, v[3].z, // back

                v[5].x, v[5].y, v[5].z,   v[0].x, v[0].y, v[0].z,   v[3].x, v[3].y, v[3].z,   v[7].x, v[7].y, v[7].z, // left
                v[1].x, v[1].y, v[1].z,   v[4].x, v[4].y, v[4].z,   v[6].x, v[6].y, v[6].z,   v[2].x, v[2].y, v[2].z, // right

                v[6].x, v[6].y, v[6].z,   v[7].x, v[7].y, v[7].z,   v[3].x, v[3].y, v[3].z,   v[2].x, v[2].y, v[2].z, // top
                v[1].x, v[1].y, v[1].z,   v[0].x, v[0].y, v[0].z,   v[5].x, v[5].y, v[5].z,   v[4].x, v[4].y, v[4].z  // bottom
        });
        setBuffer( Type.Position, 3, fpb );
        updateBound();
    }
    protected void duUpdateGeometryIndices(
    ) {
        if ( getBuffer( Type.Index ) == null ) {
            setBuffer( Type.Index
            			, 3
            			, BufferUtils.createShortBuffer( GEOMETRY_INDICES_DATA ));
        }
    }
    protected void duUpdateGeometryNormals(
    ) {
        if ( getBuffer(Type.Normal ) == null ) {
            setBuffer( Type.Normal
			            , 3
			            , BufferUtils.createFloatBuffer( GEOMETRY_NORMALS_DATA ));
        }
    }
    protected void duUpdateGeometryTextures(
    ) {
        if ( getBuffer( Type.TexCoord ) == null ) {
            setBuffer( Type.TexCoord, 2, BufferUtils.createFloatBuffer( GEOMETRY_TEXTURE_DATA ));
        }
    }
    /** Service routine that constructs the array of vectors representing the 8 vertices of the box */
    protected final Vector3f[] computeVertices(
    ) {
        Vector3f[] axes = {
            Vector3f.UNIT_X.mult( mExtentX )
        ,   Vector3f.UNIT_Y.mult( mExtentY )
        ,	Vector3f.UNIT_Z.mult( mExtentZ )
        };
        // Define the 8 points that specify the cube
        return new Vector3f[] {
            sCenter.subtract(axes[0]).subtractLocal(axes[1]).subtractLocal(axes[2])	// 0- Back Left Bottom
        ,	sCenter.add(axes[0]).subtractLocal(axes[1]).subtractLocal(axes[2])		// 1- Back Right Bottom
        ,	sCenter.add(axes[0]).addLocal(axes[1]).subtractLocal(axes[2])			// 2- Back Right Top
        ,	sCenter.subtract(axes[0]).addLocal(axes[1]).subtractLocal(axes[2])		// 3- Back Left Top
        ,	sCenter.add(axes[0]).subtractLocal(axes[1]).addLocal(axes[2])			// 4- Front Right Bottom
        ,	sCenter.subtract(axes[0]).subtractLocal(axes[1]).addLocal(axes[2])		// 5- Front Left Bottom
        ,	sCenter.add(axes[0]).addLocal(axes[1]).addLocal(axes[2])				// 6- Front Right Top
        ,	sCenter.subtract(axes[0]).addLocal(axes[1]).addLocal(axes[2])			// 7- Front Left Top
        };
    }

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( this.getClass()
													, sCSGBoxRevision
													, sCSGBoxDate
													, pBuffer ) );
	}

}
