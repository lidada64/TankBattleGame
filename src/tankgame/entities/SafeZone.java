package tankgame.entities;

// ---------- 神佑之地 ----------
public class SafeZone {
    public double x, y, width, height;
    public boolean active = false;
    public double remaining = 0;
    public SafeZone(double x, double y, double w, double h) {
        this.x = x; this.y = y; this.width = w; this.height = h;
    }
    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}
