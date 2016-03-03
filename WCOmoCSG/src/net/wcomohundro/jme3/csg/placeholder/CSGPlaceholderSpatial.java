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
package net.wcomohundro.jme3.csg.placeholder;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;

import net.wcomohundro.jme3.csg.CSGLibrary;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI.CSGErrorCode;
import net.wcomohundro.jme3.math.CSGTransform;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingVolume;
import com.jme3.collision.Collidable;
import com.jme3.collision.CollisionResults;
import com.jme3.collision.UnsupportedCollisionException;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

/** This class is a 'proxy' Spatial that can resolve itself into the true thing. 
  
 	The only anticipated use of this class is during XML construction, allowing us
 	better management of shared components.
 	
 	The spatial can be located via:
 	1)	model='some asset key'		which loads a spatial via the AssetManager
 	2)	reference='some name'		which looks the name up in the library tree
 	
 	The located spatial can then be cloned with a new name via
 		cloneAs='new name'
 		
 	Any transform defined on the placeholder is applied to the resolved spatial.
 	This allows you to use multiple placeholders on the same target, but adjust
 	the position on each.
 	
 	In addition to placement of the spatial, you can also apply 'fixup' to the
 	resolved spatial.  This allows you to adjust materials and lights on the element.
 */
public class CSGPlaceholderSpatial 
	extends Spatial
	implements CSGPlaceholder<Spatial>
{
	/** The true Spatial we are referencing */
	protected String				mReference;				// Expected up in the library chain
	protected Spatial				mModel;					// Externally defined and loaded
	/** If the Spatial is to be cloned, then this is its new name */
	protected String				mCloneAs;
	/** Map of fixup elements to apply */
	protected Map<String,Savable>	mFixup;	
	
	
	/** Simple null constructor */
	public CSGPlaceholderSpatial(
	) {
		super();
	}
	
	/** Resolve the true Spatial */
	@Override
	public Spatial resolveItem(
		CSGElement		pContext
	,	boolean			pSearchFromTop
	) throws CSGConstructionException {
		Object aSpatial = mModel;
		if ( mReference != null ) {
			// Look up the reference within the given context
			aSpatial = CSGLibrary.getLibraryItem( pContext, mReference, pSearchFromTop );
			if ( aSpatial == null ) {
				// Nothing can be resolved at this time
				return( null );
			}
		}
		if ( aSpatial instanceof Spatial ) {
			// Found the spatial, what are we doing to it?
			Spatial useSpatial;
			if ( mCloneAs != null ) {
				// Make a copy
				useSpatial = ((Spatial)aSpatial).clone( true );
				useSpatial.setName( mCloneAs );
			} else {
				// Directly use the spatial given
				useSpatial = (Spatial)aSpatial;
			}
			// Apply any special transform
			Transform placeholderTransform = this.getLocalTransform();
			if ( !Transform.IDENTITY.equals( placeholderTransform ) ) {
				// Apply the transform given
				useSpatial.move( placeholderTransform.getTranslation() );
				useSpatial.rotate( placeholderTransform.getRotation() );
				
				Vector3f aScale = placeholderTransform.getScale();
				useSpatial.scale( aScale.x, aScale.y, aScale.z );
			}
			// Fixup?
			if ( mFixup != null ) {
				applyFixup( useSpatial, mFixup );
			}
			return( useSpatial );
		} else {
			throw new CSGConstructionException( CSGErrorCode.CONSTRUCTION_FAILED
												, "Null Spatial reference" );
		}
	}
	
	/** Service routine to locate the appropriate components and apply fixup */
	protected void applyFixup(
		Spatial					pSpatial
	,	Map<String,Savable>		pFixup
	) {
		// Walk the items
		for( Map.Entry<String,Savable> anEntry : pFixup.entrySet() ) {
			applyItemFixup( pSpatial, anEntry.getKey(), anEntry.getValue() );
		}
	}
	protected void applyItemFixup(
		Spatial			pSpatial
	,	String			pItemName
	,	Savable			pNewValue
	) {
		// What are we trying to change
		if ( pNewValue instanceof Material ) {
			// Alter the material
			Material newMaterial = (Material)pNewValue;
			String materialName = newMaterial.getName();
			
			Geometry aGeometry;
			if ( materialName == null ) {
				aGeometry = matchMaterial( pSpatial, pItemName, true );
			} else {
				aGeometry = matchMaterial( pSpatial, materialName, false );
			}
			if ( aGeometry != null ) {
				// Adjust the Geometry
				aGeometry.setMaterial( newMaterial );
			}
		} else if ( pNewValue instanceof Light ) {
			// Adjust the item in the local light list
			Light newLight = (Light)pNewValue;
			String lightName = newLight.getName();
			
			Light oldLight;
			if ( lightName == null ) {
				// Match by item name, or use a singleton
				oldLight = matchLight( pSpatial, pItemName, true );
			} else {
				// Match only by name
				oldLight = matchLight( pSpatial, lightName, false );
			}
			if ( oldLight != null ) {
				// For now, we only adjust the color
				oldLight.getColor().set( newLight.getColor() );
			}
		} else if ( pNewValue instanceof ColorRGBA ) {
			// Try to match a light or a material
			Material oldMaterial = null;
			Light oldLight = matchLight( pSpatial, pItemName, false );
			if ( oldLight == null ) {
				// If we match an explictly named material, then adjust it
				Geometry aGeometry = matchMaterial( pSpatial, pItemName, false );
				if ( aGeometry != null ) {
					// Adjust the material
					oldMaterial = aGeometry.getMaterial();
				} else {
					// Check if we match any spatial name
					Spatial aTarget = null;
					if ( pItemName.equals( pSpatial.getName() ) ) {
						aTarget = pSpatial;
					} else if ( pSpatial instanceof Node ) {
						aTarget = ((Node)pSpatial).getChild( pItemName );
					}
					if ( aTarget != null ) {
						// If there is a singleton light, then use it
						LightList aLightList = aTarget.getLocalLightList();
						if ( aLightList.size() == 1 ) {
							// Affect the singleton light
							oldLight = aLightList.get( 0 );
						} else if ( aTarget instanceof Geometry ) {
							// Adjust the material
							oldMaterial = ((Geometry)aTarget).getMaterial();
						}
					}
				}
			}
			if ( oldLight != null ) {
				oldLight.getColor().set( (ColorRGBA)pNewValue );
			} else if ( oldMaterial != null ) {
				oldMaterial.setColor( "Color", (ColorRGBA)pNewValue );
			}
		}
	}
	/** Service routine to scan through a Spatial and match a Material - by name */
	protected Geometry matchMaterial(
		Spatial		pSpatial
	,	String		pName
	,	boolean		pMatchSpatialName
	) {
		if ( pSpatial instanceof Node ) {
			// We never match at the Node level, so try the children
			for( Spatial aChild : ((Node)pSpatial).getChildren() ) {
				Geometry aGeometry = matchMaterial( aChild, pName, pMatchSpatialName );
				if ( aGeometry != null ) {
					// This is the match
					return( aGeometry );
				}
			}
		} else {
			// What is the geometry currently using?
			Geometry aGeometry = (Geometry)pSpatial;
			String oldName = aGeometry.getMaterial().getName();
			
			if ( pName.equals( oldName ) ) {
				// Direct match on the Material name
				return( aGeometry );
			} else if ( pMatchSpatialName && pName.equals( aGeometry.getName() ) ) {
				// Matched on the spatial name instead
				return( aGeometry );
			}
		}
		// Nothing matched
		return( null );
	}
	
	/** Service routine to match a light by name */
	protected Light matchLight(
		Spatial		pSpatial
	,	String		pLightName
	,	boolean		pMatchSpatialName
	) {
		LightList aLightList = pSpatial.getLocalLightList();
		for( Light aLight : aLightList ) {
			if ( pLightName.equals( aLight.getName() ) ) {
				// Matched this light
				return( aLight );
			}
		}
		if ( pSpatial instanceof Node ) {
			// Look through the children
			for( Spatial aChild : ((Node)pSpatial).getChildren() ) {
				Light aLight = matchLight( aChild, pLightName, pMatchSpatialName );
				if ( aLight != null ) {
					return( aLight );
				}
			}
		}
		if ( pMatchSpatialName && pLightName.equals( pSpatial.getName() ) && (aLightList.size() == 1) ) {
			// Use the first and only
			return( aLightList.get( 0 ) );
		} else {
			// No such light
			return( null );
		}
	}
	
	///////////////////////////////// SAVABLE ///////////////////////////////////////////////
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
    	AssetManager aManager = pImporter.getAssetManager();
		InputCapsule aCapsule = pImporter.getCapsule( this );
		
		// Let the super do its thing, looking for possible modifiers on the reference
		super.read( pImporter );
		
		// Look for specially defined tranform 
		if ( this.localTransform == Transform.IDENTITY ) {
			// No explicit transform, look for a proxy
			CSGTransform proxyTransform = (CSGTransform)aCapsule.readSavable( "csgtransform", null );
			if ( proxyTransform != null ) {
				localTransform = proxyTransform.getTransform();
			}
		}
		// What are we referencing 
		mReference = aCapsule.readString( "reference", null );
		if ( mReference == null ) {
			// Not a library reference, how about an external model
			String modelName = aCapsule.readString( "model", null );
			if ( modelName != null ) {
	    		// Load the given model
	        	mModel = pImporter.getAssetManager().loadModel( modelName );
			}
		}
		// Are we cloning it?
		mCloneAs = aCapsule.readString( "cloneAs", null );
		
		// Fixups to apply
		mFixup = (Map<String,Savable>)aCapsule.readStringSavableMap( "fixup", null );
		mFixup = CSGLibrary.fillLibrary( null, mFixup, aManager, false );
	}
	
    /** IMPLEMENT abstract Spatial Methods */
	@Override
    public void updateModelBound() {}

	@Override
    public void setModelBound(BoundingVolume modelBound) {}

	@Override
    public int getVertexCount() { return 0; }

	@Override
    public int getTriangleCount() { return 0; }

	@Override
	public int collideWith(Collidable other, CollisionResults results) { return 0;}

	@Override
	public Spatial deepClone() { return null; }

	@Override
	public void depthFirstTraversal(SceneGraphVisitor visitor) {}

	@Override
	protected void breadthFirstTraversal(SceneGraphVisitor visitor, Queue<Spatial> queue) { }

}
