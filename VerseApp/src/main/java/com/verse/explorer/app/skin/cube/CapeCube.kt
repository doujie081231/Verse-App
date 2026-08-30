package com.verse.explorer.app.skin.cube

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.ArrayList

import javax.microedition.khronos.opengles.GL10

/**
 * 披风立方体类 - 用于渲染玩家披风
 * 披风是一个扁平的矩形面片，从玩家背部垂下
 */
open class CapeCube(
    private val sizeX: Float,
    private val sizeY: Float,
    private val sizeZ: Float,
    offsetX: Float,
    offsetY: Float,
    offsetZ: Float
) {

    protected var scale = floatArrayOf(sizeX, sizeY, sizeZ)  // 缩放参数 [x, y, z]
    protected var offset = floatArrayOf(offsetX, offsetY, offsetZ)  // 位置偏移 [x, y, z]

    // GL顶点坐标数组（右手坐标系）
    protected var faceVertices = floatArrayOf(
        // Face 0: 前面 (Z=+1)
        -1.0f, -1.0f, 1.0f,
        -1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f,
        1.0f, -1.0f, 1.0f,
        // Face 1: 上面 (Y=+1)
        -1.0f, 1.0f, 1.0f,
        -1.0f, 1.0f, -1.0f,
        1.0f, 1.0f, -1.0f,
        1.0f, 1.0f, 1.0f,
        // Face 2: 下面 (Y=-1)
        1.0f, -1.0f, 1.0f,
        1.0f, -1.0f, -1.0f,
        -1.0f, -1.0f, -1.0f,
        -1.0f, -1.0f, 1.0f,
        // Face 3: 右面 (X=+1)
        1.0f, -1.0f, 1.0f,
        1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, -1.0f,
        1.0f, -1.0f, -1.0f,
        // Face 4: 左面 (X=-1)
        -1.0f, -1.0f, -1.0f,
        -1.0f, 1.0f, -1.0f,
        -1.0f, 1.0f, 1.0f,
        -1.0f, -1.0f, 1.0f,
        // Face 5: 后面 (Z=-1)
        1.0f, -1.0f, -1.0f,
        1.0f, 1.0f, -1.0f,
        -1.0f, 1.0f, -1.0f,
        -1.0f, -1.0f, -1.0f
    )

    // 法向量数组 - 每个面4个顶点共享同一个法向量方向
    protected var normalVertices = floatArrayOf(
        // Face 0: 前面 - 法向量指向+Z
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
        // Face 1: 上面 - 法向量指向+Y
        0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        // Face 2: 下面 - 法向量指向-Y
        0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f,
        // Face 3: 右面 - 法向量指向+X
        1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        // Face 4: 左面 - 法向量指向-X
        -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        // Face 5: 后面 - 法向量指向-Z
        0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f
    )

    protected lateinit var vertexBuffer: FloatBuffer           // 顶点缓冲
    protected lateinit var normalVertexBuffer: FloatBuffer     // 法向量缓冲
    protected var textureBuffers = ArrayList<FloatBuffer>()    // 纹理缓冲列表

    init {
        // 根据缩放参数调整顶点坐标
        for (i in 0 until 24) {
            this.faceVertices[i * 3] = this.faceVertices[i * 3] * this.scale[0] / 2.0f
            this.faceVertices[i * 3 + 1] = this.faceVertices[i * 3 + 1] * this.scale[1] / 2.0f
            this.faceVertices[i * 3 + 2] = this.faceVertices[i * 3 + 2] * this.scale[2] / 2.0f
        }
        // 创建顶点缓冲
        val allocateDirectFace = ByteBuffer.allocateDirect(this.faceVertices.size * 4)
        allocateDirectFace.order(ByteOrder.nativeOrder())
        this.vertexBuffer = allocateDirectFace.asFloatBuffer()
        this.vertexBuffer.put(this.faceVertices)
        this.vertexBuffer.position(0)
        // 创建法向量缓冲
        val allocateDirectNormal = ByteBuffer.allocateDirect(this.normalVertices.size * 4)
        allocateDirectNormal.order(ByteOrder.nativeOrder())
        this.normalVertexBuffer = allocateDirectNormal.asFloatBuffer()
        this.normalVertexBuffer.put(this.normalVertices)
        this.normalVertexBuffer.position(0)
        this.textureBuffers = ArrayList()
    }

    /**
     * 添加纹理坐标缓冲
     * @param texture 纹理坐标数组
     * @return 创建的纹理缓冲
     */
    fun addTextures(texture: FloatArray): FloatBuffer {
        val allocateDirect = ByteBuffer.allocateDirect(texture.size * 4)
        allocateDirect.order(ByteOrder.nativeOrder())
        val floatBuffer = allocateDirect.asFloatBuffer()
        floatBuffer.put(texture)
        floatBuffer.position(0)
        this.textureBuffers.add(floatBuffer)
        return floatBuffer
    }

    /**
     * 清空所有纹理缓冲
     */
    fun clearAllTextures() {
        this.textureBuffers.clear()
    }

    /**
     * 绘制披风
     * @param gl10 OpenGL ES 1.0上下文
     */
    fun draw(gl10: GL10) {
        gl10.glEnable(GL10.GL_BLEND)
        gl10.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
        gl10.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl10.glEnableClientState(GL10.GL_NORMAL_ARRAY)
        gl10.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl10.glVertexPointer(3, GL10.GL_FLOAT, 0, this.vertexBuffer)
        gl10.glNormalPointer(GL10.GL_FLOAT, 0, this.normalVertexBuffer)
        // 保存当前矩阵并应用变换
        gl10.glPushMatrix()
        gl10.glTranslatef(this.offset[0], this.offset[1], this.offset[2])
        // 披风倾斜效果：绕X轴旋转12度
        gl10.glTranslatef(0.0f, this.scale[1] / 4.0f * 3.0f, 0.0f)
        gl10.glRotatef(12f, 1f, 0f, 0f)
        gl10.glTranslatef(0.0f, -this.scale[1] / 4.0f * 3.0f, 0.0f)
        // 绘制所有纹理层
        for (i in 0 until this.textureBuffers.size) {
            gl10.glTexCoordPointer(2, GL10.GL_FLOAT, 0, this.textureBuffers[i])
            for (j in 0 until 6) {
                gl10.glDrawArrays(GL10.GL_TRIANGLE_FAN, j * 4, 4)
            }
        }
        // 恢复矩阵并禁用相关状态
        gl10.glPopMatrix()
        gl10.glDisable(GL10.GL_BLEND)
        gl10.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl10.glDisableClientState(GL10.GL_NORMAL_ARRAY)
        gl10.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}