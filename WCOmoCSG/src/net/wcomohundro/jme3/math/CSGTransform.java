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
	
	Logic and Inspiration taken from https://github.com/andychase/fabian-csg 
	and http://hub.jmonkeyengine.org/users/fabsterpal, which apparently was taken from 
	https://github.com/evanw/csg.js
**/
package net.wcomohundro.jme3.math;

import java.io.IOException;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

import net.wcomohundro.jme3.csg.CSGEnvironment;

/** The core jme3 Transform class is FINAL and cannot be subclassed.  However, we can
 	provide a proxy that knows how to produces a real Transform from custom components
 */
public class CSGTransform 
	implements Savable
{
	/** The real transform that we a proxy for */
	protected Transform		mTransform;
	
	/** Simple null constructor */
	public CSGTransform(
	) {
		mTransform = Transform.IDENTITY;
	}
	
	/** Accessor to the real transform */
	public Transform getTransform() { return mTransform; }
	public void setTransform( Transform pTransform ) { mTransform = pTransform; }
	
	/** Make it savable */
    @Override
    public void write(
    	JmeExporter		pExporter
    ) throws IOException {
        OutputCapsule aCapsule = pExporter.getCapsule( this );
        
		// Rather than write a 'nested' Savable, just dupe what the real Transform does
        aCapsule.write( mTransform.getRotation(), "rot", Quaternion.IDENTITY );
        aCapsule.write( mTransform.getTranslation(), "translation", Vector3f.ZERO );
        aCapsule.write( mTransform.getScale(), "scale", Vector3f.UNIT_XYZ );
    }

    @Override
    public void read(
    	JmeImporter 	pExporter
    ) throws IOException {
        InputCapsule aCapsule = pExporter.getCapsule( this );
        
        Object aRotation = aCapsule.readSavable( "rot", null );
        if ( aRotation == null ) {
        	aRotation = Quaternion.IDENTITY;
        } else if ( aRotation instanceof CSGQuaternion ) {
        	aRotation = ((CSGQuaternion)aRotation).getQuaternion();
        }
        Vector3f aTranslation = (Vector3f)aCapsule.readSavable( "translation", Vector3f.ZERO );
        Vector3f aScale = (Vector3f)aCapsule.readSavable( "scale", Vector3f.UNIT_XYZ );
        
        mTransform = new Transform( aTranslation, (Quaternion)aRotation, aScale );
    }

}
