/*
 * Copyright (c) 2015- jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.export.xml;

import java.util.Collections;
import java.util.Map;
import java.util.ResourceBundle;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetProcessor;
import com.jme3.asset.cache.AssetCache;
import com.jme3.export.Savable;


/** Define a generic key that can provide XML processing context to the
 	XML load process.
 	
 	It can be configured to be caching, or not.
 	
 */
public class XMLContextKey 
	extends AssetKey<Object> 
{
	/** What caching is supported ? */
	protected Class<? extends AssetCache>		mAssetCache;
	/** What processor is needed? */
	protected Class<? extends AssetProcessor>	mAssetProcessor;
	/** XML extension:  map of reference names to translation ResourceBundles */
	protected Map<String,ResourceBundle>		mTranslationBundles;
	/** XML extension:  map of reference names to predefined Savable instances */
	protected Map<String,Savable>				mSeedValues;
	
	
	/** Basic constructor with a path to the asset to load */
    public XMLContextKey(
    	String	pName
    ) {
        super( pName );
        setSeedValues( null );
    }

    /** Null constructor for Savable processing */
    public XMLContextKey(
    ) {
        super();
        setSeedValues( null );
    }
    
    /** Provide translation resource bundles to the XML processing */
    public Map<String,ResourceBundle> getTranslationBundles() { return mTranslationBundles; }
    public void setTranslationBundles(
    	Map<String,ResourceBundle>	pBundleMap
    ) {
    	mTranslationBundles = pBundleMap;
    }
    public ResourceBundle getResourceBundle( 
    	String		pReferenceName
    ) {
    	if ( mTranslationBundles != null ) {
    		return( mTranslationBundles.get( pReferenceName ) );
    	} 
    	return( null );
    }
    
    /** Provide seed values to the XML processing */
    public Map<String,Savable> getSeedValues() { return mSeedValues; }
    public void setSeedValues(
    	Map<String,Savable>		pSeedValues
    ) {
    	mSeedValues = (pSeedValues == null) ? Collections.EMPTY_MAP : pSeedValues;
    }

    //////////////////////////////// Customize AssetKey ////////////////////////////////////////
    @Override
    public Class<? extends AssetCache> getCacheType() { return( mAssetCache ); }
    public void setCacheType( Class<? extends AssetCache> pClass ) { mAssetCache = pClass; }
    
    @Override
    public Class<? extends AssetProcessor> getProcessorType() { return( mAssetProcessor ); }
    public void setProcessorType( Class<? extends AssetProcessor> pClass ) { mAssetProcessor = pClass; }
}
