/** Copyright (c) 2003-2014 jMonkeyEngine
	Copyright (c) 2015, WCOmohundro
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

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import com.jme3.asset.AssetKey;
import com.jme3.material.Material;

/** Provide standard services for all materials applied within CSG shapes */
public class CSGMaterialManager 
{	
	/** Version tracking support */
	public static final String sCSGMaterialManagerRevision="$Rev$";
	public static final String sCSGMaterialManagerDate="$Date$";
	
	/** The predefined 'generic' material that applies to a shape as a whole */
	public static final Integer sGenericMaterialIndex = new Integer( 0 );

	/** Overloaded mapping of:
	 		Material AssetKey := Integer Material Index
	 		Integer Material Index := Material
	 */
	protected Map<Object,Object>		mMaterialMap;
	/** Count of materials defined */
	protected int						mMaterialCount;
	/** Stack of active 'generic' indexes (to handle nested groups of shapes) */
	protected Stack<Integer>			mGenericIndexStack;
	/** Control flag to force a single material */
	protected boolean					mForceSingleMaterial;
	
	
	/** Constructor based on a given 'generic' material */
	public CSGMaterialManager(
		Material		pGenericMaterial
	,	boolean			pForceSingleMaterial
	) {
		mForceSingleMaterial = pForceSingleMaterial;
		mMaterialMap = new HashMap( 7 );
		
		mGenericIndexStack = new Stack();
		mGenericIndexStack.push( sGenericMaterialIndex );
		
		// Define the generic material, even if null */
		mMaterialMap.put( sGenericMaterialIndex, pGenericMaterial );
		if ( pGenericMaterial != null ) {
			AssetKey materialKey = pGenericMaterial.getKey();
			if ( materialKey != null ) {
				mMaterialMap.put( materialKey, sGenericMaterialIndex );
			}
		}
	}
	
	/** Adjustments to the active generic index */
	public void pushGenericIndex(
		Material	pGenericMaterial
	) {
		mGenericIndexStack.push( resolveMaterialIndex( pGenericMaterial ) );
	}
	public void popGenericIndex(
	) {
		mGenericIndexStack.pop();
	}
	
	/** Get the count of materials that have been defined (in addition to the generic) */
	public int getMaterialCount() { return mMaterialCount; }
	
	/** Define a material for subsequent use, returning its associated index */
	public Integer resolveMaterialIndex(
		Material	pMaterial
	) {
		if ( mForceSingleMaterial || (pMaterial == null) ) {
			// By definition, the null material is the generic material
			return( mGenericIndexStack.peek() );
		} else {
			// The material's key is used to share the same material
			Integer materialIndex;
			AssetKey materialKey = pMaterial.getKey();
			if ( (materialKey != null) && mMaterialMap.containsKey( materialKey ) ) {
				// Use the material already found
				materialIndex = (Integer)mMaterialMap.get( materialKey );
			} else {
				// Use the next index in sequence
				materialIndex = new Integer( ++mMaterialCount );
				
				if ( materialKey != null ) {
					// Track the material by its AssetKey
					mMaterialMap.put( materialKey, materialIndex );
				}
				mMaterialMap.put( materialIndex, pMaterial );
			}
			return( materialIndex );
		}
	}
	
	/** Return the material used for a given index */
	public Material resolveMaterial(
		Integer		pMaterialIndex
	) {
		if ( mForceSingleMaterial ) {
			// Everything uses the generic
			pMaterialIndex = sGenericMaterialIndex;
		}
		Material aMaterial = (Material)mMaterialMap.get( pMaterialIndex );
		return( aMaterial );
	}
}
