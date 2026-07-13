package GraphServer;

/** A validated primitive used by both the room server and the client map. */
public final class MapShape {

    public static final int CIRCLE = 0;
    public static final int RECTANGLE = 1;
    public static final int MAX_SHAPES = 128;

    private final int type;
    private final int x;
    private final int y;
    private final int a;
    private final int b;

    private MapShape(int type, int x, int y, int a, int b) {
        if(type != CIRCLE && type != RECTANGLE) {
            throw new IllegalArgumentException("Unknown map shape type");
        }
        if(a <= 0 || (type == RECTANGLE && b <= 0)) {
            throw new IllegalArgumentException("Map shape dimensions must be positive");
        }
        this.type = type;
        this.x = x;
        this.y = y;
        this.a = a;
        this.b = b;
        if(!isWithinMap()) {
            throw new IllegalArgumentException("Map shape is outside the map");
        }
    }

    public static MapShape circle(int x, int y, int radius) {
        return new MapShape(CIRCLE, x, y, radius, 0);
    }

    public static MapShape rectangle(int x, int y, int width, int height) {
        return new MapShape(RECTANGLE, x, y, width, height);
    }

    public static MapShape decode(int type, int x, int y, int a, int b) {
        return new MapShape(type, x, y, a, b);
    }

    public int getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getA() {
        return a;
    }

    public int getB() {
        return b;
    }

    public boolean isWithinMap() {
        if(type == CIRCLE) {
            return x-a >= 0 && x+a < Constants.PLANE_LENGTH
                    && y-a >= 0 && y+a < Constants.PLANE_HEIGHT;
        }
        return x >= 0 && y >= 0 && x+a <= Constants.PLANE_LENGTH && y+b <= Constants.PLANE_HEIGHT;
    }

    public boolean intersects(int centerX, int centerY, int radius) {
        if(type == CIRCLE) {
            long dx = centerX - x;
            long dy = centerY - y;
            long distance = (long) (a + radius) * (a + radius);
            return dx*dx + dy*dy <= distance;
        }

        int closestX = Math.max(x, Math.min(centerX, x+a));
        int closestY = Math.max(y, Math.min(centerY, y+b));
        long dx = centerX - closestX;
        long dy = centerY - closestY;
        return dx*dx + dy*dy <= (long) radius*radius;
    }

    public String encode() {
        return type+","+x+","+y+","+a+","+b;
    }
}
