/** Copyright (c) 2003-2014 jMonkeyEngine
	Copyright (c) 2016 WCOmohundro
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

import java.io.IOException;
import java.util.Map;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.math.CSGTransform;

import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.math.Transform;
import com.jme3.scene.Spatial;

/** Define a placeholder for a Material
 
 	This is a real Material with real default values.
 	However, if given a 'reference', then .resolveItem() will look up a substitute.
 	
 	Bare minimum default values seem to be:
 		material_def='Common/MatDefs/Misc/Unshaded.j3md'
 */
public class CSGPlaceholderMaterial 
	extends Material
	implements CSGPlaceholder<Material>
{
	/** How to lookup the Material */
	protected String	mReference;
	
	/** Standard null contructor */
	public CSGPlaceholderMaterial() {}
	
	
	/** Resolve the true Material */
	@Override
	public Material resolveItem(
		CSGElement		pContext
	,	boolean			pSearchFromTop
	) throws CSGConstructionException {
		Savable aMaterial = null;
	
		if ( mReference != null ) {
			// Look up the reference within the given context
			aMaterial = CSGLibrary.getLibraryItem( pContext, mReference, pSearchFromTop );
			if ( aMaterial instanceof Material ) {
				// Use this color
				return( (Material)aMaterial );
			}
		}
		// Nothing to use so act as your own default
		return( this );
	}
	
	///////////////////////////////// SAVABLE ///////////////////////////////////////////////
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		// The super will prep this instance with default values
		super.read( pImporter );
		
		InputCapsule aCapsule = pImporter.getCapsule( this );
		
		// What are we referencing 
		mReference = aCapsule.readString( "reference", null );
	}
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		super.write( pExporter );
		
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		
		// What are we referencing 
		aCapsule.write( mReference, "reference", null );
	}

}
