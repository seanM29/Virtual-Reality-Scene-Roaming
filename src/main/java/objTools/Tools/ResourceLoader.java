package objTools.Tools;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import android.net.Uri;
import android.opengl.ETC1Util;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.util.Log;

import objTools.Models.Material;
import objTools.Models.ModelObject;
import objTools.Models.ObjObject;

/**
 * Created by stardust on 2017/1/2.
 */

public class ResourceLoader {

    private static final String TAG = "ObjLoader";

    private static ResourceLoader instance = null;

//    private static Map<String, ObjObject> ObjObjectes = new HashMap<String,ObjObject>();

    private ResourceLoader() {

    }

    public static ResourceLoader getResourceLoader() {

        if (instance == null) {
            instance = new ResourceLoader();
        }

        return instance;
    }

    public static void parseFace(String[] words, List<Short> positionIndices,
                                 List<Short> textureCoordIndices, List<Short> normalIndices) {

        String[] parts;
        int i=1;
        for (i=1; i<4; i++) {
            parts = words[i].split("/");
            short s = Short.parseShort(parts[0]);
            s--;
            short s2 = Short.parseShort(parts[1]);
            s2--;
            positionIndices.add(s );
            textureCoordIndices.add(s2 );
            normalIndices.add(s );
        }

        //return indices;
    }
    public String readTextFile(String filename) {

        BufferedReader br = null;
        try {
            InputStream is = new FileInputStream(filename);
            StringBuilder builder = new StringBuilder();
            String line;
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                builder.append(line);
                builder.append("\n");
            }

            return builder.toString();
        } catch (IOException ex) {
            Log.e(TAG, "Could not open file: " + filename);
        }

        return null;
    }

    public ObjObject loadObjObject(String ObjObjectName, String fileName) {
        Vector<Material> materials = null;
        BufferedReader br = null;

        try{
            InputStream is = new FileInputStream(fileName);
            return this.loadObjObject(is, ObjObjectName, fileName);
        }catch (FileNotFoundException ex){
            ex.printStackTrace();
        }
        return null;
    }

    public ObjObject loadObjObject(InputStream is, String ObjObjectName, String fileName){
        Vector<Material> materials = null;
        BufferedReader br = null;
        try {
//            InputStream is = assetMgr.open(fileName,
//                    AssetManager.ACCESS_STREAMING);
            Log.i(TAG, "Loaded the stream" + is);

            String line;
            br = new BufferedReader(new InputStreamReader(is));
            List<Vector3f> positionVertices = new ArrayList<Vector3f>();
            List<Vector2f> textureVertices = new ArrayList<Vector2f>();
            List<Vector3f> vertexNormals = new ArrayList<Vector3f>();

            List<Short> positionIndices = new ArrayList<Short>();
            List<Short> textureCoordIndices = new ArrayList<Short>();
            List<Short> normalIndices = new ArrayList<Short>();

            List<Vertex> finalVertices = new ArrayList<Vertex>();
            List<Short> finalIndices = new ArrayList<Short>();

            while ((line = br.readLine()) != null) {
                String[] words;
                words = line.split(" ");

                if (words[0].equals("v")) {

                    float x = Float.parseFloat(words[1]);
                    float y = Float.parseFloat(words[2]);
                    float z = Float.parseFloat(words[3]);

                    Vector3f v = new Vector3f(x, y, z);
                    positionVertices.add(v);
                }
                else if (words[0].equals("vt")) {

                    float s = Float.parseFloat(words[1]);
                    float t = Float.parseFloat(words[2]);

                    t = 1-t;
                    Vector2f v = new Vector2f(s, t);
                    textureVertices.add(v);
                }
                else if (words[0].equals("f")) {

                    parseFace(words, positionIndices, textureCoordIndices, normalIndices);

                }else if( words[0].equals("mtllib") ){
                    //materials = MTLReader.loadMTL(fileName.substring(0, fileName.lastIndexOf('/'))+"/"+words[1]);
                }

            }

            for (int i=0; i<positionVertices.size(); i++) {
                vertexNormals.add(new Vector3f(0,0,0));
            }

            // compute the normals
            for (int i=0; i<positionIndices.size()-3; i++) {

                short i1 = positionIndices.get(i);
                short i2 = positionIndices.get(i+1);
                short i3 = positionIndices.get(i+2);

                Vector3f v1 = positionVertices.get(i1);
                Vector3f v2 = positionVertices.get(i2);
                Vector3f v3 = positionVertices.get(i3);

                Vector3f a = v1.sub(v2);
                Vector3f b = v1.sub(v3);

                Vector3f norm = Vector3f.cross(a, b);
                norm.normalize();

                Vector3f current;
                current = vertexNormals.get(i1);
                current = current.add(norm);
                current.normalize();
                vertexNormals.set(i1, current);

                current = vertexNormals.get(i2);
                current = current.add(norm);
                current.normalize();
                vertexNormals.set(i2, current);

                current = vertexNormals.get(i3);
                current = current.add(norm);
                current.normalize();
                vertexNormals.set(i3, current);
            }


            short index = 0;
            Map<String, Vertex> map = new HashMap<String, Vertex>();
            for (int i=0; i<textureCoordIndices.size(); i++) {

                short posCoordIndex = positionIndices.get(i);
                short textureCoordIndex = textureCoordIndices.get(i);
                short normalIndex = normalIndices.get(i);


                String key = posCoordIndex + ":" + textureCoordIndex + ":" + normalIndex;
                Vertex v;
                if (map.containsKey(key)) {

                    v = map.get(key);

                } else {

                    v = new Vertex();
                    v.position = positionVertices.get(posCoordIndex);
                    v.tex = textureVertices.get(textureCoordIndex);
                    v.normal = vertexNormals.get(normalIndex);
                    v.index = index;
                    index ++;

                }

                finalVertices.add(v);
                finalIndices.add(v.index);
            }


            ObjObject object = new ObjObject(finalIndices, finalVertices);
            if( materials != null )
                object.setMaterials(materials);

            object.initialize();
            Log.v(TAG, "Successfully loaded model from OBJ");
//            ObjObjectes.put(ObjObjectName, object);
            return object;

        } catch (IOException ex) {
            Log.e(TAG, "Could not find the model");
        }
        return null;
    }

    public int loadTexture(String filename) {

        final int[] textureHandle = new int[1];

        Log.w(TAG, "ETC1 texture support: " + ETC1Util.isETC1Supported());

        GLES20.glGenTextures(1, textureHandle, 0);

        //BufferedReader br = null;
        InputStream is = null;

        try {
            is = new FileInputStream(filename);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            ETC1Util.loadTexture(GLES10.GL_TEXTURE_2D, 0, 0, GLES10.GL_RGB, GLES10.GL_UNSIGNED_SHORT_5_6_5, is);

            Log.v(TAG, "Loaded texture: " + filename);
        } catch (IOException ex) {

        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {

            }
        }

        if (textureHandle[0] == 0 ) {
            throw new RuntimeException("Error loading texture.");
        }

        return textureHandle[0];
    }

//    public ObjObject getObjObjectByName(String name) {
//        return ObjObjectes.get(name);
//    }
}