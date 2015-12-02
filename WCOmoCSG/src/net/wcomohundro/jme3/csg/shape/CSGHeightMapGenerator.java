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

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.math.Spline;
import com.jme3.math.Spline.SplineType;
import com.jme3.terrain.heightmap.FaultHeightMap;
import com.jme3.terrain.heightmap.FluidSimHeightMap;
import com.jme3.terrain.heightmap.HeightMap;
import com.jme3.terrain.heightmap.HillHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.terrain.heightmap.MidpointDisplacementHeightMap;
import com.jme3.terrain.heightmap.ParticleDepositionHeightMap;
import com.jme3.terrain.heightmap.RawHeightMap;

/** Define a helper class that can construct a HeightMap for us, especially from .xml Savable files.
 * 
 	The CSGHeightMapGenerator eliminates the need to alter the core jme3 HeightMap code to produce
 	itself from Savable settings.  Instead, this class produces the desired heights.
 	
 	Configuration Settings:
 		type - HeightMapType to be constructed
 		extent - size of the map (width/height along an edge)

 */
public class CSGHeightMapGenerator
	implements Savable, ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGHeightMapGeneratorRevision="$Rev: 97 $";
	public static final String sCSGHeightMapGeneratorDate="$Date: 2015-10-27 15:14:38 -0500 (Tue, 27 Oct 2015) $";

	/** The types we support */
	public enum HeightMapType {
		FAULT( FaultHeightMap.class )
	,	FLUID( FluidSimHeightMap.class )
	,	HILL( HillHeightMap.class )
	,	GRAYSCALE_IMAGE( ImageBasedHeightMap.class )
	,	DISPLACEMENT( MidpointDisplacementHeightMap.class )
	,	PARTICLE( ParticleDepositionHeightMap.class )
	,	RAW_IMAGE( RawHeightMap.class )
	;
		// The associated class
		private Class	mMapClass;
		private HeightMapType( Class pClass ) { mMapClass = pClass; }
	}
	
	/** Enumerated fault types */
	public enum FaultMapTypeShape {
		// The types
		STEP( FaultHeightMap.FAULTTYPE_STEP )
	,   LINEAR( FaultHeightMap.FAULTTYPE_LINEAR )
	,   COSINE( FaultHeightMap.FAULTTYPE_COSINE )
	,   SINE( FaultHeightMap.FAULTTYPE_SINE )
	
		// The shapes
	,   LINE( FaultHeightMap.FAULTSHAPE_LINE )
	,   CIRCLE( FaultHeightMap.FAULTSHAPE_CIRCLE )
	;
		// The associated class
		private int		mFaultTypeValue;
		private FaultMapTypeShape( int pValue ) { mFaultTypeValue = pValue; }
		
		public int getValue() { return mFaultTypeValue; }
	}

	/** The type of height map to produce */
	protected HeightMapType		mMapType;
	/** The HeightMap */
	protected HeightMap			mHeightMap;


	
	/** Null constructor */
	public CSGHeightMapGenerator(
	) {
	}
	
	/** Accessor to the height map */
	public HeightMap getHeightMap() { return mHeightMap; }
	
	
	/** Implement minimal Savable */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule outCapsule = pExporter.getCapsule( this );
        
        outCapsule.write( mMapType, "type", HeightMapType.HILL );
    }

    @Override
    public void read(
    	JmeImporter		pImporter
    ) throws IOException {
        InputCapsule in = pImporter.getCapsule(this);
        
        mMapType = in.readEnum( "type", HeightMapType.class, HeightMapType.HILL );
    	int extent = in.readInt( "size", 257 );
    	int iterations = in.readInt( "iterations", 100 );
    	long seed = in.readLong( "seed", 0 );
    	if ( seed == 0 ) seed = new Random().nextLong();
    	
        try { switch( mMapType ) {
        case HILL:
        	float minRadius = in.readFloat( "minRadius", 10.0f );
        	float maxRadius = in.readFloat( "maxRadius", 25.0f );
        	mHeightMap = new HillHeightMap( extent, iterations, minRadius, maxRadius, seed );
        	
        	break;
        	
        case FAULT:
        	FaultMapTypeShape faultType 
        		= in.readEnum( "faultType", FaultMapTypeShape.class, FaultMapTypeShape.STEP );
        	FaultMapTypeShape faultShape
    			= in.readEnum( "faultShape", FaultMapTypeShape.class, FaultMapTypeShape.LINE );
        	float minFaultHeight = in.readFloat( "minHeight", 10.0f );
        	float maxFaultHeight = in.readFloat( "maxHeight", 25.0f );
        	mHeightMap = new FaultHeightMap( extent
        									,	iterations
        									,	faultType.getValue()
        									, 	faultShape.getValue()
        									, 	minFaultHeight
        									, 	maxFaultHeight
        									, 	seed );
        	break;
        	
        case FLUID:
        	float minInitialHeight = in.readFloat( "minInitialHeight", -100.0f );
        	float maxInitialHeight = in.readFloat( "maxHeight", 100.0f );
        	float viscosity = in.readFloat( "viscosity", 100.0f );
        	float waveSpeed = in.readFloat( "waveSpeed", 100.0f );
        	float timeStep = in.readFloat( "timeStep", 0.033f );
        	float nodeDistance = in.readFloat( "nodeDistance", 10.0f );
        	mHeightMap = new FluidSimHeightMap( extent
        									,	iterations
        									, 	minInitialHeight
        									, 	maxInitialHeight
        									,	viscosity
        									,	waveSpeed
        									,	timeStep
        									,	nodeDistance
        									, 	seed );
        	break;
        	
        case DISPLACEMENT:
        	float range = in.readFloat( "range", 1.0f );
        	float persistence = in.readFloat( "persistence", 0.5f );
        	mHeightMap = new MidpointDisplacementHeightMap( extent, range, persistence, seed );
        	
        	break;

        case PARTICLE:
        	int jumps = in.readInt( "jumps", 50 );
        	int peakWalk = in.readInt( "peakWalk", 5 );
        	int minParticles = in.readInt( "minParticles", 10 );
        	int maxParticles = in.readInt( "maxParticles", 25 );
        	float caldera = in.readFloat( "caldera", 0.5f );
        	mHeightMap = new ParticleDepositionHeightMap( extent
        									, jumps
        									, peakWalk
        									, minParticles
        									, maxParticles
        									, caldera );
        	break;
        	
        default:
        	throw new IllegalArgumentException( "Not yet supported: " + mMapType );
        	
        } } catch( Exception ex ) {
        	throw new IOException( "HeightMap generation failed", ex );
        }
        if ( mHeightMap != null ) {
        	float scale = in.readFloat( "scale", Float.NaN );
        	if ( !Float.isNaN( scale ) ) {
        		mHeightMap.setHeightScale( scale );
        	}        	
        }
    }

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( CSGVersion.getVersion( this.getClass()
													, sCSGHeightMapGeneratorRevision
													, sCSGHeightMapGeneratorDate
													, pBuffer ) );
	}

}
