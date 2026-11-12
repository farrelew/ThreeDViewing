package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Main {
    private long window;
    private final int WIDTH = 1024, HEIGHT = 768;

    // Toggle State Indikator Penilaian
    private boolean useBlinnPhong = true;
    private boolean flatShading = false;
    private boolean enableDepthTest = true;
    private boolean enableCulling = true;
    private boolean useTexture = false;

    private int sphereVAO, sphereVBO, sphereIndexCount;
    private int planeVAO, planeVBO;
    private int phongProgram, shadowProgram;
    private ShadowMapFBO shadowFBO;

    // GLSL Shaders Embedded
    private final String SHADOW_VERT_SRC = 
        "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "uniform mat4 lightSpaceMatrix;\n" +
        "uniform mat4 model;\n" +
        "void main() {\n" +
        "    gl_Position = lightSpaceMatrix * model * vec4(aPos, 1.0);\n" +
        "}\n";

    private final String SHADOW_FRAG_SRC = 
        "#version 330 core\n" +
        "void main() {\n" +
        "}\n";

    private final String PHONG_VERT_SRC = 
        "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "layout (location = 1) in vec3 aNormal;\n" +
        "layout (location = 2) in vec2 aTexCoords;\n" +
        "out vec3 FragPos;\n" +
        "out vec3 Normal;\n" +
        "out vec2 TexCoords;\n" +
        "out vec4 FragPosLightSpace;\n" +
        "uniform mat4 model;\n" +
        "uniform mat4 view;\n" +
        "uniform mat4 projection;\n" +
        "uniform mat4 lightSpaceMatrix;\n" +
        "void main() {\n" +
        "    FragPos = vec3(model * vec4(aPos, 1.0));\n" +
        "    mat3 normalMatrix = transpose(inverse(mat3(model)));\n" +
        "    Normal = normalMatrix * aNormal;\n" +
        "    TexCoords = aTexCoords;\n" +
        "    FragPosLightSpace = lightSpaceMatrix * vec4(FragPos, 1.0);\n" +
        "    gl_Position = projection * view * vec4(FragPos, 1.0);\n" +
        "}\n";

    private final String PHONG_FRAG_SRC = 
        "#version 330 core\n" +
        "out vec4 FragColor;\n" +
        "in vec3 FragPos;\n" +
        "in vec3 Normal;\n" +
        "in vec2 TexCoords;\n" +
        "in vec4 FragPosLightSpace;\n" +
        "uniform vec3 lightPos;\n" +
        "uniform vec3 viewPos;\n" +
        "uniform vec3 lightColor;\n" +
        "uniform vec3 objectColor;\n" +
        "uniform sampler2D textureSampler;\n" +
        "uniform sampler2D shadowMap;\n" +
        "uniform bool useTexture;\n" +
        "uniform bool useBlinnPhong;\n" +
        "uniform bool flatShading;\n" +
        "float CalculationShadow(vec4 fragPosLightSpace, vec3 normal, vec3 lightDir) {\n" +
        "    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;\n" +
        "    projCoords = projCoords * 0.5 + 0.5;\n" +
        "    if(projCoords.z > 1.0) return 0.0;\n" +
        "    float closestDepth = texture(shadowMap, projCoords.xy).r;\n" +
        "    float currentDepth = projCoords.z;\n" +
        "    float bias = max(0.05 * (1.0 - dot(normal, lightDir)), 0.005);\n" +
        "    float shadow = 0.0;\n" +
        "    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);\n" +
        "    for(int x = -1; x <= 1; ++x) {\n" +
        "        for(int y = -1; y <= 1; ++y) {\n" +
        "            float pcfDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;\n" +
        "            shadow += currentDepth - bias > pcfDepth ? 1.0 : 0.0;\n" +
        "        }\n" +
        "    }\n" +
        "    shadow /= 9.0;\n" +
        "    return shadow;\n" +
        "}\n" +
        "void main() {\n" +
        "    vec3 norm = flatShading ? normalize(cross(dFdx(FragPos), dFdy(FragPos))) : normalize(Normal);\n" +
        "    vec3 lightDir = normalize(lightPos - FragPos);\n" +
        "    float ambientStrength = 0.2;\n" +
        "    vec3 ambient = ambientStrength * lightColor;\n" +
        "    float diff = max(dot(norm, lightDir), 0.0);\n" +
        "    vec3 diffuse = diff * lightColor;\n" +
        "    float specularStrength = 0.8;\n" +
        "    vec3 viewDir = normalize(viewPos - FragPos);\n" +
        "    float spec = 0.0;\n" +
        "    if (useBlinnPhong) {\n" +
        "        vec3 halfwayDir = normalize(lightDir + viewDir);\n" +
        "        spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);\n" +
        "    } else {\n" +
        "        vec3 reflectDir = reflect(-lightDir, norm);\n" +
        "        spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0);\n" +
        "    }\n" +
        "    vec3 specular = specularStrength * spec * lightColor;\n" +
        "    float shadow = CalculationShadow(FragPosLightSpace, norm, lightDir);\n" +
        "    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb : objectColor;\n" +
        "    vec3 lighting = (ambient + (1.0 - shadow) * (diffuse + specular));\n" +
        "    FragColor = vec4(lighting * baseColor, 1.0);\n" +
        "}\n";

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("Gagal menginisialisasi GLFW!");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Minggu 11 - Visual Realism Suite (Sphere Mesh)", 0, 0);
        if (window == 0) throw new RuntimeException("Gagal membuat jendela GLFW!");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        // System Konsol Kontrol Interaktif (Keyboard Triggers)
        System.out.println("=================================================");
        System.out.println(" KONTROL INTERAKTIF VISUAL REALISM SUITE:");
        System.out.println(" [B] Toggle Shading Model: Blinn-Phong vs Phong");
        System.out.println(" [F] Toggle Shading Mode : Smooth vs Flat Shading");
        System.out.println(" [Z] Toggle HSR          : Z-Buffer Depth Test");
        System.out.println(" [C] Toggle HSR          : Back-Face Culling");
        System.out.println(" [T] Toggle Texture Mode : Texture / Solid Color");
        System.out.println("=================================================");

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_B) {
                    useBlinnPhong = !useBlinnPhong;
                    System.out.println("[KEY B] Shading Model set to: " + (useBlinnPhong ? "Blinn-Phong" : "Phong Standard"));
                }
                if (key == GLFW_KEY_F) {
                    flatShading = !flatShading;
                    System.out.println("[KEY F] Normal Interpolation set to: " + (flatShading ? "Flat Shading (Faceted)" : "Smooth Shading"));
                }
                if (key == GLFW_KEY_Z) {
                    enableDepthTest = !enableDepthTest;
                    System.out.println("[KEY Z] Z-Buffer Depth Test: " + (enableDepthTest ? "ENABLED" : "DISABLED"));
                }
                if (key == GLFW_KEY_C) {
                    enableCulling = !enableCulling;
                    System.out.println("[KEY C] Back-Face Culling: " + (enableCulling ? "ENABLED" : "DISABLED"));
                }
                if (key == GLFW_KEY_T) {
                    useTexture = !useTexture;
                    System.out.println("[KEY T] Texture Mapping: " + (useTexture ? "ENABLED" : "DISABLED"));
                }
            }
        });

        phongProgram = compileShaderProgram(PHONG_VERT_SRC, PHONG_FRAG_SRC);
        shadowProgram = compileShaderProgram(SHADOW_VERT_SRC, SHADOW_FRAG_SRC);

        setupSphereMesh(1.0f, 32, 32); // Generasi Objek Bulat 3D
        setupPlaneMesh();
        shadowFBO = new ShadowMapFBO();
    }

    private void loop() {
        Vector3f lightPos = new Vector3f(2.0f, 4.0f, 2.0f);

        while (!glfwWindowShouldClose(window)) {
            // Pass 1: Depth Pass (Shadow Map FBO)
            shadowFBO.bindForWriting();
            glUseProgram(shadowProgram);

            Matrix4f lightProjection = new Matrix4f().ortho(-5.0f, 5.0f, -5.0f, 5.0f, 1.0f, 7.5f);
            Matrix4f lightView = new Matrix4f().lookAt(lightPos, new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));
            Matrix4f lightSpaceMatrix = new Matrix4f(lightProjection).mul(lightView);

            glUniformMatrix4fv(glGetUniformLocation(shadowProgram, "lightSpaceMatrix"), false, lightSpaceMatrix.get(new float[16]));
            renderScene(shadowProgram);
            shadowFBO.unbind(WIDTH, HEIGHT);

            // Pass 2: Main Lighting Pass
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Terapkan HSR Manager (Indikator 3)
            HSRManager.setupDepthTest(enableDepthTest);
            HSRManager.setupBackFaceCulling(enableCulling);

            glUseProgram(phongProgram);

            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0), (float) WIDTH / HEIGHT, 0.1f, 100.0f);
            Matrix4f view = new Matrix4f().lookAt(new Vector3f(0.0f, 2.5f, 4.5f), new Vector3f(0.0f, 0.5f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));

            glUniformMatrix4fv(glGetUniformLocation(phongProgram, "projection"), false, projection.get(new float[16]));
            glUniformMatrix4fv(glGetUniformLocation(phongProgram, "view"), false, view.get(new float[16]));
            glUniformMatrix4fv(glGetUniformLocation(phongProgram, "lightSpaceMatrix"), false, lightSpaceMatrix.get(new float[16]));

            glUniform3f(glGetUniformLocation(phongProgram, "lightPos"), lightPos.x, lightPos.y, lightPos.z);
            glUniform3f(glGetUniformLocation(phongProgram, "viewPos"), 0.0f, 2.5f, 4.5f);
            glUniform3f(glGetUniformLocation(phongProgram, "lightColor"), 1.0f, 1.0f, 1.0f);

            glUniform1i(glGetUniformLocation(phongProgram, "useBlinnPhong"), useBlinnPhong ? 1 : 0);
            glUniform1i(glGetUniformLocation(phongProgram, "flatShading"), flatShading ? 1 : 0);
            glUniform1i(glGetUniformLocation(phongProgram, "useTexture"), useTexture ? 1 : 0);

            shadowFBO.bindDepthMap(1);
            glUniform1i(glGetUniformLocation(phongProgram, "shadowMap"), 1);

            renderScene(phongProgram);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderScene(int shader) {
        // Render Objek Bola 3D (Sphere)
        Matrix4f model = new Matrix4f().translate(0.0f, 1.0f, 0.0f).rotate((float) glfwGetTime() * 0.4f, 0.0f, 1.0f, 0.0f);
        glUniformMatrix4fv(glGetUniformLocation(shader, "model"), false, model.get(new float[16]));
        glUniform3f(glGetUniformLocation(shader, "objectColor"), 0.8f, 0.3f, 0.2f);
        glBindVertexArray(sphereVAO);
        glDrawElements(GL_TRIANGLES, sphereIndexCount, GL_UNSIGNED_INT, 0);

        // Render Bidang Alas (Lantai Penerima Bayangan)
        model = new Matrix4f();
        glUniformMatrix4fv(glGetUniformLocation(shader, "model"), false, model.get(new float[16]));
        glUniform3f(glGetUniformLocation(shader, "objectColor"), 0.5f, 0.5f, 0.5f);
        glBindVertexArray(planeVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    // Algoritma Generasi Objek Bulat 3D (Sphere Mesh Generator)
    private void setupSphereMesh(float radius, int rings, int sectors) {
        List<Float> verticesList = new ArrayList<>();
        List<Integer> indicesList = new ArrayList<>();

        float R = 1.0f / (float)(rings - 1);
        float S = 1.0f / (float)(sectors - 1);

        for (int r = 0; r < rings; ++r) {
            for (int s = 0; s < sectors; ++s) {
                float y = (float) Math.sin(-Math.PI / 2 + Math.PI * r * R);
                float x = (float) (Math.cos(2 * Math.PI * s * S) * Math.sin(Math.PI * r * R));
                float z = (float) (Math.sin(2 * Math.PI * s * S) * Math.sin(Math.PI * r * R));

                // Posisi
                verticesList.add(x * radius);
                verticesList.add(y * radius);
                verticesList.add(z * radius);

                // Normal Vektor
                verticesList.add(x);
                verticesList.add(y);
                verticesList.add(z);

                // Koordinat Tekstur UV
                verticesList.add(s * S);
                verticesList.add(r * R);
            }
        }

        for (int r = 0; r < rings - 1; ++r) {
            for (int s = 0; s < sectors - 1; ++s) {
                int first = (r * sectors) + s;
                int second = first + sectors;

                indicesList.add(first);
                indicesList.add(second);
                indicesList.add(first + 1);

                indicesList.add(second);
                indicesList.add(second + 1);
                indicesList.add(first + 1);
            }
        }

        float[] vertices = new float[verticesList.size()];
        for (int i = 0; i < verticesList.size(); i++) vertices[i] = verticesList.get(i);

        int[] indices = new int[indicesList.size()];
        for (int i = 0; i < indicesList.size(); i++) indices[i] = indicesList.get(i);

        sphereIndexCount = indices.length;

        sphereVAO = glGenVertexArrays();
        sphereVBO = glGenBuffers();
        int ebo = glGenBuffers();

        glBindVertexArray(sphereVAO);

        glBindBuffer(GL_ARRAY_BUFFER, sphereVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);
    }

    private void setupPlaneMesh() {
        float[] vertices = {
             5.0f, 0.0f,  5.0f,  0.0f, 1.0f, 0.0f,  2.0f, 0.0f,
            -5.0f, 0.0f,  5.0f,  0.0f, 1.0f, 0.0f,  0.0f, 0.0f,
            -5.0f, 0.0f, -5.0f,  0.0f, 1.0f, 0.0f,  0.0f, 2.0f,

             5.0f, 0.0f,  5.0f,  0.0f, 1.0f, 0.0f,  2.0f, 0.0f,
            -5.0f, 0.0f, -5.0f,  0.0f, 1.0f, 0.0f,  0.0f, 2.0f,
             5.0f, 0.0f, -5.0f,  0.0f, 1.0f, 0.0f,  2.0f, 2.0f
        };

        planeVAO = glGenVertexArrays();
        planeVBO = glGenBuffers();

        glBindVertexArray(planeVAO);
        glBindBuffer(GL_ARRAY_BUFFER, planeVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);
    }

    private int compileShaderProgram(String vertCode, String fragCode) {
        int vertShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertShader, vertCode);
        glCompileShader(vertShader);
        if (glGetShaderi(vertShader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Gagal kompilasi Vertex Shader: " + glGetShaderInfoLog(vertShader));
        }

        int fragShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragShader, fragCode);
        glCompileShader(fragShader);
        if (glGetShaderi(fragShader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Gagal kompilasi Fragment Shader: " + glGetShaderInfoLog(fragShader));
        }

        int program = glCreateProgram();
        glAttachShader(program, vertShader);
        glAttachShader(program, fragShader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Gagal link Shader Program: " + glGetProgramInfoLog(program));
        }

        glDeleteShader(vertShader);
        glDeleteShader(fragShader);

        return program;
    }
}