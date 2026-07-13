package GraphServer;

public class MapShapeTest {
    public static void main(String[] args) {
        MapShape circle = MapShape.circle(100, 100, 20);
        MapShape rectangle = MapShape.rectangle(200, 80, 40, 30);
        if (!circle.intersects(100, 100, 7) || !rectangle.intersects(220, 95, 7)) {
            throw new AssertionError("shape intersection failed");
        }
        if (circle.intersects(150, 100, 7) || rectangle.intersects(100, 100, 7)) {
            throw new AssertionError("shape intersection false positive");
        }
        if (circle.encode().split(",").length != 5 || rectangle.encode().split(",").length != 5) {
            throw new AssertionError("shape encoding is not fixed-width");
        }
        System.out.println("map-shape-check: PASS");
    }
}
