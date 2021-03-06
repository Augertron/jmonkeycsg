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
package net.wcomohundro.jme3.csg;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

import java.io.IOException;

import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI.CSGErrorCode;



/** SHAPE extension to load a mesh via an external definition */
public class CSGExternal 
	extends CSGShape
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGExternalRevision="$Rev$";
	public static final String sCSGExternalDate="$Date$";

	/** Standard null constructor */
	public CSGExternal(
	) {
	}

	/** 'faces' not supported */
	public void scaleFaceTextureCoordinates(
		float		pScaleX
	,	float		pScaleY
	,	int			pFacemask
	) {
	}

	/** OVERRIDE:  shape with no mesh or subshapes so expect a MODEL */
	protected void readNoContents(
		JmeImporter		pImporter
	,	InputCapsule	pInCapsule
	) throws IOException {
    	String modelName = pInCapsule.readString( "model", null );
    	if ( modelName != null ) try {
    		// Load the given model
        	Spatial aSpatial = pImporter.getAssetManager().loadModel( modelName );
        	if ( aSpatial instanceof Geometry ) {
	        	this.mesh = ((Geometry)aSpatial).getMesh();
	        	
	        	// We are valid if we have a mesh
	        	if ( this.mesh == null ) {
	        		setError( new CSGConstructionException( CSGErrorCode.EMPTY_SHAPE
	        				,	"CSGExternal.read - model has no mesh: "  + modelName
	        				,	this ) );
	        	}
        	} else {
        		// Not a valid shape
        		setError( new CSGConstructionException( CSGErrorCode.EMPTY_SHAPE
        				,	"CSGExternal.read - model not a Geometry: "  + modelName
        				,	this ) );
        	}
    	} catch( Exception ex ) {
    		// Retain the error, which marks this shape invalid
    		setError( new CSGConstructionException( CSGErrorCode.LOAD_FAILED
    					,	"CSGExternal.read - model load failed: " + modelName
    					,	this
    					,	ex ) );
    	} else {
			setError( new CSGConstructionException( CSGErrorCode.EMPTY_SHAPE
					, 	"CSGExternal.read() with no mesh/shapes/model"
					,	this ) );
    	}
	}

        
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( CSGExternal.class
													, sCSGExternalRevision
													, sCSGExternalDate
													, pBuffer ) );
	}

}
