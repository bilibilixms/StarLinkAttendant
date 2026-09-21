package com.starlink.common.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可推进的仿真时钟（仅用于开发/测试的「计费时间快进」）。
 *
 * <h3>为什么需要它</h3>
 * 计费依赖 {@code LocalDateTime.now()}。要验证「上机 4 小时后余额耗尽自动下机」，
 * 若按真实时间等待，一次测试要跑几个小时。把计费模块的时间来源抽象为 {@link Clock} 后，
 * 测试即可在几秒内推进数小时，而<b>业务代码、计费规则一行不改</b>。
 *
 * <h3>安全边界（重要）</h3>
 * <ul>
 *   <li>本类<b>只影响注入它的模块</b>（当前仅上机计费模块）。不修改 Windows/MySQL 系统时间，
 *       不影响 JWT（其直接用 {@code new Date()}）、不影响其它业务模块。</li>
 *   <li>未 {@link #enable()} 时 {@link #instant()} 完全等价于系统时间，{@link #advance(Duration)}
 *       直接抛异常 —— 生产环境即便误注入本类也不会发生时间漂移。</li>
 *   <li>仅在显式开启仿真开关时（{@code starlink.billing.simulation-enabled=true}）
 *       才会被注册为 Spring Bean 并 enable，生产不配置该开关。</li>
 * </ul>
 */
public final class SimulationClock extends Clock {

    private final ZoneId zone;
    private final Clock base;
    /** 相对基准时间的偏移量；{@link ZoneId} 派生实例共享同一偏移 */
    private final AtomicLong offsetMillis;
    private volatile boolean enabled;

    public SimulationClock() {
        this(ZoneId.systemDefault());
    }

    public SimulationClock(ZoneId zone) {
        this(zone, Clock.system(zone), new AtomicLong(0L), false);
    }

    private SimulationClock(ZoneId zone, Clock base, AtomicLong offsetMillis, boolean enabled) {
        this.zone = zone;
        this.base = base;
        this.offsetMillis = offsetMillis;
        this.enabled = enabled;
    }

    /** 开启仿真：此后 {@link #instant()} 返回「基准时间 + 偏移量」 */
    public SimulationClock enable() {
        this.enabled = true;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 推进仿真时间。
     *
     * @param duration 需要推进的时长（可为负数，但业务测试不应回退时间）
     * @throws IllegalStateException 未开启仿真时调用（防止生产误用）
     */
    public void advance(Duration duration) {
        if (!enabled) {
            throw new IllegalStateException("SimulationClock 未启用，禁止推进时间");
        }
        offsetMillis.addAndGet(duration.toMillis());
    }

    /** 推进指定分钟数（业务测试最常用） */
    public void advanceMinutes(long minutes) {
        advance(Duration.ofMinutes(minutes));
    }

    /** 清零偏移量（仿真结束/复用同一容器时） */
    public void reset() {
        offsetMillis.set(0L);
    }

    /** 当前累计偏移量（分钟），便于日志与断言 */
    public long offsetMinutes() {
        return offsetMillis.get() / 60000L;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    /**
     * 派生指定时区的时钟，并<b>共享</b>同一偏移量（避免 withZone 后丢偏移）。
     */
    @Override
    public Clock withZone(ZoneId newZone) {
        if (newZone.equals(this.zone)) {
            return this;
        }
        SimulationClock derived = new SimulationClock(newZone, Clock.system(newZone), offsetMillis, enabled);
        return derived;
    }

    @Override
    public Instant instant() {
        Instant baseInstant = base.instant();
        return enabled ? baseInstant.plusMillis(offsetMillis.get()) : baseInstant;
    }
}
