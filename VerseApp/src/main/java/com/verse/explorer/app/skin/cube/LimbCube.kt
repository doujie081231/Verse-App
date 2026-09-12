package com.verse.explorer.app.skin.cube

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.ArrayList

import javax.microedition.khronos.opengles.GL10

open class LimbCube(
    scaleX: Float, scaleY: Float, scaleZ: Float,
    offsetX: Float, offsetY: Float, offsetZ: Float,
    mainAxisX: Float, mainAxisY: Float, mainAxisZ: Float,
    mainStepValue: Float, mainMaxAngle: Float, mainMinAngle: Float,
    subAxisX: Float, subAxisY: Float, subAxisZ: Float,
    subStepValue: Float, subMaxAngle: Float, subMinAngle: Float
) {

    protected var scale = floatArrayOf(scaleX, scaleY, scaleZ)
    protected var offset = floatArrayOf(offsetX, offsetY, offsetZ)
    protected var faceIndecies = intArrayOf(
        0, 8, 9, 1,   // 面0: 下前侧
        8, 3, 2, 9,   // 面1: 上前侧
        3, 7, 6, 2,   // 面2: 上后侧
        0, 4, 5, 1,   // 面3: 下后侧
        1, 9, 10, 5,  // 面4: 右下侧
        9, 2, 6, 10,  // 面5: 右上侧
        4, 11, 8, 0,  // 面6: 左下侧
        11, 7, 3, 8,  // 面7: 左上侧
        5, 10, 11, 4, // 面8: 后端面（连接身体）
        10, 6, 7, 11  // 面9: 后端面上部
    )

    // 基础顶点坐标（归一化）- 12个顶点
    protected var vertices = floatArrayOf(
        -1.0f, -1.0f, 1.0f,  // 顶点0: 前下左
        1.0f, -1.0f, 1.0f,   // 顶点1: 前下右
        1.0f, 1.0f, 1.0f,    // 顶点2: 前上右
        -1.0f, 1.0f, 1.0f,   // 顶点3: 前上左
        -1.0f, -1.0f, -1.0f, // 顶点4: 后下左
        1.0f, -1.0f, -1.0f,  // 顶点5: 后下右
        1.0f, 1.0f, -1.0f,   // 顶点6: 后上右
        -1.0f, 1.0f, -1.0f,  // 顶点7: 后上左
        -1.0f, 0.0f, 1.0f,   // 顶点8: 中前左（关节点）
        1.0f, 0.0f, 1.0f,    // 顶点9: 中前右（关节点）
        1.0f, 0.0f, -1.0f,   // 顶点10: 中后右（关节点）
        -1.0f, 0.0f, -1.0f   // 顶点11: 中后左（关节点）
    )

    protected lateinit var faceVertices: FloatArray

    // 法向量数组 - 每个面4个法向量，共10个面
    protected var normalVertices = floatArrayOf(
        // 面0: 下前侧 (Z=+1)
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
        // 面1: 上前侧 (Z=+1)
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
        // 面2: 上后侧 (Y=+1)
        0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        // 面3: 下后侧 (Y=-1)
        0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f,
        // 面4: 右下侧 (X=+1)
        1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        // 面5: 右上侧 (X=+1)
        1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        // 面6: 左下侧 (X=-1)
        -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        // 面7: 左上侧 (X=-1)
        -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        // 面8: 后端面下部 (Z=-1) - 连接身体
        0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f,
        // 面9: 后端面上部 (Z=-1) - 连接身体
        0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f
    )

    protected var mainAngleAxis = floatArrayOf(mainAxisX, mainAxisY, mainAxisZ)
    protected var mainAngle = 0.0f
    protected var mainMaxAngle = mainMaxAngle
    protected var mainMinAngle = mainMinAngle
    protected var mainStepValue = mainStepValue
    protected var subAngleAxis = floatArrayOf(subAxisX, subAxisY, subAxisZ)
    protected var subAngle = 0.0f
    protected var subMaxAngle = subMaxAngle
    protected var subMinAngle = subMinAngle
    protected var subStepValue = subStepValue

    /** 摆动幅度缩放（0 = 静止；由 SkinModel 按动作配置，区分肢体强弱） */
    var swingScale: Float = 1.0f

    /** 摆动速度倍数（区别走路/跑步等） */
    var swingSpeed: Float = 1.0f

    protected lateinit var vertexBuffer: FloatBuffer
    protected lateinit var normalVertexBuffer: FloatBuffer
    protected var textureBuffers = ArrayList<FloatBuffer>()

    init {
        this.faceVertices = FloatArray(this.faceIndecies.size * 3)
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

    fun draw(gl10: GL10, isRunning: Boolean) {
        gl10.glEnable(GL10.GL_BLEND)
        gl10.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
        gl10.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl10.glEnableClientState(GL10.GL_NORMAL_ARRAY)
        gl10.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl10.glNormalPointer(GL10.GL_FLOAT, 0, this.normalVertexBuffer)
        for (i in 0 until this.faceIndecies.size) {
            val n = this.faceIndecies[i]
            val n2 = this.vertices[n * 3 + 1] * this.scale[1] / 2.0f
            val n3 = this.vertices[n * 3 + 2] * this.scale[2] / 2.0f
            this.vertexBuffer.put(i * 3, this.vertices[n * 3] * this.scale[0] / 2.0f)
            this.vertexBuffer.put(i * 3 + 1, n2)
            this.vertexBuffer.put(i * 3 + 2, n3)
        }
        gl10.glVertexPointer(3, GL10.GL_FLOAT, 0, this.vertexBuffer)
        gl10.glPushMatrix()
        gl10.glTranslatef(this.offset[0], this.offset[1], this.offset[2])
        if (isRunning && swingScale > 0.0f) {
            // 应用主旋转（手臂前后摆动）
            gl10.glTranslatef(0.0f, this.scale[1] / 4.0f * 3.0f, 0.0f)
            gl10.glRotatef(this.mainAngle * swingScale, this.mainAngleAxis[0], this.mainAngleAxis[1], this.mainAngleAxis[2])
            gl10.glTranslatef(0.0f, -this.scale[1] / 4.0f * 3.0f, 0.0f)
            // 更新主角度（速度按 swingSpeed 缩放，幅度边界按 swingScale 缩放）
            this.mainAngle += this.mainStepValue * swingSpeed
            val maxAngle = this.mainMaxAngle * swingScale
            val minAngle = this.mainMinAngle * swingScale
            if (this.mainAngle >= maxAngle) {
                this.mainStepValue *= -1.0f
                this.mainAngle = maxAngle
            } else if (this.mainAngle <= minAngle) {
                this.mainStepValue *= -1.0f
                this.mainAngle = minAngle
            }
        }
        for (i in 0 until this.textureBuffers.size) {
            gl10.glTexCoordPointer(2, GL10.GL_FLOAT, 0, this.textureBuffers[i])
            for (j in 0 until 10) {
                gl10.glDrawArrays(GL10.GL_TRIANGLE_FAN, j * 4, 4)
            }
        }
        gl10.glPopMatrix()
        gl10.glDisable(GL10.GL_BLEND)
        gl10.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl10.glDisableClientState(GL10.GL_NORMAL_ARRAY)
        gl10.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}