package tankgame.entities;

public class Wall {
    public int x, y, width, height;
    public Wall(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    public boolean intersects(double rx, double ry, double rw, double rh) {
        return rx < this.x + this.width && rx + rw > this.x && ry < this.y + this.height && ry + rh > this.y;
    }
}
