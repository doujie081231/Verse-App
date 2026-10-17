package com.verse.explorer.app.skin

import com.verse.explorer.app.skin.cube.arm.normal.LeftArm
import com.verse.explorer.app.skin.cube.arm.normal.LeftArmOverlay
import com.verse.explorer.app.skin.cube.arm.normal.RightArm
import com.verse.explorer.app.skin.cube.arm.normal.RightArmOverlay
import com.verse.explorer.app.skin.cube.arm.slim.LeftArmSlim
import com.verse.explorer.app.skin.cube.arm.slim.LeftArmSlimOverlay
import com.verse.explorer.app.skin.cube.arm.slim.RightArmSlim
import com.verse.explorer.app.skin.cube.arm.slim.RightArmSlimOverlay
import com.verse.explorer.app.skin.cube.body.Body
import com.verse.explorer.app.skin.cube.body.BodyOverlay
import com.verse.explorer.app.skin.cube.cape.Cape
import com.verse.explorer.app.skin.cube.head.Hat
import com.verse.explorer.app.skin.cube.head.Head
import com.verse.explorer.app.skin.cube.leg.LeftLeg
import com.verse.explorer.app.skin.cube.leg.LeftLegOverlay
import com.verse.explorer.app.skin.cube.leg.RightLeg
import com.verse.explorer.app.skin.cube.leg.RightLegOverlay
import com.verse.explorer.app.skin.cube.LimbCube

import javax.microedition.khronos.opengles.GL10

class SkinModel {

    private var head: Head? = null
    private var hat: Hat? = null
    private var body: Body? = null
    private var bodyOverlay: BodyOverlay? = null
    private var leftArm: LeftArm? = null
    private var leftArmOverlay: LeftArmOverlay? = null
    private var rightArm: RightArm? = null
    private var rightArmOverlay: RightArmOverlay? = null
    private var leftArmSlim: LeftArmSlim? = null
    private var leftArmSlimOverlay: LeftArmSlimOverlay? = null
    private var rightArmSlim: RightArmSlim? = null
    private var rightArmSlimOverlay: RightArmSlimOverlay? = null
    private var leftLeg: LeftLeg? = null
    private var leftLegOverlay: LeftLegOverlay? = null
    private var rightLeg: RightLeg? = null
    private var rightLegOverlay: RightLegOverlay? = null
    private var cape: Cape? = null

    private val rotate = floatArrayOf(0.0f, 0.0f, 0.0f)
    private val rotateStep = floatArrayOf(2.0f, 2.0f, 2.0f)

    /** 自动旋转展示开关 */
    var autoRotate = false

    var scale: Float = 1f
        set(value) {
            field = value
            initModel()
        }

    init {
        initModel()
    }

    private fun initModel() {
        this.head = Head(scale)
        this.hat = Hat(scale)
        this.body = Body(scale)
        this.bodyOverlay = BodyOverlay(scale)
        this.leftArm = LeftArm(scale)
        this.rightArm = RightArm(scale)
        this.leftArmOverlay = LeftArmOverlay(scale)
        this.rightArmOverlay = RightArmOverlay(scale)
        this.leftArmSlim = LeftArmSlim(scale)
        this.rightArmSlim = RightArmSlim(scale)
        this.leftArmSlimOverlay = LeftArmSlimOverlay(scale)
        this.rightArmSlimOverlay = RightArmSlimOverlay(scale)
        this.leftLeg = LeftLeg(scale)
        this.rightLeg = RightLeg(scale)
        this.leftLegOverlay = LeftLegOverlay(scale)
        this.rightLegOverlay = RightLegOverlay(scale)
        this.cape = Cape(scale)
    }

    fun reset() {
        initModel()
    }

    fun setRotate(n1: Float, n2: Float, n3: Float) {
        this.rotate[0] = n1
        this.rotate[1] = n2
        this.rotate[2] = n3
    }

    fun setRotateStep(n1: Float, n2: Float) {
        if (Math.abs(n1) >= 1.0f) {
            val rotate = this.rotate
            rotate[1] += this.rotateStep[1] * n1
        }
        if (Math.abs(n2) >= 1.0f) {
            val rotate = this.rotate
            rotate[0] += this.rotateStep[0] * n2
        }
    }

    fun setXRotation(n: Int) {
        this.rotate[0] = n.toFloat()
    }

    fun setYRotation(n: Int) {
        this.rotate[1] = n.toFloat()
    }

    fun setZRotation(n: Int) {
        this.rotate[2] = n.toFloat()
    }

    fun getXRotation(): Float {
        return this.rotate[0]
    }

    fun getYRotation(): Float {
        return this.rotate[1]
    }

    fun getZRotation(): Float {
        return this.rotate[2]
    }

    fun drawBodyModel(gl10: GL10, slim: Boolean) {
        // 自动旋转展示：绕 Y 轴匀速转动（旋转开关开启时；按真实时间推进，低帧率下速度不变）
        if (this.autoRotate) {
            val rotate = this.rotate
            rotate[1] += this.rotateStep[1] * 0.6f * this.lastFrameScale
        }
        // 呼吸姿态采样（按真实时间推进，与渲染帧率完全解耦）
        val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
        if (this.lastFrameNanos != 0L) {
            val dtSeconds = (nowNanos - this.lastFrameNanos) / 1_000_000_000f
            this.breatheTime += dtSeconds.coerceIn(0f, 0.25f)
            this.lastFrameScale = (dtSeconds * 60f).coerceIn(0f, 4f)
        }
        this.lastFrameNanos = nowNanos
        if (this.breatheTime >= CLIP_SECONDS) {
            this.breatheTime -= CLIP_SECONDS
        }
        val armX = sampleKey(KEY_ARM_X)
        val armZ = sampleKey(KEY_ARM_Z)
        val headX = sampleKey(KEY_HEAD_X)
        val bodyRX = sampleKey(KEY_BODY_RX)
        val bodyRZ = sampleKey(KEY_BODY_RZ)
        val bodyTY = sampleKey(KEY_BODY_TY)
        val arm = if (!slim) this.rightArm else this.rightArmSlim
        val armOverlay = if (!slim) this.rightArmOverlay else this.rightArmSlimOverlay
        val arm2 = if (!slim) this.leftArm else this.leftArmSlim
        val arm2Overlay = if (!slim) this.leftArmOverlay else this.leftArmSlimOverlay
        arm?.externalPose = true
        arm?.setExternalMainAngle(armX)
        arm?.setExternalSubAngle(armZ)
        armOverlay?.externalPose = true
        armOverlay?.setExternalMainAngle(armX)
        armOverlay?.setExternalSubAngle(armZ)
        arm2?.externalPose = true
        arm2?.setExternalMainAngle(armX)
        arm2?.setExternalSubAngle(-armZ)
        arm2Overlay?.externalPose = true
        arm2Overlay?.setExternalMainAngle(armX)
        arm2Overlay?.setExternalSubAngle(-armZ)

        gl10.glMatrixMode(GL10.GL_MODELVIEW)
        gl10.glTranslatef(0.0f, bodyTY * scale, 0.0f)
        gl10.glRotatef(this.rotate[0], 1.0f, 0.0f, 0.0f)
        gl10.glRotatef(this.rotate[1], 0.0f, 1.0f, 0.0f)
        gl10.glRotatef(this.rotate[2], 0.0f, 0.0f, 1.0f)
        gl10.glRotatef(bodyRX, 1.0f, 0.0f, 0.0f)
        gl10.glRotatef(bodyRZ, 0.0f, 0.0f, 1.0f)
        gl10.glPushMatrix()
        gl10.glTranslatef(0.0f, 8.0f * scale, 0.0f)
        gl10.glRotatef(headX, 1.0f, 0.0f, 0.0f)
        gl10.glTranslatef(0.0f, -8.0f * scale, 0.0f)
        this.head!!.draw(gl10)
        this.hat!!.draw(gl10)
        gl10.glPopMatrix()
        this.body!!.draw(gl10)
        this.bodyOverlay!!.draw(gl10)
        this.leftLeg!!.draw(gl10, false)
        this.rightLeg!!.draw(gl10, false)
        this.leftLegOverlay!!.draw(gl10, false)
        this.rightLegOverlay!!.draw(gl10, false)
        if (!slim) {
            this.leftArm!!.draw(gl10, true)
            this.rightArm!!.draw(gl10, true)
            this.leftArmOverlay!!.draw(gl10, true)
            this.rightArmOverlay!!.draw(gl10, true)
        } else {
            this.leftArmSlim!!.draw(gl10, true)
            this.rightArmSlim!!.draw(gl10, true)
            this.leftArmSlimOverlay!!.draw(gl10, true)
            this.rightArmSlimOverlay!!.draw(gl10, true)
        }
    }

    fun drawCapeModel(gl10: GL10) {
        this.cape!!.draw(gl10)
    }

    /** 关键帧线性插值采样：91 帧均匀分布在 0..3s */
    private fun sampleKey(keys: FloatArray): Float {
        val pos = (this.breatheTime / CLIP_SECONDS) * (KEY_COUNT - 1)
        val k = pos.toInt().coerceIn(0, KEY_COUNT - 2)
        val frac = pos - k
        return keys[k] + (keys[k + 1] - keys[k]) * frac
    }

    /** 呼吸动画时间相位（秒） */
    private var breatheTime = 0.0f

    /** 上一帧时间戳（纳秒；0 表示尚未渲染过首帧） */
    private var lastFrameNanos = 0L

    /** 上一帧间隔折算的 60fps 步进系数（旋转速度换算用） */
    private var lastFrameScale = 1f

    companion object {
        private const val CLIP_SECONDS = 3.0f

        /** 关键帧数量 */
        private const val KEY_COUNT = 91

        private val KEY_ARM_X = floatArrayOf(
            -1.58f, -1.39f, -1.20f, -1.01f, -0.82f, -0.63f, -0.44f,
            -0.26f, -0.08f, 0.09f, 0.26f, 0.43f, 0.59f, 0.75f,
            0.90f, 1.05f, 1.19f, 1.32f, 1.45f, 1.57f, 1.68f,
            1.79f, 1.89f, 1.98f, 2.06f, 2.14f, 2.20f, 2.26f,
            2.30f, 2.34f, 2.37f, 2.38f, 2.39f, 2.38f, 2.36f,
            2.32f, 2.28f, 2.22f, 2.15f, 2.07f, 1.99f, 1.90f,
            1.80f, 1.70f, 1.58f, 1.47f, 1.35f, 1.22f, 1.09f,
            0.96f, 0.82f, 0.67f, 0.53f, 0.38f, 0.23f, 0.08f,
            -0.08f, -0.23f, -0.39f, -0.55f, -0.72f, -0.88f, -1.04f,
            -1.20f, -1.37f, -1.53f, -1.69f, -1.85f, -2.01f, -2.16f,
            -2.31f, -2.45f, -2.59f, -2.72f, -2.84f, -2.94f, -3.01f,
            -3.05f, -3.04f, -3.03f, -3.01f, -2.97f, -2.92f, -2.84f,
            -2.72f, -2.57f, -2.40f, -2.20f, -2.00f, -1.79f, -1.58f,
        )
        private val KEY_ARM_Z = floatArrayOf(
            1.76f, 1.67f, 1.57f, 1.46f, 1.35f, 1.22f, 1.09f,
            0.96f, 0.83f, 0.70f, 0.58f, 0.46f, 0.35f, 0.25f,
            0.17f, 0.10f, 0.04f, 0.01f, 0.00f, 0.00f, 0.02f,
            0.04f, 0.07f, 0.11f, 0.16f, 0.21f, 0.27f, 0.33f,
            0.40f, 0.47f, 0.55f, 0.63f, 0.71f, 0.80f, 0.89f,
            0.99f, 1.09f, 1.18f, 1.29f, 1.39f, 1.49f, 1.60f,
            1.70f, 1.81f, 1.92f, 2.02f, 2.13f, 2.23f, 2.34f,
            2.44f, 2.54f, 2.64f, 2.73f, 2.83f, 2.91f, 3.00f,
            3.07f, 3.15f, 3.21f, 3.27f, 3.31f, 3.35f, 3.37f,
            3.38f, 3.38f, 3.38f, 3.37f, 3.35f, 3.34f, 3.32f,
            3.29f, 3.26f, 3.23f, 3.19f, 3.14f, 3.09f, 3.04f,
            2.98f, 2.91f, 2.84f, 2.76f, 2.68f, 2.59f, 2.50f,
            2.40f, 2.30f, 2.20f, 2.09f, 1.98f, 1.87f, 1.76f,
        )
        private val KEY_HEAD_X = floatArrayOf(
            -0.33f, -0.22f, -0.12f, -0.01f, 0.10f, 0.21f, 0.31f,
            0.41f, 0.51f, 0.61f, 0.71f, 0.80f, 0.90f, 0.99f,
            1.07f, 1.15f, 1.23f, 1.31f, 1.38f, 1.45f, 1.51f,
            1.57f, 1.62f, 1.67f, 1.72f, 1.75f, 1.79f, 1.82f,
            1.84f, 1.86f, 1.87f, 1.88f, 1.88f, 1.88f, 1.86f,
            1.84f, 1.81f, 1.78f, 1.74f, 1.69f, 1.64f, 1.59f,
            1.53f, 1.46f, 1.40f, 1.33f, 1.25f, 1.18f, 1.10f,
            1.02f, 0.93f, 0.85f, 0.76f, 0.67f, 0.58f, 0.48f,
            0.39f, 0.29f, 0.20f, 0.10f, 0.00f, -0.09f, -0.19f,
            -0.29f, -0.39f, -0.49f, -0.59f, -0.68f, -0.78f, -0.87f,
            -0.96f, -1.05f, -1.13f, -1.21f, -1.28f, -1.34f, -1.39f,
            -1.41f, -1.40f, -1.39f, -1.36f, -1.32f, -1.27f, -1.19f,
            -1.09f, -0.98f, -0.85f, -0.71f, -0.58f, -0.45f, -0.33f,
        )
        private val KEY_BODY_RX = floatArrayOf(
            0.34f, 0.31f, 0.28f, 0.26f, 0.24f, 0.22f, 0.20f,
            0.18f, 0.16f, 0.14f, 0.12f, 0.11f, 0.09f, 0.08f,
            0.06f, 0.05f, 0.04f, 0.03f, 0.02f, 0.01f, 0.00f,
            0.00f, -0.01f, -0.02f, -0.02f, -0.03f, -0.03f, -0.04f,
            -0.04f, -0.04f, -0.04f, -0.05f, -0.05f, -0.05f, -0.05f,
            -0.05f, -0.05f, -0.05f, -0.05f, -0.05f, -0.04f, -0.04f,
            -0.03f, -0.03f, -0.02f, -0.01f, 0.00f, 0.01f, 0.02f,
            0.03f, 0.04f, 0.05f, 0.06f, 0.08f, 0.09f, 0.10f,
            0.12f, 0.13f, 0.14f, 0.16f, 0.17f, 0.19f, 0.20f,
            0.22f, 0.24f, 0.25f, 0.27f, 0.28f, 0.30f, 0.32f,
            0.33f, 0.35f, 0.37f, 0.38f, 0.40f, 0.41f, 0.43f,
            0.44f, 0.46f, 0.47f, 0.49f, 0.50f, 0.50f, 0.50f,
            0.50f, 0.49f, 0.47f, 0.45f, 0.42f, 0.38f, 0.34f,
        )
        private val KEY_BODY_RZ = floatArrayOf(
            -0.08f, -0.09f, -0.11f, -0.13f, -0.14f, -0.16f, -0.18f,
            -0.20f, -0.21f, -0.23f, -0.24f, -0.26f, -0.27f, -0.29f,
            -0.30f, -0.31f, -0.32f, -0.33f, -0.34f, -0.35f, -0.36f,
            -0.36f, -0.37f, -0.37f, -0.38f, -0.38f, -0.38f, -0.37f,
            -0.37f, -0.36f, -0.36f, -0.35f, -0.34f, -0.33f, -0.32f,
            -0.31f, -0.30f, -0.29f, -0.27f, -0.26f, -0.25f, -0.23f,
            -0.22f, -0.20f, -0.19f, -0.17f, -0.15f, -0.14f, -0.12f,
            -0.10f, -0.08f, -0.07f, -0.05f, -0.03f, -0.01f, 0.00f,
            0.02f, 0.04f, 0.06f, 0.07f, 0.09f, 0.10f, 0.12f,
            0.13f, 0.15f, 0.16f, 0.17f, 0.18f, 0.19f, 0.19f,
            0.20f, 0.20f, 0.19f, 0.19f, 0.19f, 0.18f, 0.17f,
            0.16f, 0.15f, 0.14f, 0.12f, 0.10f, 0.08f, 0.06f,
            0.04f, 0.02f, 0.00f, -0.02f, -0.04f, -0.06f, -0.08f,
        )
        private val KEY_BODY_TY = floatArrayOf(
            -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f,
            -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f,
            -0.04f, -0.03f, -0.03f, -0.03f, -0.03f, -0.03f, -0.03f,
            -0.03f, -0.03f, -0.03f, -0.03f, -0.03f, -0.03f, -0.03f,
            -0.03f, -0.03f, -0.03f, -0.02f, -0.02f, -0.02f, -0.02f,
            -0.02f, -0.02f, -0.02f, -0.02f, -0.02f, -0.02f, -0.02f,
            -0.02f, -0.02f, -0.02f, -0.02f, -0.02f, -0.02f, -0.02f,
            -0.02f, -0.02f, -0.02f, -0.02f, -0.02f, -0.02f, -0.02f,
            -0.02f, -0.02f, -0.02f, -0.02f, -0.03f, -0.03f, -0.03f,
            -0.03f, -0.03f, -0.03f, -0.03f, -0.03f, -0.03f, -0.03f,
            -0.03f, -0.03f, -0.03f, -0.03f, -0.03f, -0.03f, -0.04f,
            -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f,
            -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f, -0.04f,
        )
    }
}
