/** Copyright (c) 2003-2014 jMonkeyEngine
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
**/
package net.wcomohundro.jme3.math;

import java.io.IOException;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Quaternion;

import net.wcomohundro.jme3.csg.CSGEnvironment;


/** Quaternion is FINAL so we cannot subclass it.  But we can produce an equivalent that
    can generate a real Quaternion based on pitch/yawl/roll
 */
public class CSGQuaternion
	implements Savable
{
	/** The real Quaternion instance we are the proxy for */
	protected Quaternion	mQuaternion;
	
	/** Simple null constructor */
	public CSGQuaternion(
	) {
		mQuaternion = Quaternion.IDENTITY;
	}
	
	/** Accessor to the real Quaternion */
	public Quaternion getQuaternion() { return mQuaternion; }
	public void setQuaternion( Quaternion pQuaternion ) { mQuaternion = pQuaternion; }
	
	/** Make this proxy 'savable' */
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		
		// Rather than write a 'nested' Savable, just dupe what the real Quaternion does
		aCapsule.write( mQuaternion.getX(), "x", 0 );
		aCapsule.write( mQuaternion.getY(), "y", 0 );
		aCapsule.write( mQuaternion.getZ(), "z", 0 );
		aCapsule.write( mQuaternion.getW(), "w", 1 );		
	}

	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule aCapsule = pImporter.getCapsule(this);
		
		// Look for pitch/yawl/roll
		float pitch = CSGEnvironment.readPiValue( aCapsule, "pitch", Float.NaN );
		float yawl = CSGEnvironment.readPiValue( aCapsule, "yawl", Float.NaN );
		float roll = CSGEnvironment.readPiValue( aCapsule, "roll", Float.NaN );
		if ( Float.isNaN( pitch ) && Float.isNaN( yawl ) && Float.isNaN( roll ) ) {
			// NO pitch/yawl/roll
			float x = aCapsule.readFloat( "x", 0 );
	        float y = aCapsule.readFloat( "y", 0 );
	        float z = aCapsule.readFloat( "z", 0 );
	        float w = aCapsule.readFloat( "w", 1 );
	        mQuaternion = new Quaternion( x, y, z, w );
		} else {
			// Build from p/y/r
			mQuaternion = new Quaternion();
			if ( Float.isNaN( pitch ) ) pitch = 0.0f;
			if ( Float.isNaN( yawl ) ) yawl = 0.0f;
			if ( Float.isNaN( roll ) ) roll = 0.0f;
			mQuaternion.fromAngles( pitch, yawl, roll );
		}
	}
	
	/** Treat as .equals if the underlying Quaternion is equal */
	@Override
	public boolean equals(
		Object		pOther
	) {
		if ( pOther instanceof CSGQuaternion ) {
			return( this.mQuaternion.equals( ((CSGQuaternion)pOther).mQuaternion ) );
		} else if ( pOther instanceof Quaternion ) {
			return( this.mQuaternion.equals( pOther ) );
		} else {
			// Let the super deal with it
			return( super.equals( pOther ) );
		}
	}
}
