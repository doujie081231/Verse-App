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
	/**
	 * 控件组数据（仓库布局的 viewGroups；旧版布局为 null，视为全部常驻）。
	 * 必须随布局一起持久化：转换后的布局会写回磁盘，组信息一旦丢失，
	 * 后续加载会把所有组的控件当成常驻，切换控件组（选择布局）的按钮就彻底失效。
	 * 组内的按钮/摇杆成员不落盘（transient），加载后按 groupId 重建，避免重复存储控件数据。
	 */
	public List<ControlGroupData> mControlGroups;

	/** 单个控件组：一组按钮/摇杆共用显示状态，由绑定按钮点击切换 */
	public static class ControlGroupData {
		public String id;
		public String name;
		public boolean visible = true;
		public transient final List<ControlData> buttons = new ArrayList<>();
		public transient final List<ControlJoystickData> joysticks = new ArrayList<>();
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

	
	/**
	 * 加载后按 {@link ControlData#groupId} 重建控件组成员。
	 * 成员表不落盘，必须在使用（渲染/切换控件组）之前重建。
	 */
	public void rebuildControlGroups() {
		if (mControlGroups == null) return;
		for (ControlGroupData group : mControlGroups) {
			group.buttons.clear();
			group.joysticks.clear();
		}
		if (mControlDataList != null) {
			for (ControlData data : mControlDataList) {
				ControlGroupData group = findGroup(data == null ? null : data.groupId);
				if (group != null) group.buttons.add(data);
			}
		}
		if (mJoystickDataList != null) {
			for (ControlJoystickData joystick : mJoystickDataList) {
				ControlGroupData group = findGroup(joystick == null ? null : joystick.groupId);
				if (group != null) group.joysticks.add(joystick);
			}
		}
	}

	private ControlGroupData findGroup(String groupId) {
		if (groupId == null || groupId.isEmpty()) return null;
		for (ControlGroupData group : mControlGroups) {
			if (groupId.equals(group.id)) return group;
		}
		return null;
	}

	public void save(String path) throws IOException {
		//Current version is the V3.2 so the version as to be marked as 8 !
		version = 8;

		Tools.write(path, Tools.GLOBAL_GSON.toJson(this));
	}
}
