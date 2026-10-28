package net.kdt.pojavlaunch.customcontrols.buttons;

import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.sendMouseButton;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.MainActivity;
import com.verse.explorer.app.R;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

@SuppressLint({"ViewConstructor", "AppCompatCustomView"})
public class ControlButton extends TextView implements ControlInterface {
    private final Paint mRectPaint = new Paint();
    protected ControlData mProperties;
    private final ControlLayout mControlLayout;

    /* Cache value from the ControlData radius for drawing purposes */
    private float mComputedRadius;

    protected boolean mIsToggled = false;
    protected boolean mIsPointerOutOfBounds = false;

    /** 按下本按钮的指针 ID（多指支持：只有落在本按钮内的指针才触发按下/抬起，其他指针事件不影响本按钮） */
    private int mDownPointerId = -1;

    /** 按下时刻与按下位置：用于识别点击（点击才触发控件组切换，与按下按键事件区分） */
    private long mDownTime;
    private float mDownX, mDownY;

    public ControlButton(ControlLayout layout, ControlData properties) {
        super(layout.getContext());
        mControlLayout = layout;
        setGravity(Gravity.CENTER);
        setAllCaps(LauncherPreferences.PREF_BUTTON_ALL_CAPS);
        setTextColor(Color.WHITE);
        setPadding(4, 4, 4, 4);
        setTextSize(14); // Nullify the default size setting
        setOutlineProvider(null); // Disable shadow casting, removing one drawing pass

        //setOnLongClickListener(this);

        //When a button is created, the width/height has yet to be processed to fit the scaling.
        setProperties(preProcessProperties(properties, layout));

        injectBehaviors();
    }

    @Override
    public View getControlView() {return this;}

    public ControlData getProperties() {
        return mProperties;
    }

    public void setProperties(ControlData properties, boolean changePos) {
        mProperties = properties;
        ControlInterface.super.setProperties(properties, changePos);
        mComputedRadius = ControlInterface.super.computeCornerRadius(mProperties.cornerRadius);

        if (mProperties.isToggle) {
            //For the toggle layer
            final TypedValue value = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.colorAccent, value, true);
            mRectPaint.setColor(value.data);
            mRectPaint.setAlpha(128);
        } else {
            mRectPaint.setColor(Color.WHITE);
            mRectPaint.setAlpha(60);
        }

        setText(properties.name);
    }

    /** 控件边缘描边画笔（「显示控件边缘」开关） */
    private final Paint mBoundaryPaint = new Paint();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mIsToggled || (!mProperties.isToggle && isActivated()))
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), mComputedRadius, mComputedRadius, mRectPaint);
        if (mControlLayout.getShowViewBoundaries()) {
            mBoundaryPaint.setStyle(Paint.Style.STROKE);
            mBoundaryPaint.setStrokeWidth(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1f, getResources().getDisplayMetrics()));
            mBoundaryPaint.setColor(Color.argb(160, 255, 255, 255));
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), mComputedRadius, mComputedRadius, mBoundaryPaint);
        }
    }


    public void loadEditValues(EditControlSideDialog editControlPopup){
        editControlPopup.loadValues(getProperties());
    }

    /** Add another instance of the ControlButton to the parent layout */
    public void cloneButton(){
        ControlData cloneData = new ControlData(getProperties());
        cloneData.dynamicX = "0.5 * ${screen_width}";
        cloneData.dynamicY = "0.5 * ${screen_height}";
        ((ControlLayout) getParent()).addControlButton(cloneData);
    }

    /** Remove any trace of this button from the layout */
    public void removeButton() {
        getControlLayoutParent().getLayout().mControlDataList.remove(getProperties());
        getControlLayoutParent().removeView(this);
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                //Send the event to be taken as a mouse action
                //  仅转发「按下本按钮」的那根手指：其他手指只是滑动经过，转发会把
                //  过路触摸当成游戏触摸（视角跳/闪）；控件只应持有自己按下的那根手指
                if(getProperties().passThruEnabled && CallbackBridge.isGrabbing() && mDownPointerId != -1){
                    View gameSurface = getControlLayoutParent().getGameSurface();
                    if(gameSurface != null) gameSurface.dispatchTouchEvent(event);
                }

                // 多指支持：只跟踪按下本按钮的指针（mDownPointerId）；其他指针的移动不影响本按钮
                final int moveIndex = mDownPointerId == -1 ? -1 : event.findPointerIndex(mDownPointerId);
                if(moveIndex == -1) break;

                //If out of bounds
                if(event.getX(moveIndex) < getControlView().getLeft() || event.getX(moveIndex) > getControlView().getRight() ||
                        event.getY(moveIndex) < getControlView().getTop()  || event.getY(moveIndex) > getControlView().getBottom()){
                    if(getProperties().isSwipeable && !mIsPointerOutOfBounds){
                        //Remove keys
                        if(!triggerToggle()) {
                            sendKeyPresses(false);
                        }
                    }
                    mIsPointerOutOfBounds = true;
                    getControlLayoutParent().onTouch(this, event);
                    break;
                }

                //Else if we now are in bounds
                if(mIsPointerOutOfBounds) {
                    getControlLayoutParent().onTouch(this, event);
                    //RE-press the button
                    if(getProperties().isSwipeable && !getProperties().isToggle){
                        sendKeyPresses(true);
                    }
                }
                mIsPointerOutOfBounds = false;
                break;

            case MotionEvent.ACTION_DOWN: // 0
            case MotionEvent.ACTION_POINTER_DOWN: // 5
                // 多指支持：仅当新指针（actionIndex）落在本按钮内才按下本按钮，
                // 其他指针按下（如另一指点在游戏区）不触发本按钮，避免按钮误按下/粘滞
                final int newIndex = event.getActionIndex();
                final float newX = event.getX(newIndex);
                final float newY = event.getY(newIndex);
                if (newX >= 0 && newX <= getWidth() && newY >= 0 && newY <= getHeight()) {
                    mDownPointerId = event.getPointerId(newIndex);
                    mDownTime = event.getEventTime();
                    mDownX = newX;
                    mDownY = newY;
                    // 按下即切换控件组：仓库布局把切换键的绑定放在 pressEvent 上，
                    // 只在抬手点击时处理会让这类布局的所有面板都打不开
                    if (getProperties().pressBindViewGroups != null
                            && !getProperties().pressBindViewGroups.isEmpty()) {
                        mControlLayout.toggleControlGroups(getProperties().pressBindViewGroups);
                    }
                    if(!getProperties().isToggle){
                        sendKeyPresses(true);
                    }
                }
                break;

            case MotionEvent.ACTION_UP: // 1
            case MotionEvent.ACTION_CANCEL: // 3
            case MotionEvent.ACTION_POINTER_UP: // 6
                // 多指支持：仅按下本按钮的指针抬起时才释放按键；其他指针抬起不影响本按钮
                final int upIndex = event.getActionIndex();
                final int upPointerId = event.getPointerId(upIndex);
                if (mDownPointerId != -1 && upPointerId != mDownPointerId) break;
                if(getProperties().passThruEnabled){
                    View gameSurface = getControlLayoutParent().getGameSurface();
                    if(gameSurface != null) gameSurface.dispatchTouchEvent(event);
                }
                if(mIsPointerOutOfBounds) getControlLayoutParent().onTouch(this, event);
                boolean wasOutOfBounds = mIsPointerOutOfBounds;
                mIsPointerOutOfBounds = false;

                // 点击切换控件组（clickEvent.bindViewGroup）：与 FCL 一致，
                // 仅短按（≤100ms）且位移≤10px 的点击触发，滑出边界或取消的手势不触发
                if (event.getActionMasked() != MotionEvent.ACTION_CANCEL
                        && !wasOutOfBounds
                        && mProperties.bindViewGroups != null
                        && !mProperties.bindViewGroups.isEmpty()
                        && event.getEventTime() - mDownTime <= 100
                        && Math.abs(event.getX(upIndex) - mDownX) <= 10
                        && Math.abs(event.getY(upIndex) - mDownY) <= 10) {
                    mControlLayout.toggleControlGroups(mProperties.bindViewGroups);
                }

                if(!triggerToggle()) {
                    sendKeyPresses(false);
                }
                mDownPointerId = -1;
                break;

            default:
                return false;
        }

        return super.onTouchEvent(event);
    }



    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean triggerToggle(){
        //returns true a the toggle system is triggered
        if(mProperties.isToggle){
            mIsToggled = !mIsToggled;
            invalidate();
            sendKeyPresses(mIsToggled);
            return true;
        }
        return false;
    }

    public void sendKeyPresses(boolean isDown){
        setActivated(isDown);
        for(int keycode : mProperties.keycodes){
            if(keycode >= GLFW_KEY_UNKNOWN){
                sendKeyPress(keycode, CallbackBridge.getCurrentMods(), isDown);
                CallbackBridge.setModifiers(keycode, isDown);
            }else{
                Log.i("punjabilauncher", "sendSpecialKey("+keycode+","+isDown+")");
                sendSpecialKey(keycode, isDown);
            }
        }
    }

    private void sendSpecialKey(int keycode, boolean isDown){
        switch (keycode) {
            case ControlData.SPECIALBTN_KEYBOARD:
                if(isDown) MainActivity.switchKeyboardState();
                break;

            case ControlData.SPECIALBTN_TOGGLECTRL:
                if(isDown)getControlLayoutParent().toggleControlVisible();
                break;

            case ControlData.SPECIALBTN_VIRTUALMOUSE:
                if(isDown) MainActivity.toggleMouse(getContext());
                break;

            case ControlData.SPECIALBTN_MOUSEPRI:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSEMID:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSESEC:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, isDown);
                break;

            case ControlData.SPECIALBTN_SCROLLDOWN:
                if (!isDown) CallbackBridge.sendScroll(0, -1d);
                break;

            case ControlData.SPECIALBTN_SCROLLUP:
                if (!isDown) CallbackBridge.sendScroll(0, 1d);
                break;
            case ControlData.SPECIALBTN_MENU:
                mControlLayout.notifyAppMenu();
                break;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
