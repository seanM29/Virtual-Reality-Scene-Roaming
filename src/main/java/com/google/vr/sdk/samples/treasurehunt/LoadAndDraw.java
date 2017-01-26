package com.google.vr.sdk.samples.treasurehunt;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Created by SD on 2017/1/9.
 */

public class LoadAndDraw implements Runnable {
    //IMPORTANT 需要在外面loadshader
    protected float[] modelWell;
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

    private FloatBuffer wellVertices;
    private FloatBuffer wellTextures;
    private FloatBuffer wellNormals;
    private IntBuffer wellIndices;

    private InputStream inStream;
    private InputStream vertexShaderStream;
    private InputStream fragmentShaderStream;
    public boolean laodFinish; //load obj ready

    LoadAndDraw(InputStream inputStream,InputStream vertex_shader_stream,InputStream fragment_shader_stream){
        inStream = inputStream;
        vertexShaderStream = vertex_shader_stream;
        fragmentShaderStream = fragment_shader_stream;
        modelWell = new float[16];
    }

    /// 获取inputStream 中的所有内容
    private String ReadRawFile(InputStream inputStream){
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
    @Override
    public void run() {
        String data = ReadRawFile(inStream);
        Log.i("ObjLoader","Load well finished!");
        OBJLoader objLoader = new OBJLoader(data);
        well_vertices = objLoader.getVertices();
        well_normals = objLoader.getNormals();
        well_textures = objLoader.getTexture();
        well_indices = objLoader.getIndex();


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


        int wellVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, vertexShaderStream);
        int wellFragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderStream);

        // generate program
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

        Matrix.setIdentityM(modelWell, 0);
        laodFinish = true;

    }

    public void draw(float[] lightPosInEyeSpace,float[] view, float[] perspective) {
        if (!laodFinish){
            // 如果没有加载完obj，就直接返回
            return;
        }
        float[] modelView = new float[16];
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelView, 0, view, 0, modelWell, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);

        GLES20.glUseProgram(wellProgram);
        GLES20.glUniform3fv(wellLightPosParam, 1, lightPosInEyeSpace, 0);

        GLES20.glUniformMatrix4fv(wellModelParam, 1, false, modelWell, 0);
        GLES20.glUniformMatrix4fv(wellModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(wellModelViewProjectionParam, 1, false, modelViewProjection, 0);

        GLES20.glVertexAttribPointer(wellPositionParam, 3, GLES20.GL_FLOAT, false, 0, wellVertices);
        GLES20.glVertexAttribPointer(wellNormalParam, 3, GLES20.GL_FLOAT, false, 0, wellNormals);
        GLES20.glVertexAttribPointer(wellTextureParam, 2, GLES20.GL_FLOAT, false, 0, wellTextures);

        GLES20.glEnableVertexAttribArray(wellPositionParam);
        GLES20.glEnableVertexAttribArray(wellNormalParam);
        GLES20.glEnableVertexAttribArray(wellTextureParam);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, well_indices.length , GLES20.GL_UNSIGNED_INT, wellIndices);
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,well_vertices.length);
        checkGLError("Drawing obj");
    }

    public void setPosition(float x,float y,float z){
        Matrix.setIdentityM(modelWell,0);
        Matrix.translateM(modelWell, 0, x, y, z);
    }

    private static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e("LoadAndDraw", label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }
    private int loadGLShader(int type, InputStream inputfile) {
        String code = ReadRawFile(inputfile);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e("Load and Draw", "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }
}
