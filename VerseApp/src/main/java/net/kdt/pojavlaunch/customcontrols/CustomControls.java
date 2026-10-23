package net.kdt.pojavlaunch.customcontrols;

import com.verse.explorer.app.R;
import android.content.*;

import androidx.annotation.Keep;

import java.io.IOException;
import java.util.*;
import net.kdt.pojavlaunch.*;

@Keep
public class CustomControls {
	public int version = -1;
    public float scaledAt;
	public List<ControlData> mControlDataList;
	public List<ControlDrawerData> mDrawerDataList;
	public List<ControlJoystickData> mJoystickDataList;
	/** 控件组数据（仓库布局的 viewGroups；旧版布局为 null，视为全部常驻） */
	public transient List<ControlGroupData> mControlGroups;

	/** 单个控件组：一组按钮/摇杆共用显示状态，由绑定按钮点击切换 */
	public static class ControlGroupData {
		public String id;
		public String name;
		public boolean visible = true;
		public final List<ControlData> buttons = new ArrayList<>();
		public final List<ControlJoystickData> joysticks = new ArrayList<>();
	}
	public CustomControls() {
		this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	}



	public CustomControls(List<ControlData> mControlDataList, List<ControlDrawerData> mDrawerDataList, List<ControlJoystickData> mJoystickDataList) {
		this.mControlDataList = mControlDataList;
		this.mDrawerDataList = mDrawerDataList;
		this.mJoystickDataList = mJoystickDataList;
		this.scaledAt = 100f;
	}
	
	// Generate default control
	// Here for historical reasons
	// Just admire it idk
	@SuppressWarnings("unused")
	public CustomControls(Context ctx) {
		this();
		this.mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[0])); // Keyboard
		this.mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[1])); // GUI
		this.mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[2])); // Primary Mouse mControlDataList
		this.mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[3])); // Secondary Mouse mControlDataList
		this.mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[4])); // Virtual mouse toggle

		this.mControlDataList.add(new ControlData(ctx, R.string.control_debug, new int[]{LwjglGlfwKeycode.GLFW_KEY_F3}, "${margin}", "${margin}", false));
		this.mControlDataList.add(new ControlData(ctx, R.string.control_chat, new int[]{LwjglGlfwKeycode.GLFW_KEY_T}, "${margin} * 2 + ${width}", "${margin}", false));
		this.mControlDataList.add(new ControlData(ctx, R.string.control_listplayers, new int[]{LwjglGlfwKeycode.GLFW_KEY_TAB}, "${margin} * 4 + ${width} * 3", "${margin}", false));
		this.mControlDataList.add(new ControlData(ctx, R.string.control_thirdperson, new int[]{LwjglGlfwKeycode.GLFW_KEY_F5}, "${margin}", "${height} + ${margin}", false));

		this.mControlDataList.add(new ControlData(ctx, R.string.control_up, new int[]{LwjglGlfwKeycode.GLFW_KEY_W}, "${margin} * 2 + ${width}", "${bottom} - ${margin} * 3 - ${height} * 2", true));
		this.mControlDataList.add(new ControlData(ctx, R.string.control_left, new int[]{LwjglGlfwKeycode.GLFW_KEY_A}, "${margin}", "${bottom} - ${margin} * 2 - ${height}", true));
		this.mControlDataList.add(new ControlData(ctx, R.string.control_down, new int[]{LwjglGlfwKeycode.GLFW_KEY_S}, "${margin} * 2 + ${width}", "${bottom} - ${margin}", true));
		this.mControlDataList.add(new ControlData(ctx, R.string.control_right, new int[]{LwjglGlfwKeycode.GLFW_KEY_D}, "${margin} * 3 + ${width} * 2", "${bottom} - ${margin} * 2 - ${height}", true));

		this.mControlDataList.add(new ControlData(ctx, R.string.control_inventory, new int[]{LwjglGlfwKeycode.GLFW_KEY_E}, "${margin} * 3 + ${width} * 2", "${bottom} - ${margin}", true));
        
        ControlData shiftData = new ControlData(ctx, R.string.control_shift, new int[]{LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT}, "${margin} * 2 + ${width}", "${screen_height} - ${margin} * 2 - ${height} * 2", true);
		shiftData.isToggle = true;
		this.mControlDataList.add(shiftData);
		this.mControlDataList.add(new ControlData(ctx, R.string.control_jump, new int[]{LwjglGlfwKeycode.GLFW_KEY_SPACE}, "${right} - ${margin} * 2 - ${width}", "${bottom} - ${margin} * 2 - ${height}", true));

		//The default controls are conform to the V3
		version = 8;
	}

	
	public void save(String path) throws IOException {
		//Current version is the V3.2 so the version as to be marked as 8 !
		version = 8;

		Tools.write(path, Tools.GLOBAL_GSON.toJson(this));
	}
}
