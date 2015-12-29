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
	
	Logic and Inspiration taken from a java implementation of the IOB process 
		Danilo Balby Silva Castanheira (danbalby@yahoo.com)

**/
package net.wcomohundro.jme3.csg.iob;

import java.io.IOException;
import java.util.logging.Level;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.bsp.CSGShapeBSP;


/** An IOB specific implementation of the CSG Environment */
public class CSGEnvironmentIOB 
	extends CSGEnvironment<CSGShapeIOB>
{
	/** Define a 'tolerance' for when two items are so close, they are effectively the same */
	// Tolerance to decide if a given point in 'on' a plane
	public static final float EPSILON_ONPLANE_FLT = 1.0e-5f;
	public static final double EPSILON_ONPLANE_DBL = 1.0e-7;
	// Tolerance to determine if two points are close enough to be considered the same point
	public static final float EPSILON_BETWEEN_POINTS_FLT = 1.0e-5f;
	public static final double EPSILON_BETWEEN_POINTS_DBL = 1.0e-7;
	// Tolerance if a given value is near enough to zero to be treated as zero
	public static final float EPSILON_NEAR_ZERO_FLT = 1.0e-7f;
	public static final double EPSILON_NEAR_ZERO_DBL = 1.0e-10;
	
	// NOTE that '5E-15' may cause points on a plane to report problems.  In other words,
	//		when near_zero gets this small, the precision errors cause points on a plane to
	//		NOT look like they are on the plane, even when those points were used to create the
	//		plane.....
	
	
	/** Define a 'tolerance' for when two points are so far apart, it is ridiculous to consider it */
	public static final double EPSILON_BETWEEN_POINTS_MAX = 1e+3;
	
	
	/** Null constructor produces the 'standard' */
	public CSGEnvironmentIOB(
	) {
		super( true
		,	CSGShapeIOB.class
		,	EPSILON_NEAR_ZERO_DBL
		,	EPSILON_BETWEEN_POINTS_DBL
		,	EPSILON_ONPLANE_DBL
		,	EPSILON_NEAR_ZERO_FLT
		,	EPSILON_BETWEEN_POINTS_FLT
		,	EPSILON_ONPLANE_FLT );
	}
	
	/** Support the persistence of this Environment */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		super.write( pExporter );
		
		// Save the configuration options
		OutputCapsule aCapsule = pExporter.getCapsule( this );

		if ( mDoublePrecision ) {
			aCapsule.write( mEpsilonNearZeroDbl, "epsilonNearZero", EPSILON_NEAR_ZERO_DBL );
			aCapsule.write( mEpsilonBetweenPointsDbl, "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_DBL );
			aCapsule.write( mEpsilonOnPlaneDbl, "epsilonOnPlane", EPSILON_ONPLANE_DBL );
		} else {
			aCapsule.write( mEpsilonNearZeroFlt, "epsilonNearZero", EPSILON_NEAR_ZERO_FLT );
			aCapsule.write( mEpsilonBetweenPointsFlt, "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_FLT );
			aCapsule.write( mEpsilonOnPlaneFlt, "epsilonOnPlane", EPSILON_ONPLANE_FLT );
		}
	}
	
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		super.read( pImporter );
		
		InputCapsule aCapsule = pImporter.getCapsule( this );
		if ( mDoublePrecision ) {
			mEpsilonNearZeroDbl = aCapsule.readDouble( "epsilonNearZero", EPSILON_NEAR_ZERO_DBL );
			mEpsilonBetweenPointsDbl = aCapsule.readDouble( "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_DBL );
			mEpsilonOnPlaneDbl = aCapsule.readDouble( "epsilonOnPlane", EPSILON_ONPLANE_DBL );
		} else {
			mEpsilonNearZeroFlt = aCapsule.readFloat( "epsilonNearZero", EPSILON_NEAR_ZERO_FLT );
			mEpsilonBetweenPointsFlt = aCapsule.readFloat( "epsilonBetweenPoints", EPSILON_BETWEEN_POINTS_FLT );
			mEpsilonOnPlaneFlt = aCapsule.readFloat( "epsilonOnPlane", EPSILON_ONPLANE_FLT );
		}
	}


}
