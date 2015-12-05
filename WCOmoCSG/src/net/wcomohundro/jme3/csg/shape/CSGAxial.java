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

import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;



/** Abstract base class for those 'shapes' that produce their meshes by processing
 	a set of slices along the zAxis.  The span of the processing is determined by
 	the zExtent.  The shape runs from +zExtent to -zExtent, so the effective 'height'
 	of the shape is 2*zExtent.
 	
 	An axial is considered to be 'closed' if front/back endcaps are generated for the shape
 	
 	An 'inverted' axial is designed to be viewed from the inside
 */
public abstract class CSGAxial 
	extends CSGMesh
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGAxialRevision="$Rev$";
	public static final String sCSGAxialDate="$Date$";


	/** How many sample slices to generate along the axis */
    protected int 			mAxisSamples;
    /** How tall is the shape along the z axis (where the 'extent' is half the total height) */
    protected float 		mExtentZ;


	protected CSGAxial(
	) {
		this( 32, 1, true, false );
	}
    protected CSGAxial(
    	int 		pAxisSamples
    , 	float 		pZExtent
    ,	boolean		pClosed
    ,	boolean		pInverted
    ) {
        mAxisSamples = pAxisSamples;
        mExtentZ = pZExtent;
        setClosed( pClosed );
        setInverted( pInverted );
    }

    /** Configuration accessors */
    public int getAxisSamples() { return mAxisSamples; }
    public void setAxisSamples( int pSampleCount ) { mAxisSamples = pSampleCount; }
    
    public float getZExtent() { return mExtentZ; }
    public void setZExtent( float pZExtent ) { mExtentZ = pZExtent; }
    
    public float getHeight() { return mExtentZ * 2; }
    public void setHeight( float pHeight ) { mExtentZ = pHeight / 2.0f; }
    
    /** An Axial is considered 'closed' if the front/back faces are generated */
    public boolean isClosed(
    ) { 
    	return( (mGeneratedFacesMask & Face.FRONT_BACK.getMask()) == Face.FRONT_BACK.getMask() ); 
    }
    public void setClosed( 
    	boolean pIsClosed 
    ) { 
    	if ( pIsClosed ) {
    		mGeneratedFacesMask |= Face.FRONT_BACK.getMask();
    	} else {
    		mGeneratedFacesMask &= ~Face.FRONT_BACK.getMask();
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
        outCapsule.write( mExtentZ, "zExtent", 1 );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        // Let the super do its thing
        super.read( pImporter );
        
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mAxisSamples = inCapsule.readInt( "axisSamples", mAxisSamples );
        
        // Let each shape apply its own zExtent default
        mExtentZ = inCapsule.readFloat( "zExtent", 0 );
        if ( mExtentZ == 0 ) {
        	float aHeight = inCapsule.readFloat( "height", 0 );
        	if ( aHeight > 0 ) {
        		// An explicit height sets the zExtent
        		mExtentZ = aHeight / 2.0f;
        	}
        }
    }
        
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( CSGAxial.class
													, sCSGAxialRevision
													, sCSGAxialDate
													, pBuffer ) );
	}

}
