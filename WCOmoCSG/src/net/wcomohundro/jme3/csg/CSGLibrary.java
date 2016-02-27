/** Copyright (c) 2016, WCOmohundro
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.export.Savable;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.SceneProcessor;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/** Define a Factory level class that knows how to manage a set of library items.
 
 	I considered making this its own 'Savable' with its own ability to read/write.  But for now,
 	I will manage a Map<String,Savable> as the library itself and provide static service routines
 	to operate on the library.
 */
public class CSGLibrary 
{
	/** Factory level only */
	protected CSGLibrary() {}
	
	/** Service routine to populate a library
	 	-	Every Savable is associated with a given name
	 	-	Savables with inherent names will also register these names as 'alternates'
	 	-	Placeholders will be resolved to their corresponding elements where possible
	 	-	AssetKeys will be loaded via the provided AssetManager
	 */
	public static Map<String,Savable> fillLibrary(
		CSGElement			pContext
	,	Map<String,Savable>	pLibraryMap
	,	AssetManager		pAssetManager
	,	boolean				pRegisterAlternates
	) {
		if ( pLibraryMap != null ) {
			Map<String,Savable> altMap = null;
			
			// Register every item in the library
			for( Map.Entry<String,Savable> anEntry : pLibraryMap.entrySet() ) {
				String itemName = anEntry.getKey(), altName = null;
				Savable anItem = anEntry.getValue();
				
				if ( anItem instanceof CSGPlaceholder ) {
					// Resolve any placeholder items in the library
					Object resolvedItem = ((CSGPlaceholder)anItem).resolveItem( pContext, false );
					if ( resolvedItem instanceof Savable ) {
						// Retain the resolved item, not the placeholder
						anItem = (Savable)resolvedItem;
						anEntry.setValue( anItem );
					}
				}
				if ( (pAssetManager != null) && (anItem instanceof AssetKey) ) {
					// The key name becomes the default item name
					altName = ((AssetKey)anItem).getName();
					
					// Interpret/Load the underlying asset
					AssetInfo keyInfo = pAssetManager.locateAsset( (AssetKey)anItem );
					if ( keyInfo != null ) {
						// NOTE - using the pImporter to load the asset seems to destroy
						//		  the 'currentElement' context
						//anItem = (Savable)pImporter.load( keyInfo );
						anItem = pAssetManager.loadAsset( (AssetKey)anItem );
						anEntry.setValue( anItem );
					}
				}
				if ( altName == null ) {
					if ( (anItem instanceof Spatial) && (((Spatial)anItem).getName() != null) ) {
						altName = ((Spatial)anItem).getName();
					} else if ((anItem instanceof Light) && (((Light)anItem).getName() != null) ) {
						altName = ((Light)anItem).getName();
					} else if ( (anItem instanceof Material) && (((Material)anItem).getName() != null) ) {
						altName = ((Material)anItem).getName();
					}
				}
				// If we have an alternate name, register the item for a second time
				if ( pRegisterAlternates && (altName != null) && !altName.equals( itemName ) ) {
					if ( altMap == null ) altMap = new HashMap();
					altMap.put( altName,  anItem );
				}
			}
			// Once out of the iterator, add back all the alternates
			if ( altMap != null ) {
				pLibraryMap.putAll( altMap );
			}
		} else {
			// Nothing defined
			pLibraryMap = Collections.EMPTY_MAP;
		}
		return( pLibraryMap );
	}

	/** Service routine to locate an item in the library */
	public static Savable getLibraryItem(
		CSGElement		pContext
	,	String			pItemName
	,	boolean			pFromTop
	) {
		Savable anItem = null;
		if ( pContext != null ) {
			Map<String,Savable> aLibrary = pContext.getLibrary();
			if ( pFromTop ) {
				// Look down from the top first
				anItem = getLibraryItem( pContext.getParentElement(), pItemName, pFromTop );
				if ( anItem == null ) {
					// Nothing above, try looking local
					anItem = aLibrary.get( pItemName );
				}
			} else {
				// Look local first
				anItem = aLibrary.get( pItemName );
				if ( anItem == null ) {
					// Not local, so look up the tree
					anItem = getLibraryItem( pContext.getParentElement(), pItemName, pFromTop );
				}
			}
		}
		return( anItem );
	}
	
	/** Service routine to scan through all libraries, looking for environmental adjustments */
	public static void filtersAndProcessors( 
		Spatial					pContext
	, 	FilterPostProcessor		pFilterProcessor
	, 	ViewPort				pViewPort
	, 	AssetManager			pAssetManager
	) {
		// Scan through all elements
		if ( pContext instanceof CSGElement ) {
			// Look through this local library
			CSGElement anElement = (CSGElement)pContext;
			Map<String,Savable> aLibrary = anElement.getLibrary();
			for( Map.Entry<String,Savable> anEntry : aLibrary.entrySet() ) {
				String itemName = anEntry.getKey(), altName = null;
				Savable anItem = anEntry.getValue();
				
				if ( anItem instanceof CSGPlaceholder ) {
					// Resolve any placeholder items in the library
					Object resolvedItem = ((CSGPlaceholder)anItem).resolveItem( anElement, false );
					if ( resolvedItem instanceof Savable ) {
						// Retain the resolved item, not the placeholder
						anItem = (Savable)resolvedItem;
						anEntry.setValue( anItem );
					}
				}
				if ( anItem instanceof AssetKey ) {
					if ( pAssetManager == null ) continue;

					// Interpret/Load the underlying asset
					AssetInfo keyInfo = pAssetManager.locateAsset( (AssetKey)anItem );
					if ( keyInfo != null ) {
						// NOTE - using the pImporter to load the asset seems to destroy
						//		  the 'currentElement' context
						//anItem = (Savable)pImporter.load( keyInfo );
						anItem = pAssetManager.loadAsset( (AssetKey)anItem );
						anEntry.setValue( anItem );
					}
				} else if ( anItem instanceof Filter ) {
					if ( pFilterProcessor == null ) continue;
					
					// Include this filter in the given post processor
					pFilterProcessor.addFilter( (Filter)anItem );
					
				} else if ( anItem instanceof SceneProcessor ) {
					if ( pViewPort == null ) continue;
					
					// Attach this processor to the given view port
					pViewPort.addProcessor( (SceneProcessor)anItem );
				}
			}
		}
		// Scan through any subelements
		if ( pContext instanceof Node ) {
			for( Spatial aChild : ((Node)pContext).getChildren() ) {
				filtersAndProcessors( aChild, pFilterProcessor, pViewPort, pAssetManager );
			}
		}
	}

}
