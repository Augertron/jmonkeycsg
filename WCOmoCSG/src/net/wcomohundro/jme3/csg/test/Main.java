package net.wcomohundro.jme3.csg.test;

import java.util.Stack;

import net.wcomohundro.jme3.csg.CSGGeonode;
import net.wcomohundro.jme3.csg.CSGShape;
import net.wcomohundro.jme3.csg.ConstructiveSolidGeometry.CSGElement;
import net.wcomohundro.jme3.csg.shape.CSGBox;
import net.wcomohundro.jme3.csg.shape.CSGCylinder;

import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Triangle;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

public class Main 
	extends SimpleApplication 
	implements Runnable
{
	public static void main( 
		String...	pArgs
	) {
	    SimpleApplication app = new Main();		    
	    app.start();
	}
	
	/** Scene being displayed */
	protected Spatial			mLastScene;
	/** Spot for a bit of text */
	protected BitmapText		mTextDisplay;
	/** String to post */
	protected Stack<String>		mPostText;
	protected boolean			mRefreshText;
	/** Active item for wireframe display */
	protected Geometry			mWireframe;
	/** Selected triangle for wireframe display */
	protected Geometry			mSelectedTriangle;
	/** Element being built */
	protected CSGGeonode		mActiveElement;
	/** Just some counters */
	protected int				mShapeCounter;
	protected int				mUpdateCounter;
	
	public Main(
	) {
		super( new StatsAppState(), new FlyCamAppState(), new DebugKeysAppState() );
	}

    @Override
    public void simpleInitApp(
    ) {
		// Free the mouse up for debug support
    	if ( this.flyCam != null ) {
		    this.flyCam.setMoveSpeed( 20 );			// Move a bit faster
		    this.flyCam.setDragToRotate( true );	// Only use the mouse while it is clicked
    	}
        this.cam.setFrustumPerspective( 45f, (float)cam.getWidth() / cam.getHeight(), 0.1f, 250f);
	    
	    // Establish the text display
		mPostText = new Stack();
	    mTextDisplay = defineTextDisplay( this, this.guiFont );
	    
	    // Build the primitive selected triangle display
	    mSelectedTriangle = new Geometry( "Selected" );
	    Material triangleMat = new Material( assetManager, "Common/MatDefs/Misc/Unshaded.j3md" );
	    triangleMat.setColor( "Color", ColorRGBA.Gray );
	    mSelectedTriangle.setMaterial( triangleMat );
	    
        // Ready interaction
        createListeners();
          
        // By default, assume the entire root is the scene we use for click-on
        mLastScene = this.rootNode;
        
		this.mPostText.push( "QWASDZ to move, <ESC> to exit" );
		this.mRefreshText = true;
		
		// Start the background thread that performs the actions
    	Thread aThread = new Thread( null, this, "BackgroundLoad" );
    	aThread.setPriority( Thread.MIN_PRIORITY );
    	aThread.setDaemon( true );
    	aThread.start();
    }
    
    /////////////////////// Implement Runnable ////////////////
    public void run(
    ) {
    	Spatial aGeometry;
    	
	    // Long cylinder that pokes out of the cube
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.UNION, false );
    	aGeometry.move( -3f, 0, 0f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
    	aGeometry.move( 0f, 0f, 0f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.INTERSECTION, false );
    	aGeometry.move( 3f, 0f, 0f );
    	
    	// Short cylinder that does not span out of the cube
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.UNION, false );
    	aGeometry.move( -3f, 3f, 0f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
    	aGeometry.move( 0f, 3f, 0f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.INTERSECTION, false );
    	aGeometry.move( 3f, 3f, 0f );
    	
	    // Long cylinder that pokes out of the cube
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.UNION, false );
    	aGeometry.move( -3f, 0, -5f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
    	aGeometry.move( 0f, 0f, -5f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.INTERSECTION, true );
    	aGeometry.move( 3f, 0f, -5f );
    	
    	// Short cylinder that does not span out of the cube
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.UNION, false );
    	aGeometry.move( -3f, 3f, -5f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.DIFFERENCE, false );
    	aGeometry.move( 0f, 3f, -5f );
    	
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 1.5f, CSGGeonode.CSGOperator.INTERSECTION, false );
    	aGeometry.move( 3f, 3f, -5f );
    	
    	// Just the box
    	aGeometry = buildShape( CSGGeonode.CSGOperator.UNION, 2.5f, CSGGeonode.CSGOperator.SKIP, false );
    	aGeometry.move( -3f, 0f, 5f );

    	// Just the cylinder
    	aGeometry = buildShape( CSGGeonode.CSGOperator.SKIP, 2.5f, CSGGeonode.CSGOperator.UNION, true );
    	aGeometry.move( 3f, 0f, 5f );
    	
       	mRefreshText = true;    	
    }
    
    protected Spatial buildShape(
    	CSGGeonode.CSGOperator		pOperator1
    ,	float						pLength
    ,	CSGGeonode.CSGOperator		pOperator2
    ,	boolean						pColored
    ) {
    	while( mActiveElement != null ) synchronized(this) { try {
    		this.wait();
    	} catch( InterruptedException ex ) {
    	}}
    	mShapeCounter += 1;
    	
	    // Basic material for the CSG
        Material mat_csg = new Material( assetManager, "Common/MatDefs/Misc/ShowNormals.j3md" );

    	CSGGeonode aGeometry = new CSGGeonode( "Blend-" + mShapeCounter );
    	aGeometry.setMaterial( mat_csg );

    	CSGShape aCube 
    		= new CSGShape( "Box-" + mShapeCounter, new CSGBox(1,1,1) );
    	aGeometry.addShape( aCube, pOperator1 );

    	CSGShape aCylinder 
    		= new CSGShape( "Cylinder-" + mShapeCounter, new CSGCylinder( 32, 32, 1.1f, pLength/2f ) );
    	if ( pColored ) {
            Material mat1 = new Material( assetManager, "Common/MatDefs/Misc/Unshaded.j3md" );
            mat1.setColor( "Color", ColorRGBA.Yellow );
            aCylinder.setMaterial( mat1 );
    	}
    	aGeometry.addShape( aCylinder, pOperator2 );
    	
    	mActiveElement = aGeometry;
    	aGeometry.deferSceneChanges( true );
    	aGeometry.regenerate();
    	return( aGeometry );
    }
    
    /** Service routine to activate the interactive listeners */
    protected void createListeners(
    ) {
        inputManager.addMapping( "pickItem"
        ,   new MouseButtonTrigger( MouseInput.BUTTON_RIGHT ) );
        inputManager.addMapping( "wireframe"
        ,   new MouseButtonTrigger( 3 ) );
        inputManager.addMapping( "wireframe"
        ,   new MouseButtonTrigger( 4 ) );

        ActionListener aListener = new CSGTestActionListener(); 
        inputManager.addListener( aListener, "pickItem" );
        inputManager.addListener( aListener, "wireframe" );
    }  
    
    /** Inner helper class that processes user input */
    protected class CSGTestActionListener
    	implements ActionListener
    {
        public void onAction(
            String      pName
        ,   boolean     pKeyPressed
        ,   float       pTimePerFrame
        ) {
            if ( pKeyPressed ) {
                if ( pName.equals( "pickItem" ) ) {
                	// Report on the click
                	mPostText.push( resolveSelectedItem() );
                	mRefreshText = true;
                	
                } else if ( pName.equals( "wireframe" ) ) {
                	// Report on the click
                	CollisionResults aCollisions = resolveSelectedCollision();
                	if ( aCollisions != null ) {
                		CollisionResult aCollision = aCollisions.getClosestCollision();
	                	mWireframe = aCollision.getGeometry();
	                	mWireframe.getMaterial().getAdditionalRenderState().setWireframe( true );
	                	
	                	Triangle aTriangle = aCollision.getTriangle( null );
	                	Vector3f aNormal = aTriangle.getNormal();
	                	
	                	Mesh aMesh = new Mesh();
	                    Vector3f[] vertices = new Vector3f[3];
	                    Vector3f[] normals = new Vector3f[3];
	                    int[] indexes = new int[] { 0, 1, 2 };
	                    vertices[0] = aTriangle.get1();
	                    vertices[1] = aTriangle.get2();
	                    vertices[2] = aTriangle.get3();
	                    normals[0] = aNormal;
	                    normals[1] = aNormal;
	                    normals[2] = aNormal;
	                	aMesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
	                	aMesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
	                    //mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoord)); 
	                    aMesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(indexes));
	                	mSelectedTriangle.setMesh( aMesh );
	                	mSelectedTriangle.setLocalTransform( mWireframe.getLocalTransform() );
	                	mWireframe.getParent().attachChild( mSelectedTriangle );
	                	
	                	StringBuilder aBuffer = new StringBuilder( 128 );
	                	aBuffer.append( "(" )
	                		.append( aTriangle.get1() ).append( ", " )
	                		.append( aTriangle.get2() ).append( ", " )
	                		.append( aTriangle.get3() )
	                		.append( ") - " )
	                		.append( aCollision.getDistance() );
	                	mPostText.push( aBuffer.toString() );
	                	System.out.println( aBuffer.toString() );
	                	mRefreshText = true;
                	}
                }
            } else {
            	if ( pName.equals( "pickItem" ) ) {
            		if ( !mPostText.isEmpty() ) mPostText.pop();
            		mRefreshText = true;
            		
            	} else if ( pName.equals( "wireframe" ) ) {
            		if ( mWireframe != null ) {
            			mWireframe.getParent().detachChild( mSelectedTriangle );
                		mWireframe.getMaterial().getAdditionalRenderState().setWireframe( false );            			
            			mWireframe = null;
            			
                		if ( !mPostText.isEmpty() ) mPostText.pop();
                		mRefreshText = true;
            		}
            	}
            }
        }
    }

    @Override
    public void update(
    ) {
    	super.update();
    	
		if ( mActiveElement != null ) {
	    	mUpdateCounter += 1;
	        if ( mUpdateCounter == 10 ) {
	        	StringBuilder aBuffer = new StringBuilder( 256 );
	        	aBuffer.append( "** LOADING ==> " );
	        	mActiveElement.reportStatus( aBuffer, false );
	        	
				postText( this, mTextDisplay, aBuffer.toString(), false );
	            mUpdateCounter = 0;
	        }
	        if ( mActiveElement.applySceneChanges()  ) synchronized(this) {
	        	// Changes have been applied
	        	rootNode.attachChild( mActiveElement );
	        	mActiveElement = null;
	        	this.notifyAll();
	        }
		}	
    	if ( mRefreshText ) {
    		String aMessage = (mPostText.isEmpty()) ? "" : mPostText.peek();
        	postText( this, mTextDisplay, aMessage, true );    			
        	mRefreshText = false;
    	}
    }
    
    /** What is the user looking at? */
    protected String resolveSelectedItem(
    ) {
    	StringBuilder aBuffer = null;
    	CollisionResults aCollisions = resolveSelectedCollision();
    	if ( aCollisions != null ) {
    		aBuffer = new StringBuilder( 128 );
    		for( CollisionResult aCollision : aCollisions ) {
    			Geometry picked = aCollision.getGeometry();
    			if ( aBuffer.length() > 0 ) aBuffer.append( "; " );
    			aBuffer.append( picked.getName() );
    			
    			//System.out.println( aBuffer.toString() +  " - " + picked.getWorldBound() );
    		}
    	}
    	return( (aBuffer == null) ? "...nothing..." : aBuffer.toString() );
    }
    protected Geometry resolveSelectedGeometry(
    ) {
    	CollisionResults aCollision = resolveSelectedCollision();
    	return( (aCollision == null) ? null : aCollision.getClosestCollision().getGeometry() );
    }
    protected CollisionResults resolveSelectedCollision(
    ) {
    	CollisionResults results = new CollisionResults();
    	
        // To pick what the camera is directly looking at
        //Ray aRay = new Ray( cam.getLocation(), cam.getDirection() );
         
        // To pick what the mouse is over
        Vector2f click2d = inputManager.getCursorPosition();
        Vector3f click3d = cam.getWorldCoordinates( new Vector2f(click2d.x, click2d.y), 0f).clone();
        Vector3f dir = cam.getWorldCoordinates(new Vector2f(click2d.x, click2d.y), 1f).subtractLocal(click3d).normalizeLocal();
        Ray aRay = new Ray( click3d, dir );
        
        // What all was selected
        if ( mLastScene != null ) {
	        mLastScene.collideWith( aRay, results );
	        if ( results.size() > 0 ) {
	        	return( results );
	        }
        }
    	return( null );
    }
    
	/** Service routine to display a bit of text */
	protected BitmapText defineTextDisplay(
		SimpleApplication	pApplication
	,	BitmapFont 			pGuiFont
	) {
		BitmapText textDisplay = new BitmapText( pGuiFont, false );
    	textDisplay.setSize( pGuiFont.getCharSet().getRenderedSize() );

    	pApplication.getGuiNode().attachChild( textDisplay );
    	return( textDisplay );
	}
	
	/** Service routine to post a bit of text */
    protected void postText(
    	SimpleApplication	pApplication
    ,	BitmapText			pTextDisplay
    ,	String				pMessage
    ,	boolean				pCentered
    ) {
        pTextDisplay.setText( pMessage );
        
        Camera aCam = pApplication.getCamera();
        if ( pCentered ) {
        	pTextDisplay.setLocalTranslation( (aCam.getWidth() - pTextDisplay.getLineWidth()) / 2, aCam.getHeight(), 0);
        } else {
        	pTextDisplay.setLocalTranslation( 0, aCam.getHeight(), 0);
        }
        pTextDisplay.render( pApplication.getRenderManager(), null );
    }

}
