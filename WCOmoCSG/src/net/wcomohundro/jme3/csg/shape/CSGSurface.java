/** Copyright (c) 2009-2012 jMonkeyEngine
    Copyright (c) 2015, WCOmohundro
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
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.terrain.heightmap.HeightMap;
import com.jme3.util.BufferUtils;
import com.jme3.util.TangentBinormalGenerator;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.ArrayList;

import net.wcomohundro.jme3.csg.CSGMeshManager;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;

/** Constructive Solid Geometry (CSG) - 2D Surface
 
 	This is a direct ripoff of the GeoMap underpinnings of Terrain, used to create 
 	a 2D surface in X/Z, with height in Y.
 	
 	For simplicity sake, we are assuming a Square with equal extents in X and Z.
 	The height data can be provided by the standard JME3 HeightMap
 */
public class CSGSurface 
	extends CSGMesh
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGSurfaceRevision="$Rev$";
	public static final String sCSGSurfaceDate="$Date$";

	
	/** Basic size */
	protected int		mExtent;
	/** Inherent scale */
	protected Vector3f	mScale;
	/** Height data */
    protected float[]	mHeightData;

	
	/** Standard null constructor */
	public CSGSurface(
	) {
		this( 32, Vector3f.UNIT_XYZ, null, false );
	}
	/** Constructor based on the given extents */
	public CSGSurface(
		int		pExtent
	) {
		this( pExtent, Vector3f.UNIT_XYZ, null, true );
	}
	public CSGSurface(
		int			pExtent
	,	Vector3f	pScale
	,	float[]		pHeightData
	,	boolean		pReadyGeometry
	) {
		mExtent = pExtent;
		mScale = pScale;
		mHeightData = pHeightData;
		
        if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
	}
	
	/** Accessors to the extents */
    public int getExtent() { return mExtent; }
    public void setExtent( int pExtent ) { mExtent = pExtent; }
    
    /** Accessors to width/height (should I every decide to go non-square) */
    public int getWidth() { return mExtent; }
    public int getHeight() { return mExtent; }
    
    /** Accessor to the height data */
    public float getValue(
    	int		pX
    , 	int 	pZ
    ) {
        return( mHeightData == null) ? 0f :  mHeightData[ (pZ * getWidth()) +  pX ];
    }
    public float[] getHeightMap(
    ) {
    	if ( mHeightData != null ) {
    		return( mHeightData );
    	} else {
    		return( new float[ getWidth() * getHeight() ] );
    	}
    }
    public void setHeightMap( float[] pHeightMap ) { mHeightData = pHeightMap; }
    
    /** Rebuilds the surface based on the current set of configuration parameters */
    @Override
    protected void updateGeometryProlog(
    ) {
    	// Apply scale as we go, rather than post-process in the epilog
    	Vector2f texScale = Vector2f.UNIT_XY;
		if ( mFaceProperties != null ) {
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( Face.SURFACE ) && aProperty.hasScaleTexture()) {
					texScale = aProperty.getScaleTexture();
				}
        	}
        }
		// Populate the buffers
        FloatBuffer pb = writeVertexBuffer( null, mScale, true );
        FloatBuffer tb = writeTexCoordBuffer( null, Vector2f.ZERO, texScale );
        FloatBuffer nb = writeNormalBuffer( null, mScale );
        IntBuffer ib = writeIndexBuffer( null );
        
        setBuffer( Type.Position, 3, pb );
        setBuffer( Type.Normal, 3, nb );
        setBuffer( Type.TexCoord, 2, tb );
        setBuffer( Type.Index, 3, ib );
        
        setStatic();
        updateBound();
    }
    @Override
	protected void updateGeometryEpilog(
	) {
    	// Texture scaling and tangent generation occur during construction and does not
    	// need to be applied now
    }

    /** Apply texture coordinate scaling to selected 'faces' of the sphere */
    @Override
    public void scaleFaceTextureCoordinates(
    	Vector2f	pScaleTexture
    ,	int			pFaceMask
    ) {
        VertexBuffer tc = getBuffer( Type.TexCoord );
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
        boolean doSurface = (pFaceMask & Face.SURFACE.getMask()) != 0;

        if ( doSurface ) {
	        for( int i = 0, j = aBuffer.limit() / 2; i < j; i += 1 ) {
	            float x = aBuffer.get();
	            float y = aBuffer.get();
	            aBuffer.position( aBuffer.position()-2 );
	            x *= pScaleTexture.x;
	            y *= pScaleTexture.y;
	            aBuffer.put(x).put(y);
	        }
	        aBuffer.clear();
	        tc.updateData( aBuffer );
        }
    }
    

    /** Support texture scaling AND reconstruction from the configuration parameters */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule outCapsule = pExporter.getCapsule( this );

    	// Let the super do its thing
        super.write( pExporter );
        
        outCapsule.write( mExtent, "extent", 32 );
        outCapsule.write( mScale,  "scale", Vector3f.UNIT_XYZ );
        
        // I am not yet convinced that saving the raw height data is a useful thing to do
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing
        super.read( pImporter );
        
        InputCapsule inCapsule = pImporter.getCapsule( this );
        mExtent = inCapsule.readInt( "extent", 32 );
        mScale = (Vector3f)inCapsule.readSavable( "scale", Vector3f.UNIT_XYZ );
        
        // Expect some help with the height map data
        CSGHeightMapGenerator heightHelper 
        	= (CSGHeightMapGenerator)inCapsule.readSavable( "heightMap", null );
        if ( heightHelper != null ) {
        	HeightMap aMap = heightHelper.getHeightMap();
        	if ( aMap != null ) {
        		mHeightData = aMap.getScaledHeightMap();
        	}
        }
        // Standard trigger of updateGeometry() to build the shape 
        this.updateGeometry();
    }
    
    /** Service routine that fills the Vertex buffer */
    protected FloatBuffer writeVertexBuffer(
    	FloatBuffer 	pStore
    , 	Vector3f 		pScale
    , 	boolean 		pIsCentered
    ) {
    	// X/Y/Z coordinate for every point
    	int bufferSize = getWidth() * getHeight() * 3;
        if ( pStore != null ){
            if ( pStore.remaining() < bufferSize ) {
                throw new BufferUnderflowException();
            }
        }else{
        	pStore = BufferUtils.createFloatBuffer( bufferSize );
        }
        Vector3f offset;
        if ( pIsCentered ) {
        	// The offset to the center around the zero point
        	offset = new Vector3f(	-getWidth() * pScale.x * 0.5f
                                  ,  0
                                  ,	-getWidth() * pScale.z * 0.5f );
        } else {
        	// Not centered on the zero point, so no offset
        	offset = Vector3f.ZERO;
        }
        int i = 0;
        for( int z = 0, height = getHeight(); z < height; z += 1 ) {
            for( int x = 0, width = getWidth(); x < width; x += 1 ) {
                pStore.put( ((float)x * pScale.x) + offset.x );
                pStore.put( getValue( x, z ) * pScale.y );
                pStore.put( ((float)z * pScale.z) + offset.z );
            }
        }
        return( pStore );
    }
    
    /** Service routine that fills the Normal buffer */
    protected FloatBuffer writeNormalBuffer(
    	FloatBuffer 	pStore
    , 	Vector3f 		pScale
    ) {
    	// X/Y/Z coordinate for every point
    	int bufferSize = getWidth() * getHeight() * 3;
        if ( pStore != null ){
            if ( pStore.remaining() < bufferSize ) {
                throw new BufferUnderflowException();
            }
        } else {
        	pStore = BufferUtils.createFloatBuffer( bufferSize );
        }
        // Points in the triangle
        Vector3f oppositePoint = new Vector3f();
        Vector3f adjacentPoint = new Vector3f();
        Vector3f rootPoint = new Vector3f();
        Vector3f tempNorm = new Vector3f();
        
        int normalIndex = 0;
        for( int z = 0, lastHeight = getHeight() -1; z <= lastHeight; z += 1 ) {
            for( int x = 0, lastWidth = getWidth() -1; x <= lastWidth; x += 1 ) {
                rootPoint.set( x, getValue( x, z ), z );
                if ( z == lastHeight) {
                    if ( x == lastWidth) {  // case #4 : last row, last col
                        // left cross up
//                            adj = normalIndex - getWidth();
//                            opp = normalIndex - 1;
                        adjacentPoint.set( x, getValue( x, z-1 ), z-1 );
                        oppositePoint.set( x-1, getValue( x-1, z ), z );
                    } else {                    // case #3 : last row, except for last col
                        // right cross up
//                            adj = normalIndex + 1;
//                            opp = normalIndex - getWidth();
                        adjacentPoint.set( x+1, getValue( x+1, z ), z );
                        oppositePoint.set( x, getValue( x, z-1 ), z-1 );
                    }
                } else {
                    if (x == lastWidth) {  // case #2 : last column except for last row
                        // left cross down
                        adjacentPoint.set( x-1, getValue( x-1, z ), z );
                        oppositePoint.set( x, getValue( x, z+1 ), z+1 );
//                            adj = normalIndex - 1;
//                            opp = normalIndex + getWidth();
                    } else {                    // case #1 : most cases
                        // right cross down
                        adjacentPoint.set( x, getValue( x, z+1 ), z+1 );
                        oppositePoint.set( x+1, getValue( x+1, z ), z );
//                            adj = normalIndex + getWidth();
//                            opp = normalIndex + 1;
                    }
                }
                tempNorm.set( adjacentPoint ).subtractLocal( rootPoint )
                        .crossLocal( oppositePoint.subtractLocal( rootPoint ) );
                tempNorm.multLocal( pScale ).normalizeLocal();
                BufferUtils.setInBuffer( tempNorm, pStore, normalIndex++ );
            }
        }
        return( pStore );
    }
    
    /** Service routine that fills the Texture Coordinate buffer */
    protected FloatBuffer writeTexCoordBuffer(
    	FloatBuffer		pStore
    , 	Vector2f 		pOffset
    , 	Vector2f 		pScale
    ){
    	// X/Y coordinate for every point
    	int bufferSize = getWidth() * getHeight() * 2;
        if ( pStore != null ){
            if ( pStore.remaining() < bufferSize ) {
                throw new BufferUnderflowException();
            }
        } else {
        	pStore = BufferUtils.createFloatBuffer( bufferSize );
        }
        Vector2f tcStore = new Vector2f();
        for( int z = 0, height = getHeight(); z < height; z += 1 ) {
            for( int x = 0, width = getWidth(); x < width; x += 1 ) {
            	// The texture is applied linearly across the width/height
                tcStore.set( (float)x / (float)width, (float)z / (float)height );

                pStore.put( pOffset.x + tcStore.x * pScale.x );
                pStore.put( pOffset.y + tcStore.y * pScale.y );
            }
        }
        return( pStore );
    }
    
    /** Service routine that fills the Index buffer */
    protected IntBuffer writeIndexBuffer(
    	IntBuffer 	pStore
    ) {
        int faceN = (getWidth()-1)*(getHeight()-1)*2;
        if ( pStore != null ) {
            if ( pStore.remaining() < faceN*3 ) {
                throw new BufferUnderflowException();
            }
        } else {
        	pStore = BufferUtils.createIntBuffer(faceN*3);
        }

        int i = 0;
        for( int z = 0, lastHeight = getHeight()-1; z < lastHeight; z += 1 ) {
            for( int x = 0, lastWidth = getWidth()-1; x < lastWidth; x += 1 ) {
                pStore.put( i ).put( i + getWidth() ).put( i + getWidth() + 1 );
                pStore.put( i + getWidth()+1 ).put( i+1 ).put( i );
                i += 1;

                // TODO: There's probably a better way to do this..
                if ( x == getWidth()-2 ) i += 1;
            }
        }
        pStore.flip();

        return( pStore );
    }
    

    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( CSGSurface.class
													, sCSGSurfaceRevision
													, sCSGSurfaceDate
													, pBuffer ) );
	}

}
