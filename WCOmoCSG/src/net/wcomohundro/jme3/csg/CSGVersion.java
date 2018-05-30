/** Copyright (c) 2011 Evan Wallace (http://madebyevan.com/)
 	Copyright (c) 2003-2014 jMonkeyEngine
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
	
	Logic and Inspiration taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.csg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.wcomohundro.jme3.csg.bsp.CSGPartition;
import net.wcomohundro.jme3.csg.math.CSGPlaneFlt;
import net.wcomohundro.jme3.csg.math.CSGPolygonFlt;
import net.wcomohundro.jme3.csg.math.CSGVertexFlt;
import net.wcomohundro.jme3.csg.shape.*;
import net.wcomohundro.jme3.math.Vector3d;

import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

/** Report on the Versions of the CSG source code
 	NOTE
 		Once upon a time, I implemented CSG using Java 1.8 facilities, particularly static
 		methods on an interface.  The code below was supported directly within the
 		ConstructiveSolidGeometry interface.
 		
 		I have decided to retrofit back to 1.6/1.7 to be more in line with jMonkey, so 
 		I have extracted the version reporting processing here.
*/
public class CSGVersion 
{
	
    /** Service routine to set a rotation based on two vectors 
	 	
	 	I am keeping this around strictly for historical documentation. It may be useful
	 	again sometime in the future.  In any case, watch out for 0 and 180 degree rotation
	 */
	public static Quaternion computeQuaternion(
		Vector3f	pNormalU
	,	Vector3f	pNormalV
	) {
		Quaternion aRotation;
if ( true ) {
		aRotation = new Quaternion();
		
		// Since we are working on 'normals', we know that the 'dot' will be 
		// in the range -1 : +1, so arccosine will operate properly
	    float cos_theta = pNormalU.dot( pNormalV );
	    float anAngle = FastMath.acos( cos_theta );
	
		Vector3f anAxis = pNormalU.cross( pNormalV ).normalizeLocal();
		aRotation.fromAngleNormalAxis( anAngle, anAxis );
}   	    
/** FROM   http://lolengine.net/blog/2013/09/18/beautiful-maths-quaternion-from-vectors
	float cos_theta = dot(normalize(u), normalize(v));
	float angle = acos(cos_theta);
	vec3 w = normalize(cross(u, v));
	return quat::fromaxisangle(angle, w);
*/
		    
if ( false ) {	// Looks like the following gets you the same results as above with fewer calculations
				// BUT YOU MUST DEAL WITH THE 180 degree PROBLEM (which occurs on 3/4 circle)
		float real_part = 1.0f + pNormalU.dot( pNormalV );
		Vector3f aVector = pNormalU.cross( pNormalV );
		aRotation = new Quaternion( aVector.x, aVector.y, aVector.z, real_part );
		aRotation.normalizeLocal();
}
/** FROM   http://lolengine.net/blog/2014/02/24/quaternion-from-two-vectors-finalqa
		// If you know you are exclusively dealing with unit vectors, you can replace all 
		// occurrences of norm_u_norm_v with the value 1.0f in order to avoid a useless square root.
	float norm_u_norm_v = sqrt(dot(u, u) * dot(v, v));
	float real_part = norm_u_norm_v + dot(u, v);
	vec3 w;
	
	if (real_part < 1.e-6f * norm_u_norm_v)
	{
	    // If u and v are exactly opposite, rotate 180 degrees
	    // around an arbitrary orthogonal axis. Axis normalisation
	    // can happen later, when we normalise the quaternion.
	    real_part = 0.0f;
	    w = abs(u.x) > abs(u.z) ? vec3(-u.y, u.x, 0.f)
	                            : vec3(0.f, -u.z, u.y);
	}
	else
	{
	    // Otherwise, build quaternion the standard way.
	    w = cross(u, v);
	}
	return normalize(quat(real_part, w.x, w.y, w.z));
**/
		return( aRotation );
	}
	
	/** Produce a report on what we know about the active environment */
	public static void reportVersion(
	) {
		reportVersion( CSGEnvironment.resolveEnvironment(), CSGEnvironment.sLogger );
	}
	public static void reportVersion(
		CSGEnvironment	pEnvironment
	,	Logger			pLogger
	) {
	    StringBuilder aBuffer = new StringBuilder( 1024 );
	    
	    getVersion( ConstructiveSolidGeometry.class, aBuffer );
	    
		(new CSGGeometry()).getVersion( aBuffer );
		(new CSGGeonode()).getVersion( aBuffer );
		(new CSGLinkNode()).getVersion( aBuffer );
		(new CSGPartition()).getVersion( aBuffer );
		(new CSGPlaneFlt()).getVersion( aBuffer );
		(new CSGPolygonFlt()).getVersion( aBuffer );
		(new CSGVertexFlt()).getVersion( aBuffer );
		
		(new CSGBox()).getVersion( aBuffer );
		(new CSGCylinder()).getVersion( aBuffer );
		(new CSGSphere()).getVersion( aBuffer );
		(new CSGPipe()).getVersion( aBuffer );
		(new CSGSplineGenerator()).getVersion( aBuffer );
		
		if ( pEnvironment != null ) {
			pEnvironment.getVersion( aBuffer );
		}
		pLogger.log( Level.CONFIG, aBuffer.toString() );
	}
	
	/** Service routine to report on the global CSG version */
	public static StringBuilder getVersion(
		Class			pCSGClass
	,	StringBuilder	pBuffer
	) {
		return( getVersion( pCSGClass
							, ConstructiveSolidGeometry.sCSGVersionMajor
							, ConstructiveSolidGeometry.sCSGVersionMinor
							, ConstructiveSolidGeometry.sCSGRevision
							, ConstructiveSolidGeometry.sCSGDate
							, pBuffer ) );
	}
	public static StringBuilder getVersion(
		Class			pCSGClass
	,	String			pRevision
	,	String			pDate
	,	StringBuilder	pBuffer
	) {
		return( getVersion( pCSGClass
							, ConstructiveSolidGeometry.sCSGVersionMajor
							, ConstructiveSolidGeometry.sCSGVersionMinor
							, pRevision
							, pDate
							, pBuffer ) );
	}

	public static StringBuilder getVersion(
		Class			pCSGClass
	,	int				pMajor
	,	int				pMinor
	,	String			pRevision
	,	String			pDate
	,	StringBuilder	pBuffer
	) {
		if ( pBuffer == null ) pBuffer = new StringBuilder( 256 );
		pBuffer.append( pCSGClass.getSimpleName() ).append( ": " );
		pBuffer.append( "v" ).append( pMajor ).append( "." ).append( pMinor );
		if ( pRevision != null ) pBuffer.append( ", rev: " ).append( pRevision );
		if ( pDate != null ) pBuffer.append( ", date: " ).append( pDate );
		pBuffer.append( "\n" );
		return( pBuffer );
	}
}
