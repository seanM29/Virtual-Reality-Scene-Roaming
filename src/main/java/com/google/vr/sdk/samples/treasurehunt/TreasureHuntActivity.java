/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.vr.sdk.samples.treasurehunt;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Google VR sample application.
 * </p><p>
 * The TreasureHunt scene consists of a planar ground grid and a floating
 * "treasure" cube. When the user looks at the cube, the cube will turn gold.
 * While gold, the user can activate the Cardboard trigger, which will in turn
 * randomly reposition the cube.
 */
public class TreasureHuntActivity extends GvrActivity implements GvrView.StereoRenderer {

    protected float[] modelCube;
    protected float[] modelPosition;
    protected float[] modelWell;
    protected float[] modelBed;
    protected float[] modelHouse;


    private static final String TAG = "TreasureHuntActivity";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 1000.0f;

    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.3f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

    private static final int COORDS_PER_VERTEX = 3;

    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[]{0.0f, 2.0f, 0.0f, 1.0f};

    // Convenience vector for extracting the position from a matrix via multiplication.
    private static final float[] POS_MATRIX_MULTIPLY_VEC = {0, 0, 0, 1.0f};

    private static final float MIN_MODEL_DISTANCE = 3.0f;
    private static final float MAX_MODEL_DISTANCE = 7.0f;

    private static final String OBJECT_SOUND_FILE = "cube_sound.wav";
    private static final String SUCCESS_SOUND_FILE = "success.wav";

    private final float[] lightPosInEyeSpace = new float[4];

    private FloatBuffer floorVertices;
    private FloatBuffer floorColors;
    private FloatBuffer floorNormals;

    private FloatBuffer cubeVertices;
    private FloatBuffer cubeColors;
    private FloatBuffer cubeFoundColors;
    private FloatBuffer cubeNormals;

    private FloatBuffer wellVertices;
    private FloatBuffer wellTextures;
    private FloatBuffer wellNormals;
    private IntBuffer wellIndices;

    private FloatBuffer bedVertices;
    private FloatBuffer bedTextures;
    private FloatBuffer bedNormals;
    private IntBuffer bedIndices;

    private FloatBuffer houseVertices;
    private FloatBuffer houseTextures;
    private FloatBuffer houseNormals;
    private IntBuffer houseIndices;

    private int cubeProgram;
    private int floorProgram;

    private int cubePositionParam;
    private int cubeNormalParam;
    private int cubeColorParam;
    private int cubeModelParam;
    private int cubeModelViewParam;
    private int cubeModelViewProjectionParam;
    private int cubeLightPosParam;

    private int floorPositionParam;
    private int floorNormalParam;
    private int floorColorParam;
    private int floorModelParam;
    private int floorModelViewParam;
    private int floorModelViewProjectionParam;
    private int floorLightPosParam;

    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelViewProjection;
    private float[] modelView;
    private float[] modelFloor;

    private float[] tempPosition;
    private float[] headRotation;

    private float objectDistance = MAX_MODEL_DISTANCE / 2.0f;
    private float floorDepth = 20f;

    private Vibrator vibrator;

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;
    private Context ctx;

    private int wellProgram;
    private int wellPositionParam;
    private int wellNormalParam;
    private int wellTextureParam;
    private int wellModelParam;
    private int wellModelViewParam;
    private int wellModelViewProjectionParam;
    private int wellLightPosParam;
    //  private Object3dContainer wellObj3D;
    private float[] well_vertices;
    private float[] well_normals;
    private float[] well_textures;
    private int[] well_indices;

    private int bedProgram;
    private int bedPositionParam;
    private int bedNormalParam;
    private int bedTextureParam;
    private int bedModelParam;
    private int bedModelViewParam;
    private int bedModelViewProjectionParam;
    private int bedLightPosParam;
    private float[] bed_vertices;
    private float[] bed_normals;
    private float[] bed_textures;
    private int[] bed_indices;

    private float[] house_vertices;
    private float[] house_normals;
    private float[] house_textures;
    private float[] house_indices;

    private LoadAndDraw wellObj;
    private float posy;

//  ObjObject myObjObject;

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param type  The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The shader object handler.
     */
    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     */
    private static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    /**
     * Sets the view to our GvrView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeGvrView();

        modelCube = new float[16];
        modelWell = new float[16];
        modelBed = new float[16];
        modelHouse = new float[16];
        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        modelFloor = new float[16];
        tempPosition = new float[4];
        // Model first appears directly in front of user.
        modelPosition = new float[]{0.0f, 0.0f, -MAX_MODEL_DISTANCE / 2.0f};
        headRotation = new float[4];
        headView = new float[16];
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Initialize 3D audio engine.
        gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
    }

    public void initializeGvrView() {
        setContentView(R.layout.common_ui);

        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);
    }

    @Override
    public void onPause() {
        gvrAudioEngine.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        gvrAudioEngine.resume();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     * <p>
     * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up bed.

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        cubeVertices = bbVertices.asFloatBuffer();
        cubeVertices.put(WorldLayoutData.CUBE_COORDS);
        cubeVertices.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COLORS.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        cubeColors = bbColors.asFloatBuffer();
        cubeColors.put(WorldLayoutData.CUBE_COLORS);
        cubeColors.position(0);

        ByteBuffer bbFoundColors =
                ByteBuffer.allocateDirect(WorldLayoutData.CUBE_FOUND_COLORS.length * 4);
        bbFoundColors.order(ByteOrder.nativeOrder());
        cubeFoundColors = bbFoundColors.asFloatBuffer();
        cubeFoundColors.put(WorldLayoutData.CUBE_FOUND_COLORS);
        cubeFoundColors.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        cubeNormals = bbNormals.asFloatBuffer();
        cubeNormals.put(WorldLayoutData.CUBE_NORMALS);
        cubeNormals.position(0);

        // load well obj
        InputStream wellObjFile;
        wellObjFile = this.getResources().openRawResource(R.raw.cartoon_well_obj);
        InputStream wellVertexShaderSrc;
        wellVertexShaderSrc =this.getResources().openRawResource(R.raw.well_vertex_shader);
        InputStream wellFragmentShaderSrc;
        wellFragmentShaderSrc = this.getResources().openRawResource(R.raw.well_fragment_shader);
//        Thread loadwell = new Thread(wellObj = new LoadAndDraw(wellObjFile,wellVertexShaderSrc,wellFragmentShaderSrc));
//        loadwell.start();
        InputStream bedObjFile;
        bedObjFile = this.getResources().openRawResource(R.raw.cartoonmedhouse1_obj);

//
//    ResourceLoader resourceLoader = ResourceLoader.getResourceLoader();
//    myObjObject = resourceLoader.loadObjObject(wellObjFile,"cartoon_well","cartoon_well");


        String data = null;
        data = myReadRawFile(wellObjFile);
        Log.i("ObjLoader","Load well finished!");
        OBJLoader objLoader = new OBJLoader(data);
        well_vertices = objLoader.getVertices();
        well_normals = objLoader.getNormals();
        well_textures = objLoader.getTexture();
        well_indices = objLoader.getIndex();

        // well buffer
        ByteBuffer bbWellVertices = ByteBuffer.allocateDirect(well_vertices.length * 4);
        bbWellVertices.order(ByteOrder.nativeOrder());
        wellVertices = bbWellVertices.asFloatBuffer();
        wellVertices.put(well_vertices);
        wellVertices.position(0);

        ByteBuffer bbWellNormals = ByteBuffer.allocateDirect(well_normals.length * 4);
        bbWellNormals.order(ByteOrder.nativeOrder());
        wellNormals = bbWellNormals.asFloatBuffer();
        wellNormals.put(well_normals);
        wellNormals.position(0);

        ByteBuffer bbWellTextures = ByteBuffer.allocateDirect(well_textures.length * 4);
        bbWellTextures.order(ByteOrder.nativeOrder());
        wellTextures = bbWellTextures.asFloatBuffer();
        wellTextures.put(well_textures);
        wellTextures.position(0);

        ByteBuffer bbWellIndices = ByteBuffer.allocateDirect(well_indices.length * 4);
        bbWellTextures.order(ByteOrder.nativeOrder());
        wellIndices = bbWellTextures.asIntBuffer();
        wellIndices.put(well_indices);
        wellIndices.position(0);

        int wellVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.well_vertex_shader);
        int wellFragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.well_fragment_shader);

        // load bed

        data = null;
        data = myReadRawFile(bedObjFile);
        Log.i("ObjLoader","Load bed finished!");
        objLoader = new OBJLoader(data);
        bed_vertices = objLoader.getVertices();
        bed_normals = objLoader.getNormals();
        bed_indices = objLoader.getIndex();
        bed_textures = objLoader.getTexture();

        // bed buffer
        ByteBuffer bbBedVertices = ByteBuffer.allocateDirect(bed_vertices.length * 4);
        bbBedVertices.order(ByteOrder.nativeOrder());
        bedVertices = bbBedVertices.asFloatBuffer();
        bedVertices.put(bed_vertices);
        bedVertices.position(0);

        ByteBuffer bbBedNormals = ByteBuffer.allocateDirect(bed_normals.length * 4);
        bbBedNormals.order(ByteOrder.nativeOrder());
        bedNormals = bbBedNormals.asFloatBuffer();
        bedNormals.put(bed_normals);
        bedNormals.position(0);

        ByteBuffer bbBedTextures = ByteBuffer.allocateDirect(bed_textures.length * 4);
        bbBedTextures.order(ByteOrder.nativeOrder());
        bedTextures = bbBedTextures.asFloatBuffer();
        bedTextures.put(bed_textures);
        bedTextures.position(0);

        ByteBuffer bbBedIndices = ByteBuffer.allocateDirect(bed_indices.length * 4);
        bbBedTextures.order(ByteOrder.nativeOrder());
        bedIndices = bbBedTextures.asIntBuffer();
        bedIndices.put(bed_indices);
        bedIndices.position(0);

        int bedVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.well_vertex_shader);
        int bedFragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.well_fragment_shader);


//    bedVertices = (myObjObject.getBuffer(MeshObject.BUFFER_TYPE.BUFFER_TYPE_VERTEX));
//    bedNormals = (myObjObject.getBuffer(MeshObject.BUFFER_TYPE.BUFFER_TYPE_NORMALS));
//    bedTextures = (myObjObject.getBuffer(MeshObject.BUFFER_TYPE.BUFFER_TYPE_VERTEX));

        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        floorVertices = bbFloorVertices.asFloatBuffer();
        floorVertices.put(WorldLayoutData.FLOOR_COORDS);
        floorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        floorNormals = bbFloorNormals.asFloatBuffer();
        floorNormals.put(WorldLayoutData.FLOOR_NORMALS);
        floorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        floorColors = bbFloorColors.asFloatBuffer();
        floorColors.put(WorldLayoutData.FLOOR_COLORS);
        floorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
        int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);
//    int objVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER,R.raw.obj_vertex);
//    int objFragementShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER,R.raw.obj_fragment);
        cubeProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(cubeProgram, vertexShader);
        GLES20.glAttachShader(cubeProgram, passthroughShader);
        GLES20.glLinkProgram(cubeProgram);
        GLES20.glUseProgram(cubeProgram);

        checkGLError("Cube program");

        cubePositionParam = GLES20.glGetAttribLocation(cubeProgram, "a_Position");
        cubeNormalParam = GLES20.glGetAttribLocation(cubeProgram, "a_Normal");
        cubeColorParam = GLES20.glGetAttribLocation(cubeProgram, "a_Color");

        cubeModelParam = GLES20.glGetUniformLocation(cubeProgram, "u_Model");
        cubeModelViewParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVMatrix");
        cubeModelViewProjectionParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVP");
        cubeLightPosParam = GLES20.glGetUniformLocation(cubeProgram, "u_LightPos");

        checkGLError("Cube program params");

        // well program
        wellProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(wellProgram, wellVertexShader);
        GLES20.glAttachShader(wellProgram, wellFragmentShader);
        GLES20.glLinkProgram(wellProgram);
        GLES20.glUseProgram(wellProgram);

        checkGLError("Well program");

        wellPositionParam = GLES20.glGetAttribLocation(wellProgram, "a_Position");
        wellNormalParam = GLES20.glGetAttribLocation(wellProgram, "a_Normal");
        wellTextureParam = GLES20.glGetAttribLocation(wellProgram, "a_TextureCoord");

        wellModelParam = GLES20.glGetUniformLocation(wellProgram, "u_Model");
        wellModelViewParam = GLES20.glGetUniformLocation(wellProgram, "u_MVMatrix");
        wellModelViewProjectionParam = GLES20.glGetUniformLocation(wellProgram, "u_MVP");
        wellLightPosParam = GLES20.glGetUniformLocation(wellProgram, "u_LightPos");

        checkGLError("Well program params");

        // bed program
        bedProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(bedProgram, bedVertexShader);
        GLES20.glAttachShader(bedProgram, bedFragmentShader);
        GLES20.glLinkProgram(bedProgram);
        GLES20.glUseProgram(bedProgram);

        checkGLError("Bed program");

        bedPositionParam = GLES20.glGetAttribLocation(bedProgram, "a_Position");
        bedNormalParam = GLES20.glGetAttribLocation(bedProgram, "a_Normal");
        bedTextureParam = GLES20.glGetAttribLocation(bedProgram, "a_TextureCoord");

        bedModelParam = GLES20.glGetUniformLocation(bedProgram, "u_Model");
        bedModelViewParam = GLES20.glGetUniformLocation(bedProgram, "u_MVMatrix");
        bedModelViewProjectionParam = GLES20.glGetUniformLocation(bedProgram, "u_MVP");
        bedLightPosParam = GLES20.glGetUniformLocation(bedProgram, "u_LightPos");

        checkGLError("Bed program param");

        floorProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(floorProgram, vertexShader);
        GLES20.glAttachShader(floorProgram, gridShader);
        GLES20.glLinkProgram(floorProgram);
        GLES20.glUseProgram(floorProgram);

        checkGLError("Floor program");

        floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
        floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
        floorModelViewProjectionParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
        floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

        floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
        floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal");
        floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");

        checkGLError("Floor program params");

        Matrix.setIdentityM(modelFloor, 0);
        Matrix.translateM(modelFloor, 0, 0, -floorDepth, 0); // Floor appears below user.

        // Avoid any delays during start-up due to decoding of sound files.
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        // Start spatial audio playback of OBJECT_SOUND_FILE at the model position. The
                        // returned sourceId handle is stored and allows for repositioning the sound object
                        // whenever the cube position changes.
                        gvrAudioEngine.preloadSoundFile(OBJECT_SOUND_FILE);
                        sourceId = gvrAudioEngine.createSoundObject(OBJECT_SOUND_FILE);
                        gvrAudioEngine.setSoundObjectPosition(
                                sourceId, modelPosition[0], modelPosition[1], modelPosition[2]);
                        gvrAudioEngine.playSound(sourceId, true /* looped playback */);
                        // Preload an unspatialized sound to be played on a successful trigger on the cube.
                        gvrAudioEngine.preloadSoundFile(SUCCESS_SOUND_FILE);
                    }
                })
                .start();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                float theta =0;
//                float radius,centerx,centerz;
//                radius = 16;
//                centerx = 0;
//                centerz = -8;
//                while (true ){
//                    posx = ((float) (radius * Math.cos(theta)))+centerx;
//                    posz = ((float) (radius * Math.sin(theta)))+centerz;
//                    theta+=0.02f;
//                    try {
//                        Thread.sleep(30);
//                    } catch (Exception e) {
//                        Log.i(TAG, e.toString());
//                    }
//                }
//
//            }
//        }).start();

        updateModelPosition();

        checkGLError("onSurfaceCreated");
    }

    /**
     * Updates the cube model position.
     */
    protected void updateModelPosition() {
        Matrix.setIdentityM(modelCube, 0);
        Matrix.translateM(modelCube, 0, modelPosition[0], modelPosition[1], modelPosition[2]);

        Matrix.setIdentityM(modelWell, 0);
        Matrix.translateM(modelWell, 0, 10, -2, -100);

        Matrix.setIdentityM(modelBed,0);
        Matrix.translateM(modelBed,0,-105,-10,-250);
        // Update the sound location to match it with the new cube position.
        if (sourceId != GvrAudioEngine.INVALID_ID) {
            gvrAudioEngine.setSoundObjectPosition(
                    sourceId, modelPosition[0], modelPosition[1], modelPosition[2]);
        }
        checkGLError("updateCubePosition");
    }

    /**
     * Converts a raw text file into a string.
     *
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The context of the text file, or null in case of error.
     */
    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    float posx = 0;
    float posz = 0;

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        setCubeRotation();
        setObjMove();
        float[] forward = new float[3];
        headTransform.getForwardVector(forward,0);
        posx+=forward[0]/15;
        posy+=forward[1]/15;
        posz += forward[2]/15;
        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(camera, 0, posx, posy, posz, 2*posx, 2*posy, 2*posz, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(headView, 0);

        // Update the 3d audio engine with the most recent head rotation.
        headTransform.getQuaternion(headRotation, 0);
        gvrAudioEngine.setHeadRotation(
                headRotation[0], headRotation[1], headRotation[2], headRotation[3]);
        // Regular update call to GVR audio engine.
        gvrAudioEngine.update();

        checkGLError("onReadyToDraw");
    }

    protected void setCubeRotation() {
        Matrix.rotateM(modelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);
    }

    protected void setObjMove(){
        Matrix.translateM(modelWell,0,0.0f,0.0f,0.0f);
    }
    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        checkGLError("colorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

        // Set the position of the light
        Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        Matrix.multiplyMM(modelView, 0, view, 0, modelCube, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        drawCube();

        // Set modelView for the floor, so we draw floor in the correct location
        Matrix.multiplyMM(modelView, 0, view, 0, modelFloor, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        drawFloor();

//        wellObj.setPosition(0,0,100);
//        wellObj.draw(lightPosInEyeSpace,view,perspective);
        Matrix.multiplyMM(modelView, 0, view, 0, modelWell, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        drawWell();

        Matrix.multiplyMM(modelView,0,view,0,modelBed,0);
        Matrix.multiplyMM(modelViewProjection,0,perspective,0,modelView,0);
        drawBed();

    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    public void drawWell() {
        GLES20.glUseProgram(wellProgram);
        GLES20.glUniform3fv(wellLightPosParam, 1, lightPosInEyeSpace, 0);

        GLES20.glUniformMatrix4fv(wellModelParam, 1, false, modelWell, 0);
        GLES20.glUniformMatrix4fv(wellModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(wellModelViewProjectionParam, 1, false, modelViewProjection, 0);

        GLES20.glVertexAttribPointer(wellPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, wellVertices);
        GLES20.glVertexAttribPointer(wellNormalParam, 3, GLES20.GL_FLOAT, false, 0, wellNormals);
        GLES20.glVertexAttribPointer(wellTextureParam, 2, GLES20.GL_FLOAT, false, 0, wellTextures);

        GLES20.glEnableVertexAttribArray(wellPositionParam);
        GLES20.glEnableVertexAttribArray(wellNormalParam);
        GLES20.glEnableVertexAttribArray(wellTextureParam);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, well_indices.length , GLES20.GL_UNSIGNED_INT, wellIndices);
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,well_vertices.length);
        checkGLError("Drawing well");
    }
    public void drawBed(){
        GLES20.glUseProgram(bedProgram);
        GLES20.glUniform3fv(bedLightPosParam, 1, lightPosInEyeSpace, 0);

        GLES20.glUniformMatrix4fv(bedModelParam, 1, false, modelBed, 0);
        GLES20.glUniformMatrix4fv(bedModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(bedModelViewProjectionParam, 1, false, modelViewProjection, 0);

        GLES20.glVertexAttribPointer(bedPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, bedVertices);
        GLES20.glVertexAttribPointer(bedNormalParam, 3, GLES20.GL_FLOAT, false, 0, bedNormals);
        GLES20.glVertexAttribPointer(bedTextureParam, 2, GLES20.GL_FLOAT, false, 0, bedTextures);

        GLES20.glEnableVertexAttribArray(bedPositionParam);
        GLES20.glEnableVertexAttribArray(bedNormalParam);
        GLES20.glEnableVertexAttribArray(bedTextureParam);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, bed_indices.length , GLES20.GL_UNSIGNED_INT, bedIndices);
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,bed_vertices.length);
        checkGLError("Drawing bed");
    }

    /**
     * Draw the cube.
     * <p>
     * <p>We've set all of our transformation matrices. Now we simply pass them into the shader.
     */
    public void drawCube() {
        GLES20.glUseProgram(cubeProgram);

        GLES20.glUniform3fv(cubeLightPosParam, 1, lightPosInEyeSpace, 0);

        // Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelParam, 1, false, modelCube, 0);

        // Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelView, 0);

        // Set the position of the cube
        GLES20.glVertexAttribPointer(
                cubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, cubeVertices);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam, 1, false, modelViewProjection, 0);

        // Set the normal positions of the cube, again for shading
        GLES20.glVertexAttribPointer(cubeNormalParam, 3, GLES20.GL_FLOAT, false, 0, cubeNormals);
        GLES20.glVertexAttribPointer(cubeColorParam, 4, GLES20.GL_FLOAT, false, 0,
                isLookingAtObject() ? cubeFoundColors : cubeColors);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(cubePositionParam);
        GLES20.glEnableVertexAttribArray(cubeNormalParam);
        GLES20.glEnableVertexAttribArray(cubeColorParam);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
        checkGLError("Drawing cube");
    }

    /**
     * Draw the floor.
     * <p>
     * <p>This feeds in data for the floor into the shader. Note that this doesn't feed in data about
     * position of the light, so if we rewrite our code to draw the floor first, the lighting might
     * look strange.
     */
    public void drawFloor() {
        GLES20.glUseProgram(floorProgram);

        // Set ModelView, MVP, position, normals, and color.
        GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0);
        GLES20.glUniformMatrix4fv(floorModelParam, 1, false, modelFloor, 0);
        GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false, modelViewProjection, 0);
        GLES20.glVertexAttribPointer(
                floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, floorVertices);
        GLES20.glVertexAttribPointer(floorNormalParam, 3, GLES20.GL_FLOAT, false, 0, floorNormals);
        GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColors);

        GLES20.glEnableVertexAttribArray(floorPositionParam);
        GLES20.glEnableVertexAttribArray(floorNormalParam);
        GLES20.glEnableVertexAttribArray(floorColorParam);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 24);

        checkGLError("drawing floor");
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        if (isLookingAtObject()) {
            successSourceId = gvrAudioEngine.createStereoSound(SUCCESS_SOUND_FILE);
            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
            hideObject();
        }

        // Always give user feedback.
        vibrator.vibrate(50);
    }

    /**
     * Find a new random position for the object.
     * <p>
     * <p>We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
     */
    protected void hideObject() {
        float[] rotationMatrix = new float[16];
        float[] posVec = new float[4];

        // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
        // the object's distance from the user.
        float angleXZ = (float) Math.random() * 180 + 90;
        Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
        float oldObjectDistance = objectDistance;
        objectDistance =
                (float) Math.random() * (MAX_MODEL_DISTANCE - MIN_MODEL_DISTANCE) + MIN_MODEL_DISTANCE;
        float objectScalingFactor = objectDistance / oldObjectDistance;
        Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor, objectScalingFactor);
        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, modelCube, 12);

        float angleY = (float) Math.random() * 80 - 40; // Angle in Y plane, between -40 and 40.
        angleY = (float) Math.toRadians(angleY);
        float newY = (float) Math.tan(angleY) * objectDistance;

        modelPosition[0] = posVec[0];
        modelPosition[1] = newY;
        modelPosition[2] = posVec[2];

        updateModelPosition();
    }

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     *
     * @return true if the user is looking at the object.
     */
    private boolean isLookingAtObject() {
        // Convert object space to camera space. Use the headView from onNewFrame.
        Matrix.multiplyMM(modelView, 0, headView, 0, modelCube, 0);
        Matrix.multiplyMV(tempPosition, 0, modelView, 0, POS_MATRIX_MULTIPLY_VEC, 0);

        float pitch = (float) Math.atan2(tempPosition[1], -tempPosition[2]);
        float yaw = (float) Math.atan2(tempPosition[0], -tempPosition[2]);

        return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
    }
    private String myReadRawFile(InputStream inputStream){
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
