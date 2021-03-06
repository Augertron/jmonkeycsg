/** Copyright (c) 2011 Evan Wallace (http://madebyevan.com/)
 	Copyright (c) 2003-2014, jMonkeyEngine
	Copyright (c) 2015-2016, WCOmohundro
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
	
	Logic and Inspiration for BSP taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.bsp.CSGPartition;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;
import net.wcomohundro.jme3.csg.exception.CSGConstructionException;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI;
import net.wcomohundro.jme3.csg.exception.CSGExceptionI.CSGErrorCode;
import net.wcomohundro.jme3.csg.placeholder.CSGPlaceholderSpatial;
import net.wcomohundro.jme3.csg.shape.CSGBox;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;
import net.wcomohundro.jme3.csg.shape.CSGMesh;
import net.wcomohundro.jme3.csg.shape.CSGSphere;
import net.wcomohundro.jme3.math.CSGTransform;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.control.Control;
import com.jme3.scene.control.LightControl;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.scene.Mesh.Mode;
import com.jme3.material.Material;
import com.jme3.material.MatParamTexture;
import com.jme3.terrain.Terrain;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.util.TempVars;

/** Constructive Solid Geometry (CSG)
 
	Provide a basic 'shape' for CSG processing 
 
 	A CSG Shape is based on a generated set of polygons that describe the faces of the shape
 	(think Mesh for the CSG world).  Any shape can be CSG blended with another shape via
 	the boolean operators: union, difference, and intersection.  The result is a new 
 	CSG Shape with its own set of faces, from which a standard Mesh can be generated for
 	subsequent jme3 display.
 	
 	The basic Shape can be generated from the Mesh behind any JME Shape.
 	
 	The CSGShape acts like a Geometry so that transformations can be applied to the shape
 	before any CSG blending occurs.  The underlying Material applied to a shape is retained 
 	during the boolean operation, so that the final shape may have faces with differing materials.
 	
 	CSGShape supports the idea of a Mesh with multiple Materials.  Normal
 	JME3 meshes have no Material information.  But the primitive CSG shapes support assigning
 	different materials to different faces.  Therefore, if CSGShape can figure out how
 	multiple materials are assigned, then it keeps track of them.
 	
 	Similar to multiple materials, a CSGShape can also keep track of custom Physics to
 	apply to various faces.
 	
 	A CSGShape can act as a 'grouping' point for blending together a set of subshapes. This
 	allows you to build up a 'piece' with the group, and then apply a single transform 
 	to size the entire piece.  This is typically much easier than trying to individually 
 	size each component within the 'piece' and then blend it into the master.
 	
 	Lighting applied to a CSGShape is reflected in the Node constructed to represent the
 	faces this shape provides to the final result.  This is useful for lighting only 
 	the interior of the result.
 	
 	A CSGShape can also have a set of 'decorations'.  These decorations are Spatials
 	that are attached to this shape's Node in the final result.  By definition, local
 	lighting defined on a decoration is transferred to the result Node.  This allows
 	a complex entity like a Lamp to be attached to an interior shape, whose lighting
 	only applies to the interior.
 	
 	A CSGShape can be constructed from any Spatial.  If the Spatial is a Geometry, then
 	the Geometry's Mesh is used.  If the Spatial is a Node, then the CSGShape becomes a
 	parent group, and all the Node's children are added as subshapes.  In either case,
 	the Spatial's Material is retained, along with its transform.
 	
 	So a CSGShape is based on 1) a Mesh,  2) a grouping of subshapes,  3) a boolean blend
 	In the case of a 'clone' or a 'prepare' as part of regeneration, cases 1 & 2 reset the
 	generated set polygons back to empty and rebuild from scratch.  For case 3, regeneration
 	is using the result of a previous blend, so the polygons must be retained.
 	
 	In most cases, the mesh behind the shape is used in boolean operations.  However, you
 	can select which 'surface' to use in the blend.  In addition to the basic Mesh, you 
 	can choose to use a bounding Box or Sphere to represent the shape.
 	
 	The boolean processing is based on either:
 	
 	1)	BinarySpacePartitioning - where the polygons of a shape are sorted into front/back
 			elements according to their position to a given plane.  Polygons that cross the
 			plane are 'split', and the front/back elements are recursively partitioned.
 			
 	2)	In/Out/Edge processing - where a solid's faces and vertices are categorized in 
 			relationship to another shapes face, with the operator determining which 
 			faces/vertices are retained in the resultant shape.
 	
 */
public class CSGShape 
	extends Geometry
	implements Savable, ConstructiveSolidGeometry.CSGElement, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGShapeRevision="$Rev$";
	public static final String sCSGShapeDate="$Date$";
	
	/** The 'surface' to use for the shape */
	public enum CSGShapeSurface {
		USE_MESH				// Use the actual shape
	,	USE_BOUNDING_BOX		// Use a box that matches the bounds
	,	USE_BOUNDING_SPHERE		// Use a sphere that encompasses the bounds
	}
	
	/** Statistics interface */
	public interface CSGShapeStatistics {	
		/** Simple progress counter */
		public int getProgress(
		);
	}

	/** Handler interface */
	public interface CSGShapeProcessor<CSGEnvironmentT> {
		
		/** Connect to a shape */
		public CSGShapeProcessor setShape(
			CSGShape		pForShape
		);
		/** Refresh this handler and ensure it is ready for blending */
		public CSGShapeProcessor refresh(
		);
		/** Apply a transform to a resultant shape */
		public void applyTransform(
			Transform		pTransform
		,	CSGTempVars		pTempVars
		,	CSGEnvironment	pEnvironment
		);

		/** Make a copy */
		public CSGShapeProcessor clone(
			CSGShape		pForShape
		);
		
		/** Ready a list of shapes for processing */
		public List<CSGShape> prepareShapeList(
			List<CSGShape>		pShapeList
		,	CSGEnvironmentT		pEnvironment
		);	
		
		/** Add a shape into this one */
		public CSGShape union(
			CSGShape			pOtherShape
		,	CSGMeshManager		pMeshManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironmentT		pEnvironment
		) throws CSGConstructionException;
		
		/** Subtract a shape from this one */
		public CSGShape difference(
			CSGShape			pOtherShape
		,	CSGMeshManager		pMeshManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironmentT		pEnvironment
		) throws CSGConstructionException;

		/** Find the intersection with another shape */
		public CSGShape intersection(
			CSGShape			pOtherShape
		,	CSGMeshManager		pMeshManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironmentT		pEnvironment
		) throws CSGConstructionException;
		
		/** Find the merge with another shape */
		public CSGShape merge(
			CSGShape			pOtherShape
		,	CSGMeshManager		pMeshManager
		,	CSGTempVars			pTempVars
		,	CSGEnvironmentT		pEnvironment
		) throws CSGConstructionException;
		
		/** Produce the mesh(es) that corresponds to this shape
		 	The zeroth mesh in the list is the total, composite mesh.
		 	Every other mesh (if present) applies solely to a specific Material.
		  */
		public void toMesh(
			CSGMeshManager		pMeshManager
		,	boolean				pProduceSubelements
		,	CSGTempVars			pTempVars
		,	CSGEnvironmentT		pEnvironment
		);
		
		/** Get status about just what regenerate is doing */
		public StringBuilder reportStatus( 
			StringBuilder 	pBuffer 
		,	boolean			pBriefly
		);
		
		/** Statistics about what regenerate is doing */
		public CSGShapeStatistics getStaticstics();
	}	
	
	/** Service routine to assigning unique identifiers */
	protected static int sInstanceCounter;
	public static synchronized String assignInstanceKey(
		String		pSeed
	) {
		return( pSeed +  ++sInstanceCounter );
	}
	
	/** Service to create a vector buffer for a given List */
    public static FloatBuffer createVector3Buffer(
    	List<Vector3f> 	pVectors
    ) {
        FloatBuffer aBuffer = BufferUtils.createVector3Buffer( pVectors.size() );
        for( Vector3f aVector : pVectors ) {
            if ( aVector != null ) {
            	aBuffer.put( aVector.x ).put( aVector.y ).put( aVector.z );
            } else {
            	aBuffer.put(0).put(0).put(0);
            }
        }
        aBuffer.flip();
        return aBuffer;
    }
    public static FloatBuffer createVector2Buffer(
    	List<Vector2f> 	pVectors
    ) {
        FloatBuffer aBuffer = BufferUtils.createVector2Buffer( pVectors.size() );
        for( Vector2f aVector : pVectors ) {
            if ( aVector != null ) {
            	aBuffer.put( aVector.x ).put( aVector.y );
            } else {
            	aBuffer.put(0).put(0);
            }
        }
        aBuffer.flip();
        return aBuffer;
    }
    public static Buffer createIndexBuffer(
    	List<Number> 	pIndices
    ,	Mesh			pMesh
    ) {
    	Buffer aBuffer;
    	if ( pIndices.size() >= (Short.MAX_VALUE * 2) ) {
    		IntBuffer intBuffer;
	    	aBuffer = intBuffer = BufferUtils.createIntBuffer( pIndices.size() );
	        for( Number aValue : pIndices ) {
	            if ( aValue != null ) {
	            	intBuffer.put( aValue.intValue() );
	            } else {
	            	intBuffer.put( 0 );
	            }
	        }
	        if ( pMesh != null ) {
	        	pMesh.setBuffer( Type.Index, 3, intBuffer );
	        }
    	} else {
    		ShortBuffer shortBuffer;
	    	aBuffer = shortBuffer = BufferUtils.createShortBuffer( pIndices.size() );
	        for( Number aValue : pIndices ) {
	            if ( aValue != null ) {
	            	shortBuffer.put( aValue.shortValue() );
	            } else {
	            	shortBuffer.put( (short)0 );
	            }
	        }
	        if ( pMesh != null ) {
	        	pMesh.setBuffer( Type.Index, 3, shortBuffer );
	        }
    	}
        aBuffer.flip();
        return aBuffer;
    }
    
    /** Unique instance counter */
    protected static int sInstanceMarker;
    
    
    /** Unique instance marker, suitable as a key */
    protected String					mShapeKey;
    /** Valid shape control */
    protected CSGExceptionI				mInError;
	/** The active handler that performs shape manipulation */
	protected CSGShapeProcessor			mHandler;
	/** The operator applied to this shape as it is added into the geometry */
	protected CSGGeometry.CSGOperator	mOperator;
	/** Arbitrary 'ordering' of operations within the geometry */
	protected int						mOrder;
	/** Which surface to use for the shape */
	protected CSGShapeSurface			mSurface;
	/** If this shape represents a group of blended shapes, these are the subshapes */
	protected List<CSGShape>			mSubShapes;
	/** If this shape is a subshape within another element, this is the parent element */
	protected CSGElement				mParentElement;
	/** Physics that applies to this shape */
	protected PhysicsControl			mPhysics;
	/** Controls of any local lights that apply to this shape */
	protected List<Control>				mLightControls;
	/** Special 'shared' lights that are really defined/controlled by another shape */
	protected String[]					mSharedLights;
	/** The list of custom Properties to apply to the various faces of the interior components */
	protected List<CSGFaceProperties>	mFaceProperties;
	/** List of optional 'decorations' attached to this shape, which transfer to the resultant Mesh */
	protected List<Spatial>				mDecorations;
	/** Custom rendering node used within the final product */
	protected CSGNode					mRenderNode;
	/** Nanoseconds needed to regenerate this shape */
	protected long						mRegenNS;
	/** Flag if underlying mesh has already been prepared */
	protected AtomicBoolean				mMeshIsPrepared;
	/** A list of arbitrary elements that can be named and referenced during
	 	XML load processing via id='somename' and ref='somename',
	 	and subsequently referenced programmatically via its inherent name. */
	protected Map<String,Savable>		mLibraryItems;
	/** The result of regeneration of this shape */
	protected CSGShape					mPriorResult;
	/** Proxy element that supplies the element behind this shape */
	protected CSGPlaceholderSpatial		mProxy;
	/** Debug support */
	protected List<Savable>				mDebug;

	
	/** Generic constructor */
	public CSGShape(
	) {
		this( (String)"CSGShape", 0 );
	}
	/** Constructor based on a mesh */
	public CSGShape(
		String	pShapeName
	,	Mesh	pMesh
	) {
		this( pShapeName, pMesh, null, 0 );
	}
	public CSGShape(
		String			pShapeName
	,	Mesh			pMesh
	,	CSGOperator		pOperator
	,	int				pOrder
	) {
		super( pShapeName, pMesh );
		mOrder = pOrder;
		mOperator = (pOperator == null) ? CSGGeometry.CSGOperator.UNION : pOperator;
		mSurface = CSGShapeSurface.USE_MESH;
		
		mMeshIsPrepared = new AtomicBoolean();
		
		// Empty library until explicitly set
		mLibraryItems = Collections.EMPTY_MAP;
	}
	public CSGShape(
		String	pShapeName
	,	int		pOrder
	) {
		super( pShapeName );	// no mesh provided
		mOrder = pOrder;
		mOperator = CSGGeometry.CSGOperator.UNION;		
		mSurface = CSGShapeSurface.USE_MESH;
		
		mMeshIsPrepared = new AtomicBoolean();

		// Empty library until explicitly set
		mLibraryItems = Collections.EMPTY_MAP;
	}
	
	/** Constructor based on a set of subshapes */
	public CSGShape(
		String				pShapeName
    ,   List<CSGShape>		pSubShapes
    ) {
		this( pShapeName, 0 );
		
		mSubShapes = pSubShapes;
	}

	/** Constructor based on a Spatial, rather than a Mesh */
	public CSGShape(
		String		pShapeName
	,	Spatial		pSpatial
	) {
		this( (pShapeName == null) ? pSpatial.getName() : pShapeName, 0 );
		setSpatial( pSpatial, null );
	}
	
	/** NOT REALLY PUBLIC/FOR HANDLER USE ONLY - Constructor based on a given handler */
	public CSGShape(
		CSGShapeProcessor	pHandler
	,	String				pShapeName
	,	int					pOrder
	,	CSGExceptionI		pAnError
	,	CSGExceptionI		pOtherError
	) {
		super( pShapeName );
		mHandler = pHandler.setShape( this );
		mOrder = pOrder;
		mSurface = CSGShapeSurface.USE_MESH;
		
		setError( pAnError );
		setError( pOtherError );
		
		mMeshIsPrepared = new AtomicBoolean();

		// Empty library until explicitly set
		mLibraryItems = Collections.EMPTY_MAP;
	}
	
	/** Return the JME aspect of this element */
	@Override
	public Spatial asSpatial() { return this; }

	/** Accessor to the shape surface in use */
	public CSGShapeSurface getShapeSurface() { return mSurface; }
	public void setShapeSurface(
		CSGShapeSurface		pSurface
	) {
		mSurface = pSurface;
	}
	
	/** Initialization based on type of Spatial provided */
	protected void setSpatial(
		Spatial			pSpatial
	,	CSGOperator		pOperator
	) {
		if ( pSpatial != null ) {
			// Special proxy handling
			if ( pSpatial instanceof CSGPlaceholderSpatial ) {
				// A stand-in for a true 'spatial', which we will resolve when we can
				if ( this.mProxy == null ) {
					// Remember this placeholder for later processing
					this.mProxy = (CSGPlaceholderSpatial)pSpatial;
				} else {
					// Resolve the placeholder now, looking from the top of the tree to
					// override defaults with higher level specifications where appropriate
					Spatial useSpatial = ((CSGPlaceholderSpatial)pSpatial).resolveItem( this, true );
					if ( useSpatial != null ) {
						// Use what we resolved to
						pSpatial = useSpatial;
					}
				}
			}
			// Carry forward any transform that the Spatial knows of
			this.localTransform = pSpatial.getLocalTransform().clone();
			
			// Carry forward any lights the Spatial knows of
			for( Light aLight : pSpatial.getLocalLightList() ) {
				this.addLight( aLight );
			}
			// How to interpret the mesh of the given spatial
			if ( pSpatial instanceof CSGGeonode ) {
				// Use the overall Geometry as the representation of the Geonode
				pSpatial = ((CSGGeonode)pSpatial).getMasterGeometry();
			}
			if ( pSpatial instanceof Geometry ) {
				// Use the Geometry's Mesh and Material
				this.mesh = ((Geometry)pSpatial).getMesh();
				this.material = ((Geometry)pSpatial).getMaterial();	
				
			} else if ( pSpatial instanceof Node ) {
				// Treat the Node as a grouping of subshapes
				if ( pOperator == null ) {
					// Select the appropriate default operator for subelements in a node
					pOperator = CSGOperator.UNION;
					if ( pSpatial instanceof Terrain ) {
						// It makes no sense to UNION all the components of a Terrain.  
						// Just blend it all together
						pOperator = CSGOperator.MERGE;
					}
				}
				int shapeCounter = 0;
				mSubShapes = new ArrayList();
				for( Spatial aSpatial : ((Node)pSpatial).getChildren() ) {
					String subShapeName = getName() + "-" + ++shapeCounter;
					CSGShape subShape = new CSGShape( subShapeName, aSpatial );
					subShape.setOperator( pOperator );
					mSubShapes.add( subShape );
				}
			}
		}
	}
	
	/** Where did this shape come from? */
	public boolean isBooleanBlend(
	) {
		return( (this.mesh == null) && (this.mSubShapes == null) );
	}
		
	/** Get status about just what regenerate is doing */
	@Override
	public StringBuilder reportStatus( 
		StringBuilder 	pBuffer
	, 	boolean 		pBriefly	
	) {
		if ( this.mHandler != null ) {
			pBuffer = this.mHandler.reportStatus( pBuffer, pBriefly );
		}
		return( pBuffer );
	}
	@Override
	public int getProgress(
	) {
		return( (this.mHandler == null) ? 0 : this.mHandler.getStaticstics().getProgress() );
	}

	/** Ensure this shape is ready to be blended --
	 		In particular, assume that the Mesh underlying this shape may have been 
	 		altered between one regenerate() call and the next.
	 */
	public CSGShape refresh(
	) {
		if ( (this.mHandler != null) ) this.mHandler.refresh();
		return( this );
	}
	
	/** Make a copy of this shape */
	@Override
	public CSGShape clone(
	) {
		CSGTempVars tempVars = CSGTempVars.get();
		try {
			return( clone( null, this.getLodLevel(), tempVars, CSGEnvironment.resolveEnvironment() ) );
		} finally {
			tempVars.release();
		}
	}
	public CSGShape clone(
		CSGMeshManager		pMeshManager
	,	int					pLODLevel
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGShape aClone;
		
		if ( this.mesh != null ) {
			// Base it on the mesh (but do not clone the material)
			aClone = (CSGShape)super.clone( false );
			aClone.setOrder( mOrder );
			
			if ( aClone.mesh instanceof CSGMesh ) {
				// Take this opportunity to register every custom face material in the mesh
				((CSGMesh)aClone.mesh).registerFaceProperties( pMeshManager, aClone );
			}
		} else if ( this.mSubShapes != null ) {
			// No mesh, but use a SHALLOW copy of the subshapes.  The shared subshapes should
			// regenerate properly, but watch out for non-cloned lights.
			aClone = (CSGShape)super.clone( false );
			aClone.setOrder( mOrder );
		} else {
			// Empty with no mesh
			aClone = new CSGShape( this.getName(), this.mOrder );
		}
		aClone.mShapeKey = CSGShape.assignInstanceKey( aClone.name );
		aClone.setOperator( this.mOperator );
		aClone.setShapeSurface( this.mSurface );
//		aClone.setLodLevel( pLODLevel );
		
		// NOTE that since clones share the same underlying Mesh, they all share
		//		the same MeshIsPrepared instance.  This allows us to 'prepare' the
		//		underlying mesh once and only once
		aClone.mMeshIsPrepared = this.mMeshIsPrepared;
		
		// Register this shape's standard Material
		pMeshManager.resolveMeshIndex( null, aClone.getMaterial(), null, aClone );
		
		aClone.mHandler = this.getHandler( pEnvironment, null ).clone( aClone );
		
		// Ensure the copy is ready for use 
		aClone = aClone.prepareShape( pMeshManager, pTempVars, pEnvironment );
		return( aClone );
	}
	
	/** Unique keystring identifying this element */
	@Override
	public String getInstanceKey(
	) { 
		if ( mShapeKey == null ) {
			mShapeKey = CSGShape.assignInstanceKey( this.name );
		}
		return mShapeKey; 
	}
	
	/** The shape knows if it is 'valid' or not */
	@Override
	public boolean isValid() { return( mInError == null ); }
	@Override
	public CSGExceptionI getError() { return mInError; }
	public void setError(
		CSGExceptionI	pError
	) {
		mInError = CSGConstructionException.registerError( mInError, pError );
	}
	
	/** Mostly internal access to the handler */
	public CSGShapeProcessor getHandler(
		CSGEnvironment		pEnvironment
	,	CSGShapeProcessor	pBaseHandler
	) {
		if ( mHandler == null ) {
			if ( pBaseHandler != null ) {
				// Use a copy of the base
				mHandler = pBaseHandler.clone( this );
			} else if ( pEnvironment != null ) {
				// Start from scratch
				mHandler = pEnvironment.resolveShapeProcessor().setShape( this );
			}
		}
		return( mHandler );
	}
	
	/** Accessor to the parent shape */
	@Override
	public CSGElement getParentElement(
	) { 
		if ( mParentElement != null ) {
			// By definition, the active parent shape is the parent element
			return mParentElement; 
		} else if ( this.parent instanceof CSGElement ) {
			// Looks like the parent Node can handle Element duties
			return (CSGElement)this.parent;
		} else {
			// No active parent
			return( null );
		}
	}
	public void setParentElement( CSGElement pParent ) { mParentElement = pParent; }
	
	/** Access to library elements */
	@Override
	public Map<String,Savable> getLibrary() { return mLibraryItems; }
	public void setLibrary( Map<String,Savable> pLibraryItems ) { mLibraryItems = pLibraryItems; } 
	
	/** Accessor to the list of active LightControls */
	public List<Control> getLightControls() { return mLightControls; }
	
	/** Accessor to the shared light list */
	public String[] getSharedLights() { return mSharedLights; }
	public void setSharedLights( String[] pSharedLights ) { mSharedLights = pSharedLights; }
	
	/** Accessor to the custom rendering node used in the final product */
	public CSGNode getRenderNode() { return mRenderNode; }
	public void setRenderNode( CSGNode pNode ) { mRenderNode = pNode; }
	
	/** Ready a list of shapes for processing */
	public List<CSGShape> prepareShapeList(
		List<CSGShape>	pShapeList
	,	CSGEnvironment	pEnvironment
	) {
		return( getHandler( pEnvironment, null ).prepareShapeList( pShapeList, pEnvironment ) );
	}
		
	/** Accessor to the mesh that applies to the given surface */
	public Integer getMeshIndex(
		CSGMeshManager		pMeshManager
	,	int					pFaceIndex
	) {
		if ( this.mesh instanceof CSGMesh ) {
			// CSGMesh based primitives may support per-face materials in their own right
			String useName = ((CSGMesh)this.mesh).getMeshName( pFaceIndex );
			
			Material useMaterial = ((CSGMesh)this.mesh).getMaterial( pFaceIndex );
			if ( useMaterial == null ) {
				// Nothing special, use the standard
				useMaterial = this.getMaterial();
			}
			PhysicsControl usePhysics = ((CSGMesh)this.mesh).getPhysics( pFaceIndex );
			return( pMeshManager.resolveMeshIndex( useName, useMaterial, usePhysics, this ) );
			
		} else {
			// Base the index on the underlying material only
			return( pMeshManager.resolveMeshIndex( null, this.getMaterial(), null, this ) );
		}
	}
	
	/** Accessor to the transform to apply to the underlying Mesh */
	public Transform getCSGTransform(
		CSGEnvironment		pEnvironment
	) {
		// The local transform applies for sure
		Transform aTransform = this.getLocalTransform();
		if ( (aTransform == Transform.IDENTITY) || Transform.IDENTITY.equals( aTransform ) ) {
			// Not really a transform
			aTransform = null;
		}
		// If we are a subshape of a SHAPE, then the parent's transform applies as well
		// during 'preTransform' processing.  This is when a mesh is totally transformed
		// before any shape blending is applied.
		if ( pEnvironment.mPreTransform && (mParentElement instanceof CSGShape) ) {
			Transform parentTransform = ((CSGShape)mParentElement).getCSGTransform( pEnvironment );
			if ( parentTransform != null ) {
				// The parent transform must be incorporated
				if ( aTransform == null ) {
					// Nothing locally, just use the parent
					aTransform = parentTransform;
				} else {
					// Blend together
					aTransform = aTransform.clone().combineWithParent( parentTransform );
				}
			}
		}
		return( aTransform );
	}

	/** Accessor to the operator */
	public CSGGeometry.CSGOperator getOperator() { return mOperator; }
	public void setOperator(
		CSGGeometry.CSGOperator	pOperator
	) {
		mOperator = pOperator;
	}
	
	/** Accessor to the order of operations */
	public int getOrder() { return mOrder; }
	public void setOrder(
		int		pOrder
	) {
		mOrder = pOrder;
	}
	
	/** Accessor to the Physics */
	@Override
	public boolean hasPhysics() { return( mPhysics != null ); }
	@Override
	public PhysicsControl getPhysics() { return mPhysics; }
	public void setPhysics(
		PhysicsControl		pPhysics
	) {
		mPhysics = pPhysics;
	}
	@Override
	public void applyPhysics(
		PhysicsSpace		pPhysicsSpace
	,	Node				pRoot
	) {
		// Shapes really play no part in the final 'world'
	}
	
	/** Accessor to Face oriented properties */
	@Override
	public boolean hasFaceProperties() { return( (mFaceProperties != null) && !mFaceProperties.isEmpty() ); }
	@Override
	public List<CSGFaceProperties>	getFaceProperties() { return mFaceProperties; }

    /** Accessor to the decorations */
    public boolean hasDecorations() { return( mDecorations != null ); }
    public List<Spatial> getDecorations() { return( mDecorations ); }
    public void setDecorations( 
    	List<Spatial> pDecorations 
    ) {
		if ( mDecorations == null ) {
			mDecorations = new ArrayList( pDecorations.size() );
		}
		mDecorations.addAll( pDecorations );
	}

    /** Special provisional setMaterial() that does NOT override anything 
	 	already in force, but supplies a default if any element is missing 
	 	a material
	 */
    @Override
	public void setDefaultMaterial(
		Material	pMaterial
	) {
    	if ( this.material == null ) {
    		this.material = pMaterial;
    	}
    }
	
	/** Add a shape into this one */
	public CSGShape union(
		CSGShape			pOtherShape
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		CSGShape useShape = this; //this.prepareShape( pMeshManager, pTempVars, pEnvironment );
		CSGShape useOther = pOtherShape.prepareShape( pMeshManager, pTempVars, pEnvironment );
		return( useShape.getHandler( pEnvironment, null ).union( useOther, pMeshManager, pTempVars, pEnvironment ) );
	}
	
	/** Subtract a shape from this one */
	public CSGShape difference(
		CSGShape			pOtherShape
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		CSGShape useShape = this; //this.prepareShape( pMeshManager, pTempVars, pEnvironment );
		CSGShape useOther = pOtherShape.prepareShape( pMeshManager, pTempVars, pEnvironment );
		return( useShape.getHandler( pEnvironment, null ).difference( useOther, pMeshManager, pTempVars, pEnvironment ) );
	}

	/** Find the intersection with another shape */
	public CSGShape intersection(
		CSGShape			pOtherShape
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		CSGShape useShape = this; // this.prepareShape( pMeshManager, pTempVars, pEnvironment );
		CSGShape useOther = pOtherShape.prepareShape( pMeshManager, pTempVars, pEnvironment );
		return( useShape.getHandler( pEnvironment, null ).intersection( useOther, pMeshManager, pTempVars, pEnvironment ) );
	}

	/** Find the merge with another shape */
	public CSGShape merge(
		CSGShape			pOtherShape
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) throws CSGConstructionException {
		CSGShape useShape = this; // this.prepareShape( pMeshManager, pTempVars, pEnvironment );
		CSGShape useOther = pOtherShape.prepareShape( pMeshManager, pTempVars, pEnvironment );
		return( useShape.getHandler( pEnvironment, null ).merge( useOther, pMeshManager, pTempVars, pEnvironment ) );
	}

	/** Produce the mesh(es) that corresponds to this shape
	 	The active handler knows how to construct a Mesh from its own underlying structure.
	 	Each Mesh will then be registered with the MeshManager under its appropriate
	 	index.  The MeshManager can subsequently be asked for Meshes/Spatials based on 
	 	any given index.
	  */
	public void toMesh(
		CSGMeshManager		pMeshManager
	,	boolean				pProduceSubelements
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		CSGShape useShape = this.prepareShape( pMeshManager, pTempVars, pEnvironment );
		useShape.getHandler( pEnvironment, null ).toMesh( pMeshManager, pProduceSubelements, pTempVars, pEnvironment  );
	}
		
	/** Mediate access to the mesh representing this shape */
	@Override
    public Mesh getMesh(
    ) {
		Mesh useMesh = this.mesh;
		if ( useMesh != null ) {
			if ( mSurface == CSGShapeSurface.USE_MESH ) {
				// Use what we know
				return( useMesh );
			}
			// Determine the span of the underlying mesh
			// NOTE that the localTransform applied to this shape should account
			//		for any positioning of the BoundingVolume.  No extra manipulation
			//		of the Volume is needed.
			Vector3f extent = new Vector3f();
			BoundingVolume aVolume = this.mesh.getBound(); //.transform( this.getLocalTransform() );
			
			switch( aVolume.getType() ) {
			case AABB:
				((BoundingBox)aVolume).getExtent( extent );
				break;
			case Sphere:
				float radius = ((BoundingSphere)aVolume).getRadius();
				extent.set( radius, radius, radius );
				break;
			}
			if ( !extent.equals( Vector3f.ZERO ) ) {
				switch( mSurface ) {
				case USE_BOUNDING_BOX:
					useMesh = new CSGBox( extent.x, extent.y, extent.z );
					break;
				case USE_BOUNDING_SPHERE:
					useMesh = new CSGSphere( 32, 32, extent.x );
					break;
				}
			}
		}
		// Looks like an empty shape
		return( useMesh );
    }
	
	/** Action to generate the mesh based on the given shapes
	 	NOTE
	 		I am not too sure if this is at all meaningful, but we need it anyway for CSGElement
	 */
	@Override
	public CSGShape regenerate(
	) {
		return( regenerate( false, null ) );		
	}
	@Override
	public CSGShape regenerate(
		boolean				pOnlyIfNeeded
	,	CSGEnvironment		pEnvironment
	) {
		if ( pOnlyIfNeeded && (mPriorResult != null) ) {
			// Use what we generated last time
			return( mPriorResult );
		}
		pEnvironment = CSGEnvironment.resolveEnvironment( pEnvironment, this );
		CSGTempVars tempVars = CSGTempVars.get();
		CSGMeshManager meshManager = new CSGMeshManager( this, false );
		try {
			CSGShape aShape = regenerateShape( mSubShapes, meshManager, tempVars, pEnvironment );
			return( mPriorResult = aShape );
		} finally {
			tempVars.release();
		}
	}
	/** How long did it take to build this shape */
	@Override
	public long getShapeRegenerationNS() { return mRegenNS; }


	/** Make this shape 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		// Let the geometry do its thing
		super.write( pExporter );
		
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		aCapsule.write( mOrder, "order", 0 );
		aCapsule.write( mOperator, "operator", CSGOperator.UNION );
		
		aCapsule.writeSavableArrayList( (ArrayList)mFaceProperties, "faceProperties", null );
	}

	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		
		// Support arbitrary Library items, defined BEFORE we process the rest of the items
        // Such items can be referenced within the XML stream itself via the
        //		id='name' and ref='name'
        // mechanism, and can be reference programmatically via their inherent names
		mLibraryItems = (Map<String,Savable>)aCapsule.readStringSavableMap( "library", null );
		mLibraryItems = CSGLibrary.fillLibrary( this, mLibraryItems, pImporter.getAssetManager(), true );

		// Let the geometry do its thing, which includes Mesh, Material, and LocalTransform
		super.read( pImporter );
		
		mOrder = aCapsule.readInt( "order", 0 );
		mOperator = aCapsule.readEnum( "operator", CSGOperator.class, CSGOperator.UNION );
		mSurface = aCapsule.readEnum( "surface", CSGShapeSurface.class, CSGShapeSurface.USE_MESH );
		if ( this.mesh != null ) {
	        // Extended attributes
	        Vector2f aScale = (Vector2f)aCapsule.readSavable( "scaleTexture", null );
	        if ( aScale != null ) {
	        	this.mesh.scaleTextureCoordinates( aScale );
	        }
	        boolean repeatTexture = aCapsule.readBoolean( "repeatTexture", false );
	        if ( repeatTexture && (this.material != null)) {
	        	MatParamTexture aParam = this.getMaterial().getTextureParam( "DiffuseMap" );
	        	if ( aParam != null ) {
	        		aParam.getTextureValue().setWrap( Texture.WrapMode.Repeat );
	        	}
	        }
		} else {
			// If we have no mesh, look for a subgroup of shapes
			mSubShapes = (List<CSGShape>)aCapsule.readSavableArrayList( "shapes", null );
			
			if ( mSubShapes == null ) {
				// No mesh and no subgroup -- how about a Spatial?
				Spatial aSpatial = (Spatial)aCapsule.readSavable( "spatial", null );
				
				if ( aSpatial != null ) {
					// Base this shape on the given spatial
					setSpatial( aSpatial, null );
				} else {
					// No mesh/subshapes/spatial -- so just what is this???
					this.readNoContents( pImporter, aCapsule );
				}
			}
		}
		// Look for specially configured transform
		CSGTransform proxyTransform = (CSGTransform)aCapsule.readSavable( "csgtransform", null );
		if ( proxyTransform != null ) {
			localTransform = proxyTransform.getTransform();
		}
        // Any physics?
        mPhysics = (PhysicsControl)aCapsule.readSavable( "physics", null );
        
        // Special shared lights
        mSharedLights = aCapsule.readStringArray( "sharedLights", null );

        // Look for possible face properties to apply to interior mesh/subgroup
        mFaceProperties = aCapsule.readSavableArrayList( "faceProperties", null );
        
        // Any decorations?
        mDecorations = aCapsule.readSavableArrayList( "decorations", null );
        
        // Special debug??
        mDebug = aCapsule.readSavableArrayList( "debug", null );
	}
	/** FOR SUBCLASS OVERRIDE:  shape with no mesh or subshapes */
	protected void readNoContents(
		JmeImporter		pImporter
	,	InputCapsule	pInCapsule
	) throws IOException {
		setError( new CSGConstructionException( CSGErrorCode.EMPTY_SHAPE
				, 	"CSGShape.read() with no mesh/shapes"
				,	this ) );		
	}

	/** Service routine to use the appropriate representation of this shape */
	protected CSGShape prepareShape(
		CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		// If a proxy is involved, resolve it now
		if ( mProxy != null ) {
			setSpatial( mProxy, null );
		}
		// Ready a place to stash all the lights
		// Since we rebuild the list on any 'prepare', then it is safe for a subshape list to 
		// use shared copies of the shapes.
		mLightControls = CSGLightControl.configureLightControls( null
																, null
																, getLocalLightList()
																, true
																, getLocalTransform() );
		// Ensure all the decorations are ready for use
		if ( mDecorations != null ) {
			for( int i = 0, j = mDecorations.size(); i < j; i += 1 ) {
				Spatial aSpatial = mDecorations.get( i );
				
				// If we are dealing with a placeholder, resolve it to the real component now
				// NOTE that when resolving decorations, we start from the top of the hierarchy
				//		to allow outer layers to override inner defaults
				if ( aSpatial instanceof CSGPlaceholderSpatial ) {
					aSpatial = ((CSGPlaceholderSpatial)aSpatial).resolveItem( this, true );
					mDecorations.set( i, aSpatial );
				}
				// By definition, any local lighting in the decoration really applies to the shape
				// BUT decoration based lights are not affected by the local transform, only 
				// the transform within the decoration itself.
				mLightControls = CSGLightControl.configureLightControls( mLightControls
																		, null
																		, aSpatial.getLocalLightList()
																		, true
																		, aSpatial.getLocalTransform() );
				// The decoration retains no lights of its own
				aSpatial.getLocalLightList().clear();
				aSpatial.removeControl( LightControl.class );
				aSpatial.removeControl( CSGLightControl.class );
			}
		}
		if ( mSubShapes == null ) {
			// NOT based on subshapes, so we rely on a spatial
			// If we have face properties here at the Shape level, apply scaling now.
			// If cloning was involved, then the mesh may already have been scaled.  We
			// only need to prep the mesh once.
			if ( !mMeshIsPrepared.getAndSet( true ) && (mesh instanceof CSGMesh) ) {
				// We are wrapping around a Mesh that supports per-face texture scale
				CSGMesh thisMesh = (CSGMesh)mesh;
				
				// Apply every scaling that is appropriate to the faces supported by the
				// underlying mesh.
				Iterable<Face> faceList = CSGFaceProperties.getIterableFaceMask( thisMesh.getSupportedFacesMask() );
				for( Face aFace : faceList ) {
					// What scaling applies?
					Vector2f texScale = CSGFaceProperties.resolveTextureScale( this, aFace.getMask() );
					if ( texScale != null ) {
						thisMesh.scaleFaceTextureCoordinates( texScale, aFace.getMask() );
		        	}
				}
			}
			return( this );
		} else {
			// This shape acts as a blending parent of the given subshapes
			return( regenerateShape( mSubShapes, pMeshManager, pTempVars, pEnvironment ) );
		}
	}
	protected CSGShape regenerateShape(
		List<CSGShape>		pShapes
	,	CSGMeshManager		pMeshManager
	,	CSGTempVars			pTempVars
	,	CSGEnvironment		pEnvironment
	) {
		long startTimeNS = System.nanoTime();
		
		// Sort the shapes as needed by their handler
		List<CSGShape> sortedShapes = prepareShapeList( pShapes, pEnvironment );
		
		// Use this Material as the generic one for all the sub shapes
		pMeshManager.pushGenericIndex( this.getMaterial(), this );
		try {
			// Operate on each shape in turn, blending it into the common
			CSGShape aProduct = null;
			for( CSGShape aShape : sortedShapes ) {
				if ( !aShape.isValid() ) {
					// We cannot use invalid shapes
					this.setError( aShape.getError() );
					continue;
				}
				aShape.setParentElement( this );
				
				// Apply the operator
				switch( aShape.getOperator() ) {
				case UNION:
					if ( aProduct == null ) {
						// A place to start
						aProduct = aShape.clone( pMeshManager, this.getLodLevel(), pTempVars, pEnvironment );
					} else {
						// Blend together
						aProduct = aProduct.union( aShape.refresh(), pMeshManager, pTempVars, pEnvironment );
					}
					break;
					
				case DIFFERENCE:
					if ( aProduct == null ) {
						// NO PLACE TO START
					} else {
						// Blend together
						aProduct = aProduct.difference( aShape.refresh(), pMeshManager, pTempVars, pEnvironment );
					}
					break;
					
				case INTERSECTION:
					if ( aProduct == null ) {
						// A place to start
						aProduct = aShape.clone( pMeshManager, this.getLodLevel(), pTempVars, pEnvironment );
					} else {
						// Blend together
						aProduct = aProduct.intersection( aShape.refresh(), pMeshManager, pTempVars, pEnvironment );
					}
					break;
					
				case SKIP:
					// This shape is not taking part
					break;
					
				case MERGE:
					if ( aProduct == null ) {
						// A place to start
						aProduct = aShape.clone( pMeshManager, this.getLodLevel(), pTempVars, pEnvironment );
					} else {
						// Treat multiple meshes as a single mesh
						aProduct = aProduct.merge( aShape.refresh(), pMeshManager, pTempVars, pEnvironment );
					}
					break;
				}
			}
			// The final product if what is used as the result of the group
			if ( this.getName() != null ) {
				// Apply the explicit name of 'this' shape to the resultant blend
				aProduct.setName( this.getName() );
			}
			if ( this.getLocalLightList().size() > 0 ) {
				// Local lights on this shape must be applied to the end product
				// This can be done by duplicating the light controls from this shape
				aProduct.mLightControls = CSGLightControl.configureLightControls( null
																					, null
																					, getLocalLightList()
																					, true
																					, getLocalTransform() );
			}
			if ( this.mRenderNode != null ) {
				aProduct.mRenderNode = this.mRenderNode;
			}
			// Is there a per shape transform to apply?
			Transform shapeTransform = this.getCSGTransform( pEnvironment );
			if ( !pEnvironment.mPreTransform && (shapeTransform != null) ) {
				// If we did NOT apply the transform directly to the mesh, we must
				// apply the shape transform now.
				aProduct.getHandler( pEnvironment, null ).applyTransform( shapeTransform, pTempVars, pEnvironment );
			}
			return( aProduct );
			
		} finally {
			pMeshManager.popGenericIndex();
			mRegenNS = System.nanoTime() - startTimeNS;
		}
	}

		
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGShapeRevision
													, sCSGShapeDate
													, pBuffer ) );
	}
	
	////// Debug support
	@Override
	public String toString(
	) {
		return( (this.name == null) ? getInstanceKey() : getInstanceKey() + "|" + getName() );
	}

}

