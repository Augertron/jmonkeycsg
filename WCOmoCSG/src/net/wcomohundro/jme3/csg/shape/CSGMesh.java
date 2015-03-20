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
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.VertexBuffer.Type;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

/** Constructive Solid Geometry (CSG)
 
 	Once upon a time, I thought the proper approach to CSG support in jMonkey was to leverage
 	the existing primitive shapes like Box, Cylinder, ...
 	
 	And CGShape does operate from a basic Mesh, so that the jme primitives can still be used.
 	But as I worked with shapes, blending them together to form more complex elements, I found
 	that you have to pay a lot more attention to the shape's orientation and texture processing
 	to make them very useful. In particular, you want to retain the concept of a shape's 'side'
 	as you go.
 	
 	Rather than altering the underlying primitives, I have decided to create a set of CSG 
 	specific shapes that liberally copy code from the primitives, but which address the issues
 	of orientation, texture, and sides.
 	
 	To this end, all CSG shapes will be built centered on the origin, and oriented along the 
 	z-axis.  Think of it this way:
 	
 	1)	The 'front' of the shape is in the x/y plane with a positive z, facing the viewer
 	2)	The 'back' of the shape is in the x/y plane with a negative z, facing away from the viewer
 	3)	The 'top' of the shape is in the x/z plane with a positive y, facing up
 	4)	The 'bottom' of the shape is in the x/z plane with a negative y, facing down
 	5)	The 'left' of the shape is in the y/z plane with a negative x, facing to the left
 	6)	The 'right' of the shape is in the y/z plane with a positive x, facing to the right
 	
 	In particular, those shapes that are based on samples along an axis will follow the z axis, 
 	producing vertices in x/y for the given z point.
 	
 	The various subclasses of CSGMesh are designed to be constructed by XML based import/export
 	files, where all the configuration parameters can be pulled from the XML, and then the
 	full mesh regenerated.

 */
public abstract class CSGMesh 
	extends Mesh
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGMeshRevision="$Rev$";
	public static final String sCSGMeshDate="$Date$";

	/** Retain the texture scaling configuration */
	protected Vector2f			mScaleTexture;
	/** Retain the face texture scaling configuration */
	protected List<Vector3f>	mScaleFaceTexture;
	
	/** Every CSGMesh is expected to be able to rebuild itself from its fundamental
	 	configuration parameters.
	 */
	public abstract void updateGeometry();
	
	/** If this CSGMesh supports various 'faces', then the texture scaling on each
	 	separate face may be set independently
	 */
	public abstract void scaleFaceTextureCoordinates(
		float		pScaleX
	,	float		pScaleY
	,	int			pFacemask
	);
	
    /** Support texture scaling AND reconstruction from the configuration parameters */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule outCapsule = pExporter.getCapsule( this );

        // We are not interested in the constructed buffers, so rather than calling
    	// the super processing, we copy the pertinent pieces here.  Anything that
    	// is regenerated via updateGeometry() is ignored.
        outCapsule.write( this.getMode(), "mode", Mode.Triangles );

        // Extended attributes
        outCapsule.write( mScaleTexture, "scaleTexture", null );
        outCapsule.writeSavableArrayList( (ArrayList)mScaleFaceTexture, "scaleFaces", null );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );

        // Similar to .write(), we are not interested reading the various transient
    	// elements that will be built by .updateGeometry().  So rather than
    	// than letting super.read() try to rebuild buffers etc, just update the
    	// geometry based on the configuration parameters that we assume our
    	// various subclasses have already loaded.
        this.setMode( inCapsule.readEnum( "mode", Mode.class, Mode.Triangles ) );

        this.updateGeometry( );
        
        // Extended attributes
        mScaleTexture = (Vector2f)inCapsule.readSavable( "scaleTexture", null );
        if ( mScaleTexture != null ) {
        	this.scaleTextureCoordinates( mScaleTexture );
        }
        
        mScaleFaceTexture = inCapsule.readSavableArrayList( "scaleFaces", null );
        if ( mScaleFaceTexture != null ) {
        	// x and y are the normal texture scaling factors.  z is the bitmask of faces
        	for( Vector3f faceScale : mScaleFaceTexture ) {
        		this.scaleFaceTextureCoordinates( faceScale.x, faceScale.y, (int)faceScale.z );
        	}
        }
    }

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGMeshRevision
													, sCSGMeshDate
													, pBuffer ) );
	}

}