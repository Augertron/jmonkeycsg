/** Copyright (c) ????, Danilo Balby Silva Castanheira (danbalby@yahoo.com)
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
	
	Logic and Inspiration taken from:
		D. H. Laidlaw, W. B. Trumbore, and J. F. Hughes.  
 		"Constructive Solid Geometry for Polyhedral Objects" 
 		SIGGRAPH Proceedings, 1986, p.161. 
**/
package net.wcomohundro.jme3.csg.iob;

import java.util.List;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVertexDbl;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.csg.CSGVertex;

import com.jme3.scene.plugins.blender.math.Vector3d;


/** Simple 'volume' extent applied to a face or complete solid */
public class CSGBounds 
	implements ConstructiveSolidGeometry
{
	/** Version tracking support */
	public static final String sCSGBoundsRevision="$Rev$";
	public static final String sCSGBoundsDate="$Date$";

	//private static final double TOL = 1e-10f;

	/** Extent of the bounding volume */
	protected double	minX, maxX, minY, maxY, minZ, maxZ;
	
	/** Null constructor */
	public CSGBounds(
	) {
		minX = minY = minZ = Double.MAX_VALUE;
		maxX = maxY = maxZ = Double.MIN_VALUE;
	}
	/** Basic constructor based on a given face */
	public CSGBounds(
		CSGFace		pFace
	) {
		this();
		for( CSGVertex aVertex : pFace.getVertices() ) {
			Vector3d aPoint = ((CSGVertexIOB)aVertex).getPosition();
			if ( aPoint.x < minX ) minX = aPoint.x;
			if ( aPoint.x > maxX ) maxX = aPoint.x;
			if ( aPoint.y < minY ) minY = aPoint.y;
			if ( aPoint.y > maxY ) maxY = aPoint.y;
			if ( aPoint.z < minZ ) minZ = aPoint.z;
			if ( aPoint.z > maxZ ) maxZ = aPoint.z;
		}
	}
	/** Basic constructor based on a set of faces */
	public CSGBounds(
		List<CSGFace>	pFaceList
	) {
		this();
		for( CSGFace aFace : pFaceList ) {
			for( CSGVertex aVertex : aFace.getVertices() ) {
				Vector3d aPoint = ((CSGVertexIOB)aVertex).getPosition();
				if ( aPoint.x < minX ) minX = aPoint.x;
				if ( aPoint.x > maxX ) maxX = aPoint.x;
				if ( aPoint.y < minY ) minY = aPoint.y;
				if ( aPoint.y > maxY ) maxY = aPoint.y;
				if ( aPoint.z < minZ ) minZ = aPoint.z;
				if ( aPoint.z > maxZ ) maxZ = aPoint.z;
			}
		}
	}
	
	/** Check for overlap with another set of bounds */
	public boolean overlap(
		CSGBounds		pOther
	,	CSGEnvironment	pEnvironment
	) {
		double tolerance = pEnvironment.mEpsilonNearZeroDbl; // TOL;  
		if ( (this.minX > pOther.maxX + tolerance)
		|| (this.maxX < pOther.minX - tolerance)
		|| (this.minY > pOther.maxY + tolerance)
		|| (this.maxY < pOther.minY - tolerance)
		|| (this.minZ > pOther.maxZ + tolerance)
		|| (this.maxZ < pOther.minZ - tolerance) ) {
			// No possible overlap
			return( false );
		} else {
			// Some coordinate overlaps the range somewhere
			return( true );
		}
	}
	/** OVERRIDE: debug report */
	@Override
	public String toString(
	) {
		return( "x: " + minX + ":" + maxX 
				+ "\ty: " + minY + ":" + maxY 
				+ "\tz: "+ minZ + ":" + maxZ );
	}

	/////// Implement ConstructiveSolidGeometry
	@Override
	public StringBuilder getVersion(
		StringBuilder	pBuffer
	) {
		return( ConstructiveSolidGeometry.getVersion( this.getClass()
													, sCSGBoundsRevision
													, sCSGBoundsDate
													, pBuffer ) );
	}

}
