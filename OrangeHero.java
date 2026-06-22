package mybots;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;

/**
 * OrangeHero - 增强版 Robocode 机器人
 * 核心：正弦速度震荡移动、高精度圆形预测瞄准2.0、自适应火力管理
 */
public class OrangeHero extends AdvancedRobot {

    // ===== 核心常量 =====
    private static final double WALL_MARGIN = 65;
    private static final double WALL_STICK = 160;
    private static final double IDEAL_DISTANCE = 260; // 略微拉开空间以利于闪避

    // ===== 目标状态 =====
    private String targetName = null;
    private double targetBearing = 0;
    private double targetDistance = Double.POSITIVE_INFINITY;
    private double targetEnergy = 100;
    private double lastEnemyHeading = 0;
    private long lastSeenTime = -100;

    // ===== 移动状态 (增强：伪随机/准对等移动) =====
    private double moveDirection = 1;
    private long lastReverseTime = 0;
    private double sineWavePhase = 0; // 用于生成速度震荡

    public void run() {
        // 解耦组件旋转
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // ===== 主题配色：OrangeHero =====
        setColors(
            new Color(255, 140, 0),  // Body: Dark Orange
            new Color(255, 69, 0),   // Gun: Red Orange
            Color.WHITE,             // Radar: White
            new Color(255, 165, 0),  // Bullet: Orange
            new Color(255, 255, 255) // Scan Arc: White
        );

        // 开局雷达全扫
        while (true) {
            if (targetName == null || getTime() - lastSeenTime > 10) {
                targetName = null;
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // 1. 智能目标切换逻辑
        if (!shouldTrack(e)) return;

        targetName = e.getName();
        targetDistance = e.getDistance();
        targetBearing = e.getBearingRadians();
        lastSeenTime = getTime();

        double absBearing = getHeadingRadians() + targetBearing;

        // 2. 精密雷达锁定 (Infinity Lock)
        // 使用更具侵略性的系数快速锁定目标中心
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2.2);

        // 3. 增强：非线性/速度震荡移动 (Anti-Pattern Movement)
        updateHeroMovement(e, absBearing);

        // 4. 增强：高精度圆形预测瞄准 2.0
        predictAndFire(e, absBearing);
        
        // 更新快照
        targetEnergy = e.getEnergy();
        lastEnemyHeading = e.getHeadingRadians();
    }

    /**
     * 增强移动系统：伪随机速度震荡
     * 这会让敌人的线性瞄准和基础预测瞄准完全失效。
     */
    private void updateHeroMovement(ScannedRobotEvent e, double absBearing) {
        // === 能量跳变检测 (基础闪避) ===
        double energyDrop = targetEnergy - e.getEnergy();
        if (energyDrop > 0.09 && energyDrop <= 3.0) {
            // 距离适中且未频繁变向时，高概率变向
            if (getTime() - lastReverseTime > 6) {
                if (Math.random() < 0.90) reverseDirection();
            }
        }

        // === 随机周期性变向 (反统计瞄准) ===
        if (getTime() - lastReverseTime > (20 + Math.random() * 30) && Math.random() < 0.1) {
            reverseDirection();
        }

        // === 靠墙检测与紧急修正 ===
        if (tooCloseToWall()) {
            if (getTime() - lastReverseTime > 4) reverseDirection();
        }

        // === 伪随机速度震荡 (移动核心增强) ===
        // 不再保持 constant velocity，而是使用正弦波在 4.0 - 8.0 之间震荡
        sineWavePhase += 0.20; // 震荡频率
        double currentMaxVelocity = 6.0 + Math.sin(sineWavePhase) * 2.0;
        
        // 如果需要大幅转向，减速以获得更小转向半径
        if (Math.abs(getTurnRemainingRadians()) > 0.35) {
            setMaxVelocity(3.5);
        } else {
            setMaxVelocity(currentMaxVelocity);
        }

        // === 距离与角度控制 (切向移动 + 墙平滑) ===
        double distFactor = (targetDistance - IDEAL_DISTANCE) / IDEAL_DISTANCE;
        // 动态角度补偿：距离越远越逼近，越近越平移
        double angleOffset = (Math.PI / 2) - (limit(-0.45, distFactor, 0.45));
        
        double desiredAngle = absBearing + (moveDirection * angleOffset);
        
        // 墙壁平滑 (进阶版)
        desiredAngle = wallSmoothing(getX(), getY(), desiredAngle, moveDirection);

        // === 最优化移动执行 (背身逻辑) ===
        double turnAngle = Utils.normalRelativeAngle(desiredAngle - getHeadingRadians());
        
        // 寻找最短转向路径，决定前进还是后退
        if (Math.abs(turnAngle) > Math.PI / 2) {
            turnAngle = Utils.normalRelativeAngle(turnAngle + Math.PI);
            setBack(150); // 增加指令距离，防止短停
        } else {
            setAhead(150);
        }
        setTurnRightRadians(turnAngle);
    }


    private void predictAndFire(ScannedRobotEvent e, double absBearing) {
        // 1. 自适应火力选择
        double firePower = chooseHeroFirePower(e.getDistance(), e.getEnergy());
        double bulletSpeed = Rules.getBulletSpeed(firePower);
        
        // 2. 目标基础数据
        double enemyX = getX() + Math.sin(absBearing) * e.getDistance();
        double enemyY = getY() + Math.cos(absBearing) * e.getDistance();
        double enemyHeading = e.getHeadingRadians();
        double enemyHeadingChange = Utils.normalRelativeAngle(enemyHeading - lastEnemyHeading);
        double enemyVelocity = e.getVelocity();

        // 3. 迭代预测系统
        double predictX = enemyX, predictY = enemyY, predictH = enemyHeading;
        int deltaTime = 0;
        
        // 最多预测100步，或直到子弹到达
        while (++deltaTime < 100) { 
            double dist = Point2D.distance(getX(), getY(), predictX, predictY);
            double bulletTime = dist / bulletSpeed;
            
            if (deltaTime > bulletTime) break; // 模拟时间到达

            predictX += Math.sin(predictH) * enemyVelocity;
            predictY += Math.cos(predictH) * enemyVelocity;
            predictH += enemyHeadingChange; // 考虑角速度

            // 场界约束优化 (使用更紧凑的边界)
            if (!isInsideField(predictX, predictY, 12)) {
                predictX = limit(12, predictX, getBattleFieldWidth() - 12);
                predictY = limit(12, predictY, getBattleFieldHeight() - 12);
                break; // 敌人撞墙，预测终止
            }
        }

        // 4. 执行瞄准
        double finalAim = Math.atan2(predictX - getX(), predictY - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(finalAim - getGunHeadingRadians()));

        // 5. 动态开火阈值 (增强)
        // 距离越近，角直径越大，阈值越宽松，有利于近战泼水
        double threshold = Math.atan(16.0 / targetDistance); 
        
        // 炮管转动余量极小时才开火，保证精度
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemainingRadians()) < threshold) {
            setFire(firePower);
        }
    }

    // ===== 自适应火力管理 (增强) =====
    private double chooseHeroFirePower(double dist, double enemyEnergy) {
        // 保命模式：优先弹速快速吸血
        if (getEnergy() < 15) return 0.8; 

        double power = 0;
        if (dist < 120) power = 3.0;
        else if (dist < 250) power = 2.4;
        else if (dist < 400) power = 1.9;
        else if (dist < 600) power = 1.4;
        else power = 0.7;

        // 混战管理：多人存在时略微降低威力增加弹速
        if (getOthers() > 1) power = Math.min(power, 2.2);

        // 自身能量管理
        if (getEnergy() < 40) power = Math.min(power, 1.8);

        // 敌人残血管理：不浪费
        return limit(0.1, Math.min(power, enemyEnergy / 4.0 + 0.1), 3.0);
    }

    // ===== 智能目标选择 (Melee 优化) =====
    private boolean shouldTrack(ScannedRobotEvent e) {
        if (targetName == null || e.getName().equals(targetName)) return true;

        // 抢人头逻辑：如果新目标可以一枪收掉，且距离不远，立即切换
        double bulletDamage = Rules.getBulletDamage(chooseHeroFirePower(e.getDistance(), e.getEnergy()));
        if (e.getEnergy() < bulletDamage && e.getDistance() < 500) return true;

        // 距离优先逻辑：只有当新目标显著更近时才切换（例如0.6倍距离）
        return e.getDistance() < targetDistance * 0.6;
    }

    // ===== 工具方法 =====

    private double wallSmoothing(double x, double y, double angle, double dir) {
        double wallStick = WALL_STICK;
        // 逐步偏移角度直到避开墙壁边界
        while (!isInsideField(x + Math.sin(angle) * wallStick, y + Math.cos(angle) * wallStick, WALL_MARGIN)) {
            angle += dir * 0.08; 
        }
        return angle;
    }

    private void reverseDirection() {
        moveDirection = -moveDirection;
        lastReverseTime = getTime();
    }

    private boolean tooCloseToWall() {
        return !isInsideField(getX(), getY(), WALL_MARGIN);
    }

    private boolean isInsideField(double x, double y, double margin) {
        return x > margin && y > margin && x < getBattleFieldWidth() - margin && y < getBattleFieldHeight() - margin;
    }

    private double limit(double min, double val, double max) {
        return Math.max(min, Math.min(max, val));
    }
    
    // 基础事件处理
    public void onHitRobot(HitRobotEvent e) { reverseDirection(); }
    public void onHitWall(HitWallEvent e) { reverseDirection(); }
}