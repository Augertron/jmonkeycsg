/** Copyright (c) 2018, WCOmohundro
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
package com.jme3.export.xml;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.ColorRGBA;

/** This is a simple Savable wrapper around a List, handy for
 	XML definitions that work on some collection of Savables.
 */
public class SavableList 
	extends ArrayList<Savable>
	implements Savable
{
	/** Null Constructor for Savable processing */
	public SavableList() {}
	
	/** Constructor based on a Collection */
	public SavableList(
		Collection	pElements
	) {
		super( pElements );
	}

	////////////////////////////////////////// Savable //////////////////////////////////////////////
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule outCapsule = pExporter.getCapsule( this );
		for( Object anItem : this ) {
			if ( anItem instanceof Savable ) {
				((Savable)anItem).write( pExporter );
			}
		}
	}

	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule inCapsule = pImporter.getCapsule( this );
		List contents = inCapsule.readSavableArrayList( null, null );
		if ( contents != null ) {
			this.addAll( contents );
		}
	}
}
