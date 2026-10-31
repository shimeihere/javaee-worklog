package com.worklog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.worklog.common.ResultCode;
import com.worklog.common.exception.BusinessException;
import com.worklog.dto.DeptSaveRequest;
import com.worklog.entity.SysDept;
import com.worklog.entity.SysUser;
import com.worklog.mapper.SysDeptMapper;
import com.worklog.mapper.SysUserMapper;
import com.worklog.service.DeptService;
import com.worklog.vo.DeptTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 部门服务实现：一次性取全部部门 + 一次分组统计人数，在内存里组装层级，避免递归查库。
 *
 * <p>系统只支持两级部门：顶级（parentId = 0）与二级。新增/编辑时强制校验，
 * 不允许把部门挂到二级部门下形成三级。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;

    /** 顶级部门的 parent_id 约定值 */
    private static final long ROOT_PARENT_ID = 0L;

    @Override
    public List<DeptTreeVO> tree() {
        List<SysDept> depts = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSortOrder)
                .orderByAsc(SysDept::getId));
        Map<Long, Long> userCounts = userCountByDept();

        Map<Long, DeptTreeVO> nodeMap = new LinkedHashMap<>();
        for (SysDept dept : depts) {
            nodeMap.put(dept.getId(), DeptTreeVO.builder()
                    .id(dept.getId())
                    .parentId(dept.getParentId())
                    .deptName(dept.getDeptName())
                    .sortOrder(dept.getSortOrder())
                    .userCount(userCounts.getOrDefault(dept.getId(), 0L))
                    .children(new ArrayList<>())
                    .build());
        }

        List<DeptTreeVO> roots = new ArrayList<>();
        for (DeptTreeVO node : nodeMap.values()) {
            Long parentId = node.getParentId();
            boolean isRoot = parentId == null || parentId == ROOT_PARENT_ID;
            DeptTreeVO parent = isRoot ? null : nodeMap.get(parentId);
            if (parent == null) {
                // 顶级部门，或父部门已不存在（脏数据兜底，直接挂到根级，避免节点丢失）
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** 各直属部门人数：一次 group by 查询，避免 N+1 */
    private Map<Long, Long> userCountByDept() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.select("dept_id", "count(*) as cnt").groupBy("dept_id");

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : sysUserMapper.selectMaps(wrapper)) {
            Object deptId = row.get("dept_id");
            Object cnt = row.get("cnt");
            if (deptId instanceof Number id && cnt instanceof Number count) {
                counts.put(id.longValue(), count.longValue());
            }
        }
        return counts;
    }

    // ------------------------------------------------------------------
    // 新增 / 编辑
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeptTreeVO create(DeptSaveRequest request) {
        long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        validateParent(parentId, null);

        SysDept dept = new SysDept();
        dept.setParentId(parentId);
        dept.setDeptName(request.getDeptName().trim());
        dept.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        sysDeptMapper.insert(dept);

        log.info("新增部门：id={}, name={}, parentId={}", dept.getId(), dept.getDeptName(), parentId);
        return toVO(dept, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeptTreeVO update(Long id, DeptSaveRequest request) {
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }

        long parentId = request.getParentId() == null ? dept.getParentId() : request.getParentId();
        if (Objects.equals(parentId, id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上级部门不能是自己");
        }
        validateParent(parentId, id);
        if (parentId != ROOT_PARENT_ID && hasChildren(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该部门下还有子部门，不能再挂到别的部门下");
        }

        dept.setParentId(parentId);
        dept.setDeptName(request.getDeptName().trim());
        dept.setSortOrder(request.getSortOrder() == null ? dept.getSortOrder() : request.getSortOrder());
        sysDeptMapper.updateById(dept);

        log.info("编辑部门：id={}, name={}, parentId={}", id, dept.getDeptName(), parentId);
        return toVO(dept, countUsers(id));
    }

    /** 只允许挂到顶级部门下（两级结构） */
    private void validateParent(long parentId, Long selfId) {
        if (parentId == ROOT_PARENT_ID) {
            return;
        }
        SysDept parent = sysDeptMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上级部门不存在");
        }
        if (Objects.equals(parent.getId(), selfId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上级部门不能是自己");
        }
        if (parent.getParentId() != null && parent.getParentId() != ROOT_PARENT_ID) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持两级部门，不能挂在二级部门下");
        }
    }

    private boolean hasChildren(Long deptId) {
        return sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, deptId)) > 0;
    }

    private long countUsers(Long deptId) {
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, deptId));
    }

    private DeptTreeVO toVO(SysDept dept, long userCount) {
        return DeptTreeVO.builder()
                .id(dept.getId())
                .parentId(dept.getParentId())
                .deptName(dept.getDeptName())
                .sortOrder(dept.getSortOrder())
                .userCount(userCount)
                .children(new ArrayList<>())
                .build();
    }
}
