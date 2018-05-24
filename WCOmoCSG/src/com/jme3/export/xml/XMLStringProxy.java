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

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.ColorRGBA;

/** This is a simple Savable wrapper around a naked string, handy for
 	XML definitions that work on some collection of Savables.
 */
public class XMLStringProxy 
	implements Savable
{
	/** The actual string we stand for */
	protected String	mString;
	
	/** Null Constructor for Savable processing */
	public XMLStringProxy() {}
	
	/** Constructor based on a string */
	public XMLStringProxy(
		String		pString
	) {
		mString = pString;
	}

	/** Standard access to the string */
	@Override
	public String toString() { return mString; }
	
	
	/** Interpret a String as a corresponding color */
	public ColorRGBA asColor( 
	) {
		// Looks like ColorRGBA does NOT have a standard string interpreter
		// Accepting:
		//		#rgb  #rgba  #rrggbb  #rrggbbaa
		int red = 256, blue = 256, green = 256, alpha = 256;
		int length = (mString == null) ? 0 : mString.length();
		
		if ( (length >= 4) && ( mString.charAt( 0 ) == '#') ) try {
			if ( length > 6 ) {
				// Long mode only accepts double digits
				red = Integer.parseInt( mString.substring( 1, 3 ), 16 );
				green = Integer.parseInt( mString.substring( 3, 5 ), 16 );
				blue = Integer.parseInt( mString.substring( 5, 7 ), 16 );
				
				if ( length > 8 ) {
					alpha = Integer.parseInt( mString.substring( 7, 9 ), 16 );
				}
			} else {
				// Short mode only accepts single digits
				red = Integer.parseInt( mString.substring( 1, 2 ), 16 ) * 0x11;
				green = Integer.parseInt( mString.substring( 2, 3 ), 16 ) * 0x11;
				blue = Integer.parseInt( mString.substring( 3, 4 ), 16 ) * 0x11;
				
				if ( length > 4 ) {
					alpha = Integer.parseInt( mString.substring( 4, 5 ), 16 ) * 0x11;
				}
			}
		} catch( Exception ex ) {
			// Punt it
			return( null );
		} else {
			// Nothing we understand
			return( null );
		}
		ColorRGBA aColor = new ColorRGBA( red / 255f, green / 255f, blue / 255f, alpha / 255f );
		return( aColor );
	}

	////////////////////////////////////////// Savable //////////////////////////////////////////////
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule outCapsule = pExporter.getCapsule( this );
		outCapsule.write( mString, "value", null );
	}

	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
		InputCapsule inCapsule = pImporter.getCapsule( this );
		mString = inCapsule.readString( "value", null );
	}
}
