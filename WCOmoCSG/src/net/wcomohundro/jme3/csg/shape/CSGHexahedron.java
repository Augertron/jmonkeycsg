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
 	to a CSGAxialBox.  Otherwise, the X extent is the average of top and bottom, and Y
 	extent is the average of right and left.
 	
 	This results in a deformed 'cube' with rectangularly shaped sides, but with faces
 	that are any four sided figure.

 */
public class CSGHexahedron 
	extends CSGAxialBox
{
	/** Version tracking support */
	public static final String sCSGHexahedronRevision="$Rev: $";
	public static final String sCSGHexahedronDate="$Date: $";

	/** The size of the box (remember to *2 for full width/height/depth) */
    protected float mExtentTop, mExtentBottom, mExtentLeft, mExtentRight;
    
    
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
		
		mExtentLeft = pExtentLeft;
		mExtentRight = pExtentRight;
		mExtentTop = pExtentTop;
		mExtentBottom = pExtentBottom;
		
        if ( pReadyGeometry ) {
    		this.updateGeometry();
        }
	}
	
	/** Accessors to the extents */
	@Override
    public void setXExtent( float pExtent ) { mExtentX = mExtentTop = mExtentBottom = pExtent; }
    @Override
    public void setYExtent( float pExtent ) { mExtentY = mExtentLeft = mExtentRight = pExtent; }

    public float getLeftExtent() { return mExtentLeft; }
    public void setLeftExtent( float pExtent ) { mExtentLeft = pExtent; }
    
    public float getRightExtent() { return mExtentRight; }
    public void setRightExtent( float pExtent ) { mExtentRight = pExtent; }

    public float getTopExtent() { return mExtentTop; }
    public void setTopExtent( float pExtent ) { mExtentTop = pExtent; }

    public float getBottomExtent() { return mExtentBottom; }
    public void setBottomExtent( float pExtent ) { mExtentBottom = pExtent; }

    @Override
    public float getRadius() { return( (mExtentTop + mExtentBottom + mExtentLeft + mExtentRight) / 4.0f ); }
    @Override
    public void setRadius( 
    	float pRadius 
    ) {
    	mExtentX = mExtentTop = mExtentBottom = pRadius;
    	mExtentY = mExtentLeft = mExtentRight = pRadius;
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
        	pResult.multLocal( mExtentBottom, mExtentRight, mExtentZ );
    		break;
    	case 1:			// left, bottom, back
        	pResult.multLocal( mExtentBottom, mExtentLeft, mExtentZ );
    		break;
    	case 2:			// left, top, back
        	pResult.multLocal( mExtentTop, mExtentLeft, mExtentZ );
    		break;
    	case 3:			// right, top, back	
        	pResult.multLocal( mExtentTop, mExtentRight, mExtentZ );
    		break;
		
    	case 4:			// left, bottom, front
        	pResult.multLocal( mExtentBottom, mExtentLeft, mExtentZ );
    		break;
    	case 5:			// right, bottom, front	
        	pResult.multLocal( mExtentBottom, mExtentRight, mExtentZ );
    		break;
    	case 6:			// right, top, front
        	pResult.multLocal( mExtentTop, mExtentRight, mExtentZ );
    		break;
    	case 7:			// left, top, front	
        	pResult.multLocal( mExtentTop, mExtentLeft, mExtentZ );
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
    		float maxX = (mExtentTop > mExtentBottom) ? mExtentTop : mExtentBottom;
    		float maxY = (mExtentLeft > mExtentRight) ? mExtentLeft : mExtentRight;
    		
    		float offsetX = pPosition.x + maxX;
    		float offsetY = pPosition.y + maxY;
    		float texX = offsetX / (2 * maxX);
    		float texY = offsetY / (2 * maxY);
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
		float perimeter = (2 * mExtentRight) + (2 * mExtentLeft) + (2 * mExtentTop) + (2 * mExtentBottom);
		pRightSpan.set( (mExtentRight * 2) / perimeter, 1 );
		pLeftSpan.set( (mExtentLeft * 2) / perimeter, 1 );
		
		pTopSpan.set( (mExtentTop * 2) / perimeter, 1 );    	
		pBottomSpan.set( (mExtentBottom * 2) / perimeter, 1 );    	
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
        mExtentTop = pInCapsule.readFloat( "topExtent", 1 );
        mExtentBottom = pInCapsule.readFloat( "bottomExtent", 1 );
        mExtentLeft = pInCapsule.readFloat( "leftExtent", 1 );
        mExtentRight = pInCapsule.readFloat( "rightExtent", 1 );
        
        mExtentX = (mExtentTop + mExtentBottom) / 2.0f;
        mExtentY = (mExtentLeft + mExtentRight) / 2.0f;
    }
    
    /** Preserve this shape */
    @Override
    protected void writeExtents(
    	JmeExporter		pExporter
    ,	OutputCapsule	pOutCapsule
    ) throws IOException {
    	pOutCapsule.write( mExtentZ, "zExtent", 1 );
    	pOutCapsule.write( mExtentTop, "topExtent", 1 );
    	pOutCapsule.write( mExtentBottom, "bottomExtent", 1 );
    	pOutCapsule.write( mExtentLeft, "leftExtent", 1 );
    	pOutCapsule.write( mExtentRight, "rightExtent", 1 );
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
