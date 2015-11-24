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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.jme3.asset.AssetKey;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.Control;
import com.jme3.scene.control.LightControl;

/** In order to provide for mapping various materials to the surfaces created by the
 	CSG blending process, it is necessary to produce independent Meshes for those
 	differing materials.
 	Likewise, to provide independent lighting to selected surfaces, independent Meshes
 	are needed.  
 	In addition, custom Physics may be applied requiring independent Meshes.
 	
 	The CSGMeshManager keeps track of the Materials, lighting Nodes and Physics
 	used during the construction of a blended shape.  Shapes register themselves 
 	with the MeshManager, which records the Materials/Nodes/Physics being used.  
 	
 	The result of registration is a Mesh Index, which can be assigned to every surface 
 	of any given shape.  The resultant surfaces in the final shape will trace back
 	via the given index.
 	
 	The MeshManager can produce the 'master' Mesh, which is a blend of all surfaces
 	regardless of Material or Node.  This Mesh is useful for CSGGeometry, which
 	operates solely on a single resultant Mesh, and for boundary and collision 
 	processing for CSGGeonode.
 	
 	The secondary Meshes are returned as a set of Geometrys and/or Nodes.  A Geometry
 	will represent a Mesh associated with a given Material.  A Node will be a 
 	collection of Geometrys and/or Nodes to which custom Lighting/Physics applies.
 */
public class CSGMeshManager 
{	
	/** Version tracking support */
	public static final String sCSGMaterialManagerRevision="$Rev$";
	public static final String sCSGMaterialManagerDate="$Date$";
	
	/** The predefined master mesh that crosses all components */
	public static final Integer sMasterMeshIndex = new Integer( -1 );
	
	/** The predefined 'generic' mesh that applies to the master Material */
	public static final Integer sGenericMeshIndex = new Integer( 0 );

	/** Overloaded mapping of:
	 		Material AssetKey := CSGMeshInfo
	 		Integer Mesh Index := CSGMeshInfo
	 */
	protected Map<Object,CSGMeshInfo>	mMeshMap;
	/** Count of distinct meshes OTHER than the generic master */
	protected int						mMeshCount;
	/** Stack of active 'generic' indexes (to handle nested groups of shapes) */
	protected Stack<CSGMeshInfo>		mGenericIndexStack;
	/** Control flag to force a single material */
	protected boolean					mForceSingleMaterial;
	
	
	/** Constructor based on a given 'generic' material */
	public CSGMeshManager(
		Material		pGenericMaterial
	,	boolean			pForceSingleMaterial
	) {
		mForceSingleMaterial = pForceSingleMaterial;
		mMeshMap = new HashMap( 7 );
		
		CSGMeshInfo genericInfo = new CSGMeshInfo( sGenericMeshIndex, pGenericMaterial, null, null );
		mGenericIndexStack = new Stack();
		mGenericIndexStack.push( genericInfo );
		mMeshMap.put( sGenericMeshIndex, genericInfo );
		
		// Define the generic material, even if null */
		if ( pGenericMaterial != null ) {
			AssetKey materialKey = pGenericMaterial.getKey();
			if ( materialKey != null ) {
				// Keep track of the generic Material via its standard key
				mMeshMap.put( materialKey, genericInfo );
			}
		}
	}
	
	/** Adjustments to the active generic index */
	public void pushGenericIndex(
		Material	pGenericMaterial
	,	CSGShape	pShape
	) {
		CSGMeshInfo meshInfo = resolveMeshInfo( pGenericMaterial, pShape );
		if ( meshInfo.mMaterial == null ) {
			// No specific override, still using the generic in force
			meshInfo.mMaterial = this.resolveMaterial( sGenericMeshIndex );
		}
		mGenericIndexStack.push( meshInfo  );
	}
	public void popGenericIndex(
	) {
		mGenericIndexStack.pop();
	}
	
	/** Get the count of distinct Meshes that have been defined (in addition to the generic) */
	public int getMeshCount() { return mMeshCount; }
	
	/** Get a list of appropriate Spatials that represent all the custom Meshes being managed */
	public List<Spatial> getSpatials(
		String		pCoreName
	,	Control		pLightControl
	) {
		Map<String,CSGGeonode> nodeMap = new HashMap();
		
		List<Spatial> aList = new ArrayList( mMeshCount + 1 );
		for( int i = 0; i <= mMeshCount; i += 1 ) {
			Spatial aSpatial;
			CSGMeshInfo meshInfo = mMeshMap.get( new Integer( i ) );
			if ( meshInfo.mMesh != null ) {
				// Build a Geometry that covers the given mesh with the desired Material,
				// applying local lights/physics as needed
				aSpatial = new Geometry( pCoreName + i, meshInfo.mMesh );
				if ( meshInfo.mMaterial != null ) {
					aSpatial.setMaterial( meshInfo.mMaterial.clone() );
				}
				StringBuilder shapeKey = new StringBuilder( 128 );
				if ( meshInfo.mLightListOwner != null ) {
					// Custom lights have been defined.  
					shapeKey.append( "LL" );
					shapeKey.append( meshInfo.mLightListOwner.getShapeKey() );
				}
				if ( meshInfo.mPhysicsOwner != null ) {
					// Custom physics have been defined
					shapeKey.append( "P" );
					shapeKey.append( meshInfo.mPhysicsOwner.getShapeKey() );
				}
				if ( shapeKey.length() > 0 ) {
					// In anticipation of multiple meshes sharing the same set of lights
					// and/or physics, attach it all to a Node
					String stringKey = shapeKey.toString();
					CSGGeonode aNode = nodeMap.get( stringKey );
					if ( aNode == null ) {
						// This shape's Node has not yet been created
						aNode = new CSGGeonode( pCoreName + i + "Node" );
						aList.add( aNode );
						nodeMap.put( stringKey, aNode );
						
						// Include the lights at the node level
						if ( meshInfo.mLightListOwner != null ) {
							CSGLightControl.applyLightControl( pLightControl
															, meshInfo.mLightListOwner.getLocalLightList()
															, meshInfo.mLightListOwner.getLocalTransform()
															, aNode
															, true );
						}
						if ( meshInfo.mPhysicsOwner != null ) {
							aNode.setPhysics( meshInfo.mLightListOwner.getPhysics() );
						}
					}
					// Attach the mesh/material to the node with the lights
					aNode.attachChild( aSpatial );
					
				} else {
					// No lights, just add the Geometry as is
					aList.add( aSpatial );
				}
			}
		}
		return( aList );
	}
	
	/** Define a material for subsequent use, returning its associated Mesh index */
	public Integer resolveMeshIndex(
		Material	pMaterial
	,	CSGShape	pShape
	) {
		return( resolveMeshInfo( pMaterial, pShape ).mIndex );
	}
	
	/** Register the Master mesh */
	public void registerMasterMesh(
		Mesh		pMesh
	) {
		// By definition, the master mesh relies on the generic material
		CSGMeshInfo masterInfo = new CSGMeshInfo( sMasterMeshIndex, pMesh, resolveMaterial( sGenericMeshIndex ) );
		mMeshMap.put( sMasterMeshIndex, masterInfo );
	}
	
	/** Register a Mesh under a given index */
	public void registerMesh(
		Mesh		pMesh
	,	Integer		pMeshIndex
	) {
		CSGMeshInfo meshInfo = mMeshMap.get( pMeshIndex );
		if ( meshInfo == null ) {
			throw new IllegalArgumentException( "Mesh Index not active: " + pMeshIndex );
		} else if ( meshInfo.mMesh != null ) {
			throw new IllegalArgumentException( "Mesh Index already has mesh: " + pMeshIndex );
		}
		meshInfo.mMesh = pMesh;
	}
	
	/** Return the material used for a given index */
	public Material resolveMaterial(
		Integer		pMeshIndex
	) {
		if ( mForceSingleMaterial ) {
			// Everything uses the generic
			pMeshIndex = sGenericMeshIndex;
		}
		Material aMaterial = mMeshMap.get( pMeshIndex ).mMaterial;
		return( aMaterial );
	}
	
	/** Return the mesh used for a given index */
	public Mesh resolveMesh(
		Integer		pMeshIndex
	) {
		Mesh aMesh = mMeshMap.get( pMeshIndex ).mMesh;
		return( aMesh );
	}
	
	/** Service routine that constructs the appropriate lookup key to select
	 	Mesh information based on the active material and active lighting
	 */
	protected Object selectKey(
		Material	pMaterial
	,	CSGShape	pLightListOwner
	,	CSGShape	pPhysicsOwner
	) {
		// Base the key on the material itself
		AssetKey materialKey = null;
		if ( (pMaterial != null) && !mForceSingleMaterial ) {
			materialKey = pMaterial.getKey();
		}
		StringBuilder aBuffer = new StringBuilder( 128 );
		if ( pLightListOwner != null ) {
			aBuffer.append( "L" );
			aBuffer.append( pLightListOwner.getShapeKey() );
		}
		if ( pPhysicsOwner != null ) {
			if ( aBuffer.length() > 0 ) aBuffer.append( "-" );
			aBuffer.append( "P" );
			aBuffer.append( pPhysicsOwner.getShapeKey() );
		}
		if ( materialKey != null ) {
			if ( aBuffer.length() == 0 ) {
				// No custom lights/physics are active, rely solely on the material key
				return( materialKey );
			} else {
				// Blend in the key
				aBuffer.append( "-" );
				aBuffer.append( materialKey.getName() );
			}
		}
		return( (aBuffer.length() > 0) ? aBuffer.toString() : null );
	}
	
	/** Service routine that locates or creates the appropriate MeshInfo to use */
	protected CSGMeshInfo resolveMeshInfo(
		Material	pMaterial
	,	CSGShape	pShape
	) {
		// Decide which 'key' we are looking up
		CSGShape lightOwner = null;
		if ( pShape.getLocalLightList().size() > 0 ) {
			// This shape has custom lights, remember it
			lightOwner = pShape;
		} else {
			// If the given shape has no local lights, then use the lights of the shape
			// who has defined the active 'generic'
			lightOwner = mGenericIndexStack.peek().mLightListOwner;
		}
		CSGShape physicsOwner = null;
		if ( pShape.getPhysics() != null ) {
			// This shape has custom physics, remember it
			physicsOwner = pShape;
		} else {
			// If the given shape has no local physics, then use the physics of the
			// shape who has definced the active 'generic'
			physicsOwner = mGenericIndexStack.peek().mPhysicsOwner;
		}
		Object materialKey = selectKey( pMaterial, lightOwner, physicsOwner );
		
		// Look for custom material
		if ( materialKey == null ) {
			// By definition, the null material is the generic material
			return( mGenericIndexStack.peek() );
			
		} else {
			// The material's key is used to share the same material
			CSGMeshInfo meshInfo;
			if ( (materialKey != null) && mMeshMap.containsKey( materialKey ) ) {
				// Use the material already found
				meshInfo = mMeshMap.get( materialKey );
			} else {
				// Use the next index in sequence
				Integer meshIndex = new Integer( ++mMeshCount );
				
				// At this point, we must have a Material, so use the 
				// generic as needed
				if ( pMaterial == null ) {
					pMaterial = mGenericIndexStack.peek().mMaterial;
				}
				meshInfo = new CSGMeshInfo( meshIndex, pMaterial, lightOwner, physicsOwner );
				
				if ( materialKey != null ) {
					// Track the material by its AssetKey
					mMeshMap.put( materialKey, meshInfo );
				}
				mMeshMap.put( meshIndex, meshInfo );
			}
			return( meshInfo );
		}
	}


}
/** Helper class that tracks the appropriate Material/Lighting/Mesh for a given index */
class CSGMeshInfo
{
	/** The index that selects this info */
	protected Integer		mIndex;
	/** The Material that applies */
	protected Material		mMaterial;
	/** The custom lighting that applies */
	protected CSGShape		mLightListOwner;
	/** The custom physics that applies */
	protected CSGShape		mPhysicsOwner;
	/** The generated mesh */
	protected Mesh			mMesh;
	
	/** Standard constructor */
	CSGMeshInfo(
		Integer			pIndex
	,	Material		pMaterial
	,	CSGShape		pLightListOwner
	,	CSGShape		pPhysicsOwner
	) {
		this.mIndex = pIndex;
		this.mMaterial = pMaterial;
		if ( (pLightListOwner != null) && (pLightListOwner.getLocalLightList().size() > 0) ) {
			// Remember the active lights
			this.mLightListOwner = pLightListOwner;
		}
		if ( (pPhysicsOwner != null) && (pPhysicsOwner.getPhysics() != null) ) {
			// Remember the active physics
			this.mPhysicsOwner = pPhysicsOwner;
		}
	}
	CSGMeshInfo(
		Integer			pIndex
	,	Mesh			pMesh
	,	Material		pMaterial
	) {
		this.mIndex = pIndex;
		this.mMesh = pMesh;
		this.mMaterial = pMaterial;
	}

}
