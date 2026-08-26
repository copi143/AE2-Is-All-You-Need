#version 150

uniform sampler2D Sampler0;
uniform float PxRange;
uniform float Weight;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float median3(vec3 p) {
    return max(min(p.r, p.g), min(max(p.r, p.g), p.b));
}

void main() {
    vec4 msdf = texture(Sampler0, texCoord0);
    float msdfDist = median3(msdf.rgb) - 0.5 + Weight;
    float sdfDist = msdf.a - 0.5 + Weight;
    float screenPx = max(PxRange, 1.0);
    float msdfOpa = clamp(screenPx * msdfDist + 0.5, 0.0, 1.0);
    float sdfOpa = clamp(screenPx * sdfDist + 0.5, 0.0, 1.0);
    float w = smoothstep(5.0, 10.0, screenPx);
    float opa = mix(sdfOpa, msdfOpa, w) * vertexColor.a;
    if (opa < 0.004) discard;
    fragColor = vec4(vertexColor.rgb * opa, opa);
}
