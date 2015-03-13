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
package net.wcomo.jme3.csg.shape;

import java.io.IOException;
import java.nio.FloatBuffer;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

/** Extensions/customizations of a Box for CSG support.
 	In particular, retain some knowledge of the various 'faces' of the box.
 */
public class Box 
	extends com.jme3.scene.shape.Box
{
	/** Identify the 6 faces of the box  */
	public enum Face {
		BACK, RIGHT, FRONT, LEFT, TOP, BOTTOM;
		
		private int mask;
		Face() {
			mask = 1 << this.ordinal();
		}
		public int getMask() { return mask; }
	}
	
	/** NOTE that the super box 
	
	/** Standard null constructor */
	public Box(
	) {
		super();
	}
	
    /** Support texture scaling */
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        super.read( pImporter );
        if ( getBuffer(Type.Index) == null ) {
        	// Buffers not restored, so rebuild from scratch
        	this.updateGeometry( null, xExtent, yExtent, zExtent );
        }
        // Extended attributes
        InputCapsule inCapsule = pImporter.getCapsule( this );
        Vector2f aScale = (Vector2f)inCapsule.readSavable( "scaleTexture", null );
        if ( aScale != null ) {
        	this.scaleTextureCoordinates( aScale );
        }
        
        Savable[] faceScales = inCapsule.readSavableArray( "scaleFaces", null );
        if ( faceScales != null ) {
        	// x and y are the normal texture scaling factors.  z is the bitmask of faces
        	for( Savable faceScale : faceScales ) {
        		this.scaleFaceTextureCoordinates( (Vector3f)faceScale );
        	}
        }
    }

    /** Apply texture coordinate scaling to selected 'faces' of the box */
    public void scaleFaceTextureCoordinates(
    	Vector3f	pScale
    ) {
    	scaleFaceTextureCoordinates( pScale.x, pScale.y, (int)pScale.z );
    }
    public void scaleFaceTextureCoordinates(
    	float		pX
    ,	float		pY
    ,	Face		pFace
    ) {
    	scaleFaceTextureCoordinates( pX, pY, pFace.getMask() );
    }
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
        
    	// Process over the faces selected in the mask 
    	for( int aFace = 0, aMask = pFaceMask; aMask != 0; aFace += 1, aMask >>= 1 ) {
    		if ( (aMask & 0x01) == 0 ) continue;	// Skip this face
    		
    		// Select the points where to apply the texture
    		// Each face is defined by 4 x/y points, in the order of the Face values
    		//	@see Box.GEOMETRY_TEXTURE_DATA
    		for( int i = 0, index = (aFace * 8); i < 4; i += 1 ) {
    			float x = aBuffer.get( index  );
    			float y = aBuffer.get( index + 1 );

                x *= pX;
                y *= pY;
                aBuffer.put( index++, x ).put( index++, y);
            }
    	}
    	aBuffer.clear();
        tc.updateData( aBuffer );
    }
    
    /** OVERRIDE to run the texture along appropriate x /y 
     		(For top/bottom, the super class applies the x texture along the z axis, and the y texture
     		 along the x axis.  The configuration below applies the x texture along the x axis, and the
     		 y texture along the z axis.  Then scaling applied to x and y is much more uniform for 
     		 top/bottom in comparison to front/back
     */
    @Override
    protected void duUpdateGeometryVertices(
    ) {
        FloatBuffer fpb = BufferUtils.createVector3Buffer(24);
        Vector3f[] v = computeVertices();
        fpb.put(new float[] {
                v[0].x, v[0].y, v[0].z, v[1].x, v[1].y, v[1].z, v[2].x, v[2].y, v[2].z, v[3].x, v[3].y, v[3].z, // back
                v[1].x, v[1].y, v[1].z, v[4].x, v[4].y, v[4].z, v[6].x, v[6].y, v[6].z, v[2].x, v[2].y, v[2].z, // right
                v[4].x, v[4].y, v[4].z, v[5].x, v[5].y, v[5].z, v[7].x, v[7].y, v[7].z, v[6].x, v[6].y, v[6].z, // front
                v[5].x, v[5].y, v[5].z, v[0].x, v[0].y, v[0].z, v[3].x, v[3].y, v[3].z, v[7].x, v[7].y, v[7].z, // left

                v[6].x, v[6].y, v[6].z, v[7].x, v[7].y, v[7].z, v[3].x, v[3].y, v[3].z, v[2].x, v[2].y, v[2].z, // top
                v[1].x, v[1].y, v[1].z, v[0].x, v[0].y, v[0].z, v[5].x, v[5].y, v[5].z, v[4].x, v[4].y, v[4].z  // bottom
        });
        setBuffer(Type.Position, 3, fpb);
        updateBound();
    }

}
