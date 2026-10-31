package com.worklog.controller;

import com.worklog.common.Result;
import com.worklog.constant.RoleConstant;
import com.worklog.security.RequireRole;
import com.worklog.service.BoardService;
import com.worklog.vo.BoardOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板接口（M6）。
 *
 * <p>仅高管与部门负责人可用：管理员只做系统配置，不开放经营大盘（防泄密）。</p>
 */
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 看板聚合数据：GET /api/board/overview?range=week&deptId=2&projectId=1
     */
    @GetMapping("/overview")
    @RequireRole({RoleConstant.EXECUTIVE, RoleConstant.DEPT_LEADER})
    public Result<BoardOverviewVO> overview(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long projectId) {
        return Result.ok(boardService.overview(range, deptId, projectId));
    }
}
