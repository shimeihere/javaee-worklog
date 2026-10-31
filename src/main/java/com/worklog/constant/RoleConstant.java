package com.worklog.constant;

import java.util.Map;

/**
 * 角色编码常量（与 sys_user.role 取值一致）。
 *
 * <p>四类角色：</p>
 * <ul>
 *   <li>EXECUTIVE 高管：看全公司汇总与看板、可看任何人明细，不填报；</li>
 *   <li>ADMIN 系统管理员：只做系统设置，看不到汇总/看板（防泄密）；</li>
 *   <li>DEPT_LEADER 部门负责人：本部门汇总/看板/明细，可催报，可填报；</li>
 *   <li>EMPLOYEE 员工：只填报、只看自己。</li>
 * </ul>
 */
public final class RoleConstant {

    /** 系统管理员 */
    public static final String ADMIN = "ADMIN";

    /** 高管 */
    public static final String EXECUTIVE = "EXECUTIVE";

    /** 部门负责人 */
    public static final String DEPT_LEADER = "DEPT_LEADER";

    /** 员工 */
    public static final String EMPLOYEE = "EMPLOYEE";

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            ADMIN, "系统管理员",
            EXECUTIVE, "高管",
            DEPT_LEADER, "部门负责人",
            EMPLOYEE, "员工"
    );

    private RoleConstant() {
    }

    /** 角色编码 -> 中文名称，用于前端展示 */
    public static String displayName(String role) {
        if (role == null) {
            return "";
        }
        return DISPLAY_NAMES.getOrDefault(role, role);
    }

    /** 全局数据范围（管理员、高管可见全公司数据） */
    public static boolean isGlobal(String role) {
        return ADMIN.equals(role) || EXECUTIVE.equals(role);
    }

    /** 是否可以填报日报/周报（只有员工与部门负责人） */
    public static boolean canFillReport(String role) {
        return EMPLOYEE.equals(role) || DEPT_LEADER.equals(role);
    }
}
