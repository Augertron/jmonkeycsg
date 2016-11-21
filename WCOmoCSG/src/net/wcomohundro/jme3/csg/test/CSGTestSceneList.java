package net.wcomohundro.jme3.csg.test;

import java.io.IOException;
import java.util.Map;

import net.wcomohundro.jme3.csg.CSGLibrary;

import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;

public class CSGTestSceneList 
	implements Savable
{
	/** A list of arbitrary elements that can be named and referenced during
	 	XML load processing via id='somename' and ref='somename',
	 	and subsequently referenced programmatically via its inherent name. */
	protected Map<String,Savable>	mLibraryItems;
	/** The list of scenes to process */
	protected String[]				mSceneList;
	
	/** Null constructor */
	public CSGTestSceneList() {}
	
	/** Accessor to the scene list */
	public String[] getSceneList() { return mSceneList; }
	public void setSceneList( String[]  pScenes ) { mSceneList = pScenes; }
	
	
	///////////////////////////////////// Savable //////////////////////////////////
	@Override
	public void read(
		JmeImporter		pImporter
	) throws IOException {
    	AssetManager aManager = pImporter.getAssetManager();
		InputCapsule aCapsule = pImporter.getCapsule( this );
		
		// Support arbitrary Library items, defined BEFORE we process the rest of the items
        // Such items can be referenced within the XML stream itself via the
        //		id='name' and ref='name'
        // mechanism, and can be reference programmatically via their inherent names
		mLibraryItems = (Map<String,Savable>)aCapsule.readStringSavableMap( "library", null );
		mLibraryItems = CSGLibrary.fillLibrary( null, mLibraryItems, aManager, true );
		
		mSceneList = aCapsule.readStringArray( "scenes", null );
	}
	
	@Override
	public void write(
		JmeExporter		pExporter
	) throws IOException {
		OutputCapsule aCapsule = pExporter.getCapsule( this );
		
		aCapsule.write( mSceneList, "scenes", null );
	}
}
