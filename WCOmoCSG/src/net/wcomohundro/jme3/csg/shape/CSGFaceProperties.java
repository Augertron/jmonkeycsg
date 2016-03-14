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
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;

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
 	
 	For texture management, semi-independent options exist for basic scale, origin override,
 	span override, and terminus override. Origin, Span and Terminus are applied during face 
 	construction.  A typical face has its texture origin starting at 0,0 and then spanning to 1,1.  
 	Multiple faces may be treated as unified whole in some circumstances (@see CSGAxialBox), but 
 	all face textures start at some origin, and run for some span.
	
	 	Scaling is just that, a multiplier applied to the various texture coordinates of a given
	 	face.  
	 	
	 	An explicit Origin overrides the normal 0,0 starting point.  This allows the texture for
	 	a face to start at some given bias, which could be useful for faces from multiple shapes 
	 	that are positioned to align on a common texture.
	 	
	 	An explicit Span overrides the normal 1,1 span length.
	 	
	 	An explicit Terminus overrides the normal ending point (Origin + Span).  This is useful 
	 	for those faces whose ending point must align with some other shape's starting point. In 
	 	this case, the origin is calculated backwards accounting for a given span.  In particular, 
	 	scaling accommodates an explicit Terminus so that the end coordinate remains fixed even if a 
	 	scaling multiplier is used.
	 	
	 	If both the Origin and Terminus are supplied, the Terminus remains fixed in terms of scaling,
	 	and the span is dynamically calculated to account for the given origin.
	 	
	 A list of CSGFaceProperties is supported at the CSGElement level.  Such a list may have
	 multiple definitions applying to the same face, so that one specification selects the
	 material while another selects the physics/texture processing.
	 Since a CSGElement can link back to its parent, it is possible to walk back through a
	 higherarchy to resolve all the texture processing that applies.
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
		private static Face[] sSingleMasks = new Face[] {
			Face.FRONT, Face.BACK, Face.LEFT, Face.RIGHT, Face.TOP, Face.BOTTOM, Face.SIDES, Face.SURFACE
		};
		
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
		public static Face matchMaskIndex(
			int		pFaceIndex
		) {
			return( sSingleMasks[ pFaceIndex ] );
		}
	}
	
	/** An iterable across a bitmask of faces */
	public static FaceMask getIterableFaceMask(
		int		pFaceMask
	) {
		return new FaceMask( pFaceMask );
	}
	
	/** Service routine to resolve the scale that applies to a given face */
	public static Vector2f resolveTextureScale(
		CSGElement		pElement
	,	int				pFaceMask
	) {
		Vector2f texScale = null;
		while( pElement != null ) {
			List<CSGFaceProperties> propList = pElement.getFaceProperties();
			if ( propList != null ) {
				// Check every property in turn
				for( CSGFaceProperties aProperty : propList ) {
					if ( aProperty.appliesToFace( pFaceMask ) 
					&& aProperty.hasTextureScale() ) {
						texScale = aProperty.getTextureScale( texScale );
					}
	        	}
			}
			pElement = pElement.getParentElement();
		}
		return( texScale );
	}

	/** The bitmask of 'faces' these properties apply to */
	protected int				mFaceMask;
	/** Special name associated with these properties */
	protected String			mName;
	/** A custom material that applies to the face(s) */
	protected Material			mMaterial;
	/** A custom texture scaling that applies to the face(s) */
	protected Vector2f			mTextureScale;
	protected Vector2f			mTextureOrigin;
	protected Vector2f			mTextureSpan;
	protected Vector2f			mTextureTerminus;
	/** A custom Physics that applies to the face(s) */
	protected PhysicsControl	mPhysics;
	
	/** Null constructor */
	public CSGFaceProperties(
	) {
	}
	/** Simple texture scaling constructor */
	public CSGFaceProperties(
		Face			pFace
	,	Material		pMaterial
	,	Vector2f		pTextureScale
	) {
		this( pFace, pMaterial, pTextureScale, null, false, null, null );
	}
	/** Constructor with all properties */
	public CSGFaceProperties(
		Face			pFace
	,	Material		pMaterial
	,	Vector2f		pTextureScale
	,	Vector2f		pTextureOrigin
	,	boolean			pAsTextureTerminus
	,	Vector2f		pTextureSpan
	,	PhysicsControl	pPhysics
	) {
		mFaceMask = pFace.getMask();
		mMaterial = pMaterial;
		mTextureScale = pTextureScale;
		if ( pAsTextureTerminus ) {
			// Define the END point of the texture, not the start
			mTextureTerminus = pTextureOrigin;
		} else {
			// Explicit start point of the texture
			mTextureOrigin = pTextureOrigin;
		}
		mTextureSpan = pTextureSpan;
		mPhysics = pPhysics;
	}
	
	/** Accessor to the mask */
	public int getFaceMask() { return mFaceMask; }
	public void setFaceMask( int pFaceMask ) { mFaceMask = pFaceMask; }
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

	/** Accessor to special name */
	public boolean hasName() { return( mName != null ); }
	public String getName() { return mName; }
	
	/** Accessor to the Material property */
	public boolean hasMaterial() { return( mMaterial != null ); }
	public Material getMaterial() { return mMaterial; }
	
	/** Accessor to the TextureScaling property */
	public boolean hasTextureScale() { return( mTextureScale != null ); }
	public Vector2f getTextureScale() { return mTextureScale; }
	public Vector2f getTextureScale(
		Vector2f 	pBlendWith
	) {
		if ( pBlendWith == null ) {
			return( mTextureScale );
		} else if ( mTextureScale == null ) {
			return( pBlendWith );
		} else {
			return( pBlendWith.clone().multLocal( mTextureScale ) );
		}
	}
	
	/** Accessor to the TextureOrigin property */
	public boolean hasTextureOrigin() { return( mTextureOrigin != null ); }
	public Vector2f getTextureOrigin() { return mTextureOrigin; }

	/** Accessor to the TextureSpan property */
	public boolean hasTextureSpan() { return( mTextureSpan != null ); }
	public Vector2f getTextureSpan() { return mTextureSpan; }

	/** Accessor to the TextureTerminus property */
	public boolean hasTextureTerminus() { return( mTextureTerminus != null ); }
	public Vector2f getTextureTerminus() { return mTextureTerminus; }


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
        mName = aCapsule.readString( "name", null );
        
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
        // Look for origin/span/terminus
        // NOTE that we allow for both specifications on the assumption that one
        //		applies to X and the other to Y
        mTextureOrigin = (Vector2f)aCapsule.readSavable( "textureOrigin", null );
        if ( mTextureOrigin == null ) {
        	// Look for simple attributes
        	float originX = CSGEnvironment.readPiValue( aCapsule, "originX", Float.NaN );
        	float originY = CSGEnvironment.readPiValue( aCapsule, "originY", Float.NaN );
        	if ( !Float.isNaN( originX ) || !Float.isNaN( originY ) ) {
        		mTextureOrigin = new Vector2f( originX, originY );
        	}
        }
        mTextureSpan = (Vector2f)aCapsule.readSavable( "textureSpan", null );
        if ( mTextureSpan == null ) {
        	// Look for simple attributes
        	float spanX = CSGEnvironment.readPiValue( aCapsule, "spanX", Float.NaN );
        	float spanY = CSGEnvironment.readPiValue( aCapsule, "spanY", Float.NaN );
        	if ( !Float.isNaN( spanX ) || !Float.isNaN( spanY ) ) {
        		mTextureSpan = new Vector2f( spanX, spanY );
        	}
        }
        mTextureTerminus = (Vector2f)aCapsule.readSavable( "textureTerminus", null );
        if ( mTextureTerminus == null ) {
        	// Look for simple attributes
        	float terminusX = CSGEnvironment.readPiValue( aCapsule, "terminusX", Float.NaN );
        	float terminusY = CSGEnvironment.readPiValue( aCapsule, "terminusY", Float.NaN );
        	if ( !Float.isNaN( terminusX ) || !Float.isNaN( terminusY ) ) {
        		mTextureTerminus = new Vector2f( terminusX, terminusY );
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
        aCapsule.write( mName, "name", null );

        aCapsule.write( mMaterial, "material", null );
        
        if ( mTextureScale != null ) {
        	aCapsule.write( mTextureScale.getX(), "scaleX", 1f );
        	aCapsule.write( mTextureScale.getY(), "scaleY", 1f );
        }
        if ( mTextureOrigin != null ) {
        	aCapsule.write( mTextureOrigin.getX(), "originX", Float.NaN );
        	aCapsule.write( mTextureOrigin.getY(), "originY", Float.NaN );
        }
        if ( mTextureSpan != null ) {
        	aCapsule.write( mTextureSpan.getX(), "spanX", Float.NaN );
        	aCapsule.write( mTextureSpan.getY(), "spanY", Float.NaN );
        }
        if ( mTextureTerminus != null ) {
        	aCapsule.write( mTextureTerminus.getX(), "terminusX", Float.NaN );
        	aCapsule.write( mTextureTerminus.getY(), "terminusY", Float.NaN );
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
	
	/** OVERRIDE: better debug report */
	@Override
	public String toString(
	) {
		return( "CSGFaceProperties[" + mFaceMask + "] " + ((mName == null) ? "" : mName) );
	}

}

/** Helper class that manages a MASK of faces */
class FaceMask
	implements Iterable
{
	/** The bitmask of faces */
	protected int		mFaceMask;
	
	/** Constructor based on a mask */
	public FaceMask(
		int		pFaceMask
	) {
		mFaceMask = pFaceMask;
	}
	/** Make it Iterable */
	@Override
	public Iterator<CSGFaceProperties.Face> iterator(
	) {
		return new FaceMaskIterator();
	}
	
	/** The iterator, slaved to its outer mFaceMask */
	class FaceMaskIterator 
		implements Iterator<CSGFaceProperties.Face>
	{
		/** Where are we in the iteration */
		protected int	mMask;
		protected int	mIndex;
		
		/** Standard null constructor */
		public FaceMaskIterator(
		) {
			mMask = 0x01;
			mIndex = 0;
		}
		
		/** Implement the iterator */
		@Override
		public boolean hasNext(
		) {
			return( mMask <= mFaceMask );
		}

		@Override
		public CSGFaceProperties.Face next(
		) {
			CSGFaceProperties.Face aFace = CSGFaceProperties.Face.matchMaskIndex( mIndex );
			mMask <<= 1;
			mIndex += 1;
			return aFace;
		}

		@Override
		public void remove(
		) {
			// Not currently supported
		}
		
	}
}