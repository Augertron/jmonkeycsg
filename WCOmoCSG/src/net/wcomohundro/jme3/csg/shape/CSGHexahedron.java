/** Copyright (c) 2019, WCOmohundro
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

import net.wcomohundro.jme3.csg.CSGVersion;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

/** A true hexahedron is a solid with six planar faces.  A CSGHexahedron is not quite
 	that generic and operates as a simple axial in Z.
 	
 	The CSGHexahedron has a single Z extent (which determines the distance between 
 	the front and back faces, but independent Top and Bottom extents (rather than a single
 	X extent) and independent Left and Right extents (rather than a single Y extent)
 	
 	A CSGHexahedron with equal Top/Bottom extents, and equal Left/Right extents is identical
 	to a CSGAxialBox.  Otherwise, all four 'corners' can be defined by independent, 
 	positive extents.
 	
 	This results in a deformed 'cube' with rectangularly shaped sides, but with faces
 	that are any four sided (convex) figure.

 */
public class CSGHexahedron 
	extends CSGAxialBox
{
	/** Version tracking support */
	public static final String sCSGHexahedronRevision="$Rev$";
	public static final String sCSGHexahedronDate="$Date$";

	/** The extents that define the four corners */
    protected Vector2f mExtentTopLeft;
    protected Vector2f mExtentTopRight;
    protected Vector2f mExtentBottomLeft;
    protected Vector2f mExtentBottomRight;
    
    
	/** Standard null constructor */
	public CSGHexahedron(
	) {
		this( 1, 1, 1, 1, 1, false );
	}
	public CSGHexahedron(
		float	pExtentTop
	,	float	pExtentBottom
	,	float	pExtentLeft
	,	float	pExtentRight
	,	float	pExtentZ
	,	boolean	pReadyGeometry
	) {
		super( (pExtentTop + pExtentBottom) / 2.0f
		, 	   (pExtentRight + pExtentLeft) / 2.0f
		,       pExtentZ
		,       false );
		
		mExtentTopLeft = new Vector2f( pExtentTop, pExtentLeft );
		mExtentTopRight = new Vector2f( pExtentTop, pExtentRight );
		mExtentBottomLeft = new Vector2f( pExtentBottom, pExtentLeft );
		mExtentBottomRight = new Vector2f( pExtentBottom, pExtentRight );
		
        if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
	}
	public CSGHexahedron(
		Vector2f	pExtentTopLeft
	,	Vector2f	pExtentTopRight
	,	Vector2f	pExtentBottomLeft
	,	Vector2f	pExtentBottomRight
	,	float		pExtentZ
	,	boolean		pReadyGeometry
	) {
		super( 0, 0, pExtentZ, false );
		setExtents( pExtentTopLeft, pExtentTopRight, pExtentBottomLeft, pExtentBottomRight );
		
        if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
	}
	
	/** Accessors to the extents */
	@Override
    public void setXExtent( 
    	float pExtent 
    ) { 
		mExtentX = pExtent; 
		mExtentTopLeft.x = pExtent;
		mExtentTopRight.x = pExtent;
		mExtentBottomLeft.x = pExtent;
		mExtentBottomRight.x = pExtent;
	}
    @Override
    public void setYExtent( 
    	float pExtent 
    ) { 
    	mExtentY = pExtent; 
		mExtentTopLeft.y = pExtent;
		mExtentTopRight.y = pExtent;
		mExtentBottomLeft.y = pExtent;
		mExtentBottomRight.y = pExtent;
    }

    @Override
    public void setRadius( 
    	float pRadius 
    ) {
    	setXExtent( pRadius );
    	setYExtent( pRadius );
    }
    
    /** Explicitly set all the extents */
    public void setExtents(
    	Vector2f	pExtentTopLeft
    ,	Vector2f	pExtentTopRight
    ,	Vector2f	pExtentBottomLeft
    ,	Vector2f	pExtentBottomRight
    ) {
		mExtentTopLeft = pExtentTopLeft;
		mExtentTopRight = pExtentTopRight;
		mExtentBottomLeft = pExtentBottomLeft;
		mExtentBottomRight = pExtentBottomRight;
		
		mExtentX = (pExtentTopLeft.x + pExtentTopRight.x + pExtentBottomLeft.x + pExtentBottomRight.x) / 4.0f;
		mExtentY = (pExtentTopLeft.y + pExtentTopRight.y + pExtentBottomLeft.y + pExtentBottomRight.y) / 4.0f;
    }
    
    /** Service routine to calculate an appropriate positional vector */
    @Override
    public Vector3f setPosition(
    	Vector3f	pResult
    ,	int			pFaceIndex
    ,	int			pWhichPoint
    ) {
		int vertexSelector = (pFaceIndex * 4) + pWhichPoint;			// 4 per face
		int whichVertex = sCanFaceVertices[ vertexSelector++ ];
		int index = whichVertex * 3;									// x/y/z per vertex
    	pResult.set( sVertices[ index++ ], sVertices[ index++ ], sVertices[ index++ ] );
    	
    	// Account for size
    	switch( whichVertex ) {
    	case 0: 		// right, bottom, back
        	pResult.multLocal( mExtentBottomRight.x, mExtentBottomRight.y, mExtentZ );
    		break;
    	case 1:			// left, bottom, back
        	pResult.multLocal( mExtentBottomLeft.x, mExtentBottomLeft.y, mExtentZ );
    		break;
    	case 2:			// left, top, back
        	pResult.multLocal( mExtentTopLeft.x, mExtentTopLeft.y, mExtentZ );
    		break;
    	case 3:			// right, top, back	
        	pResult.multLocal( mExtentTopRight.x, mExtentTopRight.y, mExtentZ );
    		break;
		
    	case 4:			// left, bottom, front
        	pResult.multLocal( mExtentBottomLeft.x, mExtentBottomLeft.y, mExtentZ );
    		break;
    	case 5:			// right, bottom, front	
        	pResult.multLocal( mExtentBottomRight.x, mExtentBottomRight.y, mExtentZ );
    		break;
    	case 6:			// right, top, front
        	pResult.multLocal( mExtentTopRight.x, mExtentTopRight.y, mExtentZ );
    		break;
    	case 7:			// left, top, front	
        	pResult.multLocal( mExtentTopLeft.x, mExtentTopLeft.y, mExtentZ );
    		break;
    	}
    	return( pResult );
    }
    
    /** Service routine to calculate an appropriate normal for a given face */
    @Override
    protected Vector3f setNormal(
    	Vector3f	pResult
    ,	int			pFaceIndex
    ,	float		pFlipNormal
    ,	Vector3f	pTemp1
    ,	Vector3f	pTemp2
    ) {
    	switch( sFaces[ pFaceIndex ] ) {
    	case FRONT:
    	case BACK:
    		// These are simple perpendiculars that match AxialBox
	    	pResult = super.setNormal( pResult, pFaceIndex, pFlipNormal, pTemp1, pTemp2 );
	        break;
	    default:
	    	// These are the sides, which must be calculated based on their slant
	    	pResult = super.setNormal( pResult, pFaceIndex, pFlipNormal, pTemp1, pTemp2 );
	    	
	    	// Three points define the plane
	    	pResult = setPosition( pResult, pFaceIndex, 0 );
	    	pTemp1 = setPosition( pTemp1, pFaceIndex, 1 );
	    	pTemp2 = setPosition( pTemp2, pFaceIndex, 2 );
	    	
	    	// Two lines in the plane
	    	pTemp1.subtractLocal( pResult );
	    	pTemp2.subtractLocal( pResult );
	    	
	    	// Cross product is perpendicular
	    	pResult = pTemp1.cross( pTemp2, pResult );
	    	pResult.normalizeLocal();
	    	pResult.multLocal( pFlipNormal );
	    	break;
    	}
        return pResult;
    }
    
    /** Service routine to calculate an appropriate texture */
    @Override
    protected Vector2f setTexture(
    	Vector2f	pResult
    ,	int			pFaceIndex
    ,	int			pWhichPoint
    ,	Vector2f	pBaseTex
    ,	Vector2f	pSpanTex
    ,	Vector3f	pPosition
    ) {
    	switch( sFaces[ pFaceIndex ] ) {
    	case FRONT:
    	case BACK:
    		// Front and Back are arbitrarily shaped and must account for each vertex
    		float leftMostX = Math.max( mExtentTopLeft.x, mExtentBottomLeft.x );
    		float rightMostX = Math.max( mExtentTopRight.x, mExtentBottomRight.x );
    		float topMostY = Math.max( mExtentTopLeft.y, mExtentTopRight.y );
    		float bottomMostY = Math.max( mExtentBottomLeft.y, mExtentBottomRight.y );
    		
    		float offsetX = pPosition.x + leftMostX;
    		float offsetY = pPosition.y + bottomMostY;
    		float texX = offsetX / (leftMostX + rightMostX);
    		float texY = offsetY / (topMostY + bottomMostY);
	    	pResult.set( pBaseTex.x + texX, pBaseTex.y + texY );
	        break;
	    default:
	    	// These are the sides, which are simple rectangles and follow AxialBox
	    	pResult = super.setTexture( pResult, pFaceIndex, pWhichPoint, pBaseTex, pSpanTex, pPosition );
	    	break;
    	}
        return pResult;	
    }

    /** Resolve the 'span' as applied to a texture on a side of the box */
    @Override
    protected void resolveTextureSpans(
    	Vector2f	pRightSpan
    ,	Vector2f	pLeftSpan
    ,	Vector2f	pTopSpan
    ,	Vector2f	pBottomSpan
    ) {
		// Account for span around the perimeter
    	// and we assume a full span (1.0) to cover the 'z' 
    	float topDistance = FastMath.sqrt( FastMath.sqr( mExtentTopLeft.x + mExtentTopRight.x )
										+	FastMath.sqr( mExtentTopLeft.y - mExtentTopRight.y ) );
    	float bottomDistance = FastMath.sqrt( FastMath.sqr( mExtentBottomLeft.x + mExtentBottomRight.x )
										+	FastMath.sqr( mExtentBottomLeft.y - mExtentBottomRight.y ) );
    	float leftDistance = FastMath.sqrt( FastMath.sqr( mExtentTopLeft.x - mExtentBottomLeft.x )
    									+ 	FastMath.sqr( mExtentTopLeft.y + mExtentBottomLeft.y ) );
    	float rightDistance = FastMath.sqrt( FastMath.sqr( mExtentTopRight.x - mExtentBottomRight.x )
										+ 	FastMath.sqr( mExtentTopRight.y + mExtentBottomRight.y ) );
		float perimeter = topDistance + bottomDistance + leftDistance + rightDistance;
		
		pRightSpan.set( rightDistance / perimeter, 1 );
		pLeftSpan.set( leftDistance / perimeter, 1 );
		pTopSpan.set( topDistance / perimeter, 1 );    	
		pBottomSpan.set( bottomDistance / perimeter, 1 );    	
    }
    
    /** Read the fundamental configuration parameters */
    @Override
    protected void readExtents(
    	JmeImporter		pImporter
    ,	InputCapsule	pInCapsule
    ) throws IOException {
        mExtentZ = pInCapsule.readFloat( "zExtent", 0 );
        if ( mExtentZ == 0 ) {
        	float aHeight = pInCapsule.readFloat( "height", 0 );
        	if ( aHeight > 0 ) {
        		// An explicit height sets the zExtent
        		mExtentZ = aHeight / 2.0f;
        	} else {
        		mExtentZ = 1.0f;
        	}
        }
        // Attempt to take the most generic specs possible
        Vector2f topLeft = (Vector2f)pInCapsule.readSavable( "topLeftExtent", null );
        Vector2f topRight = (Vector2f)pInCapsule.readSavable( "topRightExtent", null );
        Vector2f bottomLeft = (Vector2f)pInCapsule.readSavable( "bottomLeftExtent", null );
        Vector2f bottomRight = (Vector2f)pInCapsule.readSavable( "bottomRightExtent", null );
        
        float topExtent = pInCapsule.readFloat( "topExtent", 0 );
        float bottomExtent = pInCapsule.readFloat( "bottomExtent", 0 );
        float leftExtent = pInCapsule.readFloat( "leftExtent", 0 );
        float rightExtent = pInCapsule.readFloat( "rightExtent", 0 );
        
        float xExtent = pInCapsule.readFloat( "xExtent", 1.0f );
        float yExtent = pInCapsule.readFloat( "yExtent", 1.0f );
        
        if ( topLeft == null ) {
        	topLeft = new Vector2f( (topExtent == 0) ? xExtent : topExtent
        						,	(leftExtent == 0) ? yExtent : leftExtent );
        } else {
        	topLeft = topLeft.clone();
        }
        if ( topRight == null ) {
        	topRight = new Vector2f( (topExtent == 0) ? xExtent : topExtent
        						,	(rightExtent == 0) ? yExtent : rightExtent );
        } else {
        	topRight = topRight.clone();
        }
        if ( bottomLeft == null ) {
        	bottomLeft = new Vector2f( (bottomExtent == 0) ? xExtent : bottomExtent
        						,	(leftExtent == 0) ? yExtent : leftExtent );
        } else {
        	bottomLeft = bottomLeft.clone();
        }
        if ( bottomRight == null ) {
        	bottomRight = new Vector2f( (bottomExtent == 0) ? xExtent : bottomExtent
        						,	(rightExtent == 0) ? yExtent : rightExtent );
        } else {
        	bottomRight = bottomRight.clone();
        }
        setExtents( topLeft, topRight, bottomLeft, bottomRight );
    }
    
    /** Preserve this shape */
    @Override
    protected void writeExtents(
    	JmeExporter		pExporter
    ,	OutputCapsule	pOutCapsule
    ) throws IOException {
    	pOutCapsule.write( mExtentZ, "zExtent", 1 );
    	pOutCapsule.write( mExtentTopLeft, "topLeftExtent", null );
    	pOutCapsule.write( mExtentTopRight, "topRightExtent", null );
    	pOutCapsule.write( mExtentBottomLeft, "bottomLeftExtent", null );
    	pOutCapsule.write( mExtentBottomRight, "bottomRightExtent", null );
    }

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		return( CSGVersion.getVersion( this.getClass()
													, sCSGHexahedronRevision
													, sCSGHexahedronDate
													, pBuffer ) );
	}

}
