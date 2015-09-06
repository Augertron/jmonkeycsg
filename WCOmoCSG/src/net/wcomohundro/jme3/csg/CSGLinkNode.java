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

import java.io.IOException;
import java.util.List;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/** Simple extension of Node that extends Savable.read() to accept a list of external references 
 	and provides for a custom environment.
 */
public class CSGLinkNode 
	extends Node
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGLinkNodeRevision="$Rev$";
	public static final String sCSGLinkNodeDate="$Date$";
	
	/** Common environment to supply for CSG processing */
	protected CSGEnvironment mEnvironment;

	@Override
    public void read(
    	JmeImporter 	pImporter
    ) throws IOException {
        // Any custom environment?
    	AssetManager aManager = pImporter.getAssetManager();
        InputCapsule aCapsule = pImporter.getCapsule( this );
        mEnvironment = (CSGEnvironment)aCapsule.readSavable( "csgEnvironment", null );

    	// Standard Node processing
        super.read( pImporter );
        
        // Look for list of external definitions
		List<AssetKey> assetLoaderKeys
        	= (List<AssetKey>)aCapsule.readSavableArrayList( "assetLoaderKeyList", null );
		if ( assetLoaderKeys != null ) {
			// Load each associated asset
			for( AssetKey aKey : assetLoaderKeys ) {
				AssetInfo keyInfo = aManager.locateAsset( aKey );
				if ( keyInfo != null ) {
					Object aSpatial = aManager.loadAsset( aKey );
					if ( aSpatial instanceof Spatial ) {
						this.attachChild( (Spatial)aSpatial );
					}
				}
			}
		}
    }
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGLinkNodeRevision
													, sCSGLinkNodeDate
													, pBuffer ) );
	}

}
