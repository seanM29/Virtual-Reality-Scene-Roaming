package com.google.vr.sdk.samples.treasurehunt;
import android.speech.RecognitionService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by 54179 on 2017/1/4.
 */

public class OBJLoader {
    private String objectData;
    private List<Float> verts;
    private List<Float> vertNormals;
    private List<Float> textures;
    public Unpacked unpacked;

    class Unpacked {
        List<Float> verts;
        List<Float> norms;
        List<Float> textures;
        HashMap<String, Integer> hashIndices;
        List<Integer> indices;
        int index = 0;

        public Unpacked() {
            verts = new ArrayList<>();
            norms = new ArrayList<>();
            textures = new ArrayList<>();
            hashIndices = new HashMap<>();
            indices = new ArrayList<>();
        }
    }
    public float[] getVertices(){
        float[] res;
        res = new float[unpacked.verts.size()];
        for (int ii = 0;ii<unpacked.verts.size();ii++){
            res[ii] = unpacked.verts.get(ii);
        }
        return res;
    }
    public float[] getNormals(){
        float[] res;
        res = new float[unpacked.norms.size()];
        for (int i = 0;i<res.length;i++){
            res[i] = unpacked.norms.get(i);
        }
        return res;
    }
    public float[] getTexture(){
        float[] res;
        res = new float[unpacked.textures.size()];
        for (int i = 0;i<res.length;i++){
            res[i] = unpacked.textures.get(i);
        }
        return res;
    }
    public int[] getIndex(){
        int[] res;
        res = new int[unpacked.indices.size()];
        for (int i=0;i<res.length;i++){
            res[i] = unpacked.indices.get(i);
        }
        return res;
    }
    public OBJLoader(String data) {
        objectData = data;

        verts = new ArrayList<>();
        vertNormals = new ArrayList<>();
        textures = new ArrayList<>();
        unpacked = new Unpacked();

        String[] lines = objectData.split("\n");
        Pattern VERTEX_RE = Pattern.compile("^v\\s");
        Pattern NORMAL_RE = Pattern.compile("^vn\\s");
        Pattern TEXTURE_RE = Pattern.compile("^vt\\s");
        Pattern FACE_RE = Pattern.compile("^f\\s");
        String WHITESPACE_RE = "\\s+";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String[] elements = line.split(WHITESPACE_RE);
            elements = Arrays.copyOfRange(elements, 1, elements.length);

            if(VERTEX_RE.matcher(line).find()) {
                for (String element: elements) {
                    verts.add(Float.valueOf(element));
                }
            } else if (NORMAL_RE.matcher(line).find()) {
                for (String element: elements) {
                    vertNormals.add(Float.valueOf(element));
                }
            } else if (TEXTURE_RE.matcher(line).find()) {
                for (String element: elements) {
                    textures.add(Float.valueOf(element));
                }
            } else if (FACE_RE.matcher(line).find()) {
                boolean quad = false;

                for (int j = 0, eleLen = elements.length; j < eleLen; j++) {
                    if(j == 3 && !quad) {
                        j = 2;
                        quad = true;
                    }

                    if(unpacked.hashIndices.containsKey(elements[j])) {
                        unpacked.indices.add(unpacked.hashIndices.get(elements[j]));
                    } else {
                        String[] vertex = elements[j].split("/");

                        unpacked.verts.add(verts.get((Integer.valueOf(vertex[0]) - 1) * 3 + 0));
                        unpacked.verts.add(verts.get((Integer.valueOf(vertex[0]) - 1) * 3 + 1));
                        unpacked.verts.add(verts.get((Integer.valueOf(vertex[0]) - 1) * 3 + 2));

                        if(textures.size() > 0) {
                            unpacked.textures.add(textures.get((Integer.valueOf(vertex[1]) - 1) * 2 + 0));
                            unpacked.textures.add(textures.get((Integer.valueOf(vertex[1]) - 1) * 2 + 1));
                        }

                        if(vertNormals.size() > 0) {
                            unpacked.norms.add(vertNormals.get((Integer.valueOf(vertex[2]) - 1) * 3 + 0));
                            unpacked.norms.add(vertNormals.get((Integer.valueOf(vertex[2]) - 1) * 3 + 1));
                            unpacked.norms.add(vertNormals.get((Integer.valueOf(vertex[2]) - 1) * 3 + 2));
                        }

                        unpacked.hashIndices.put(elements[j], unpacked.index);
                        unpacked.indices.add(unpacked.index);
                        unpacked.index++;

                    }

                    if(j == 3 && quad) {
                        unpacked.indices.add(unpacked.hashIndices.get(elements[0]));
                    }
                }
            }
        }
    }
}
