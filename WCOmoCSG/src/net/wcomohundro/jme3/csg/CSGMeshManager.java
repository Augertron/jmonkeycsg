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

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties;

import com.jme3.asset.AssetKey;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.material.Material;
import com.jme3.math.Transform;
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
	 		Compound Key := CSGMeshInfo
	 		Integer Mesh Index := CSGMeshInfo
	 */
	protected Map<Object,CSGMeshInfo>	mMeshMap;
	/** Count of distinct meshes OTHER than the generic master */
	protected int						mMeshCount;
	/** Stack of active 'generic' indexes (to handle nested groups of shapes) */
	protected Stack<CSGMeshInfo>		mGenericIndexStack;
	/** Control flag to force a single material */
	protected boolean					mForceSingleMaterial;
	/** Mapping to resolve 'shared' lights */
	protected Map<String,List<Control>>	mLightingMap;
	
	
	/** Constructor based on a given 'generic' material */
	public CSGMeshManager(
		CSGElement		pCSGElement
	,	boolean			pForceSingleMaterial
	) {
		mForceSingleMaterial = pForceSingleMaterial;
		mMeshMap = new HashMap( 7 );
		mLightingMap = new HashMap( 7 );
		
		CSGMeshInfo genericInfo = new CSGMeshInfo( sGenericMeshIndex, pCSGElement );
		mGenericIndexStack = new Stack();
		mGenericIndexStack.push( genericInfo );
		mMeshMap.put( sGenericMeshIndex, genericInfo );
		
		// Define the generic info under its appropriate key, both as NULL material and the
		// possibly explicitly named material.  Remember that NULL is a valid key for the
		// generic
		Object genericKey
			= selectKey( null, null, genericInfo.mLightListKey, genericInfo.mPhysicsKey );
		mMeshMap.put( genericKey, genericInfo );

		if ( genericInfo.mMaterial != null ) {
			genericKey
				= selectKey( null, genericInfo.mMaterial, genericInfo.mLightListKey, genericInfo.mPhysicsKey );
			if ( genericKey != null ) {
				mMeshMap.put( genericKey, genericInfo );
			}
		}
	}
	
	/** Adjustments to the active generic index */
	public void pushGenericIndex(
		Material	pGenericMaterial
	,	CSGShape	pShape
	) {
		CSGMeshInfo meshInfo = resolveMeshInfo( null, pGenericMaterial, null, pShape );
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
		Map<String,CSGNode> nodeMap = new HashMap();
		
		List<Spatial> aList = new ArrayList( mMeshCount + 1 );
		for( int i = 0; i <= mMeshCount; i += 1 ) {
			Geometry aSpatial;
			CSGMeshInfo meshInfo = mMeshMap.get( new Integer( i ) );
			
			// We are only interested in 'real' meshes
			if ( (meshInfo.mMesh != null) && (meshInfo.mMesh.getTriangleCount() > 0) ) {
				// Build a Geometry that covers the given mesh with the desired Material,
				// applying local lights/physics as needed
				aSpatial = new Geometry( meshInfo.resolveName( pCoreName + i ), meshInfo.mMesh );
				aSpatial.updateModelBound();			// I am not 100% sure this is needed
				
				if ( meshInfo.mMaterial != null ) {
					aSpatial.setMaterial( meshInfo.mMaterial.clone() );
				}
				StringBuilder shapeKey = new StringBuilder( 128 );
				if ( meshInfo.mUniqueMesh ) {
					// Custom mesh name
					shapeKey.append( meshInfo.mName );
				}
				if ( meshInfo.mLightListKey != null ) {
					// Custom lights have been defined.  
					shapeKey.append( meshInfo.mLightListKey );
				}
				if ( meshInfo.mPhysicsKey != null ) {
					// Custom physics have been defined
					shapeKey.append( meshInfo.mPhysicsKey );
				}
				if ( (shapeKey.length() == 0) && (meshInfo.mDecorations != null) ) {
					// We need a parent Node to hold the decorations
					shapeKey.append( CSGShape.assignInstanceKey( "Decorations" ) );
				}
				if ( shapeKey.length() > 0 ) {
					// In anticipation of multiple meshes sharing the same set of lights
					// and/or physics, attach it all to a Node
					String stringKey = shapeKey.toString();
					CSGNode aNode = nodeMap.get( stringKey );
					if ( aNode == null ) {
						// This shape's Node has not yet been created
						aNode = meshInfo.mRenderNode;
						if ( aNode == null ) {
							aNode = new CSGNode( meshInfo.resolveName( pCoreName + i + "Node" ) );
						}
						aList.add( aNode );
						nodeMap.put( stringKey, aNode );
						
						// Include the lights at this new node level
						// Remember that the meshInfo.mLightControls was set based on the lights
						// associated with the shape/mesh that caused the creation of this
						// particular info entry.
						if ( meshInfo.mLightControls != null ) {
							// Attach every light to the node, and register its control
							for( Control aLightControl : meshInfo.mLightControls ) {
								aNode.addLight( CSGLightControl.resolveLight( aLightControl ) );
								aNode.addControl( aLightControl );
							}
						}
						if ( meshInfo.mSharedLights != null ) {
							// Share in the shared lights 
							// (NOT copies, and not controlled - that is the province of the original)
							for( String aLightReference : meshInfo.mSharedLights ) {
								List<Control> sharedControls = this.mLightingMap.get( aLightReference );
								if ( sharedControls != null ) {
									for( Control aLightControl : sharedControls ) {
										aNode.addLight( CSGLightControl.resolveLight( aLightControl ) );
									}
								}
							}
						}
						if ( meshInfo.mPhysics != null ) {
							// Remember the active physics that was registered with this mesh
							aNode.setPhysics( meshInfo.mPhysics );
						}
						if ( meshInfo.mDecorations != null ) {
							// All decorations applied to this mesh are included within the Node
							for( Spatial aDecoration : meshInfo.mDecorations ) {
								aNode.attachChild( aDecoration );
							}
						}
					}
					// Attach the mesh/material to the node with the lights/physics
					aNode.attachChild( aSpatial );
					
				} else {
					// No lights/physics, just add the Geometry as is
					aList.add( aSpatial );
				}
			}
		}
		return( aList );
	}
	
	/** Define a material for subsequent use, returning its associated Mesh index */
	public Integer resolveMeshIndex(
		String			pMeshName
	,	Material		pMaterial
	,	PhysicsControl	pPhysics
	,	CSGShape		pShape
	) {
		return( resolveMeshInfo( pMeshName, pMaterial, pPhysics, pShape ).mIndex );
	}
	public Integer resolveMeshIndex(
		CSGFaceProperties	pProperties
	,	CSGShape			pShape
	) {
		if ( pProperties.hasName() || pProperties.hasMaterial() || pProperties.hasPhysics()) {
			return( resolveMeshInfo( pProperties, pShape ).mIndex );
		} else {
			return( null );
		}
	}
	
	/** Register the Master mesh */
	public void registerMasterMesh(
		Mesh		pMesh
	,	String		pName
	) {
		// By definition, the master mesh relies on the generic material
		CSGMeshInfo masterInfo 
			= new CSGMeshInfo( sMasterMeshIndex, pName, pMesh, resolveMaterial( sGenericMeshIndex ) );
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
		String		pMeshName
	,	Material	pMaterial
	,	String		pLightListKey
	,	String		pPhysicsKey
	) {
		// Base the key on the material itself
		AssetKey materialKey = null;
		if ( (pMaterial != null) && !mForceSingleMaterial ) {
			materialKey = pMaterial.getKey();
			if ( materialKey == null ) {
				// We have a special material without a particular loader name,
				// and we need something unique to this particular instance.
				// identityHashCode() is probably close enough for what I need.
				materialKey = new AssetKey( "Material-" + System.identityHashCode( pMaterial ) );
			}
		}
		StringBuilder aBuffer = new StringBuilder( 128 );
		if ( pMeshName != null ) {
			aBuffer.append( pMeshName );
		}
		if ( pLightListKey != null ) {
			if ( aBuffer.length() > 0 ) aBuffer.append( "-" );
			aBuffer.append( pLightListKey );
		}
		if ( pPhysicsKey != null ) {
			if ( aBuffer.length() > 0 ) aBuffer.append( "-" );
			aBuffer.append( pPhysicsKey );
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
		CSGFaceProperties	pProperties
	,	CSGShape			pShape
	) {
		return( resolveMeshInfo( pProperties.getName()
								, pProperties.getMaterial()
								, pProperties.getPhysics()
								, pShape ) );
	}
	protected CSGMeshInfo resolveMeshInfo(
		String			pMeshName
	,	Material		pMaterial
	,	PhysicsControl	pPhysics
	,	CSGShape		pShape
	) {
		// Decide which 'key' we are looking up
		CSGMeshInfo useInfo;
		
		// NOTE that pMeshName will only be non-null if a custom name was defined
		//		via face properties
		CSGMeshInfo genericInfo = mGenericIndexStack.peek();

		// Which lights apply?
		String aLightListKey;
		List<Control> lightControls = pShape.getLightControls();
		String[] sharedLights = pShape.getSharedLights();
		boolean customLights;
		if ( lightControls != null ) {
			// This shape has custom lights
			customLights = true;
			aLightListKey = CSGMeshInfo.createLightListKey( pShape, false );
		} else {
			// If the given shape has no local lights, then use the lights of the shape
			// who has defined the active 'generic'
			customLights = false;
			lightControls = genericInfo.mLightControls;

			// Use the generic light list, and leverage its key
			aLightListKey = genericInfo.mLightListKey;
		}
		if ( sharedLights != null ) {
			// Having shared lights forces a custom mesh where lights can be blended
			String sharedLightsKey = CSGMeshInfo.createLightListKey( pShape, true );
			aLightListKey = (aLightListKey == null) ? sharedLightsKey : aLightListKey + "-" + sharedLightsKey;
		}
		// What physics apply?
		String aPhysicsKey;
		PhysicsControl aPhysics = pPhysics;
		if ( aPhysics == null ) {
			aPhysics = pShape.getPhysics();
		}
		if ( aPhysics == null ) {
			// Leverage the generic physics key, but not its actual physics
			aPhysicsKey = genericInfo.mPhysicsKey;
		} else if ( pPhysics != null ) {
			// Physics that supercedes the shape, probably a 'face' level definition
			aPhysicsKey = CSGMeshInfo.createPhysicsKey( pPhysics );
		} else {
			// Use the explicit physics for this shape
			aPhysicsKey = CSGMeshInfo.createPhysicsKey( pShape );
		}
		Object meshKey = selectKey( pMeshName, pMaterial, aLightListKey, aPhysicsKey );
		
		// Look for custom material
		if ( meshKey == null ) {
			// By definition, the null material is the generic material
			useInfo = genericInfo;
			
		} else {
			// The mesh key is used to share the same material/lights/physics
			if ( (meshKey != null) && mMeshMap.containsKey( meshKey ) ) {
				// Use the info already found
				useInfo = mMeshMap.get( meshKey );
			} else {
				// Use the next index in sequence
				Integer meshIndex = new Integer( ++mMeshCount );
				
				// At this point, we must have a Material, so use the 
				// generic as needed
				if ( pMaterial == null ) {
					pMaterial = mGenericIndexStack.peek().mMaterial;
				}
				useInfo = new CSGMeshInfo( meshIndex
											, (pMeshName == null) ? pShape.getName() : pMeshName
											, (pMeshName != null)
											, pMaterial
											, lightControls, sharedLights, aLightListKey
											, aPhysics, aPhysicsKey
											, pShape.getRenderNode() );
				if ( meshKey != null ) {
					// Track the material by its AssetKey
					mMeshMap.put( meshKey, useInfo );
				}
				mMeshMap.put( meshIndex, useInfo );
				
				if ( customLights && (pShape.getName() != null) ) {
					// This shape defines lights that may be used by someone else
					mLightingMap.put( pShape.getName(), lightControls );
				}
			}
		}
		// Retain the decorations
		if ( pShape.hasDecorations() ) {
			// Remember the decorations that apply to this mesh
			List<Spatial> decorations = pShape.getDecorations();
			if ( useInfo.mDecorations == null ) { 
				useInfo.mDecorations = new ArrayList( decorations.size() );
			}
			for( Spatial aSpatial : decorations ) {
				useInfo.mDecorations.add( aSpatial );
			}
		}
		return( useInfo );
	}


}
/** Helper class that tracks the appropriate Material/Lighting/Mesh for a given index */
class CSGMeshInfo
{
	/** Service routine to define a LightList key */
	public static String createLightListKey(
		CSGElement	pShape
	,	boolean		pShared
	) {
		return( ((pShared) ? "SL" : "LL") + pShape.getInstanceKey() );
	}
	/** Service routine to define a Physics key */
	public static String createPhysicsKey(
		CSGElement	pShape
	) {
		return( "P" + pShape.getInstanceKey() );
	}
	public static String createPhysicsKey(
		PhysicsControl	pPhysics
	) {
		return( "P" + pPhysics.toString() );
	}
	
	/** The index that selects this info */
	protected Integer			mIndex;
	/** The name associated with this mesh */
	protected String			mName;
	protected boolean			mUniqueMesh;
	/** The Material that applies */
	protected Material			mMaterial;
	/** The custom lighting that applies */
	protected String			mLightListKey;
	protected List<Control>		mLightControls;
	protected String[]			mSharedLights;
	/** The custom physics that applies */
	protected PhysicsControl	mPhysics;
	protected String			mPhysicsKey;
	/** Special decorations to attach to the generated mesh */
	protected List<Spatial>		mDecorations;
	/** Custom node used for final rendering */
	protected CSGNode			mRenderNode;
	/** The generated mesh */
	protected Mesh				mMesh;
	
	/** Standard constructor for a 'generic' definition */
	CSGMeshInfo(
		Integer			pIndex
	,	CSGElement		pElement
	) {
		this.mIndex = pIndex;
		this.mName = pElement.asSpatial().getName();
		this.mUniqueMesh = false;
		
		// Track the material
		this.mMaterial = pElement.getMaterial();
		
		// @todo ???? Track the local light list
		this.mLightControls = null;
		
		// Track the local physics via its KEY, which keeps the physics
		// unique but do NOT retain mPhysics itself, since it will only ever
		// apply to the outer node, never an inner construct
		if ( mPhysics != null ) {
			// Remember the active physics
			this.mPhysicsKey = createPhysicsKey( pElement );
		}
	}
	/** Constructor for a dynamic entry */
	CSGMeshInfo(
		Integer				pIndex
	,	String				pName
	,	boolean				pUniqueMesh
	,	Material			pMaterial
	,	List<Control>		pLightControls
	,	String[]			pSharedLights
	,	String				pLightListKey
	,	PhysicsControl		pPhysics
	,	String				pPhysicsKey
	,	CSGNode				pCustomRenderingNode
	) {
		this.mIndex = pIndex;
		this.mName = pName;
		this.mUniqueMesh = pUniqueMesh;
		
		this.mMaterial = pMaterial;
		
		// Remember the active lights
		this.mLightListKey = pLightListKey;
		this.mLightControls = pLightControls;
		this.mSharedLights = pSharedLights;

		// Remember the active physics
		this.mPhysics = pPhysics;
		this.mPhysicsKey = pPhysicsKey;
		
		this.mRenderNode = pCustomRenderingNode;
	}
	CSGMeshInfo(
		Integer			pIndex
	,	String			pName
	,	Mesh			pMesh
	,	Material		pMaterial
	) {
		this.mIndex = pIndex;
		this.mName = pName;
		this.mUniqueMesh = false;
		this.mMesh = pMesh;
		this.mMaterial = pMaterial;
	}

	/** Resolve the name to use with any entity generated to handle this mesh */
	public String resolveName(
		String		pDefault
	) {
		String aName = null;
		if ( this.mRenderNode != null ) {
			aName = this.mRenderNode.getName();
		}
		if ( aName == null ) {
			aName = this.mName;
		}
		if ( aName == null ) {
			return( pDefault );
		} else {
			return( aName );
		}
	}
	
	/** OVERRIDE: better debug report */
	@Override
	public String toString(
	) {
		return( "CSGMeshInfo[" + mIndex + "] " + ((mName == null) ? "" : mName ) );
	}
}