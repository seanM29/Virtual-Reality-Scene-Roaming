attribute vec4 vertexPosition; 
attribute vec2 vertexTexCoord;
varying vec2 texCoord;
uniform mat4 modelViewProjectionMatrix;
void main() {
   gl_Position = modelViewProjectionMatrix * vertexPosition;
   texCoord = vertexTexCoord;
}