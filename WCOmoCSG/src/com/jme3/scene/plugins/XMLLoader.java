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
package com.jme3.scene.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.FilterKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.export.Savable;
import com.jme3.export.xml.XMLImporter;
import com.jme3.scene.Spatial;

/** An XML based scene loader that leverages the Savable XML format to 
 	reconstruct items in a scene
 	
 	// Sample SimpleApplication set up
 	@Override
    public void simpleInitApp(
    ) {
 	    // Register this XMLImporter to handle xml files
        assetManager.registerLocator( ".", FileLocator.class );
        assetManager.registerLoader( com.jme3.scene.plugins.XMLLoader.class, "xml" );
        
        // Use an appropriate key for the type of object being loaded
        ModelKey aKey = new ModelKey( "SomeModelFileName.xml" );
	    Spatial aScene = assetManager.loadAsset( aKey );
	    rootNode.attachChild( aScene );
	}
 */
public class XMLLoader 
	implements AssetLoader 
{
	/** Logging support */
    protected static final Logger sLogger = Logger.getLogger( XMLLoader.class.getName() );

    /** Load the XML */
	@Override
    public Object load(
    	AssetInfo	pAssetInfo
    ) throws IOException {
		// Wire the XMLImporter back to the active asset manager
    	XMLImporter anImporter = new XMLImporter();
    	anImporter.setAssetManager( pAssetInfo.getManager() );
    	
    	InputStream aStream = null;
    	try {
    		// Read and parse the given stream
    		aStream = pAssetInfo.openStream();
    		Savable aNode = anImporter.load( aStream );
    		return( aNode );
    	
    	} catch( IOException ex ) {
    		sLogger.log( Level.WARNING, "XMLLoader failed", ex );
    		throw ex;
    	} catch( Exception ex ) {
    		sLogger.log( Level.WARNING, "XMLLoader failed", ex );
    		throw new IOException( ex );
    	} finally {
    		if ( aStream != null ) aStream.close();
    	}
    }

}
