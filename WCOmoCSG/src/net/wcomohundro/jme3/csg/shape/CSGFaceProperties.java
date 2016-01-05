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
package net.wcomohundro.jme3.csg.shape;

import java.io.IOException;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

import com.jme3.asset.AssetNotFoundException;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;

/** FaceProperties manage various configuration settings that can be applied to a given
 	'face' within a CSGMesh.  The 'face' is selected by a bitmask of appropriate faces
 	that these properties apply to.
 	
 	Properties:
 	1)	Texture scaling to apply to the face
 	2)	Custom material to apply to the face
 	3)	Custom physics to apply to the face
 */
public class CSGFaceProperties 
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGFacePropertiesRevision="$Rev$";
	public static final String sCSGFacePropertiesDate="$Date$";

	
	/** Identify the faces of the shape -- selected by bit mask */
	public enum Face {
		NONE(0)
		
,		FRONT(1)
, 		BACK(2)
,		FRONT_BACK(1+2)

, 		LEFT(4)
, 		RIGHT(8)
,		LEFT_RIGHT(4+8)

, 		TOP(16)
, 		BOTTOM(32)
,		TOP_BOTTOM(16+32)

, 		SIDES(64)
, 		SURFACE(128)
;
		private int		mMask;
		private Face( int pValue ) { mMask = pValue; }

		public int getMask() { return mMask; }
		public boolean  hasFace(
			Face	pCheckForFace
		) {
			return( (this.mMask & pCheckForFace.mMask) == pCheckForFace.mMask );
		}
		public boolean maskIncludesFace(
			int		pFaceMask
		) {
			return( (pFaceMask & this.mMask) == this.mMask );
		}
		public static Face matchMask(
			int		pFaceMask
		) {
			for( Face aFace : Face.values() ) {
				if ( aFace.mMask == pFaceMask ) {
					return( aFace );
				}
			}
			return( null );
		}
	}

	/** The bitmask of 'faces' these properties apply to */
	protected int				mFaceMask;
	/** A custom material that applies to the face(s) */
	protected Material			mMaterial;
	/** A custom texture scaling that applies to the face(s) */
	protected Vector2f			mTextureScale;
	protected Vector2f			mTextureOrigin;
	/** A custom Physics that applies to the face(s) */
	protected PhysicsControl	mPhysics;
	
	/** Null constructor */
	public CSGFaceProperties(
	) {
	}
	/** Constructor with all properties */
	public CSGFaceProperties(
		Face			pFace
	,	Material		pMaterial
	,	Vector2f		pTextureScale
	,	Vector2f		pTextureOrigin
	,	PhysicsControl	pPhysics
	) {
		mFaceMask = pFace.getMask();
		mMaterial = pMaterial;
		mTextureScale = pTextureScale;
		mTextureOrigin = pTextureOrigin;
		mPhysics = pPhysics;
	}
	
	/** Accessor to the mask */
	public int getFaceMask() { return mFaceMask; }
	public boolean appliesToFace(
		int		pFaceBit
	) {
		return( (mFaceMask & pFaceBit) == pFaceBit );
	}
	public boolean appliesToFace(
		Face	pFace
	) {
		return( pFace.maskIncludesFace( mFaceMask ) );
	}
	
	/** Accessor to the Material property */
	public boolean hasMaterial() { return( mMaterial != null ); }
	public Material getMaterial() { return mMaterial; }
	
	/** Accessor to the TextureScaling property */
	public boolean hasTextureScale() { return( mTextureScale != null ); }
	public Vector2f getTextureScale() { return mTextureScale; }
	
	/** Accessor to the TextureOrigin property */
	public boolean hasTextureOrigin() { return( mTextureOrigin != null ); }
	public Vector2f getTextureOrigin() { return mTextureOrigin; }
	
	/** Accessor to the Physics property */
	public boolean hasPhysics() { return( mPhysics != null ); }
	public PhysicsControl getPhysics() { return mPhysics; }
	

	/** Adjust the Savable actions */
	@Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
		// Look for the facemask 
        InputCapsule aCapsule = pImporter.getCapsule( this );
        Face aFace = aCapsule.readEnum( "face", Face.class, null );
        if ( aFace == null ) {
        	mFaceMask = aCapsule.readInt( "faceMask", 0 );
        } else {
        	mFaceMask = aFace.getMask();
        }
        // Look for a property
        mMaterial = null;
        String matName = aCapsule.readString( "materialName", null );
        if ( matName != null ) {
            // Material name is set, Attempt to load material via J3M
            try {
                mMaterial = pImporter.getAssetManager().loadMaterial( matName );
            } catch( AssetNotFoundException ex ) {
                // Cannot find J3M file.
                CSGEnvironment.sLogger.log( Level.FINE, "Cannot locate material: " + matName );
            }
        }
        // If material is NULL, try to load it inline
        if ( mMaterial == null) {
            mMaterial = (Material)aCapsule.readSavable( "material", null );
        }
        // Look for scaling
        mTextureScale = (Vector2f)aCapsule.readSavable( "textureScale", null );
        if ( mTextureScale == null ) {
        	// Look for simple attributes
        	float scaleX = CSGEnvironment.readPiValue( aCapsule, "scaleX", 1f );
        	float scaleY = CSGEnvironment.readPiValue( aCapsule, "scaleY", 1f );
        	if ( (scaleX != 1f) || (scaleY != 1f) ) {
        		mTextureScale = new Vector2f( scaleX, scaleY );
        	}
        }
        // Look for origin
        mTextureOrigin = (Vector2f)aCapsule.readSavable( "textureOrigin", null );
        if ( mTextureOrigin == null ) {
        	// Look for simple attributes
        	float originX = CSGEnvironment.readPiValue( aCapsule, "originX", 0f );
        	float originY = CSGEnvironment.readPiValue( aCapsule, "originY", 0f );
        	if ( (originX != 1f) || (originY != 1f) ) {
        		mTextureOrigin = new Vector2f( originX, originY );
        	}
        }
        // Look for physics
        mPhysics =(PhysicsControl)aCapsule.readSavable( "physics", null );
	}
	
	@Override
    public void write(
    	JmeExporter 	pExporter
    ) throws IOException {
		// Capture the facemask
        OutputCapsule aCapsule = pExporter.getCapsule( this );
        
        Face aFace = Face.matchMask( mFaceMask );
        if ( aFace == null ) {
            aCapsule.write( mFaceMask, "faceMask", 0 );        	
        } else {
        	aCapsule.write( aFace, "face", null );
        }
        
        aCapsule.write( mMaterial, "material", null );
        
        if ( mTextureScale != null ) {
        	aCapsule.write( mTextureScale.getX(), "scaleX", 1f );
        	aCapsule.write( mTextureScale.getY(), "scaleY", 1f );
        }
        if ( mTextureOrigin != null ) {
        	aCapsule.write( mTextureOrigin.getX(), "originX", 0f );
        	aCapsule.write( mTextureOrigin.getY(), "originY", 0f );
        }
        // For now, physics is too complicated to be captured by a write()
	}
	
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( CSGFaceProperties.class
													, sCSGFacePropertiesRevision
													, sCSGFacePropertiesDate
													, pBuffer ) );
	}

}
