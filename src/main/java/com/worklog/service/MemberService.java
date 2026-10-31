package com.worklog.service;

import com.worklog.vo.MemberVO;

import java.util.List;

/**
 * 成员花名册（部门/项目下钻名单）。
 *
 * <p>只返回姓名与角色，不含任何填报内容，符合"层层管理、不越级查看"。</p>
 */
public interface MemberService {

    /** 某部门成员 */
    List<MemberVO> deptMembers(Long deptId);

    /** 某项目成员（负责人 + 在该项目填报过的人） */
    List<MemberVO> projectMembers(Long projectId);
}
