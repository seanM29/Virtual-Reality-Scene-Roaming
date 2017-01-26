precision mediump float; 
varying vec2 texCoord;
uniform sampler2D texSampler2D;
void main()
{
   gl_FragColor = texture2D(texSampler2D, texCoord);
//               gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
} ;