package com.starlink.system.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.system.dto.req.MenuCreateRequest;
import com.starlink.system.dto.req.MenuUpdateRequest;
import com.starlink.system.dto.resp.MenuResponse;
import com.starlink.system.entity.Permission;
import com.starlink.system.mapper.PermissionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final PermissionMapper permissionMapper;

    public List<MenuResponse> getMenuTree() {
        List<Permission> allPermissions = permissionMapper.selectList(null);
        return buildTree(null, allPermissions);
    }

    private List<MenuResponse> buildTree(Long parentId, List<Permission> allPermissions) {
        List<MenuResponse> tree = new ArrayList<>();
        
        for (Permission permission : allPermissions) {
            Long pId = permission.getParentId();
            if ((parentId == null && pId == null) || (parentId != null && parentId.equals(pId))) {
                MenuResponse node = convertToResponse(permission);
                node.setChildren(buildTree(permission.getId(), allPermissions));
                tree.add(node);
            }
        }

        tree.sort((a, b) -> {
            Integer sa = a.getSortOrder() != null ? a.getSortOrder() : 0;
            Integer sb = b.getSortOrder() != null ? b.getSortOrder() : 0;
            return sa.compareTo(sb);
        });

        return tree;
    }

    public List<MenuResponse> getMenuTreeByRoleIds(List<Long> roleIds) {
        List<Permission> permissions = permissionMapper.selectByRoleIds(roleIds);
        return buildTree(null, permissions);
    }

    @Transactional
    public MenuResponse createMenu(MenuCreateRequest request) {
        Permission permission = new Permission();
        permission.setParentId(request.getParentId());
        permission.setPermName(request.getPermName());
        permission.setPermCode(request.getPermCode());
        permission.setPermType(request.getPermType());
        permission.setIcon(request.getIcon());
        permission.setRoute(request.getRoute());
        permission.setSortOrder(request.getSortOrder());

        permissionMapper.insert(permission);
        log.info("创建菜单成功: {}", permission.getPermName());

        return convertToResponse(permission);
    }

    public MenuResponse getMenuById(Long id) {
        Permission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return convertToResponse(permission);
    }

    @Transactional
    public MenuResponse updateMenu(Long id, MenuUpdateRequest request) {
        Permission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (request.getParentId() != null) {
            permission.setParentId(request.getParentId());
        }
        if (request.getPermName() != null) {
            permission.setPermName(request.getPermName());
        }
        if (request.getPermCode() != null) {
            permission.setPermCode(request.getPermCode());
        }
        if (request.getPermType() != null) {
            permission.setPermType(request.getPermType());
        }
        if (request.getIcon() != null) {
            permission.setIcon(request.getIcon());
        }
        if (request.getRoute() != null) {
            permission.setRoute(request.getRoute());
        }
        if (request.getSortOrder() != null) {
            permission.setSortOrder(request.getSortOrder());
        }

        permissionMapper.updateById(permission);
        log.info("更新菜单成功: id={}", id);

        return convertToResponse(permission);
    }

    @Transactional
    public void deleteMenu(Long id) {
        Permission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        List<Permission> children = permissionMapper.selectTreeByParentId(id);
        if (!children.isEmpty()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "存在子菜单，不能删除");
        }

        permissionMapper.deleteById(id);
        log.info("删除菜单成功: id={}", id);
    }

    private MenuResponse convertToResponse(Permission permission) {
        MenuResponse response = new MenuResponse();
        response.setId(permission.getId());
        response.setParentId(permission.getParentId());
        response.setPermName(permission.getPermName());
        response.setPermCode(permission.getPermCode());
        response.setPermType(permission.getPermType());
        response.setIcon(permission.getIcon());
        response.setRoute(permission.getRoute());
        response.setSortOrder(permission.getSortOrder());
        return response;
    }
}
