package net.wcomohundro.jme3.csg.test;

/** Helper class that associates a test class with a display name */
public class CSGTestDriverAppItem
{
	String		mDisplayItem;
	Class		mApplicationClass;
	String[]	mApplicationArgs;
	
	public CSGTestDriverAppItem( 
		String 		pName
	, 	Class 		pClass
	) {
		this( pName, pClass, (String[])null );
	}
	public CSGTestDriverAppItem( 
		String 		pName
	, 	Class 		pClass
	,	String		pArg
	) { 
		this( pName, pClass, new String[1] );
		mApplicationArgs[0] = pArg;
	}
	public CSGTestDriverAppItem( 
		String 		pName
	, 	Class 		pClass
	,	String[]	pArgs
	) { 
		mDisplayItem = pName; 
		mApplicationClass = pClass; 
		mApplicationArgs = pArgs;
	}
	
	@Override
	public String toString() { return mDisplayItem; }
}
