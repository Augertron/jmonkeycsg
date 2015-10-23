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
package net.wcomohundro.jme3.csg.test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.renderer.Camera;

import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

public class CSGTestDriver 
{
	public static void main(
		String[] args
	) {
		System.out.println( "logging config: " 
					+ System.getProperty( "java.util.logging.config.file" )
					+ "/" + System.getProperty( "java.util.logging.config.class" ) );
		CSGVersion.reportVersion();
		
	    SimpleApplication app;
	    
	    //app = new CSGTestA();		// Cube +/- sphere direct calls, no import
	    //app = new CSGTestB();		// Cube +/- cylinder direct calls, no import
	    //app = new CSGTestC();		// Teapot +/- cube direct calls, no import
	    //app = new CSGTestD();		// Export definitions
	    app = new CSGTestE();		// Cycle through imports listed in CSGTestE.txt
	    //app = new CSGTestF();		// Cycle through canned import list and apply physics
	    //app = new CSGTestG();		// Level Of Detail
	    //app = new CSGTestH();		// Test case for support ticket and raw shapes
	        
	    app.start();
	}

	/** Service routine to display a bit of text */
	public static BitmapText defineTextDisplay(
		SimpleApplication	pApplication
	,	BitmapFont 			pGuiFont
	) {
		BitmapText textDisplay = new BitmapText( pGuiFont, false );
    	textDisplay.setSize( pGuiFont.getCharSet().getRenderedSize() );

    	pApplication.getGuiNode().attachChild( textDisplay );
    	return( textDisplay );
	}
	
	/** Service routine to post a bit of text */
    public static void postText(
    	SimpleApplication	pApplication
    ,	BitmapText			pTextDisplay
    ,	String				pMessage
    ) {
        pTextDisplay.setText( pMessage );
        
        Camera aCam = pApplication.getCamera();
        pTextDisplay.setLocalTranslation( (aCam.getWidth() - pTextDisplay.getLineWidth()) / 2, aCam.getHeight(), 0);

        pTextDisplay.render( pApplication.getRenderManager(), null );
    }

    /** Service routine to fill a list of strings */
    public static List<String> readStrings(
    	String			pFileName
    ,	List<String>	pSeedValues
    ,	String[]		pDefaultValues
    ) {
    	if ( pSeedValues == null ) pSeedValues = new ArrayList();
    	int initialCount = pSeedValues.size();
    	
		// Look for a given list
		File aFile = new File( pFileName );
		if ( aFile.exists() ) {
			LineNumberReader aReader = null;
			try {
				aReader = new LineNumberReader( new FileReader( aFile ) );
				String aLine;
				while( (aLine = aReader.readLine()) != null ) {
					// Remember this line
					aLine = aLine.trim();
					
					if ( (aLine.length() > 0) && !aLine.startsWith( "//" ) ) {
						pSeedValues.add( aLine );
					}
				}
			} catch( IOException ex ) {
				System.out.println( "*** Initialization failed: " + ex );
			} finally {
				if ( aReader != null ) try { aReader.close(); } catch( Exception ignore ) {}
			}
		}
		// If nothing was loaded, then revert back to canned list
		if ( (pSeedValues.size() == initialCount) && (pDefaultValues != null) ) {
			Collections.addAll( pSeedValues, pDefaultValues );
		}
    	return( pSeedValues );
    }
}
