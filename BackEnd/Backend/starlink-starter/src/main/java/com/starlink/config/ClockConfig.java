package com.starlink.config;

import com.starlink.common.time.SimulationClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时间来源配置：计费模块通过注入的 {@link Clock} 取当前时间。
 * <p>
 * 默认注册<b>系统时钟</b>，行为与直接调用 {@code LocalDateTime.now()} 完全一致；
 * 仅当显式开启 {@code starlink.billing.simulation-enabled=true}
 * （测试属性或开发调试时手工配置）才注册可推进的 {@link SimulationClock}，
 * 用于「计费时间快进」测试。生产不配置该开关，因此时钟恒定等于系统时间。
 */
@Slf4j
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${starlink.billing.simulation-enabled:false}") boolean simulationEnabled) {
        if (simulationEnabled) {
            log.warn("【仿真模式】已启用可推进的系统时钟（SimulationClock），仅用于计费时间快进测试/调试");
            return new SimulationClock().enable();
        }
        return Clock.systemDefaultZone();
    }
}
