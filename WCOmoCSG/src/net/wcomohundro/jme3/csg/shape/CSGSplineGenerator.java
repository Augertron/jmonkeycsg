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
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.math.Spline;
import com.jme3.math.Spline.SplineType;

/** Define a helper class that can construct a Spline for us, especially from .xml Savable files.
 	It also understands how to interact with a Spline to produce a set of points along the given 
 	spline.
 */
public class CSGSplineGenerator
	implements Savable
{
	/** The Spline */
	protected Spline			mSpline;
	/** Explicit set of curve points (rather than working off of a spline) */
	protected List<Vector3f>	mPointList;
	/** The lengths of the various segments */
	protected List<Float>		mSegmentLengths;

	
	
	/** Null constructor */
	public CSGSplineGenerator(
	) {
	}
	
	/** Constructor based on a given spline */
	public CSGSplineGenerator(
		Spline		pSpline
	) {
		mSpline = pSpline;
	}
	
	/** Constructor based on an explicit set of points */
	public CSGSplineGenerator(
		List<Vector3f>	pPointList
	) {
		setPointList( pPointList );
	}
	public CSGSplineGenerator(
		Vector3f[]		pPointList
	) {
		setPointList( pPointList );
	}
	
	/** Accessor to the spline */
	public Spline getSpline() { return mSpline; }
	public void setSpline( 
		Spline pSpline 
	) { 
		mSpline = pSpline; 
		if ( mSpline != null ) {
			// There is no explicit point list, and the spline knows its segments
			mPointList = null;
			mSegmentLengths = mSpline.getSegmentsLength();
		}
	}
	
	/** Accessor to the point list */
	public List<Vector3f> getPointList() { return mPointList; }
	public void setPointList(
		List<Vector3f>	pPointList
	) {
		mPointList = pPointList;
		if ( mPointList != null ) {
			// There is no explicit spline
			mSpline = null;
			
			// Calculate the distances of all the segments
			mSegmentLengths = new ArrayList( mPointList.size() -1 );
			for( int i = 0, j = mPointList.size() -1; i < j; i += 1 ) {
				float aDistance = mPointList.get( i ).distance( mPointList.get( i + 1 ) );
				mSegmentLengths.add( aDistance );
			}
		}
	}
	public void setPointList(
		Vector3f[]		pPointList
	) {
		setPointList( Arrays.asList( pPointList ) );
	}
	
	/** How many segments are involved? */
	public List<Float> getSegmentLengths() { return mSegmentLengths; }
	public int getSegmentCount(
	) {
		return( mSegmentLengths.size() );
	}
	
	/** What is the length of all the segments */
	public float getTotalLength(
	) {
		float aDistance = 0.0f;
		for( Float aValue : mSegmentLengths ) {
			aDistance += aValue.floatValue();
		}
		return( aDistance );
	}
	
	/** Produce a set of points along the given curve 
	 	@return - a List of points along the curve with at least pSampleCount entries
	 				(possibly more if the curve demands it)
	 	NOTE
	 		that once upon a time, I had hoped that these would be evenly spaced, but
	 		the underlying Spline processing does not currently provide such a mechanism.
	 */
	public List<Vector3f> interpolate(
		int			pSampleCount
	) {
		if ( mSpline == null ) {
			if ( pSampleCount == mPointList.size() ) {
				// Just use the explicit list we have
				return( mPointList );
			}
			// Let a linear spline do the work for us
			mSpline = new Spline( SplineType.Linear, mPointList, 0, false );
			mSegmentLengths = mSpline.getSegmentsLength();
		}
		// The control points within the spline are defined differently for the different types
		List<Vector3f> pointList = new ArrayList( pSampleCount );
		
		int splinePointBias;
		switch( mSpline.getType() ) {
		case Bezier:
			// Bezier is of the form:  p0 h0 : h1 p1 h1 : h2 p2 h2 : ... : hN pN
			// I am still trying to work out a meaning of the handles, but it seems each
			// point has a left and right handle (except for the extremities)  
			// The following gives you a rather smooth (circular??) arc
			//		0.0 / 0.0 / 1.0,  0.0 / 0.0 / 0.4,  -0.4 / 0.0 / 0.0,  -1.0 / 0.0 / 0.0
			splinePointBias = 3;
			
			// FYI -- Spline.interpolate( percentToNextControlPoint, ... )
			//			does NOT give you evenly spaced points (in terms of straight line
			//			distance between the points). Maybe the distance along the curve is
			//			the same (I don't think so) but for sure, the direct point-to-point
			//			distance varies.  So we end up with a set of non-evenly spaced
			//			points.
			//	@see http://math.stackexchange.com/questions/15896/find-points-along-a-bezier-curve-that-are-equal-distance-from-one-another
			break;
			
		case Nurb:
			// 20Apr2015 - I do not understand the following (which I grabbed from Curve)
	        float minKnot = mSpline.getMinNurbKnot();
	        float maxKnot = mSpline.getMaxNurbKnot();
	        float deltaU = (maxKnot - minKnot) / pSampleCount;
	        splinePointBias = 1;
			break;
			
		case Linear:
			// Linear has a data point for each control point
			splinePointBias = 1;
			break;

		case CatmullRom:
			// CatmullRom has a data point for each control point
			// NOTE something I was not expecting for a simple CatmullRom spline:
			//		Consider a three point spline that we are trying to get to
			//		trace something like a quarter of a circle --
			//			0.0 / 0.0 / 1.0,  -0.2 / 0.0 / 0.2,  -1.0 / 0.0 / 0.0
			// 		You end up with a very simple spline with two segments.
			//		And if you 'interpolate' along the spline with a small number of 
			//		subsegments, you sort of get the arc you are expecting.
			//		But if you increase the count of subsegments, you do NOT get an
			//		even arc, but rather a bit of a snake that wavers in and out.
			//		I am assuming this is not a bug, but rather a limitation of 
			//		the CatmullRom algorithm.....
			splinePointBias = 1;
			break;
			
		default:
			splinePointBias = 1;
			break;
		}
    	int sampleCount = 0;
    	float fullLength = mSpline.getTotalLength();
    	int segmentCount = mSegmentLengths.size() -1;
    	
    	// We assume that there is one more 'control point' than there are segments,
    	// so the last center point is always special
    	for( int i = 0, pointIdx = 0; i <= segmentCount; i += 1, pointIdx += splinePointBias ) {
    		float thisSegmentPortion = mSegmentLengths.get( i ).floatValue() / fullLength;
    		int thisSegmentSamples = (int)(pSampleCount * thisSegmentPortion);
    		if ( thisSegmentSamples == 0 ) {
    			// Every segment gets at least one sample
    			thisSegmentSamples = 1;
    		}
    		// Accommodate the 'slop' in the last segment, always leaving out the last
    		// @todo - think about spreading the slop around
    		if ( i == segmentCount ) {
    			// Adjust the samples we take in the last segment
    			thisSegmentSamples = pSampleCount - sampleCount -1;
    			if ( thisSegmentSamples <= 0 ) thisSegmentSamples = 1;
    		}
    		sampleCount += thisSegmentSamples;

    		// Generate samples within this segment
    		for( int j = 0; j < thisSegmentSamples; j += 1 ) {
    			float percentWithinSegment = 0.0f;
    			if ( j > 0 ) percentWithinSegment = (float)j / (float)thisSegmentSamples;
    			Vector3f centerPoint = mSpline.interpolate( percentWithinSegment, pointIdx, null );
    			pointList.add( centerPoint );
    		}
    		if ( i == segmentCount ) {
    	    	// The last sample comes from the final control point (100% from the current to the next)
    			Vector3f centerPoint = mSpline.interpolate( 1.0f, pointIdx, null );
    			pointList.add( centerPoint );   			
    		}
    	}
    	return( pointList );
	}
	
	
	/** Implement minimal Savable */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        outCapsule.writeSavableArrayList( (ArrayList)mSpline.getControlPoints(), "controlPoints", null );
        outCapsule.write( mSpline.getType(), "type", SplineType.Linear );
        outCapsule.write( mSpline.getCurveTension(), "curveTension", 0.5f );
        outCapsule.write( mSpline.isCycle(), "cycle", false );
    }

    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule in = pImporter.getCapsule(this);

        List<Vector3f> controlPoints = (ArrayList<Vector3f>)in.readSavableArrayList( "controlPoints", null );
        SplineType aType = in.readEnum( "type", SplineType.class, SplineType.Linear );
        float curveTension = in.readFloat ("curveTension", 0.5f );
        boolean doCycle = in.readBoolean( "cycle", false );
        
        // Build the spline
        if ( aType == SplineType.Linear ) {
        	// A spline is not needed for linear, all we want is the given set of points
        	setPointList( controlPoints );
        } else {
        	// Use the given spline
        	setSpline( new Spline( aType, controlPoints, curveTension, doCycle ) );
        }
    }

}
