package de.gurkenlabs.litiengine.graphics.emitters.particles;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public class LeftLineParticle extends ShapeParticle {

  public LeftLineParticle(final float width, final float height, final Color color) {
    super(width, height, color);
  }

  @Override
  protected Shape getShape(Point2D emitterOrigin) {
    float x = this.getAbsoluteX(emitterOrigin);
    float y = this.getAbsoluteY(emitterOrigin);
    return new Line2D.Double(x + this.getWidth(), y, x, y + this.getHeight());
  }
}
