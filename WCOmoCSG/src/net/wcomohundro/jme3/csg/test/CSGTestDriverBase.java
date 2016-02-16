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

import jme3test.light.TestPointLightShadows;

import com.jme3.app.SettingsDialog;
import com.jme3.app.SimpleApplication;
import com.jme3.app.SettingsDialog.SelectionListener;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.NonCachingKey;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.renderer.Camera;
import com.jme3.scene.plugins.blender.math.Vector3d;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;

import net.wcomohundro.jme3.csg.CSGEnvironment;
import net.wcomohundro.jme3.csg.CSGVersion;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry;

/** A simple application driver that can post a dialog */
public class CSGTestDriverBase 
	extends JFrame
	implements Runnable
{
	protected static void main(
		CSGTestDriverAppItem[] 	pAppChoices
	) {
		System.out.println( "logging config: " 
					+ System.getProperty( "java.util.logging.config.file" )
					+ "/" + System.getProperty( "java.util.logging.config.class" ) );
		CSGVersion.reportVersion();
		
		// Display the standard JME startup dialog
        AppSettings aSettings = new AppSettings( true );
        if ( !JmeSystem.showSettingsDialog( aSettings, true ) ) {
        	// User cancel
            return;
        }
        // Show the AppPicker dialog
        CSGTestDriverAppItem whichApp = showAppPickerDialog( pAppChoices );
        if ( whichApp == null ) {
        	// User cancel
        	return;
        }
        // Construct the application
	    SimpleApplication app;
	    try {
	        if ( whichApp.mApplicationArgs == null ) {
	        	app = (SimpleApplication)whichApp.mApplicationClass.newInstance();
	        } else {
	        	Constructor aConstructor 
	        		= whichApp.mApplicationClass.getConstructor( new Class[] { String[].class } );
	        	app = (SimpleApplication)aConstructor.newInstance( new Object[] { whichApp.mApplicationArgs } );
	        }
		} catch( Exception ex ) {
			return;
		}
	    // Apply the settings selected above
	    app.setSettings( aSettings );
	    app.setShowSettings( false );
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
    ,	AssetManager	pAssetManager
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
		} else if ( pAssetManager != null ) try {
			// Load as an asset
	    	NonCachingKey aKey = new NonCachingKey( pFileName );
	    	Object scenes = pAssetManager.loadAsset( aKey );
	    	if ( scenes instanceof CSGTestSceneList ) {
	    		pSeedValues.addAll( Arrays.asList( ((CSGTestSceneList)scenes).getSceneList() ) );
	    	} else {
	    		// Treat as a single file reference
	    		pSeedValues.add( pFileName );
	    	}
	    } catch( AssetNotFoundException ex ) {
	    	// Just punt any problems with locating an asset
	    	
	    } catch( Exception ex ) {
	    	// Report on problems loading the asset
			System.out.println( "*** Initialization failed: " + ex );
	    	
	    } else 
		// If nothing was loaded, then revert back to canned list
		if ( (pSeedValues.size() == initialCount) && (pDefaultValues != null) ) {
			Collections.addAll( pSeedValues, pDefaultValues );
		}
    	return( pSeedValues );
    }
    
    /** Post the application picker dialog */
    protected static CSGTestDriverAppItem showAppPickerDialog(
    	CSGTestDriverAppItem[]	pAppChoices
    ) {
        // Activate the dialog
        CSGTestDriverBase aDriver = new CSGTestDriverBase( pAppChoices );
        SwingUtilities.invokeLater( aDriver );
        
        // Wait for the dialog to complete
        synchronized( aDriver ) {
            while( aDriver.mIsActive ) try {
            	aDriver.wait();
            } catch ( InterruptedException ex ) {
            }
        }
        return( (aDriver.mRunApplication) ? aDriver.mSelectedApp : null );
    }
    
    /** Active dialog flag */
    protected boolean				mIsActive;
    /** Continuation flag */
    protected boolean				mRunApplication;
    /** The application to run */
    protected CSGTestDriverAppItem	mSelectedApp;
    /** Application picker dropdown */
    protected JComboBox 			mAppPickerCombo;
    /** Resource bundle */
    protected ResourceBundle		mResourceBundle;


    /** Simple initialization of the frame */
    protected CSGTestDriverBase() {}
    
    public CSGTestDriverBase(
    	CSGTestDriverAppItem[]	pAppChoices
    ) {
    	// We are active until the dialog closes 
    	mIsActive = true;
    	
    	// Borrow the standard SettingsDialog resources
    	mResourceBundle = ResourceBundle.getBundle( "com.jme3.app/SettingsDialog" );
    	
    	// Some basic layout
        setAlwaysOnTop( true );
        setResizable( false );
        GridBagConstraints gbc;
        
        JPanel mainPanel = new JPanel( new GridBagLayout() );
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch( Exception ex ) {
            throw new IllegalStateException( "Could not set native look and feel." );
        }
        setTitle( "CSGTestDriver" );

        // Monitor the window closing
        addWindowListener( new WindowAdapter() {
            @Override
            public void windowClosing( WindowEvent pEvent ) {
                completeUserAction( false );
                dispose();
            }
        });
        
        // Monitor key strokes
        KeyListener aListener = new KeyAdapter() {
            @Override
            public void keyPressed( KeyEvent pEvent ) {
                if ( pEvent.getKeyCode() == KeyEvent.VK_ENTER ) {
                    completeUserAction( true );
                    dispose();
                }
                else if ( pEvent.getKeyCode() == KeyEvent.VK_ESCAPE ) {
                    completeUserAction( false );
                    dispose();
                }
            }
        };
        // The pick list
        mAppPickerCombo = new JComboBox();
        mAppPickerCombo.setModel( new DefaultComboBoxModel( pAppChoices ) );
        mAppPickerCombo.setSelectedItem( pAppChoices[0] );
        mAppPickerCombo.addKeyListener( aListener );
        
        // Picklist layout
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add( new JLabel( "Application" ), gbc);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 16, 4, 4);
        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add( mAppPickerCombo, gbc);

        // Some standard buttons
        JButton ok = new JButton( mResourceBundle.getString( "button.ok" ) );               
        JButton cancel = new JButton( mResourceBundle.getString( "button.cancel" ) );
        
        ok.addActionListener( new ActionListener() {
        	@Override
            public void actionPerformed( ActionEvent pEvent ) {
                completeUserAction( true );
                dispose();
            }
        });

        cancel.addActionListener( new ActionListener() {
        	@Override
            public void actionPerformed( ActionEvent pEvent ) {
                completeUserAction( false );
                dispose();
            }
        });
        // Button layout
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add( ok, gbc ); 
        
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 16, 4, 4);
        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add( cancel, gbc );
        
        // Master panel control
        this.getContentPane().add( mainPanel );
        pack();
        mainPanel.getRootPane().setDefaultButton(ok);
    }
    
    /** Manage the selection */
    protected synchronized void completeUserAction(
    	boolean		pRunApplication
    ) {
    	mRunApplication = pRunApplication;
        mIsActive = false;
        
        if ( mRunApplication ) {
        	mSelectedApp = (CSGTestDriverAppItem)mAppPickerCombo.getSelectedItem();
        }
        this.notifyAll();
    }
    
    /** Background activation */
    @Override
    public void run(
    ) {
        synchronized( this ) {
        	// Show the dialog
            setLocationRelativeTo( null );
            setVisible( true );       
            toFront();
        }
    }
}

