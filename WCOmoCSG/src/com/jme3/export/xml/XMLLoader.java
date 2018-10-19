/** Copyright (c) 2015-2018, WCOmohundro
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
package com.jme3.export.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.asset.FilterKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.export.xml.DOMInputCapsule;
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
        assetManager.registerLoader( com.jme3.export.xml.XMLLoader.class, "xml" );
        
        // Use an appropriate key for the type of object being loaded
        ModelKey aKey = new ModelKey( "SomeModelFileName.xml" );
	    Spatial aScene = assetManager.loadAsset( aKey );
	    rootNode.attachChild( aScene );
	}
	
	The original intent was for a thin wrapper that morphed a load asset request
	directly to a load via XMLImporter.  But I needed more changes in the import 
	structure and have decided to eliminate the need for XMLImporter and to 
	include the necessary functionality here.
	
	NOTE that if a Savable invokes an asset load via the AssetManager from
		 within its .read() processing, it is possible that this instance
		 if called a second time via its .load() method.  We must take care
		 to preserve the proper context it this should happen.
 */
public class XMLLoader 
	implements JmeImporter 
{
	/** Logging support */
    protected static final Logger sLogger = Logger.getLogger( XMLLoader.class.getName() );

    
    /** The active asset manager */
    protected AssetManager			mAssetManager;
    /** Stack of active loading contexts */
    protected Stack<XMLLoadContext>	mContextStack;
    
    /** The asset manager uses the null constructor */
    public XMLLoader(
    ) {
    	mContextStack = new Stack();
    }
    
    /** Service routine that fills an XMLContextKey with the current context information */
    public void fillKey(
    	XMLContextKey	pKey
    ) {
    	if ( !mContextStack.isEmpty() ) {
    		XMLLoadContext aContext = mContextStack.peek();
    		
    		pKey.setClassAbbreviations( aContext.mInCapsule.mClassAbbreviations );
    		pKey.setTranslationBundles( aContext.mInCapsule.mResourceBundles, aContext.mInCapsule.mInternalBundle );
    		pKey.setSeedValues( aContext.mInCapsule.mReferencedSavables );
    	}
    }
    
    /** Load the XML */
	@Override
    public Object load(
    	AssetInfo	pAssetInfo
    ) throws IOException {
		// Wire back to the active asset manager
		mAssetManager = pAssetInfo.getManager();
		
		// Process the stream
    	InputStream aStream = null;
    	try {
    		// Read and parse the given stream
    		aStream = pAssetInfo.openStream();
    		Savable aNode = load( pAssetInfo.getKey(), aStream );
    		return( aNode );
    	
    	} catch( IOException ex ) {
    		sLogger.log( Level.WARNING, "XMLLoader failed", ex );
    		throw ex;
    	} catch( Throwable ex ) {
    		sLogger.log( Level.WARNING, "XMLLoader failed", ex );
    		throw new IOException( ex );
    	} finally {
    		if ( aStream != null ) aStream.close();
    	}
    }

	/** Service routine that process XML and produces a 'Savable' */
    public Savable load(
    	AssetKey		pAssetKey
    ,	InputStream		pInStream
    ) throws IOException {
    	XMLLoadContext aContext = mContextStack.push( new XMLLoadContext( pAssetKey ) );
        try {
        	// Produce the DOM via standard XML services
        	Document aDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse( pInStream );
        	aContext.mInCapsule = new XMLInputCapsule( aDocument, this );
        	
        	if ( pAssetKey instanceof XMLContextKey ) {
        		// The key can provide us with extra contextual data
        		XMLContextKey xmlEnvironment = (XMLContextKey)pAssetKey;
        		
        		aContext.mInCapsule.setClassAbbreviations( xmlEnvironment.getClassAbbreviations() );
        		aContext.mInCapsule.seedReferencedSavables( xmlEnvironment.getSeedValues()
        													, xmlEnvironment.blendReferences() );
        		
        		aContext.mInCapsule.setResourceBundles( xmlEnvironment.getTranslationBundles()
        											,	xmlEnvironment.getInternalBundle() );
        	}
        	// The entire document is assumed to be a single savable, with interior components
            Savable rootNode =  aContext.mInCapsule.readSavable( null, null );
            
            // NOTE that at this point, we could retrieve the referenced savables
            //		that were produced during processing.  These could be interesting to a
            //		subsequent load????
            
            return( rootNode );
            
        } catch (SAXException e) {
            IOException ex = new IOException();
            ex.initCause(e);
            throw ex;
        } catch (ParserConfigurationException e) {
            IOException ex = new IOException();
            ex.initCause(e);
            throw ex;
        } finally {
        	mContextStack.pop();
        }
    }

	/** Provide the reading 'capsule' that operates off a DOM */
	@Override
	public InputCapsule getCapsule(
		Savable 	pID
	) {
		return mContextStack.peek().mInCapsule;
	}

	/** Simple hook to provide the active asset manager during read operations */
	@Override
	public AssetManager getAssetManager() { return mAssetManager; }
	public void setAssetManager( AssetManager pManager ) { mAssetManager = pManager; }

	/** Version not currently supported */
	@Override
	public int getFormatVersion() { return 0; }

	/** Helper class that maintains a proper active context */
	class XMLLoadContext 
	{
		AssetKey		mAssetKey;
		XMLInputCapsule	mInCapsule;
		
		XMLLoadContext(
			AssetKey		pAssetKey
		) {
			mAssetKey = pAssetKey;
		}
	}
}
