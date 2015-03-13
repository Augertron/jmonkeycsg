package com.jme3.scene.plugins;

import java.io.IOException;
import java.io.InputStream;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.export.Savable;
import com.jme3.export.xml.XMLImporter;

/** An XML based scene loader that leverages the Savable XML format to 
 	reconstruct items in a scene
 */
public class XMLLoader 
	implements AssetLoader 
{
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
    		
    	} finally {
    		if ( aStream != null ) aStream.close();
    	}
    }

}
