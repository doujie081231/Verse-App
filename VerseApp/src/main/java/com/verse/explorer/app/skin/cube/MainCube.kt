package com.verse.explorer.app.skin.cube

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.ArrayList

import javax.microedition.khronos.opengles.GL10

open class MainCube(
    private val sizeX: Float,
    private val sizeY: Float,
    private val sizeZ: Float,
    offsetX: Float,
    offsetY: Float,
    offsetZ: Float
) {

    protected var offset = floatArrayOf(offsetX, offsetY, offsetZ)

    // GL顶点坐标数组（右手坐标系）
    // 每个面4个顶点，每个顶点由x, y, z三个分量组成
    // 面顺序：前(Face 0)、上(Face 1)、下(Face 2)、右(Face 3)、左(Face 4)、后(Face 5)
    // 顶点顺序：顺时针方向（从面的外侧观察）
    protected var faceVertices = floatArrayOf(
        // Face 0: 前面 (Z=+1) - 从前方看顺时针
        -1.0f, -1.0f, 1.0f,  // 左下
        -1.0f, 1.0f, 1.0f,   // 左上
        1.0f, 1.0f, 1.0f,    // 右上
        1.0f, -1.0f, 1.0f,   // 右下
        // Face 1: 上面 (Y=+1) - 从上方看顺时针
        -1.0f, 1.0f, 1.0f,   // 后左
        -1.0f, 1.0f, -1.0f,  // 前左
        1.0f, 1.0f, -1.0f,   // 前右
        1.0f, 1.0f, 1.0f,    // 后右
        // Face 2: 下面 (Y=-1) - 从下方看顺时针
        1.0f, -1.0f, 1.0f,   // 后右
        1.0f, -1.0f, -1.0f,  // 前右
        -1.0f, -1.0f, -1.0f, // 前左
        -1.0f, -1.0f, 1.0f,  // 后左
        // Face 3: 右面 (X=+1) - 从右面看顺时针
        1.0f, -1.0f, 1.0f,   // 后下
        1.0f, 1.0f, 1.0f,    // 后上
        1.0f, 1.0f, -1.0f,   // 前上
        1.0f, -1.0f, -1.0f,  // 前下
        // Face 4: 左面 (X=-1) - 从左面看顺时针
        -1.0f, -1.0f, -1.0f, // 前下
        -1.0f, 1.0f, -1.0f,  // 前上
        -1.0f, 1.0f, 1.0f,   // 后上
        -1.0f, -1.0f, 1.0f,  // 后下
        // Face 5: 后面 (Z=-1) - 从后方看顺时针
        1.0f, -1.0f, -1.0f,  // 左下
        1.0f, 1.0f, -1.0f,   // 左上
        -1.0f, 1.0f, -1.0f,  // 右上
        -1.0f, -1.0f, -1.0f  // 右下
    )

    // 法向量数组 - 每个顶点对应一个法向量，用于光照计算
    // 法向量方向：指向面的外侧，与面垂直
    protected var normalVertices = floatArrayOf(
        // Face 0: 前面 - 法向量指向+Z方向
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
        // Face 1: 上面 - 法向量指向+Y方向
        0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        // Face 2: 下面 - 法向量指向-Y方向
        0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f,
        // Face 3: 右面 - 法向量指向+X方向
        1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        // Face 4: 左面 - 法向量指向-X方向
        -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        // Face 5: 后面 - 法向量指向-Z方向
        0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f
    )

    protected lateinit var vertexBuffer: FloatBuffer
    protected lateinit var normalVertexBuffer: FloatBuffer
    protected var textureBuffers = ArrayList<FloatBuffer>()

    init {
        // 将归一化顶点坐标缩放到实际尺寸
        for (i in 0 until 24) {
            this.faceVertices[i * 3] = this.faceVertices[i * 3] * sizeX / 2.0f
            this.faceVertices[i * 3 + 1] = this.faceVertices[i * 3 + 1] * sizeY / 2.0f
            this.faceVertices[i * 3 + 2] = this.faceVertices[i * 3 + 2] * sizeZ / 2.0f
        }
        val allocateDirectFace = ByteBuffer.allocateDirect(this.faceVertices.size * 4)
        allocateDirectFace.order(ByteOrder.nativeOrder())
        this.vertexBuffer = allocateDirectFace.asFloatBuffer()
        this.vertexBuffer.put(this.faceVertices)
        this.vertexBuffer.position(0)
        val allocateDirectNormal = ByteBuffer.allocateDirect(this.normalVertices.size * 4)
        allocateDirectNormal.order(ByteOrder.nativeOrder())
        this.normalVertexBuffer = allocateDirectNormal.asFloatBuffer()
        this.normalVertexBuffer.put(this.normalVertices)
        this.normalVertexBuffer.position(0)
        this.textureBuffers = ArrayList()
    }

    fun addTextures(texture: FloatArray): FloatBuffer {
        val allocateDirect = ByteBuffer.allocateDirect(texture.size * 4)
        allocateDirect.order(ByteOrder.nativeOrder())
        val floatBuffer = allocateDirect.asFloatBuffer()
        floatBuffer.put(texture)
        floatBuffer.position(0)
        this.textureBuffers.add(floatBuffer)
        return floatBuffer
    }

    fun clearAllTextures() {
        this.textureBuffers.clear()
    }

    fun draw(gl10: GL10) {
        gl10.glEnable(GL10.GL_BLEND)
        gl10.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
        gl10.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl10.glEnableClientState(GL10.GL_NORMAL_ARRAY)
        gl10.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl10.glVertexPointer(3, GL10.GL_FLOAT, 0, this.vertexBuffer)
        gl10.glNormalPointer(GL10.GL_FLOAT, 0, this.normalVertexBuffer)
        gl10.glPushMatrix()
        gl10.glTranslatef(this.offset[0], this.offset[1], this.offset[2])
        for (i in 0 until this.textureBuffers.size) {
            gl10.glTexCoordPointer(2, GL10.GL_FLOAT, 0, this.textureBuffers[i])
            for (j in 0 until 6) {
                gl10.glDrawArrays(6, j * 4, 4)
            }
        }
        gl10.glPopMatrix()
        gl10.glDisable(GL10.GL_BLEND)
        gl10.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl10.glDisableClientState(GL10.GL_NORMAL_ARRAY)
        gl10.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}