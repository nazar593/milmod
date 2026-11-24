#version 150

uniform sampler2D DiffuseSampler;
uniform float Mono;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    float luminance = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
    float tone = clamp(0.045 + luminance * 0.91, 0.0, 1.0);
    fragColor = vec4(mix(scene.rgb, vec3(tone), Mono), scene.a);
}
