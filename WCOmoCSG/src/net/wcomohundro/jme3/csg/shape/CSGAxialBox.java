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

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import net.wcomohundro.jme3.csg.CSGMeshManager;
import net.wcomohundro.jme3.csg.CSGTempVars;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.shape.CSGFaceProperties.Face;

import com.jme3.bullet.control.PhysicsControl;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;

/** Build a box based on axial z-extent style processing.
 	This gives us open/closed, and inverted boxes, as well as texture mapping that
 	matches how other axial/radial textures are applied.
 	
 	"1" axial sample reflects the 4 outer edges of the box (Left/Right, Top/Bottom)
 	Standard axial front/back processing will create the other two faces.
 	
 	By definition, a full box has 6 faces, each with 4 corners, for a full 24 vertices.
 	While the cube really only has 8 distinct points, each different face imposes its
 	own normal/texture to any given point.  So you are forced to have 24 vertices
 	even if the positional information may be duplicated.
 	
 	Working from back-to-front, counter clockwise, starting with the lower left
 	when facing the surface results in the following vertices:

 	             [2]--------[3]
 	            /           /|
 	           /           / |
 			[7]---------[6]  |
 			 |           |   |
 			 |   [1]     |--[0]
 			 |           | /
 			 |           |/
 			[4]---------[5]
 			
 	For drawing the faces, standard axial processing starts in the back, moves up 
 	the Z, and finishes with the front.  If we also mimic the radial processing of
 	starting in the east and moving counter clockwise, we will draw the side faces
 	Right, Top, Left, Bottom
 */
public class CSGAxialBox 
	extends CSGAxial
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGAxialBoxRevision="$Rev$";
	public static final String sCSGAxialBoxDate="$Date$";

	
	/** The expected sequence of drawing the faces */
	protected static final Face[] sFaces = new Face[] {
		Face.BACK
	,	Face.RIGHT, Face.TOP, Face.LEFT, Face.BOTTOM
	,	Face.FRONT
	};
	
	/** The eight vertices that define a box */
	protected static final float[] sVertices = new float[] {
		  1, -1, -1		// right, bottom, back		[0]
	,	 -1, -1, -1		// left, bottom, back		[1]
	,	 -1,  1, -1		// left, top, back			[2]
	,	  1,  1, -1		// right, top, back			[3]
		
	,	 -1, -1,  1		// left, bottom, front		[4]
	,	  1, -1,  1		// right, bottom, front		[5]
	,	  1,  1,  1		// right, top, front		[6]
	,	 -1,  1,  1		// left, top, front			[7]
	};
	
	/** The vertices that apply to the faces (mimics ROLLER texture) */
	protected static final int[] sRollerFaceVertices = new int[] {
		0, 1, 2, 3		// back
	
	,	5, 0, 3, 6		// right
	,	7, 6, 3, 2		// top
	,	1, 4, 7, 2		// left
	,	1, 0, 5, 4		// bottom
	
	,	4, 5, 6, 7		// front
	};
	/** The vertices that apply to the faces (mimics CAN texture) */
	protected static final int[] sCanFaceVertices = new int[] {
		0, 1, 2, 3		// back
	
	,	0, 3, 6, 5		// right
	,	3, 2, 7, 6 		// top
	,	2, 1, 4, 7		// left
	,	1, 0, 5, 4		// bottom
	
	,	4, 5, 6, 7		// front
	};
	
	/** The normals, strictly related to the face */
    protected static final float[] sNormals = new float[] {
        0,  0, -1	 	// back
       
    ,   1,  0,  0	 	// right 
    ,   0,  1,  0	 	// top
    ,  -1,  0,  0	 	// left
    ,   0, -1,  0	  	// bottom
    
	,	0,  0,  1	 	// front
   	};

    /** The textures: to match the other axial/radial shapes **/
   	protected static final float[] sRollerTexture = new float[] {
        0, 0,   1, 0,   1, 1,   0, 1	 // back	0 / 1 / 2 / 3
        
    ,   0, 1,   0, 0,   1, 0,   1, 1	 // right	5 / 0 / 3 / 6      
    ,   1, 1,   0, 1,   0, 0,   1, 0	 // top		7 / 6 / 3 / 2
    ,   1, 0,   1, 1,   0, 1,   0, 0	 // left	1 / 4 / 7 / 2
    ,   0, 0,   1, 0,   1, 1,   0, 1	 // bottom  1 / 0 / 5 / 4

    ,	0, 0,   1, 0,   1, 1,   0, 1	 // front	4 / 5 / 6 / 7
   	};
    /** This texture is applied to the sides as if the shape where tilted up to
        sit on its back surface, and then the texture is wrapped around it. 
        This matches CSGRadialCapped CAN texture */
   	protected static final float[] sCanTexture = new float[] {
        0, 0,   1, 0,   1, 1,   0, 1	 // back	0 / 1 / 2 / 3

    ,   0, 0,   1, 0,   1, 1,   0, 1	 // right   0 / 3 / 6 / 5      
    ,   0, 0,   1, 0,   1, 1,   0, 1	 // top		3 / 2 / 7 / 6 
    ,   0, 0,   1, 0,   1, 1,   0, 1	 // left	2 / 1 / 4 / 7
    ,   0, 0,   1, 0,   1, 1,   0, 1	 // bottom  1 / 0 / 5 / 4

    ,	0, 0,   1, 0,   1, 1,   0, 1	 // front	4 / 5 / 6 / 7
   	};
   	
	/** The size of the box (remember to *2 for full width/height/depth) */
    protected float mExtentX, mExtentY;

    
	/** Standard null constructor */
	public CSGAxialBox(
	) {
		this( 1, 1, 1, false );
	}
	/** Constructor based on the given extents */
	public CSGAxialBox(
		float	pExtentX
	,	float	pExtentY
	,	float	pExtentZ
	) {
		this( pExtentX, pExtentY, pExtentZ, true );
	}
	public CSGAxialBox(
		float	pExtentX
	,	float	pExtentY
	,	float	pExtentZ
	,	boolean	pReadyGeometry
	) {
		super( 1, pExtentZ, true, false );
		
		mFlatEnds = true;
		mExtentX = pExtentX;
		mExtentY = pExtentY;
		
        if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
	}
	
	/** OVERRIDE to interpret SIDES */
	@Override
	public void setFaceProperties(
		List<CSGFaceProperties>	pPropertyList
	,	boolean					pRebuild
	) {
		super.setFaceProperties( pPropertyList, false );
		
		if ( mFaceProperties != null ) {
			for( CSGFaceProperties aProperties : mFaceProperties ) {
				// SIDES is meaningless, it is really LEFT/RIGHT/TOP/BOTTOM
				if ( aProperties.getFaceMask() == Face.SIDES.getMask() ) {
					aProperties.setFaceMask( Face.LEFT_RIGHT.getMask() | Face.TOP_BOTTOM.getMask() );
				}
			}
			if ( pRebuild ) {
				this.updateGeometryEpilog();
			}
		}
		
	}
	
	/** Accessor to the full range of faces supported by this mesh */
	@Override
	public int getSupportedFacesMask(
	) {
		return( Face.FRONT_BACK.getMask() | Face.LEFT_RIGHT.getMask() | Face.TOP_BOTTOM.getMask() );
	}
	
	/** Accessors to the extents */
	@Override
    public float getXExtent() { return mExtentX; }
	@Override
    public void setXExtent( float pExtent ) { mExtentX = pExtent; }
    
    @Override
    public float getYExtent() { return mExtentY; }
    @Override
    public void setYExtent( float pExtent ) { mExtentY = pExtent; }
    
    @Override
    public float getRadius() { return( (mExtentX + mExtentY) / 2.0f ); }
    @Override
    public void setRadius( 
    	float pRadius 
    ) {
    	mExtentX = pRadius;
    	mExtentY = pRadius;
    }

    
    /** Apply gradient vertex colors */
    @Override
    public void applyGradient(
    	List<ColorRGBA>	pColors
    ) {
    }

    /** Apply the change of geometry */
    @Override
    protected void updateGeometryProlog(
    ) {
        // Allocate buffers for position/normals/texture
    	CSGAxialContext aContext = getContext( true );
    	
    	// Create all the vertices info
    	createGeometry( aContext );

        // Define the mapping indices
        VertexBuffer idxBuffer = createIndices( aContext, 0.0f );
        setBuffer( idxBuffer );
        
        if ( mLODFactors != null ) {
        	// Various Levels of Detail are not applicable to the simple box
        }
        // Establish the bounds
        updateBound();
        setStatic();
    }

    /** OVERRIDE: allocate the context */
    protected CSGAxialContext getContext(
    	boolean		pAllocateBuffers
    ) {
    	return( new CSGAxialBoxContext( mAxisSamples, mGeneratedFacesMask, pAllocateBuffers ) );
    }
    
    /** FOR SUBCLASS OVERRIDE: ready axial coordinates */
    protected void readyCoordinates(
    	CSGAxialContext		pContext
    ) {
    }
    
    /** OVERRIDE: create the elements of a given slice */
    @Override
    protected void createSlice(
        CSGAxialContext 	pContext
    ,	int					pSurface
    ,	CSGTempVars			pTempVars
    ) {
    	int faceIndex, faceMask;
    	Vector2f baseTex = pTempVars.vect2d1;
    	Vector2f spanTex = pTempVars.vect2d2;
    	Vector2f spanRight = pTempVars.vect2d3;
    	Vector2f spanLeft = pTempVars.vect2d4;
    	Vector2f spanTop = pTempVars.vect2d5;
    	Vector2f spanBottom = pTempVars.vect2d6;
    	Vector2f temp = pTempVars.vect2d7;
    	
    	if ( pSurface < 0 ) {
    		// BACK
    		faceIndex = 0;
    		faceMask = Face.BACK.getMask() & mGeneratedFacesMask;
    	} else if ( pSurface > 0 ){
    		// FRONT
    		faceIndex = sFaces.length -1;
    		faceMask = Face.FRONT.getMask() & mGeneratedFacesMask;
    	} else {
    		// SIDES
    		faceIndex = 1;
    		faceMask = (Face.LEFT_RIGHT.getMask() | Face.TOP_BOTTOM.getMask()) & mGeneratedFacesMask;
    		
    		// Account for span around the perimeter
    		resolveTextureSpans( spanRight, spanLeft, spanTop, spanBottom );
    	}
    	// Process the faces in the expected sFaces[] order
    	for( ; faceIndex < sFaces.length; faceIndex += 1 ) {
    		Face aFace = sFaces[ faceIndex ];
    		int faceBit = aFace.getMask();

    		// Texture order for the 'sides' is counter clockwise, starting from the right
    		// ie Right / Top / Left / Bottom, so we can set the 'base' with Right and 
    		// advance it as we cycle through the rest of the sides
    		switch( aFace ) {
    		case FRONT:
    		case BACK:
    			// Full span from zero to one
    			resolveTextureCoord( baseTex, spanTex
    								, Vector2f.ZERO, Vector2f.UNIT_XY
    								, faceBit
    								, temp );
    			break;
    			
    		case RIGHT:
    			baseTex.set( 0, 0 );		// First side, initialize the starting point
    			resolveTextureCoord( baseTex, spanTex
									, baseTex, spanRight
									, faceBit
									, temp );
				break;
    		case LEFT:
    			// The span is across the left/right face
    			resolveTextureCoord( baseTex, spanTex
									, baseTex, spanLeft
									, faceBit
									, temp );
    			break;
    		case TOP:
			    resolveTextureCoord( baseTex, spanTex
									, baseTex, spanTop
									, faceBit
									, temp );
				break;
    		case BOTTOM:
    			resolveTextureCoord( baseTex, spanTex
									, baseTex, spanBottom
									, faceBit
									, temp );
    			break;
    		}
    		// Check if we are drawing this face 
    		// (otherwise, we still need to keep adjusting the texture points in baseTex)
    		if ( (faceMask & faceBit) == faceBit ) {
	    		// By definition, the same normal applies to all vertices on this face
	            float normalFlip = (mInverted) ? -1 : 1;
                Vector3f aNormal = setNormal( pTempVars.vect2, faceIndex, normalFlip
                								, pTempVars.vect3, pTempVars.vect4 );
	    		
	    		// We include the 4 vertices, the index buffer will select the 2 sets of 3
	    		// to build the proper triangles
	    		for( int j = 0; j < 4; j += 1 ) {
	    			// Pick the proper vertex positions for the given face
	    			pContext.mPosVector = setPosition( pTempVars.vect1, faceIndex, j );
	                pContext.mPosBuf.put( pContext.mPosVector.x )
	                                .put( pContext.mPosVector.y )
	                                .put( pContext.mPosVector.z );	
	                
	                // The normal is the same for all the vertices of any given face
	                pContext.mNormBuf.put( aNormal.x )
					                 .put( aNormal.y )
					                 .put( aNormal.z );
	                
	                // The texture varies per vertex based on the face
	                Vector2f aTexture = setTexture( temp, faceIndex, j, baseTex, spanTex, pContext.mPosVector );
	                pContext.mTexBuf.put( temp.x )
	                			    .put( temp.y );
	    		}
    		}
    		baseTex.x += spanTex.x;
    	}
    }
    
    /** Resolve the 'span' as applied to a texture on a side of the box */
    protected void resolveTextureSpans(
    	Vector2f	pRightSpan
    ,	Vector2f	pLeftSpan
    ,	Vector2f	pTopSpan
    ,	Vector2f	pBottomSpan
    ) {
		// Account for span around the perimeter
    	// and we assume a full span (1.0) to cover the 'z' 
		float perimeter = (2 * 2 * mExtentX) + (2 * 2 * mExtentY);
		pRightSpan.set( (mExtentY * 2) / perimeter, 1 );
		pLeftSpan.set( (mExtentY * 2) / perimeter, 1 );
		
		pTopSpan.set( (mExtentX * 2) / perimeter, 1 );    	
		pBottomSpan.set( (mExtentX * 2) / perimeter, 1 );    	
    }
    
    /** Service routine to calculate an appropriate positional vector */
    protected Vector3f setPosition(
    	Vector3f	pResult
    ,	int			pFaceIndex
    ,	int			pWhichPoint
    ) {
		int vertexSelector = (pFaceIndex * 4) + pWhichPoint;			// 4 per face
		int whichVertex = sCanFaceVertices[ vertexSelector++ ];
		int index = whichVertex * 3;									// x/y/z per vertex
    	pResult.set( sVertices[ index++ ], sVertices[ index++ ], sVertices[ index++ ] );
    	
    	// Account for size
    	pResult.multLocal( mExtentX, mExtentY, mExtentZ );
    	return pResult;
    }
    
    /** Service routine to calculate an appropriate normal for a given face */
    protected Vector3f setNormal(
    	Vector3f	pResult
    ,	int			pFaceIndex
    ,	float		pFlipNormal
    ,	Vector3f	pTemp1
    ,	Vector3f	pTemp2
    ) {
    	// x/y/z per face
        int normalSelector = pFaceIndex * 3;
        pResult.set( sNormals[ normalSelector++ ] * pFlipNormal
        		,	 sNormals[ normalSelector++ ] * pFlipNormal
        		,	 sNormals[ normalSelector++ ] * pFlipNormal );
        return pResult;
    }
    
    /** Service routine to calculate an appropriate texture */
    protected Vector2f setTexture(
    	Vector2f	pResult
    ,	int			pFaceIndex
    ,	int			pWhichPoint
    ,	Vector2f	pBaseTex
    ,	Vector2f	pSpanTex
    ,	Vector3f	pPosition
    ) {
		int textureSelector = (pFaceIndex * 4 * 2) + (pWhichPoint * 2);		// x/y for 4 per face
        pResult.set(  pBaseTex.x + (sCanTexture[ textureSelector++ ] * pSpanTex.x)
    				, pBaseTex.y + (sCanTexture[ textureSelector++ ] * pSpanTex.y) );
        
        return pResult;	
    }

    /** Service routine to allocate and fill an index buffer */
    protected VertexBuffer createIndices(
    	CSGAxialContext		pContext
    ,	float				pLODFactor
    ) {
    	// Accommodate the LOD factor
    	int triangleCount 
    		= pContext.setReductionFactors( pLODFactor, mAxisSamples, 0, mGeneratedFacesMask );
    	
        // Allocate the indices, 3 points on every triangle
        ShortBuffer idxBuf = BufferUtils.createShortBuffer( 3 * triangleCount );
        
        // Check every face, in the order written into the buffers
        short i0 = 0, i1 = 1, i2 = 2, i3 = 3;
        for( Face aFace : sFaces ) {
        	if ( aFace.maskIncludesFace( mGeneratedFacesMask ) ) {
        		// This face was written into the buffer
        		if ( mInverted ) {
        			// Triangle one (inverted)
        			idxBuf.put( i0 ).put( i2 ).put( i1 );
        			// Triangle two (inverted)
        			idxBuf.put( i0 ).put( i3 ).put( i2 );        			
        		} else {
        			// Triangle one
        			idxBuf.put( i0 ).put( i1 ).put( i2 );
        			// Triangle two
        			idxBuf.put( i0 ).put( i2 ).put( i3 );
        		}
        		i0 += 4; i1 += 4; i2 += 4; i3 += 4;
        	}
        }
        // Wrap as an Index buffer
    	VertexBuffer vtxBuffer = new VertexBuffer( Type.Index );
    	vtxBuffer.setupData( Usage.Dynamic, 3, Format.UnsignedShort, idxBuf );
        return( vtxBuffer );
    }

    /** Read the fundamental configuration parameters */
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );

        // Let the super do its thing
        super.read( pImporter );
        
        if ( mGeneratedFacesMask == 0 ) {
        	// Default to full face set, along with explicit open/closed
        	mGeneratedFacesMask = this.getSupportedFacesMask();
            setClosed( inCapsule.readBoolean( "closed", true ) );     
        }
        // Standard trigger of updateGeometry() to build the shape 
        this.updateGeometry();
        
        // Any special color processing?
        List<ColorRGBA> colors = inCapsule.readSavableArrayList( "colorGradient", null );
        if ( colors != null ) {
	        // First is the north pole, second is the equator, optional third is the south pole
	        applyGradient( colors );
        }
    }
    /** Service routine to read the extents */
    @Override
    protected void readExtents(
    	JmeImporter		pImporter
    ,	InputCapsule	pInCapsule
    ) throws IOException {
    	super.readExtents( pImporter, pInCapsule );
    	
        // NOTE that super x/y/z default to zero to detect not-supplied
        if ( mExtentX == 0 ) mExtentX = 1.0f;
        if ( mExtentY == 0 ) mExtentY = 1.0f;
        if ( mExtentZ == 0 ) mExtentZ = 1.0f;
    }
    
    /** Preserve this shape */
    @Override
    protected void writeExtents(
    	JmeExporter		pExporter
    ,	OutputCapsule	pOutCapsule
    ) throws IOException {
    	super.writeExtents( pExporter, pOutCapsule );
    	pOutCapsule.write( mExtentX, "xExtent", 1 );
    	pOutCapsule.write( mExtentY, "yExtent", 1 );       
    }

    /** Apply texture coordinate scaling to selected 'faces' of the box */
    @Override
    public void scaleFaceTextureCoordinates(
    	Vector2f	pScaleTexture
    ,	int			pFaceMask
    ) {
        VertexBuffer tc = getBuffer( Type.TexCoord );
        if ( tc == null ) {
            throw new IllegalStateException( "CSGBox: no texture coordinates" );
        }
        if ( tc.getFormat() != VertexBuffer.Format.Float ) {
            throw new UnsupportedOperationException( "CSGBox: only float texture coord format is supported" );
    	}
        if ( tc.getNumComponents() != 2 ) {
            throw new UnsupportedOperationException( "CSGBox: only 2D texture coords are supported" );
        }
        FloatBuffer aBuffer = (FloatBuffer)tc.getData();
        aBuffer.clear();
        
        CSGTempVars vars = CSGTempVars.get();
//        Vector2f textureOrigin = vars.vect2d1;
//        Vector2f textureSpan = vars.vect2d2;
        try {
	        // Check every face, in the order written into the buffers
	        int index = 0;
	        for( Face aFace : sFaces ) {
	    		// Each face is defined by 4 x/y points
	        	if ( aFace.maskIncludesFace( mGeneratedFacesMask ) ) {
	        		// This face exists in the buffer
	        		if ( aFace.maskIncludesFace( pFaceMask ) ) {
	        			// Look for special 'terminus' processing
	        			float biasX = 0, biasY = 0;	
	        			Vector2f textureTerminus = this.matchFaceTerminus( aFace.getMask() );
	        			if ( textureTerminus != null ) {
	        				// If the texture was positioned based on its terminus rather than
	        				// its origin, then a bias must be introduced to keep the terminus
	        				// point constant after scaling.  Therefore:
	        				//		(terminus * scale) - bias = terminus
	        				//		bias = (terminus * scale) - terminus
	        				// and each point is determined by
	        				//		newPoint = (oldPoint * scale) - bias
	            			// Reset the texture origin as specified
	            			if ( !Float.isNaN( textureTerminus.x ) ) {
	            				biasX = (textureTerminus.x * pScaleTexture.x) - textureTerminus.x;
	            			}
	            			if ( !Float.isNaN( textureTerminus.y ) ) {
	            				biasY = (textureTerminus.y * pScaleTexture.y) - textureTerminus.y;
	            			}
	        			}
	        			// This face must be scaled, touch all four vertices
	            		for( int i = 0; i < 4; i += 1 ) {
	            			float x = aBuffer.get( index  );
	            			float y = aBuffer.get( index + 1 );
	
	                        x *= pScaleTexture.x;
	                        x -= biasX;
	                        
	                        y *= pScaleTexture.y;
	                        y -= biasY;
	                        aBuffer.put( index++, x ).put( index++, y);
	            		}
	        		} else {
	        			// Skip this face
	        			index += 8;
	        		}
	        	}
	        }
        } finally {
        	vars.release();
        }
    	aBuffer.clear();
        tc.updateData( aBuffer );
    }
 
	/** Accessor to the mesh name that applies to the given surface */
    @Override
	public String getMeshName(
		int					pFaceIndex
	) {
		// Determine the face, which we know is in the sFaces order, with 2 triangles 
    	// per face
    	Face aFace = sFaces[ pFaceIndex / 2 ];
    	String aName = resolveFaceName( aFace.getMask() );
		return( aName );
	}

	/** Accessor to the material that applies to the given surface */
    @Override
	public Material getMaterial(
		int					pFaceIndex
	) {
		// Determine the face, which we know is in the sFaces order, with 2 triangles 
    	// per face
    	Face aFace = sFaces[ pFaceIndex / 2 ];
    	Material aMaterial = resolveFaceMaterial( aFace.getMask() );
		return( aMaterial );
	}
    
	/** Accessor to the physics that applies to the given surface */
    @Override
	public PhysicsControl getPhysics(
		int					pFaceIndex
	) {
		// Subclasses must override if they support custom material per surface
		// Determine the face, which we know is in the order
    	//	Front/Back/Left/Right/Top/Bottom, with 2 triangles per face, which matches
    	//  the bits in Face
    	int faceMask = 1 << (pFaceIndex / 2);
    	PhysicsControl aPhysics = resolveFacePhysics( faceMask );
		return( aPhysics );
	}

    
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( this.getClass()
													, sCSGAxialBoxRevision
													, sCSGAxialBoxDate
													, pBuffer ) );
	}

}
/** Helper class for use during the geometry calculations */
class CSGAxialBoxContext
	extends CSGAxialContext
{	
    /** Initialize the context */
    CSGAxialBoxContext(
    	int							pAxisSamples
    ,	int							pGeneratedFacesMask
    ,	boolean						pAllocateBuffers
    ) {	
    	initializeContext( pAxisSamples, 0, pGeneratedFacesMask, pAllocateBuffers );
    }
    
    /** How many slices are needed? */
    @Override
    protected int resolveSliceCount(
    	int			pAxisSamples
    ,	int			pGeneratedFacesMask
    ) {
		// We operate with 1 basic slice (which represents the 4 outer edges) but we
    	// need to bump the slice count to account for the special front and back face
    	// processing.
    	int sliceCount = pAxisSamples;
    	if ( Face.BACK.maskIncludesFace( pGeneratedFacesMask ) ) {
    		sliceCount += 1;
    	}
    	if ( Face.FRONT.maskIncludesFace( pGeneratedFacesMask ) ) {
    		sliceCount += 1;
    	}
    	return( sliceCount );
    }

    /** How many vertices are produced? */
    @Override
    protected int resolveVetexCount(
    	int			pSliceCount
    ,	int			pRadialSamples
    ,	int			pGeneratedFacesMask
    ) {
    	// Every drawn face takes 4 separate vertices, since the normals/textures are unique
    	// to the face, even if the position may be shared.
    	int vertCount = Integer.bitCount( pGeneratedFacesMask ) * 4;
    	return( vertCount );
    }

    /** LOD does not really apply to a simple box, so just figure out the count of triangles */
    @Override
	int setReductionFactors(
		float		pLODFactor
	,	int			pAxisSamples
	,	int			pRadialSamples
	,	int			pGeneratedFacesMask
	) {

	    int triCount = 2 * Integer.bitCount( pGeneratedFacesMask );
	    return( triCount );
	}

}

