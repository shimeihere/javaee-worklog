package com.worklog.constant;

import java.util.Map;

/**
 * 审计动作编码与中文名。
 */
public final class AuditAction {

    public static final String CREATE_USER = "CREATE_USER";
    public static final String BATCH_CREATE_USER = "BATCH_CREATE_USER";
    public static final String UPDATE_USER = "UPDATE_USER";
    public static final String USER_STATUS = "USER_STATUS";

    public static final String CREATE_DEPT = "CREATE_DEPT";
    public static final String UPDATE_DEPT = "UPDATE_DEPT";

    public static final String CREATE_PROJECT = "CREATE_PROJECT";
    public static final String UPDATE_PROJECT = "UPDATE_PROJECT";
    public static final String CHANGE_PROJECT_LEADER = "CHANGE_PROJECT_LEADER";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(CREATE_USER, "新增用户"),
            Map.entry(BATCH_CREATE_USER, "批量新增用户"),
            Map.entry(UPDATE_USER, "编辑用户"),
            Map.entry(USER_STATUS, "启用/禁用账号"),
            Map.entry(CREATE_DEPT, "新增部门"),
            Map.entry(UPDATE_DEPT, "编辑部门"),
            Map.entry(CREATE_PROJECT, "新建项目"),
            Map.entry(UPDATE_PROJECT, "编辑项目"),
            Map.entry(CHANGE_PROJECT_LEADER, "变更项目负责人")
    );

    private AuditAction() {
    }

    public static String label(String action) {
        if (action == null) {
            return "";
        }
        return LABELS.getOrDefault(action, action);
    }

    public static Map<String, String> labels() {
        return LABELS;
    }
}
