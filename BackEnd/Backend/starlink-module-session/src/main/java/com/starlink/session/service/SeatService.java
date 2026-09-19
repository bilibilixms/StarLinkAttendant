package com.starlink.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.session.dto.resp.ComputerResponse;
import com.starlink.session.dto.resp.SeatAreaResponse;
import com.starlink.session.dto.resp.SeatStatusResponse;
import com.starlink.session.entity.Computer;
import com.starlink.session.entity.SeatArea;
import com.starlink.session.entity.Session;
import com.starlink.session.mapper.ComputerMapper;
import com.starlink.session.mapper.SeatAreaMapper;
import com.starlink.session.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 座位管理服务。
 * <p>
 * 提供座位图可视化所需数据、机位实时状态查询，以及区域/机位 CRUD 操作。
 * 使用 MyBatis-Plus 内置方法，不依赖 Mapper XML。
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatAreaMapper seatAreaMapper;
    private final ComputerMapper computerMapper;
    private final SessionMapper sessionMapper;

    /**
     * 获取座位图配置（区域 + 机位列表）。
     * <p>
     * 机位状态由活跃会话驱动，与 {@link #getSeatStatus()} 保持一致：
     * 有活跃会话 → 使用中(1)；无活跃会话 → 使用机位自身状态（若为1则修正为0）。
     */
    public List<SeatAreaResponse> getSeatMap() {
        // 1. 查询所有启用区域
        List<SeatArea> areas = seatAreaMapper.selectList(
                new LambdaQueryWrapper<SeatArea>()
                        .eq(SeatArea::getIsActive, 1)
                        .orderByAsc(SeatArea::getSortOrder)
                        .orderByAsc(SeatArea::getId));

        // 2. 查询所有启用机位
        List<Computer> computers = computerMapper.selectList(
                new LambdaQueryWrapper<Computer>()
                        .eq(Computer::getIsActive, 1)
                        .orderByAsc(Computer::getSortOrder)
                        .orderByAsc(Computer::getComputerNo));

        // 3. 查询活跃会话，构建机位 ID → 会话映射（与 getSeatStatus 一致）
        List<Session> activeSessions = sessionMapper.selectList(
                new LambdaQueryWrapper<Session>()
                        .in(Session::getStatus, 0, 1));
        Map<Long, Session> computerSessionMap = new LinkedHashMap<>();
        for (Session s : activeSessions) {
            computerSessionMap.putIfAbsent(s.getComputerId(), s);
        }

        // 4. 构建区域名称映射
        Map<Long, String> areaNameMap = areas.stream()
                .collect(Collectors.toMap(SeatArea::getId, SeatArea::getAreaName));

        // 5. 将机位按区域分组并转换为 DTO（状态由活跃会话驱动）
        Map<Long, List<ComputerResponse>> computerMap = computers.stream()
                .map(c -> {
                    ComputerResponse resp = new ComputerResponse();
                    BeanUtils.copyProperties(c, resp);
                    resp.setAreaName(areaNameMap.getOrDefault(c.getAreaId(), ""));
                    // 使用活跃会话驱动状态，与 getSeatStatus() 保持一致
                    byte realStatus;
                    if (computerSessionMap.containsKey(c.getId())) {
                        realStatus = 1; // 有活跃会话 → 使用中
                    } else {
                        byte raw = c.getStatus();
                        realStatus = raw == 1 ? 0 : raw; // 无活跃会话时自愈
                    }
                    resp.setStatus(realStatus);
                    resp.setStatusLabel(getComputerStatusLabel(realStatus));
                    return resp;
                })
                .collect(Collectors.groupingBy(ComputerResponse::getAreaId));

        // 6. 组装结果
        List<SeatAreaResponse> result = new ArrayList<>();
        for (SeatArea area : areas) {
            SeatAreaResponse areaResp = new SeatAreaResponse();
            BeanUtils.copyProperties(area, areaResp);
            areaResp.setComputers(computerMap.getOrDefault(area.getId(), new ArrayList<>()));
            result.add(areaResp);
        }

        return result;
    }

    /**
     * 获取所有机位实时状态（轮询接口）。
     * <p>
     * 通过多次查询替代 JOIN：先查机位列表，再查活跃会话，组装状态信息。
     */
    public List<SeatStatusResponse> getSeatStatus() {
        // 1. 查询所有启用机位
        List<Computer> computers = computerMapper.selectList(
                new LambdaQueryWrapper<Computer>()
                        .eq(Computer::getIsActive, 1)
                        .orderByAsc(Computer::getId));

        // 2. 查询所有活跃会话（状态 0-上机中 或 1-临时下机）
        List<Session> activeSessions = sessionMapper.selectList(
                new LambdaQueryWrapper<Session>()
                        .in(Session::getStatus, 0, 1));

        // 3. 构建机位 ID → 会话的映射
        Map<Long, Session> computerSessionMap = new LinkedHashMap<>();
        for (Session s : activeSessions) {
            computerSessionMap.putIfAbsent(s.getComputerId(), s);
        }

        // 4. 组装结果（状态由活跃会话驱动，保证与 getActiveSessions 一致）
        List<SeatStatusResponse> result = new ArrayList<>();
        for (Computer c : computers) {
            SeatStatusResponse resp = new SeatStatusResponse();
            resp.setId(c.getId());

            Session session = computerSessionMap.get(c.getId());
            if (session != null) {
                // 有活跃会话 → 使用中（无论 Computer.status 是什么）
                resp.setStatus((byte) 1);
                // 注：memberName 需要跨模块查询，此处暂置空，后续可集成
                resp.setStartTime(session.getStartTime());
            } else {
                // 无活跃会话 → 使用机位自身状态，但如果 Computer.status=1 则修正为 0（数据自愈）
                byte raw = c.getStatus();
                resp.setStatus(raw == 1 ? (byte) 0 : raw);
            }

            result.add(resp);
        }

        return result;
    }

    /**
     * 创建区域。
     */
    @Transactional
    public SeatArea createArea(SeatArea area) {
        seatAreaMapper.insert(area);
        log.info("创建区域: {}", area.getAreaName());
        return area;
    }

    /**
     * 更新区域。
     */
    @Transactional
    public SeatArea updateArea(Long id, SeatArea area) {
        SeatArea existing = seatAreaMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (area.getAreaName() != null) existing.setAreaName(area.getAreaName());
        if (area.getAreaColor() != null) existing.setAreaColor(area.getAreaColor());
        if (area.getSortOrder() != null) existing.setSortOrder(area.getSortOrder());
        if (area.getIsActive() != null) existing.setIsActive(area.getIsActive());

        seatAreaMapper.updateById(existing);
        log.info("更新区域: id={}", id);
        return existing;
    }

    /**
     * 删除区域（软删除）。
     */
    @Transactional
    public void deleteArea(Long id) {
        SeatArea area = seatAreaMapper.selectById(id);
        if (area == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        seatAreaMapper.deleteById(id);
        log.info("删除区域: id={}", id);
    }

    /**
     * 创建机位。
     */
    @Transactional
    public Computer createComputer(Computer computer) {
        // 检查机位编号唯一性
        Computer existing = computerMapper.selectOne(
                new LambdaQueryWrapper<Computer>()
                        .eq(Computer::getComputerNo, computer.getComputerNo()));
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "机位编号已存在");
        }

        computer.setStatus((byte) 0); // 默认空闲
        computerMapper.insert(computer);
        log.info("创建机位: {}", computer.getComputerNo());
        return computer;
    }

    /**
     * 更新机位。
     */
    @Transactional
    public Computer updateComputer(Long id, Computer computer) {
        Computer existing = computerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (computer.getAreaId() != null) existing.setAreaId(computer.getAreaId());
        if (computer.getComputerNo() != null) {
            Computer dup = computerMapper.selectOne(
                    new LambdaQueryWrapper<Computer>()
                            .eq(Computer::getComputerNo, computer.getComputerNo()));
            if (dup != null && !dup.getId().equals(id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "机位编号已存在");
            }
            existing.setComputerNo(computer.getComputerNo());
        }
        if (computer.getComputerName() != null) existing.setComputerName(computer.getComputerName());
        if (computer.getSeatLabel() != null) existing.setSeatLabel(computer.getSeatLabel());
        if (computer.getDeviceType() != null) existing.setDeviceType(computer.getDeviceType());
        if (computer.getCpu() != null) existing.setCpu(computer.getCpu());
        if (computer.getGpu() != null) existing.setGpu(computer.getGpu());
        if (computer.getMemory() != null) existing.setMemory(computer.getMemory());
        if (computer.getScreenSize() != null) existing.setScreenSize(computer.getScreenSize());
        if (computer.getMacAddress() != null) existing.setMacAddress(computer.getMacAddress());
        if (computer.getIpAddress() != null) existing.setIpAddress(computer.getIpAddress());
        if (computer.getTariffPlanId() != null) existing.setTariffPlanId(computer.getTariffPlanId());
        if (computer.getSortOrder() != null) existing.setSortOrder(computer.getSortOrder());
        if (computer.getPosX() != null) existing.setPosX(computer.getPosX());
        if (computer.getPosY() != null) existing.setPosY(computer.getPosY());
        if (computer.getIsActive() != null) existing.setIsActive(computer.getIsActive());
        if (computer.getStatus() != null) existing.setStatus(computer.getStatus());

        computerMapper.updateById(existing);
        log.info("更新机位: id={}", id);
        return existing;
    }

    /**
     * 删除机位（软删除）。
     */
    @Transactional
    public void deleteComputer(Long id) {
        Computer computer = computerMapper.selectById(id);
        if (computer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (computer.getStatus() == 1) {
            throw new BusinessException(ErrorCode.COMPUTER_NOT_AVAILABLE.getCode(), "机位使用中，无法删除");
        }
        computerMapper.deleteById(id);
        log.info("删除机位: id={}", id);
    }

    private String getComputerStatusLabel(Byte status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "空闲";
            case 1 -> "使用中";
            case 2 -> "锁定";
            case 3 -> "维修";
            case 4 -> "关机";
            default -> "未知";
        };
    }
}
