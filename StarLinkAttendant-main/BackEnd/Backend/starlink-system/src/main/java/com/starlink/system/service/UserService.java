package com.starlink.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.req.UserCreateRequest;
import com.starlink.system.dto.req.UserStatusRequest;
import com.starlink.system.dto.req.UserUpdateRequest;
import com.starlink.system.dto.resp.UserResponse;
import com.starlink.system.entity.Employee;
import com.starlink.system.mapper.EmployeeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserResponse> listUsers(PageQuery pageQuery, String realName, String phone, Byte status) {
        Page<UserResponse> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<UserResponse> result = employeeMapper.selectUserPage(page, realName, phone, status);
        
        for (UserResponse item : result.getRecords()) {
            item.setStatusLabel(getStatusLabel(item.getStatus()));
            item.setEmploymentTypeLabel(getEmploymentTypeLabel(item.getEmploymentType()));
        }

        return PageResult.from(result);
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (employeeMapper.selectByPhone(request.getPhone()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "手机号已存在");
        }

        if (employeeMapper.selectByEmployeeNo(request.getEmployeeNo()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "工号已存在");
        }

        Employee employee = new Employee();
        employee.setEmployeeNo(request.getEmployeeNo());
        employee.setRealName(request.getRealName());
        employee.setPhone(request.getPhone());
        employee.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        employee.setPosition(request.getPosition());
        employee.setEmploymentType(request.getEmploymentType());
        employee.setStatus(request.getStatus());
        employee.setIsActive((byte) 1);

        employeeMapper.insert(employee);
        log.info("创建用户成功: {}", employee.getRealName());

        return getUserById(employee.getId());
    }

    public UserResponse getUserById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        UserResponse response = new UserResponse();
        response.setId(employee.getId());
        response.setEmployeeNo(employee.getEmployeeNo());
        response.setRealName(employee.getRealName());
        response.setPhone(employee.getPhone());
        response.setPosition(employee.getPosition());
        response.setEmploymentType(employee.getEmploymentType());
        response.setEmploymentTypeLabel(getEmploymentTypeLabel(employee.getEmploymentType()));
        response.setHireDate(employee.getHireDate());
        response.setResignDate(employee.getResignDate());
        response.setStatus(employee.getStatus());
        response.setStatusLabel(getStatusLabel(employee.getStatus()));
        response.setIsActive(employee.getIsActive());
        response.setLastLoginAt(employee.getLastLoginAt());
        response.setCreatedAt(employee.getCreatedAt());
        response.setUpdatedAt(employee.getUpdatedAt());

        return response;
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (StringUtils.hasText(request.getRealName())) {
            employee.setRealName(request.getRealName());
        }
        if (StringUtils.hasText(request.getPhone())) {
            Employee existing = employeeMapper.selectByPhone(request.getPhone());
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "手机号已存在");
            }
            employee.setPhone(request.getPhone());
        }
        if (StringUtils.hasText(request.getPosition())) {
            employee.setPosition(request.getPosition());
        }
        if (request.getEmploymentType() != null) {
            employee.setEmploymentType(request.getEmploymentType());
        }
        if (request.getStatus() != null) {
            employee.setStatus(request.getStatus());
        }
        if (request.getIsActive() != null) {
            employee.setIsActive(request.getIsActive());
        }

        employeeMapper.updateById(employee);
        log.info("更新用户成功: id={}", id);

        return getUserById(id);
    }

    @Transactional
    public void deleteUser(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        employeeMapper.deleteById(id);
        log.info("删除用户成功: id={}", id);
    }

    @Transactional
    public void updateUserStatus(Long id, UserStatusRequest request) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        employee.setStatus(request.getStatus());
        employeeMapper.updateById(employee);
        log.info("更新用户状态: id={}, status={}", id, request.getStatus());
    }

    @Transactional
    public void resetPassword(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        employee.setPasswordHash(passwordEncoder.encode("123456"));
        employeeMapper.updateById(employee);
        log.info("重置用户密码: id={}", id);
    }

    private String getStatusLabel(Byte status) {
        if (status == null) return "";
        return switch (status) {
            case 1 -> "在职";
            case 2 -> "离职";
            case 3 -> "停用";
            default -> "未知";
        };
    }

    private String getEmploymentTypeLabel(Byte type) {
        if (type == null) return "";
        return switch (type) {
            case 1 -> "全职";
            case 2 -> "兼职";
            case 3 -> "实习";
            default -> "未知";
        };
    }
}
