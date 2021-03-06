/** Copyright (c) 2015, WCOmohundro
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

import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.TangentBinormalGenerator;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.ArrayList;

import net.wcomohundro.jme3.csg.CSGMeshManager;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

/** Constructive Solid Geometry (CSG)
 
 	Once upon a time, I thought the proper approach to CSG support in jMonkey was to leverage
 	the existing primitive shapes like Box, Cylinder, ...
 	
 	And CGShape does operate from a basic Mesh, so that the jme primitives can still be used.
 	But as I worked with shapes, blending them together to form more complex elements, I found
 	that you have to pay a lot more attention to the shape's orientation and texture processing
 	to make them very useful. In particular, you want to retain the concept of a shape's 'side'
 	as you go.
 	
 	Rather than altering the underlying primitives, I have decided to create a set of CSG 
 	specific shapes that liberally copy code from the primitives, but which address the issues
 	of orientation, texture, and sides.
 	
 	To this end, all CSG shapes will be built centered on the origin, and oriented along the 
 	z-axis.  Think of it this way:
 	
 	1)	The 'front' of the shape is in the x/y plane with a positive z, facing the viewer
 	2)	The 'back' of the shape is in the x/y plane with a negative z, facing away from the viewer
 	
 	3)	The 'top' of the shape is in the x/z plane with a positive y, facing up
 	4)	The 'bottom' of the shape is in the x/z plane with a negative y, facing down
 	5)	The 'left' of the shape is in the y/z plane with a negative x, facing to the left
 	6)	The 'right' of the shape is in the y/z plane with a positive x, facing to the right
 	
 	7)	If top/bottom/left/right are not appropriate, but the shape still has a surface
 		perpendicular to the front/back, this is the 'sides' face (like a cylinder or pipe)
 		
 	8)	If none of the above is appropriate, then there is the 'surface' face which applies 
 		to the whole (like a sphere)
 	
 	In particular, those shapes that are based on samples along an axis will follow the z axis, 
 	producing vertices in x/y for the given z point.
 	
 	The various subclasses of CSGMesh are designed to be constructed by XML based import/export
 	files, where all the configuration parameters can be pulled from the XML, and then the
 	full mesh regenerated.
 	The regenerated mesh can then have:
 		1)	special texture processing applied
 		2)	LevelOfDetail generation
 		3)	TangentBinormal generation (for lighting support)
 	
 	Standard configuration support:
 		generateFaces -			Bit mask of those faces of the solid to generate
 								(Subclasses will pick an appropriate default)
 								
 		faceProperties -		Array of CSGFaceProperties with possible custom material and/or
 								texture scaling, applied according to a bitmask of faces
 						
 		lodFactors -			Array of float values in the range 0.0 - 1.0, each of which causes
 								an LevelOfDetail to be built.  The smaller the percentage, the fewer
 								triangles are created for a given LOD.
 								
 		inverted - 				true/false with false being a normal facing outward structure and
 								true meaning a solid meant to be viewed from the inside
 						
 		generateTangentBinormal - true/false if the TangentBinormal information should be
 								generated for appropriate lighting

 */
public abstract class CSGMesh 
	extends Mesh
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGMeshRevision="$Rev$";
	public static final String sCSGMeshDate="$Date$";

	/** The bitmask of faces to generate */
	protected int						mGeneratedFacesMask;
	/** The list of custom Materials to apply to the various faces */
	protected List<CSGFaceProperties>	mFaceProperties;
    /** If inverted, then the cylinder is intended to be viewed from the inside */
    protected boolean 					mInverted;
	/** The LOD generation values */
	protected float[]					mLODFactors;
	/** TangentBinormal generation control */
	protected boolean					mGenerateTangentBinormal;
	
	/** FOR POSSIBLE SUBCLASS OVERRIDE: Resolve this mesh, with possible 'debug' delegate representation */
	public Mesh resolveMesh(
		boolean		pDebug
	) {
		return( this );
	}
	
	/** Accessor to the full range of faces supported by this mesh */
	public abstract int getSupportedFacesMask();
	
	/** Accessor to the bitmask of faces being generated */
	public int getGeneratedFacesMask() { return mGeneratedFacesMask; }
	public void setGeneratedFacesMask(
		int		pFaceMask
	) {
		mGeneratedFacesMask = pFaceMask;
	}
	
	/** Accessor to the per-face property configuration */
	public List<CSGFaceProperties> getFaceProperties() { return mFaceProperties; }
	public void setFaceProperties(
		List<CSGFaceProperties>	pPropertyList
	,	boolean					pRebuild
	) {
		mFaceProperties = pPropertyList;
		if ( pRebuild && (mFaceProperties != null) ) {
			this.updateGeometryEpilog();
		}
	}
	public void addFaceProperties(
		CSGFaceProperties	pProperties
	) {
		if ( mFaceProperties == null ) mFaceProperties = new ArrayList( 5 );
		mFaceProperties.add( pProperties );
	}
	
	/** Accessor to the inversion control flag */
    public boolean isInverted() { return mInverted; }
    public void setInverted( boolean pIsInverted ) { mInverted = pIsInverted; }

	/** Accessor to the LOD factors to apply */
	public float[] getLODFactors() { return mLODFactors; }
	public void setLODFactors(
		float[]		pLODFactors
	) {
		mLODFactors = pLODFactors;
	}
	
	/** Accessor to the TangentBinormal generation control flag */
	public boolean isGenerateTangentBinormal() { return mGenerateTangentBinormal; }
	public void setGenerateTangentBinormal(
		boolean		pFlag
	) {
		mGenerateTangentBinormal = pFlag;
	}
	
    /** Apply gradient vertex colors */
    public abstract void applyGradient(
    	List<ColorRGBA>	pColorList
    );
    
	/** FOR CSGShape PROCESSING: Accessor to the mesh name that applies to the given surface */
	public String getMeshName(
		int					pFaceIndex
	) {
		// Subclasses must override if they support custom material per surface
		return( null );
	}
	
	/** FOR CSGShape PROCESSING: Accessor to the material that applies to the given surface */
	public Material getMaterial(
		int					pFaceIndex
	) {
		// Subclasses must override if they support custom material per surface
		return( null );
	}
	
	/** FOR CSGShape PROCESSING: Accessor to the physics that applies to the given surface */
	public PhysicsControl getPhysics(
		int					pFaceIndex
	) {
		// Subclasses must override if they support custom physics per surface
		return( null );
	}

	
	/** FOR CSGShape PROCESSING: Register every custom face material/physics */
	public void registerFaceProperties(
		CSGMeshManager	pMeshManager
	,	CSGShape		pShape
	) {
		if ( mFaceProperties != null ) {
			// Register every property in the list.  The manager will resolve multiple
			// usages of the same item to the same index
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				pMeshManager.resolveMeshIndex( aProperty, pShape );
			}
		}
	}
	
	/** Service routing to match a set of properties to a given face */
	protected CSGFaceProperties matchFaceProperties(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one
					return( aProperty );
				}
			}
		}
		return( null );
	}
	protected String matchFaceName(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one if it has a material
					String aName = aProperty.getName();
					if ( aName != null ) {
						return( aName );
					}
					// NOTE that we could match a property based on the bitmask, but if
					//		it has no name, we will keep looking.  This lets us use
					//		separate definitions for scale versus material versus physics
				}
			}
		}
		return( null );
	}
	protected Material matchFaceMaterial(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one if it has a material
					Material aMaterial = aProperty.getMaterial();
					if ( aMaterial != null ) {
						return( aMaterial );
					}
					// NOTE that we could match a property based on the bitmask, but if
					//		it has no material, we will keep looking.  This lets us use
					//		separate definitions for scale versus material versus physics
				}
			}
		}
		return( null );
	}
	protected Vector2f matchFaceScale(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one if it has a scale
					if ( aProperty.hasTextureScale() ) {
						return( aProperty.getTextureScale() );
					}
					// NOTE that we could match a property based on the bitmask, but if
					//		it has no scale, we will keep looking.  This lets us use
					//		separate definitions for scale versus material versus physics
				}
			}
		}
		return( null );
	}
	protected Vector2f matchFaceOrigin(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one if it has a scale
					if ( aProperty.hasTextureOrigin() ) {
						return( aProperty.getTextureOrigin() );
					}
					// NOTE that we could match a property based on the bitmask, but if
					//		it has no origin, we will keep looking.  This lets us use
					//		separate definitions for origin versus material versus physics
				}
			}
		}
		return( null );
	}
	protected Vector2f matchFaceSpan(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one if it has a scale
					if ( aProperty.hasTextureSpan() ) {
						return( aProperty.getTextureSpan() );
					}
					// NOTE that we could match a property based on the bitmask, but if
					//		it has no origin, we will keep looking.  This lets us use
					//		separate definitions for origin versus material versus physics
				}
			}
		}
		return( null );
	}
	protected Vector2f matchFaceTerminus(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one if it has a scale
					if ( aProperty.hasTextureTerminus() ) {
						return( aProperty.getTextureTerminus() );
					}
					// NOTE that we could match a property based on the bitmask, but if
					//		it has no origin, we will keep looking.  This lets us use
					//		separate definitions for origin versus material versus physics
				}
			}
		}
		return( null );
	}
	protected PhysicsControl matchFacePhysics(
		int			pFaceBit
	) {
		if ( mFaceProperties != null ) {
			// Sequential scan looking for a match
			// The assumption is there are so few faces that a sequential scan is very efficient
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.appliesToFace( pFaceBit ) ) {
					// Use this one if it has physics
					PhysicsControl aPhysics = aProperty.getPhysics();
					if ( aPhysics != null ) {
						return( aPhysics );
					}
					// NOTE that we could match a property based on the bitmask, but if
					//		it has no physics, we will keep looking.  This lets us use
					//		separate definitions for scale versus material versus physics
				}
			}
		}
		return( null );
	}
	
	/** Service routine to match a custom mesh name to a given face */
	protected String resolveFaceName(
		int			pFaceBit
	) {
		String aName = matchFaceName( pFaceBit );
		return( aName );
	}
	/** Service routine to match a custom material to a given face */
	protected Material resolveFaceMaterial(
		int			pFaceBit
	) {
		Material aMaterial = matchFaceMaterial( pFaceBit );
		return( aMaterial );
	}
	/** Service routine to match a custom physics to a given face */
	protected PhysicsControl resolveFacePhysics(
		int			pFaceBit
	) {
		PhysicsControl aPhysics = matchFacePhysics( pFaceBit );
		return( aPhysics );
	}
	
    /** Resolve an appropriate base/span for texture processing.
	 	The given pResultBase and pResultSpan vectors will be set to the appropriate 
	 	origin and length to use.
	 	
	 	The calculation starts with the values in pBase and pSpan.  These apply unless
	 	values are found in the Origin/Terminus parameters.
	 	
	 	Both Origin and Terminus can be null, which means no override is used.  Within
	 	a non-null origin/terminus vector, the value itself can be NaN to indicate that
	 	no override applies to that particular value.
	 	
	 	If origin is defined, then the origin value is set into ResultBase.  If terminus is
	 	defined, then the terminus value minus the Span value is set into ResultBase.
	 	If both origin and terminus are defined, then the origin value is set into
	 	ResultBase, and the (terminus - origin) value is set into ResultSpan.
	 */
	protected void resolveTextureCoord(
		Vector2f	pResultBase
	,	Vector2f	pResultSpan
	,	Vector2f	pBase
	,	Vector2f	pSpan
	,	int			pFaceMask
	,	Vector2f	pTemp
	) {
		// Look for adjustment to the origin
		Vector2f textureOrigin = this.matchFaceOrigin( pFaceMask );
		Vector2f textureSpan = this.matchFaceSpan( pFaceMask );
		Vector2f textureTerminus = this.matchFaceTerminus( pFaceMask );
		
		float originOverrideX = (textureOrigin == null) ? Float.NaN : textureOrigin.x;
		float spanOverrideX = (textureSpan == null) ? Float.NaN : textureSpan.x;
		float terminusOverrideX = (textureTerminus == null) ? Float.NaN : textureTerminus.x;
		resolveTextureCoord( pTemp, pBase.x, pSpan.x, originOverrideX, spanOverrideX, terminusOverrideX );
		pResultBase.setX( pTemp.x );
		pResultSpan.setX( pTemp.y );
		
		float originOverrideY = (textureOrigin == null) ? Float.NaN : textureOrigin.y;
		float spanOverrideY = (textureSpan == null) ? Float.NaN : textureSpan.y;
		float terminusOverrideY = (textureTerminus == null) ? Float.NaN : textureTerminus.y;
		resolveTextureCoord( pTemp, pBase.y, pSpan.y, originOverrideY, spanOverrideY, terminusOverrideY );
		pResultBase.setY( pTemp.x );
		pResultSpan.setY( pTemp.y );
	}
	protected void resolveTextureCoord(
		Vector2f	pResult
	,	float		pBase
	,	float		pDefaultSpan
	,	float		pOverrideOrigin
	,	float		pOverrideSpan
	,	float		pOverrideTerminus
	) {    	
		// pResult.x is the 'base', pResult.y is the 'span'
		float useSpan = (Float.isNaN( pOverrideSpan )) ? pDefaultSpan : pOverrideSpan;
		if ( Float.isNaN( pOverrideOrigin ) ) {
			// No explicit origin override
			if ( Float.isNaN( pOverrideTerminus ) ) {
				// Nothing overridden, use the givens
				pResult.setX( pBase );
				pResult.setY( useSpan );
			} else {
				// Work from explicit terminus only
				pResult.setX( pOverrideTerminus - useSpan );
				pResult.setY( useSpan );
			}
		} else {
			// We have an override origin
			if ( Float.isNaN( pOverrideTerminus ) ) {
				// No explicit terminus
				pResult.setX( pOverrideOrigin );
				pResult.setY( useSpan );
			} else {
				// Work from explicit origin and terminus 
				pResult.setX( pOverrideOrigin );
				pResult.setY( pOverrideTerminus - pOverrideOrigin );
			}
		}
	}

		
	/** Every CSGMesh is expected to be able to rebuild itself from its fundamental
	 	configuration parameters.
	 */
	public void updateGeometry(
	) {
		updateGeometryProlog();
		updateGeometryEpilog();
	}
	/** FOR SUBCLASS OVERRIDE **/
	protected abstract void updateGeometryProlog();
	protected void updateGeometryEpilog(
	) {
		// Apply any scaling now
		if ( mFaceProperties != null ) {
			for( CSGFaceProperties aProperty : mFaceProperties ) {
				if ( aProperty.hasTextureScale() ) {
					this.scaleFaceTextureCoordinates( aProperty.getTextureScale(), aProperty.getFaceMask() );
				}
        	}
        }
        // Generate tangent binormals as needed
        if ( mGenerateTangentBinormal ) {
        	TangentBinormalGenerator.generate( this );
        }
	}
	
	/** If this CSGMesh supports various 'faces', then the texture scaling on each
	 	separate face may be set independently
	 */
	public abstract void scaleFaceTextureCoordinates(
		Vector2f	pScaleTexture
	,	int			pFacemask
	);
	
    /** Support texture scaling AND reconstruction from the configuration parameters */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule outCapsule = pExporter.getCapsule( this );

        // We are not interested in the constructed buffers, so rather than calling
    	// the super processing, we copy the pertinent pieces here.  Anything that
    	// is regenerated via updateGeometry() is ignored.
        outCapsule.write( this.getMode(), "mode", Mode.Triangles );

        // Extended attributes
        outCapsule.write( mGeneratedFacesMask, "generateFaces",  this.getSupportedFacesMask() );
        outCapsule.writeSavableArrayList( (ArrayList)mFaceProperties, "faceProperties", null );
        outCapsule.write( mLODFactors, "lodFactors", null );
        outCapsule.write( mInverted, "inverted", false );
        outCapsule.write( mGenerateTangentBinormal, "generateTangentBinormal", false );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );

        // Similar to .write(), we are not interested reading the various transient
    	// elements that will be built by .updateGeometry().  So rather than
    	// than letting super.read() try to rebuild buffers etc, just update the
    	// geometry based on the configuration parameters that we assume our
    	// various subclasses have already loaded.
        this.setMode( inCapsule.readEnum( "mode", Mode.class, Mode.Triangles ) );
        
        // Extended attributes
        mGeneratedFacesMask = inCapsule.readInt( "generateFaces", 0 );	// Subclasses must check for zero
        setFaceProperties( inCapsule.readSavableArrayList( "faceProperties", null ), false );
        mLODFactors = inCapsule.readFloatArray( "lodFactors", null );
        mInverted = inCapsule.readBoolean( "inverted", mInverted );
        mGenerateTangentBinormal = inCapsule.readBoolean( "generateTangentBinormal", false );
        
        //////// NOTE NOTE NOTE
        // 			That every CSGShape is expected to callback via readComplete()
        //			to generate the geometry and apply any final fixup
    }
    
    /** Service routine to resolve an existing TexCoord buffer */
    protected FloatBuffer resolveTexCoordBuffer(
    	VertexBuffer	pVertexBuffer
    ) {
    	if ( pVertexBuffer == null ) {
    		pVertexBuffer = getBuffer(Type.TexCoord);
    	}
        if ( pVertexBuffer == null ) {
            throw new IllegalStateException("The mesh has no texture coordinates");
        }
        if ( pVertexBuffer.getFormat() != VertexBuffer.Format.Float ) {
            throw new UnsupportedOperationException("Only float texture coord format is supported");
        }
        if ( pVertexBuffer.getNumComponents() != 2 ) {
            throw new UnsupportedOperationException("Only 2D texture coords are supported");
        }
        // Get the data buffer and reset its position info back to zero
        FloatBuffer aBuffer = (FloatBuffer)pVertexBuffer.getData();
        aBuffer.clear();
    	return( aBuffer );
    }
    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( CSGMesh.class
													, sCSGMeshRevision
													, sCSGMeshDate
													, pBuffer ) );
	}

}
