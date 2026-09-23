package com.starlink.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.req.RoleCreateRequest;
import com.starlink.system.dto.req.RolePermissionRequest;
import com.starlink.system.dto.req.RoleUpdateRequest;
import com.starlink.system.dto.resp.RoleResponse;
import com.starlink.system.entity.Role;
import com.starlink.system.mapper.RoleMapper;
import com.starlink.system.mapper.RolePermissionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public PageResult<RoleResponse> listRoles(PageQuery pageQuery) {
        Page<Role> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<Role> result = roleMapper.selectPage(page, null);

        return PageResult.of(result, this::convertToResponse);
    }

    public List<RoleResponse> getAllRoles() {
        return roleMapper.selectList(null).stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional
    public RoleResponse createRole(RoleCreateRequest request) {
        if (roleMapper.selectByCode(request.getRoleCode()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "角色编码已存在");
        }

        Role role = new Role();
        role.setRoleName(request.getRoleName());
        role.setRoleCode(request.getRoleCode());
        role.setDescription(request.getDescription());
        role.setSortOrder(request.getSortOrder());
        role.setIsActive(request.getIsActive());
        role.setIsSystem((byte) 0);

        roleMapper.insert(role);
        log.info("创建角色成功: {}", role.getRoleName());

        return convertToResponse(role);
    }

    public RoleResponse getRoleById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return convertToResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleUpdateRequest request) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (request.getRoleName() != null) {
            role.setRoleName(request.getRoleName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getSortOrder() != null) {
            role.setSortOrder(request.getSortOrder());
        }
        if (request.getIsActive() != null) {
            role.setIsActive(request.getIsActive());
        }

        roleMapper.updateById(role);
        log.info("更新角色成功: id={}", id);

        return convertToResponse(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (role.getIsSystem() == 1) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "系统角色不能删除");
        }

        rolePermissionMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
        log.info("删除角色成功: id={}", id);
    }

    @Transactional
    public void assignPermissions(Long roleId, RolePermissionRequest request) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        rolePermissionMapper.deleteByRoleId(roleId);
        rolePermissionMapper.batchInsert(roleId, request.getPermissionIds());
        log.info("分配角色权限: roleId={}, permissionCount={}", roleId, request.getPermissionIds().size());
    }

    private RoleResponse convertToResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setRoleName(role.getRoleName());
        response.setRoleCode(role.getRoleCode());
        response.setDescription(role.getDescription());
        response.setIsSystem(role.getIsSystem());
        response.setSortOrder(role.getSortOrder());
        response.setIsActive(role.getIsActive());
        response.setCreatedAt(role.getCreatedAt());
        response.setUpdatedAt(role.getUpdatedAt());
        return response;
    }
}
