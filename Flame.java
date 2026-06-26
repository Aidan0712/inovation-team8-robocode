package mybots;

import java.awt.Color;
import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;

public class Flame extends AdvancedRobot {

    static final Color BODY_COLOR = new Color(255, 69, 0);
    static final Color GUN_COLOR = new Color(255, 140, 0);
    static final Color RADAR_COLOR = new Color(255, 215, 0);

    private final Enemy enemy = new Enemy();

    private int moveDirection = 1;
    private double desiredDistance = 250;

    private double lastEnemyEnergy = 100;
    private double bulletPower = 2.0;

    private static final double WALL_MARGIN = 80;
    private int escapeTicks = 0;

    class Enemy {
        String name = null;
        double x;
        double y;
        double heading;
        double velocity;
        double bearing;
        double distance;
        long lastSeen = 0;

        void update(ScannedRobotEvent e) {
            name = e.getName();
            heading = e.getHeadingRadians();
            velocity = e.getVelocity();
            bearing = e.getBearingRadians();
            distance = e.getDistance();

            double absBearing = getHeadingRadians() + bearing;
            x = getX() + Math.sin(absBearing) * distance;
            y = getY() + Math.cos(absBearing) * distance;
            lastSeen = getTime();
        }
    }

    public void run() {
        setColors(BODY_COLOR, GUN_COLOR, RADAR_COLOR);
        setAdjustRadarForRobotTurn(true);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            if (enemy.name != null && getTime() - enemy.lastSeen > 25) {
                enemy.name = null;
            }

            if (enemy.name == null) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
                setTurnGunRightRadians(0.5);
                setTurnRight(20);
                setAhead(120);
            } else {
                radarLock();
                dodgeBullet();
                move();
                aimAndFire();
            }

            execute();
        }
    }

    private boolean wallDanger(double angle) {
        double x = getX();
        double y = getY();
        double testX = x + Math.sin(angle) * 120;
        double testY = y + Math.cos(angle) * 120;
        double bw = getBattleFieldWidth();
        double bh = getBattleFieldHeight();

        return testX < WALL_MARGIN
            || testX > bw - WALL_MARGIN
            || testY < WALL_MARGIN
            || testY > bh - WALL_MARGIN;
    }

    private void radarLock() {
        double absBearing = Math.atan2(enemy.x - getX(), enemy.y - getY());
        double radarTurn = normalize(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2);
    }

    private void move() {
        double x = getX();
        double y = getY();
        double bw = getBattleFieldWidth();
        double bh = getBattleFieldHeight();

        if (escapeTicks > 0) {
            escapeTicks--;
            setTurnRight(180);
            setAhead(200);
            return;
        }

        if (enemy.distance < desiredDistance - 40) {
            moveDirection = -1;
        }
        if (enemy.distance > desiredDistance + 40) {
            moveDirection = 1;
        }

        double angleToEnemy = Math.atan2(enemy.x - x, enemy.y - y);
        double moveAngle = angleToEnemy + (moveDirection > 0 ? Math.PI / 2 : -Math.PI / 2);

        if (wallDanger(moveAngle)) {
            moveDirection = -moveDirection;
            moveAngle = angleToEnemy + (moveDirection > 0 ? Math.PI / 2 : -Math.PI / 2);
        }

        if (x < WALL_MARGIN && y < WALL_MARGIN) {
            moveAngle = 0;
        } else if (x < WALL_MARGIN && y > bh - WALL_MARGIN) {
            moveAngle = -Math.PI / 2;
        } else if (x > bw - WALL_MARGIN && y < WALL_MARGIN) {
            moveAngle = Math.PI / 2;
        } else if (x > bw - WALL_MARGIN && y > bh - WALL_MARGIN) {
            moveAngle = Math.PI;
        }

        double turn = normalize(moveAngle - getHeadingRadians());
        setTurnRightRadians(turn * 0.9);
        setAhead(140);
    }

    private void dodgeBullet() {
        double energyDrop = lastEnemyEnergy - enemyEnergy();

        if (energyDrop > 0 && energyDrop <= 3) {
            moveDirection = -moveDirection;
            setAhead(160 * moveDirection);
        }

        lastEnemyEnergy = enemyEnergy();
    }

    private double enemyEnergy() {
        return getEnergy();
    }

    private void aimAndFire() {
        if (getGunHeat() > 0) {
            return;
        }

        if (enemy.distance > 400) {
            bulletPower = 1.5;
        } else if (enemy.distance < 150) {
            bulletPower = 3.0;
        } else {
            bulletPower = 2.0;
        }

        double bulletSpeed = 20 - 3 * bulletPower;
        double time = enemy.distance / bulletSpeed;

        double futureX = enemy.x + Math.sin(enemy.heading) * enemy.velocity * time;
        double futureY = enemy.y + Math.cos(enemy.heading) * enemy.velocity * time;

        futureX = Math.max(18, Math.min(getBattleFieldWidth() - 18, futureX));
        futureY = Math.max(18, Math.min(getBattleFieldHeight() - 18, futureY));

        double absBearing = Math.atan2(futureX - getX(), futureY - getY());
        double gunTurn = normalize(absBearing - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        if (Math.abs(gunTurn) < 0.12) {
            setFire(bulletPower);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        enemy.update(e);
        radarLock();
    }

    public void onHitWall(HitWallEvent e) {
        escapeTicks = 6;
        moveDirection = -moveDirection;
        setTurnRight(160);
        setAhead(-180);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        moveDirection = -moveDirection;
    }

    public void onWin(WinEvent e) {
        for (int i = 0; i < 3; i++) {
            setTurnRight(90);
            setAhead(100);
            execute();
        }
    }

    private double normalize(double angle) {
        while (angle > Math.PI) {
            angle -= 2 * Math.PI;
        }
        while (angle < -Math.PI) {
            angle += 2 * Math.PI;
        }
        return angle;
    }
}
