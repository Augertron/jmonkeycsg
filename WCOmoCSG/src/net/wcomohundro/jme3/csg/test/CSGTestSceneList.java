package net.wcomohundro.jme3.csg.test;

import java.io.IOException;

import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;

public class CSGTestSceneList 
	implements Savable
{
	/** The list of scenes to process */
	protected String[]	mSceneList;
	
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
