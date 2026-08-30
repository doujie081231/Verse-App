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

    var isRunning: Boolean = false

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
        gl10.glMatrixMode(GL10.GL_MODELVIEW)
        gl10.glRotatef(this.rotate[0], 1.0f, 0.0f, 0.0f)
        gl10.glRotatef(this.rotate[1], 0.0f, 1.0f, 0.0f)
        gl10.glRotatef(this.rotate[2], 0.0f, 0.0f, 1.0f)
        this.head!!.draw(gl10)
        this.hat!!.draw(gl10)
        this.body!!.draw(gl10)
        this.bodyOverlay!!.draw(gl10)
        this.leftLeg!!.draw(gl10, this.isRunning)
        this.rightLeg!!.draw(gl10, this.isRunning)
        this.leftLegOverlay!!.draw(gl10, this.isRunning)
        this.rightLegOverlay!!.draw(gl10, this.isRunning)
        if (!slim) {
            this.leftArm!!.draw(gl10, this.isRunning)
            this.rightArm!!.draw(gl10, this.isRunning)
            this.leftArmOverlay!!.draw(gl10, this.isRunning)
            this.rightArmOverlay!!.draw(gl10, this.isRunning)
        } else {
            this.leftArmSlim!!.draw(gl10, this.isRunning)
            this.rightArmSlim!!.draw(gl10, this.isRunning)
            this.leftArmSlimOverlay!!.draw(gl10, this.isRunning)
            this.rightArmSlimOverlay!!.draw(gl10, this.isRunning)
        }
    }

    fun drawCapeModel(gl10: GL10) {
        this.cape!!.draw(gl10)
    }
}