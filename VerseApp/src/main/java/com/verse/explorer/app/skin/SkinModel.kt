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

    /** 动作类型（对齐 versepc2 setAnim 的 8 个动作；默认呼吸站姿） */
    enum class SkinAnimationType {
        BREATHING, WALK, RUN, FLY, WAVE, CROUCH, HIT, SWIM, NONE
    }

    var animationType: SkinAnimationType = SkinAnimationType.BREATHING

    /** 呼吸相位（每帧累加，驱动整体 Y 向起伏） */
    private var breathePhase = 0.0f

    /** 呼吸起伏幅度 */
    private var breatheAmplitude = 0.015f

    /** 整体位移（蹲下下沉 / 飞行悬浮） */
    var bodyOffsetY: Float = 0.0f

    var scale: Float = 1f
        set(value) {
            field = value
            initModel()
            setAnimation(animationType)
        }

    init {
        initModel()
        setAnimation(animationType)
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
        setAnimation(animationType)
    }

    /**
     * 切换动作：按类型配置各肢体摆动幅度/速度与整体位移。
     * （overlay 与主立方体同配置，保证二者始终贴合不分离）
     */
    fun setAnimation(type: SkinAnimationType) {
        this.animationType = type
        this.breatheAmplitude = 0.015f
        this.bodyOffsetY = 0.0f
        when (type) {
            SkinAnimationType.NONE -> {
                setLimbSwing(leftArm, leftArmOverlay, 0.0f, 1.0f)
                setLimbSwing(rightArm, rightArmOverlay, 0.0f, 1.0f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 0.0f, 1.0f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 0.0f, 1.0f)
                setLimbSwing(leftLeg, leftLegOverlay, 0.0f, 1.0f)
                setLimbSwing(rightLeg, rightLegOverlay, 0.0f, 1.0f)
            }
            SkinAnimationType.BREATHING -> {
                // 呼吸：手臂轻微缓慢起伏，腿自然静止
                setLimbSwing(leftArm, leftArmOverlay, 0.25f, 0.25f)
                setLimbSwing(rightArm, rightArmOverlay, 0.25f, 0.25f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 0.25f, 0.25f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 0.25f, 0.25f)
                setLimbSwing(leftLeg, leftLegOverlay, 0.0f, 1.0f)
                setLimbSwing(rightLeg, rightLegOverlay, 0.0f, 1.0f)
            }
            SkinAnimationType.WALK -> {
                setLimbSwing(leftArm, leftArmOverlay, 0.8f, 1.0f)
                setLimbSwing(rightArm, rightArmOverlay, 0.8f, 1.0f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 0.8f, 1.0f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 0.8f, 1.0f)
                setLimbSwing(leftLeg, leftLegOverlay, 1.0f, 1.0f)
                setLimbSwing(rightLeg, rightLegOverlay, 1.0f, 1.0f)
            }
            SkinAnimationType.RUN -> {
                setLimbSwing(leftArm, leftArmOverlay, 1.0f, 2.0f)
                setLimbSwing(rightArm, rightArmOverlay, 1.0f, 2.0f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 1.0f, 2.0f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 1.0f, 2.0f)
                setLimbSwing(leftLeg, leftLegOverlay, 1.0f, 2.0f)
                setLimbSwing(rightLeg, rightLegOverlay, 1.0f, 2.0f)
            }
            SkinAnimationType.FLY -> {
                // 飞行：四肢快速摆动（扇动感）+ 模型微微上浮
                this.bodyOffsetY = 1.5f
                setLimbSwing(leftArm, leftArmOverlay, 1.0f, 2.5f)
                setLimbSwing(rightArm, rightArmOverlay, 1.0f, 2.5f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 1.0f, 2.5f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 1.0f, 2.5f)
                setLimbSwing(leftLeg, leftLegOverlay, 0.8f, 2.5f)
                setLimbSwing(rightLeg, rightLegOverlay, 0.8f, 2.5f)
            }
            SkinAnimationType.WAVE -> {
                // 挥手：右臂大幅摆动，左臂轻微，双腿静止
                setLimbSwing(leftArm, leftArmOverlay, 0.3f, 0.6f)
                setLimbSwing(rightArm, rightArmOverlay, 1.4f, 1.6f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 0.3f, 0.6f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 1.4f, 1.6f)
                setLimbSwing(leftLeg, leftLegOverlay, 0.0f, 1.0f)
                setLimbSwing(rightLeg, rightLegOverlay, 0.0f, 1.0f)
            }
            SkinAnimationType.CROUCH -> {
                // 蹲下：缓慢小幅度摆动 + 整体下沉
                this.bodyOffsetY = -2.0f
                setLimbSwing(leftArm, leftArmOverlay, 0.4f, 0.7f)
                setLimbSwing(rightArm, rightArmOverlay, 0.4f, 0.7f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 0.4f, 0.7f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 0.4f, 0.7f)
                setLimbSwing(leftLeg, leftLegOverlay, 0.6f, 0.8f)
                setLimbSwing(rightLeg, rightLegOverlay, 0.6f, 0.8f)
            }
            SkinAnimationType.HIT -> {
                // 攻击：手臂快速挥动，双腿静止
                setLimbSwing(leftArm, leftArmOverlay, 1.6f, 4.0f)
                setLimbSwing(rightArm, rightArmOverlay, 1.6f, 4.0f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 1.6f, 4.0f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 1.6f, 4.0f)
                setLimbSwing(leftLeg, leftLegOverlay, 0.0f, 1.0f)
                setLimbSwing(rightLeg, rightLegOverlay, 0.0f, 1.0f)
            }
            SkinAnimationType.SWIM -> {
                // 游泳：四肢快速摆动 + 模型略下沉
                this.bodyOffsetY = -1.0f
                setLimbSwing(leftArm, leftArmOverlay, 1.0f, 2.2f)
                setLimbSwing(rightArm, rightArmOverlay, 1.0f, 2.2f)
                setLimbSwing(leftArmSlim, leftArmSlimOverlay, 1.0f, 2.2f)
                setLimbSwing(rightArmSlim, rightArmSlimOverlay, 1.0f, 2.2f)
                setLimbSwing(leftLeg, leftLegOverlay, 1.0f, 2.2f)
                setLimbSwing(rightLeg, rightLegOverlay, 1.0f, 2.2f)
            }
        }
    }

    private fun setLimbSwing(
        limb: LimbCube?,
        overlay: LimbCube?,
        swingScale: Float,
        swingSpeed: Float
    ) {
        limb?.swingScale = swingScale
        limb?.swingSpeed = swingSpeed
        // 覆盖层必须与主立方体同步摆动，避免贴图分离
        overlay?.swingScale = swingScale
        overlay?.swingSpeed = swingSpeed
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
        gl10.glMatrixMode(GL10.GL_MODELVIEW)
        gl10.glTranslatef(0.0f, this.bodyOffsetY, 0.0f)
        gl10.glRotatef(this.rotate[0], 1.0f, 0.0f, 0.0f)
        gl10.glRotatef(this.rotate[1], 0.0f, 1.0f, 0.0f)
        gl10.glRotatef(this.rotate[2], 0.0f, 0.0f, 1.0f)
        // 呼吸：整体 Y 向轻微起伏（默认动作，站立时也能看到呼吸）
        if (this.animationType == SkinAnimationType.BREATHING) {
            this.breathePhase += 0.08f
            val breathe = 1.0f + this.breatheAmplitude * kotlin.math.sin(this.breathePhase)
            gl10.glScalef(1.0f, breathe, 1.0f)
        }
        val animate = this.animationType != SkinAnimationType.NONE
        this.head!!.draw(gl10)
        this.hat!!.draw(gl10)
        this.body!!.draw(gl10)
        this.bodyOverlay!!.draw(gl10)
        this.leftLeg!!.draw(gl10, animate)
        this.rightLeg!!.draw(gl10, animate)
        this.leftLegOverlay!!.draw(gl10, animate)
        this.rightLegOverlay!!.draw(gl10, animate)
        if (!slim) {
            this.leftArm!!.draw(gl10, animate)
            this.rightArm!!.draw(gl10, animate)
            this.leftArmOverlay!!.draw(gl10, animate)
            this.rightArmOverlay!!.draw(gl10, animate)
        } else {
            this.leftArmSlim!!.draw(gl10, animate)
            this.rightArmSlim!!.draw(gl10, animate)
            this.leftArmSlimOverlay!!.draw(gl10, animate)
            this.rightArmSlimOverlay!!.draw(gl10, animate)
        }
    }

    fun drawCapeModel(gl10: GL10) {
        this.cape!!.draw(gl10)
    }
}