package de.gurkenlabs.litiengine.graphics.emitters.particles;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class RectangleParticle extends ShapeParticle {

  public RectangleParticle(final float width, final float height, final Color color) {
    super(width, height, color);
  }

  @Override
  protected Shape getShape(Point2D emitterOrigin) {
    return new Rectangle2D.Float(this.getAbsoluteX(emitterOrigin), this.getAbsoluteY(emitterOrigin), this.getWidth(), this.getHeight());
  }
}