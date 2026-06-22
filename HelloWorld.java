package mybots;
import robocode.*;
public class HelloWorld extends Robot {

    // 主循环：机器人持续执行的行为
    public void run() {
        while (true) {
            ahead(100);      // 向前移动100像素
            turnGunRight(90); // 炮塔右转90度
        }
    }
    // 雷达扫描到敌人时触发：瞄准并开火
    public void onScannedRobot(ScannedRobotEvent e) {
        fire(1);  // 发射火力等级1的子弹
    }

    // 被子弹击中时触发：反向移动闪避
    public void onHitByBullet(HitByBulletEvent e) {
        turnLeft(90);  // 左转90度躲避
        back(50);      // 后退50像素拉开距离
    }
}
