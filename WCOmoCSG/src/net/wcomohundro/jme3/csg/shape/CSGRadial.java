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

import com.jme3.export.InputCapsule;
import com.jme3.export.OutputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.JmeExporter;
import com.jme3.export.Savable;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector2f;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.lang.ref.WeakReference;

import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;


/** Extend on the idea of producing a shape from a set of slices taken along the zAxis
 	by assuming some number of 'radial' points around the surface of each slice.
 	The basic assumption is that every slice is a circular disk of a given radius.
 	
 	Basic circular slices can be readily created by knowing the sine/cosine of each
 	radial sample point.  We maintain a cache of such values based on the count of 
 	radial points desired.  The assumption is that a given application is very likely to settle
 	on an appropriate count of radial samples that will be used over and over.
 */
public abstract class CSGRadial 
	extends CSGAxial
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGRadialRevision="$Rev$";
	public static final String sCSGRadialDate="$Date$";
	
	/** Cache of radial coordinates, keyed by the radial sample count */
	protected static Map<Integer,WeakReference<CSGRadialCoord[]>> sRadialCoordCache = new HashMap( 17 );
	protected static CSGRadialCoord[] getRadialCoordinates(
		int		pRadialSamples
	) {
		// Look in the cache
		Integer coordKey = new Integer( pRadialSamples );
		WeakReference<CSGRadialCoord[]> aReference = sRadialCoordCache.get( coordKey );
		CSGRadialCoord[] coordList = (aReference == null) ? null : aReference.get();
		if ( coordList == null ) synchronized( sRadialCoordCache ) {
			// No in the cache, so create it now 
			// (and it is not worth the effort to check the cache again once synchronized)
	        float inverseRadialSamples = 1.0f / pRadialSamples;
			coordList = new CSGRadialCoord[ pRadialSamples + 1 ];
			
			// Generate the coordinate values for each radial point
	        for( int iRadial = 0; iRadial < pRadialSamples; iRadial += 1 ) {
	            float anAngle = FastMath.TWO_PI * inverseRadialSamples * iRadial;
	            coordList[ iRadial ] = new CSGRadialCoord( anAngle );
	        }
	        // Include an extra point at the end that matches the starting point
	        coordList[ pRadialSamples ] = coordList[ 0 ];

			sRadialCoordCache.put( coordKey, new WeakReference( coordList ) );
		}
		return( coordList );
	}

	/** How many samples to take take around the surface of each slice */
    protected int 			mRadialSamples;

    /** The base radius of the surface (which may well match the zExtent) */
    protected float 		mRadius;
    /** The eccentricity (where 0 is a circle) */
    protected float			mEccentricity;

    /** If inverted, then the cylinder is intended to be viewed from the inside */
    protected boolean 		mInverted;
	
	public CSGRadial(
	) {
		this( 32, 32, 1, false );
	}
	
    public CSGRadial(
    	int 		pAxisSamples
    , 	int 		pRadialSamples
    ,	float 		pRadius
    ,	boolean		pInverted
    ) {
    	// By default, treat the radius and zExtent the same
    	super( pAxisSamples, pRadius );
        mRadialSamples = pRadialSamples;
        mRadius = pRadius;
        mInverted = pInverted;
    }

    /** Configuration accessors */
    public int getRadialSamples() { return mRadialSamples; }
    public float getRadius() { return mRadius; }
    public boolean isInverted() { return mInverted; }

    /** Support texture scaling */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
    	super.write( pExporter );
    	
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.write( mRadialSamples, "radialSamples", 32 );
        outCapsule.write( mRadius, "radius", 1 );
        outCapsule.write( mEccentricity, "eccentricity", 0 );
        outCapsule.write( mInverted, "inverted", false );
    }
    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule inCapsule = pImporter.getCapsule( this );
        
        mRadialSamples = inCapsule.readInt( "radialSamples", 32 );
        mRadius = inCapsule.readFloat( "radius", 1 );
        mEccentricity = inCapsule.readFloat( "eccentricity", 0 );
        mInverted = inCapsule.readBoolean( "inverted", false );

        // Let the super do its thing (which will updateGeometry as needed)
        super.read( pImporter );
    }
       
	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		pBuffer = super.getVersion( pBuffer );
		if ( pBuffer.length() > 0 ) pBuffer.append( "\n" );
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGRadialRevision
													, sCSGRadialDate
													, pBuffer ) );
	}
}

/** Helper class for sine/cosine values of a unit circle for a given 'radial' point */
class CSGRadialCoord
{
	/** Basic values */
	float		mSine;
	float		mCosine;
	
	/** Standard constructor */
	CSGRadialCoord(
		float	pSine
	,	float	pCosine
	) {
		mSine = pSine;
		mCosine = pCosine;
	}
	/** Construct based on an angle */
	CSGRadialCoord(
		float	pAngle
	) {
		mCosine = FastMath.cos( pAngle );
        mSine = FastMath.sin( pAngle );
	}
}
