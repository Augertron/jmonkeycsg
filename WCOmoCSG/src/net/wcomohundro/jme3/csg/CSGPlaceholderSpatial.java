/** Copyright (c) 2003-2014 jMonkeyEngine
	Copyright (c) 2016, WCOmohundro
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
import java.util.Queue;

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
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

/** This class is a 'proxy' Spatial that can resolve itself into the true thing. 
  
 	The only anticipated use of this class is during XML construction, allowing us
 	better management of shared components.
 */
public class CSGPlaceholderSpatial 
	extends Spatial
{
	/** The true Spatial we are referencing */
	protected String	mReference;
	/** If the Spatial is to be cloned, then this is its new name */
	protected String	mCloneAs;
	
	
	/** Simple null constructor */
	public CSGPlaceholderSpatial(
	) {
		super();
	}
	
	/** Resolve the true Spatial */
	public Spatial resolveSpatial(
		CSGElement		pContext
	) throws CSGConstructionException {
		if ( mReference != null ) {
			// Look up the reference within the given context
			Object aSpatial = pContext.getLibraryItem( mReference );
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
				// Apply any special movement
				Transform placeholderTransform = this.getLocalTransform();
				if ( !Transform.IDENTITY.equals( placeholderTransform ) ) {
					// Apply the transform given
					useSpatial.move( placeholderTransform.getTranslation() );
					useSpatial.rotate( placeholderTransform.getRotation() );
					
					Vector3f aScale = placeholderTransform.getScale();
					useSpatial.scale( aScale.x, aScale.y, aScale.z );
				}
				return( useSpatial );
				
			} else {
				throw new CSGConstructionException( CSGErrorCode.CONSTRUCTION_FAILED
													, "No such Spatial: " + mReference );
			}
		} else {
			throw new CSGConstructionException( CSGErrorCode.CONSTRUCTION_FAILED
												, "Null Spatial reference" );
		}
	}
	
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
		// Are we cloning it?
		mCloneAs = aCapsule.readString( "cloneAs", null );
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
