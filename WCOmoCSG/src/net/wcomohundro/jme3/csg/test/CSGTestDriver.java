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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.jme3.app.SettingsDialog;
import com.jme3.app.SimpleApplication;
import com.jme3.app.SettingsDialog.SelectionListener;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.NonCachingKey;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.renderer.Camera;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;
import net.wcomohundro.jme3.math.Vector3d;

/** A simple application driver that can post a dialog */
public class CSGTestDriver 
	extends CSGTestDriverBase
{
	public static void main(
		String[] args
	) {
        CSGTestDriverAppItem[] appChoices = new CSGTestDriverAppItem[] {
        	new CSGTestDriverAppItem( "Shapes", CSGTestE.class, "Tests/CSGTestShapes.xml" )
        ,	new CSGTestDriverAppItem( "LevelOfDetail", CSGTestG.class, "Models/CSGLoadLOD.xml" )
        ,	new CSGTestDriverAppItem( "Blends", CSGTestE.class, "Tests/CSGTestSuite.xml" )
        ,	new CSGTestDriverAppItem( "Physics", CSGTestF.class, "Tests/CSGPhysics.xml" )
        ,	new CSGTestDriverAppItem( "Floors", CSGTestE.class, "Models/CSGLoadFloorBumpySurface.xml" )
        ,	new CSGTestDriverAppItem( "Animation", CSGTestJ.class )
        ,	new CSGTestDriverAppItem( "Progressive (mouse/cheese)", CSGTestM.class )
        ,	new CSGTestDriverAppItem( "Progressive (+/- cylinder)", CSGTestL.class )
        };
        CSGTestDriverBase.main( appChoices, "CSGTestDriver" );
	    
	    //app = new CSGTestA();			// Cube +/- sphere direct calls, no import
	    //app = new CSGTestB();			// Cube +/- cylinder direct calls, no import
	    //app = new CSGTestC();			// Teapot +/- cube direct calls, no import
	    //app = new CSGTestD();			// Export definitions
	    //app = new CSGTestE( args );		// Cycle through imports listed in CSGTestE.txt
	    //app = new CSGTestF();			// Cycle through canned import list and apply physics
	    //app = new CSGTestG();			// Level Of Detail
	    //app = new CSGTestH();			// Test case for support ticket and raw shapes
	    //app = new CSGTestI( args );	// Shadow test
	    //app = new CSGTestJ();			// Dynamic animation test from JME forum
	    //app = new CSGTestK();			// 2D Surface test
	    //app = new CSGTestL();			// Progressive add/subtract cylinder from prior
	    //app = new CGGTestM();			// Progressive spherical mouse eating cube of cheese
	}
}
