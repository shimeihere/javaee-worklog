package com.worklog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 周览 Worklog 启动类。
 *
 * <p>企业日报周报与项目看板系统，前后端分离单体架构，后端统一前缀 /api。</p>
 *
 * @author Worklog Team
 */
@SpringBootApplication
public class WorklogApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorklogApplication.class, args);
    }
}
