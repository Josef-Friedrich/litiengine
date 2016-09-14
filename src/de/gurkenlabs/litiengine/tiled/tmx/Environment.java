/***************************************************************
 * Copyright (c) 2014 - 2015 , gurkenlabs, All rights reserved *
 ***************************************************************/
package de.gurkenlabs.litiengine.tiled.tmx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.gurkenlabs.configuration.Quality;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.attributes.AttributeModifier;
import de.gurkenlabs.litiengine.attributes.Modification;
import de.gurkenlabs.litiengine.entities.DecorMob;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.entities.IEntity;
import de.gurkenlabs.litiengine.entities.IMovableCombatEntity;
import de.gurkenlabs.litiengine.entities.IMovableEntity;
import de.gurkenlabs.litiengine.entities.Material;
import de.gurkenlabs.litiengine.entities.Prop;
import de.gurkenlabs.litiengine.entities.PropState;
import de.gurkenlabs.litiengine.graphics.AmbientLight;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.ImageCache;
import de.gurkenlabs.litiengine.graphics.LightSource;
import de.gurkenlabs.litiengine.graphics.RenderEngine;
import de.gurkenlabs.litiengine.graphics.animation.PropAnimationController;
import de.gurkenlabs.litiengine.graphics.particles.Emitter;
import de.gurkenlabs.litiengine.graphics.particles.emitters.FireEmitter;
import de.gurkenlabs.litiengine.graphics.particles.emitters.RainEmitter;
import de.gurkenlabs.litiengine.graphics.particles.emitters.ShimmerEmitter;
import de.gurkenlabs.litiengine.graphics.particles.emitters.SnowEmitter;
import de.gurkenlabs.litiengine.graphics.particles.emitters.Weather;
import de.gurkenlabs.tiled.tmx.IMap;
import de.gurkenlabs.tiled.tmx.IMapLoader;
import de.gurkenlabs.tiled.tmx.IMapObject;
import de.gurkenlabs.tiled.tmx.IMapObjectLayer;
import de.gurkenlabs.tiled.tmx.TmxMapLoader;
import de.gurkenlabs.tiled.tmx.utilities.MapUtilities;
import de.gurkenlabs.util.geom.GeometricUtilities;
import de.gurkenlabs.util.image.ImageProcessing;

/**
 * The Class MapContainerBase.
 */
public class Environment implements IEnvironment {
  public static String MAP_PROPERTY_AMBIENTALPHA = "AMBIENTALPHA";
  public static String MAP_PROPERTY_AMBIENTLIGHT = "AMBIENTLIGHT";

  private static int localIdSequence = 0;
  private static int mapIdSequence;
  private final List<MapLocation> spawnPoints;
  private final List<Consumer<Graphics2D>> mapRenderedConsumer;
  private final List<Consumer<Graphics2D>> entitiesRenderedConsumer;
  private final List<Consumer<Graphics2D>> overlayRenderedConsumer;

  private final List<IRenderable> groundRenderable;
  private final List<IRenderable> overlayRenderable;

  private IMap map;

  private final Map<Integer, ICombatEntity> combatEntities;
  private final List<Prop> props;
  private final Map<Integer, IMovableEntity> movableEntities;
  private final List<Emitter> groundEmitters;
  private final List<Emitter> emitters;
  private final List<Emitter> overlayEmitters;

  private final List<LightSource> lightSources;
  private Image staticShadowImage;
  private AmbientLight ambientLight;

  private Weather weather;

  private Environment() {
    this.combatEntities = new ConcurrentHashMap<>();
    this.movableEntities = new ConcurrentHashMap<>();
    this.lightSources = new CopyOnWriteArrayList<>();
    this.props = new CopyOnWriteArrayList<>();
    this.emitters = new CopyOnWriteArrayList<>();
    this.groundEmitters = new CopyOnWriteArrayList<>();
    this.overlayEmitters = new CopyOnWriteArrayList<>();
    this.mapRenderedConsumer = new CopyOnWriteArrayList<>();
    this.entitiesRenderedConsumer = new CopyOnWriteArrayList<>();
    this.overlayRenderedConsumer = new CopyOnWriteArrayList<>();
    this.spawnPoints = new CopyOnWriteArrayList<>();

    this.groundRenderable = new CopyOnWriteArrayList<>();
    this.overlayRenderable = new CopyOnWriteArrayList<>();
  }

  /**
   * Instantiates a new map container base.
   *
   * @param map
   *          the map
   */
  public Environment(final String mapPath) {
    this();
    final IMapLoader tmxLoader = new TmxMapLoader();
    this.map = tmxLoader.LoadMap(mapPath);
    mapIdSequence = MapUtilities.getMaxMapId(this.getMap());
  }

  public Environment(IMap map) {
    this();
    this.map = map;
    mapIdSequence = MapUtilities.getMaxMapId(this.getMap());
  }

  @Override
  public void add(final int mapId, final IMovableCombatEntity entity) {
    this.addCombatEntity(mapId, entity);
    this.addMovableEntity(mapId, entity);
  }

  @Override
  public void add(final IRenderable renderable, final RenderType type) {
    switch (type) {
    case GROUND:
      this.getGroundRenderable().add(renderable);
      break;
    case OVERLAY:
      this.getOverlayRenderable().add(renderable);
      break;

    default:
      break;
    }
  }

  @Override
  public void addAmbientLight() {
    final String alphaProp = this.getMap().getCustomProperty(MAP_PROPERTY_AMBIENTALPHA);
    final String colorProp = this.getMap().getCustomProperty(MAP_PROPERTY_AMBIENTLIGHT);
    int ambientAlpha = 0;
    Color ambientColor = Color.WHITE;
    try {
      ambientAlpha = Integer.parseInt(alphaProp);
      ambientColor = Color.decode(colorProp);
    } catch (final NumberFormatException e) {
    }

    this.ambientLight = new AmbientLight(this, ambientColor, ambientAlpha);
  }

  @Override
  public void addCollisionBox(final IMapObject mapObject) {
    if (mapObject.getType().equals(MapObjectTypes.COLLISIONBOX)) {
      Game.getPhysicsEngine().add(mapObject.getCollisionBox());
    }
  }

  @Override
  public void addCombatEntity(final int mapId, final ICombatEntity entity) {
    this.combatEntities.put(mapId, entity);
  }

  @Override
  public void addDecorMob(final IMapObject mapObject) {
    if (!mapObject.getType().equalsIgnoreCase(MapObjectTypes.DECORMOB)) {
      return;
    }
    final DecorMob mob = new DecorMob(mapObject.getLocation(), mapObject.getCustomProperty(MapObjectProperties.MOBTYPE));
    mob.setCollision(Boolean.valueOf(mapObject.getCustomProperty(MapObjectProperties.COLLISION)));
    if (mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXWIDTHFACTOR) != null) {
      mob.setCollisionBoxWidthFactor(Float.parseFloat(mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXWIDTHFACTOR)));
    }
    if (mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXHEIGHTFACTOR) != null) {
      mob.setCollisionBoxHeightFactor(Float.parseFloat(mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXHEIGHTFACTOR)));
    }
    mob.setSize(mapObject.getDimension().width, mapObject.getDimension().height);
    this.addMovableEntity(mapObject.getId(), mob);
    if (mob.hasCollision()) {
      Game.getPhysicsEngine().add(mob);
    }
  }

  @Override
  public void addEmitter(final IMapObject mapObject) {
    if (mapObject.getType() != MapObjectTypes.EMITTER) {
      return;
    }
    Emitter emitter = null;
    switch (mapObject.getCustomProperty(MapObjectProperties.EMITTERTYPE)) {
    case "fire":
      emitter = new FireEmitter(mapObject.getLocation().x, mapObject.getLocation().y);
      this.getEmitters().add(emitter);
      final LightSource light = new LightSource(this, new Point2D.Double(emitter.getOrigin().getX(), emitter.getOrigin().getY()), 32, 30, 50, Color.ORANGE, LightSource.ELLIPSE);
      this.getLightSources().add(light);
      break;
    case "shimmer":
      emitter = new ShimmerEmitter(mapObject.getLocation().x, mapObject.getLocation().y);
      this.getOverlayEmitters().add(emitter);
      break;
    }

    if (emitter != null) {
      emitter.setMapId(mapObject.getId());
    }
  }

  @Override
  public void addLightSource(final IMapObject mapObject) {
    if (!mapObject.getType().equals(MapObjectTypes.LIGHTSOURCE)) {
      return;
    }
    final String propBrightness = mapObject.getCustomProperty(MapObjectProperties.LIGHTBRIGHTNESS);
    final String propColor = mapObject.getCustomProperty(MapObjectProperties.LIGHTCOLOR);
    if (propBrightness == null || propBrightness.isEmpty() || propColor == null || propColor.isEmpty()) {
      return;
    }

    final int brightness = Integer.parseInt(propBrightness);
    final Color color = Color.decode(propColor);

    String lightType;
    switch (mapObject.getCustomProperty(MapObjectProperties.LIGHTSHAPE)) {
    case LightSource.ELLIPSE:
      lightType = LightSource.ELLIPSE;
      break;
    case LightSource.RECTANGLE:
      lightType = LightSource.RECTANGLE;
      break;
    default:
      lightType = LightSource.ELLIPSE;
    }
    final LightSource light = new LightSource(this, new Point(mapObject.getLocation()), (int) mapObject.getDimension().getWidth(), (int) mapObject.getDimension().getHeight(), brightness, new Color(color.getRed(), color.getGreen(), color.getBlue(), brightness), lightType);
    this.getLightSources().add(light);
  }

  @Override
  public void addMapObject(final IMapObject mapObject) {
    this.addCollisionBox(mapObject);
    this.addLightSource(mapObject);
    this.addSpawnpoint(mapObject);
    this.addProp(mapObject);
    this.addDecorMob(mapObject);
    this.addMob(mapObject);
  }

  @Override
  public void addMob(final IMapObject mapObject) {
    // TODO Auto-generated method stub

  }

  @Override
  public void addMovableEntity(final int mapId, final IMovableEntity entity) {
    this.movableEntities.put(mapId, entity);
  }

  @Override
  public void addProp(final IMapObject mapObject) {
    if (!mapObject.getType().equalsIgnoreCase(MapObjectTypes.PROP)) {
      return;
    }

    // set map properties by map object
    Material material = mapObject.getCustomProperty(MapObjectProperties.MATERIAL) == null ? Material.UNDEFINED : Material.valueOf(mapObject.getCustomProperty(MapObjectProperties.MATERIAL));
    final Prop prop = new Prop(mapObject.getLocation(), mapObject.getCustomProperty(MapObjectProperties.SPRITESHEETNAME), material);
    prop.setMapId(mapObject.getId());
    if (mapObject.getCustomProperty(MapObjectProperties.INDESTRUCTIBLE) != null && !mapObject.getCustomProperty(MapObjectProperties.INDESTRUCTIBLE).isEmpty()) {
      prop.setIndestructible(Boolean.valueOf(mapObject.getCustomProperty(MapObjectProperties.INDESTRUCTIBLE)));
    }

    if (mapObject.getCustomProperty(MapObjectProperties.HEALTH) != null) {
      prop.getAttributes().getHealth().addMaxModifier(new AttributeModifier<>(Modification.Set, Integer.parseInt(mapObject.getCustomProperty(MapObjectProperties.HEALTH))));
    }

    if (mapObject.getCustomProperty(MapObjectProperties.COLLISION) != null) {
      prop.setCollision(Boolean.valueOf(mapObject.getCustomProperty(MapObjectProperties.COLLISION)));
    }

    if (mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXWIDTHFACTOR) != null) {
      prop.setCollisionBoxWidthFactor(Float.parseFloat(mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXWIDTHFACTOR)));
    }
    if (mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXHEIGHTFACTOR) != null) {
      prop.setCollisionBoxHeightFactor(Float.parseFloat(mapObject.getCustomProperty(MapObjectProperties.COLLISIONBOXHEIGHTFACTOR)));
    }
    prop.setSize(mapObject.getDimension().width, mapObject.getDimension().height);

    if (mapObject.getCustomProperty(MapObjectProperties.TEAM) != null) {
      prop.setTeam(Integer.parseInt(mapObject.getCustomProperty(MapObjectProperties.TEAM)));
    }

    this.add(prop);
  }

  @Override
  public void add(Prop prop) {
    if (!prop.isIndestructible()) {
      prop.getAnimationController().add(PropAnimationController.createAnimation(prop, PropState.DAMAGED));
      prop.getAnimationController().add(PropAnimationController.createAnimation(prop, PropState.DESTROYED));
    }

    this.getProps().add(prop);
    this.addCombatEntity(prop.getMapId(), prop);
    if (prop.hasCollision()) {
      Game.getPhysicsEngine().add(prop);
    }
  }

  @Override
  public void addSpawnpoint(final IMapObject mapObject) {
    if (!mapObject.getType().equals(MapObjectTypes.SPAWNPOINT)) {
      return;
    }

    this.getSpawnPoints().add(new MapLocation(mapObject.getId(), new Point(mapObject.getLocation())));
  }

  private void addStaticShadows() {
    // build map specific cache key, respecting the lights and color
    final StringBuilder sb = new StringBuilder();
    for (final IMapObject col : this.getCollisionBoxes()) {
      sb.append(col.getId() + "_" + col.getCollisionBox());
    }

    final String cacheKey = "STATICSHADOWS_" + this.getMap().getName().replaceAll("[\\/]", "-") + "_" + sb.toString().hashCode();
    final Image cachedImg = ImageCache.IMAGES.get(cacheKey);
    if (cachedImg != null) {
      this.staticShadowImage = cachedImg;
      return;
    }

    final int shadowOffset = 10;
    final List<Path2D> staticShadows = new ArrayList<>();
    // check if the collision boxes have shadows. if so, determine which
    // shadow is needed, create the shape and add it to the
    // list of static shadows.
    for (final IMapObject col : this.getCollisionBoxes()) {
      final double shadowX = col.getCollisionBox().getX();
      final double shadowY = col.getCollisionBox().getY();
      final double shadowWidth = col.getCollisionBox().getWidth();
      final double shadowHeight = col.getCollisionBox().getHeight();

      final String shadowType = col.getCustomProperty(MapObjectProperties.SHADOWTYPE);
      if (shadowType == null) {
        continue;
      }
      final String down = "DOWN";
      final String downLeft = "DOWNLEFT";
      final String downRight = "DOWNRIGHT";
      final String left = "LEFT";
      final String leftDown = "LEFTDOWN";
      final String leftRight = "LEFTRIGHT";
      final String rightLeft = "RIGHTLEFT";
      final String right = "RIGHT";
      final String rightDown = "RIGHTDOWN";
      final String noOffset = "NOOFFSET";

      final Path2D parallelogram = new Path2D.Double();
      if (shadowType.equals(down)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight + shadowOffset);
        parallelogram.closePath();
      } else if (shadowType.equals(downLeft)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth - shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight + shadowOffset);
        parallelogram.closePath();
      } else if (shadowType.equals(downRight)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth + shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight + shadowOffset);
        parallelogram.closePath();
      } else if (shadowType.equals(left)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth - shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX - shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight);
        parallelogram.closePath();
      } else if (shadowType.equals(leftDown)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX - shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight);
        parallelogram.closePath();
      } else if (shadowType.equals(leftRight)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth + shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX - shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight);
        parallelogram.closePath();
      } else if (shadowType.equals(rightLeft)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth - shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX + shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight);
        parallelogram.closePath();
      } else if (shadowType.equals(right)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth + shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX + shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight);
        parallelogram.closePath();
      } else if (shadowType.equals(rightDown)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX + shadowOffset / 2, shadowY + shadowHeight + shadowOffset);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight);
        parallelogram.closePath();
      } else if (shadowType.equals(noOffset)) {
        parallelogram.moveTo(shadowX, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY);
        parallelogram.lineTo(shadowX + shadowWidth, shadowY + shadowHeight);
        parallelogram.lineTo(shadowX, shadowY + shadowHeight);
        parallelogram.closePath();
      }

      if (parallelogram.getWindingRule() != 0) {
        staticShadows.add(parallelogram);
      }
    }

    final BufferedImage img = ImageProcessing.getCompatibleImage((int) this.getMap().getSizeInPixels().getWidth(), (int) this.getMap().getSizeInPixels().getHeight());
    final Graphics2D g = img.createGraphics();
    g.setColor(new Color(0, 0, 0, 75));

    final Area ar = new Area();
    for (final Path2D staticShadow : staticShadows) {
      final Area staticShadowArea = new Area(staticShadow);
      for (final LightSource light : this.getLightSources()) {
        if (light.getDimensionCenter().getY() > staticShadow.getBounds2D().getMaxY() || staticShadow.getBounds2D().contains(light.getDimensionCenter())) {
          staticShadowArea.subtract(new Area(light.getLightShape()));
        }
      }
      ar.add(staticShadowArea);

    }

    g.fill(ar);
    g.dispose();

    this.staticShadowImage = img;
    ImageCache.IMAGES.putPersistent(cacheKey, img);
  }

  @Override
  public void clear() {
    this.dispose(this.combatEntities.values());
    this.dispose(this.movableEntities.values());
    this.dispose(this.getLightSources());
    this.dispose(this.getGroundEmitters());
    this.dispose(this.getEmitters());
    this.dispose(this.getOverlayEmitters());
    this.dispose(this.getProps());
    this.spawnPoints.clear();
    this.combatEntities.clear();
    this.movableEntities.clear();
    this.lightSources.clear();
    this.getGroundEmitters().clear();
    this.getEmitters().clear();
    this.getOverlayEmitters().clear();
    this.getProps().clear();
  }

  private void dispose(final Collection<? extends IEntity> entities) {
    for (final IEntity entity : entities) {
      if (entity instanceof IUpdateable) {
        Game.getLoop().unregisterFromUpdate((IUpdateable) entity);
      }

      if (entity.getAnimationController() != null) {
        entity.getAnimationController().dispose();
      }

      if (entity instanceof IMovableEntity) {
        if (((IMovableEntity) entity).getMovementController() != null) {
          Game.getLoop().unregisterFromUpdate(((IMovableEntity) entity).getMovementController());
        }
      }
    }
  }

  @Override
  public List<ICombatEntity> findCombatEntities(final Shape shape) {
    return this.findCombatEntities(shape, (entity) -> true);
  }

  @Override
  public List<ICombatEntity> findCombatEntities(final Shape shape, final Predicate<ICombatEntity> condition) {
    final ArrayList<ICombatEntity> entities = new ArrayList<>();
    if (shape == null) {
      return entities;
    }

    // for rectangle we can just use the intersects method
    if (shape instanceof Rectangle2D) {
      final Rectangle2D rect = (Rectangle2D) shape;
      for (final ICombatEntity combatEntity : this.getCombatEntities().stream().filter(condition).collect(Collectors.toList())) {
        if (combatEntity.getHitBox().intersects(rect)) {
          entities.add(combatEntity);
        }
      }

      return entities;
    }

    // for other shapes, we check if the shape's bounds intersect the hitbox and
    // if so, we then check if the actual shape intersects the hitbox
    for (final ICombatEntity combatEntity : this.getCombatEntities().stream().filter(condition).collect(Collectors.toList())) {
      if (combatEntity.getHitBox().intersects(shape.getBounds())) {
        if (GeometricUtilities.shapeIntersects(combatEntity.getHitBox(), shape)) {
          entities.add(combatEntity);
        }
      }
    }

    return entities;
  }

  private List<IEntity> getAllEntities() {
    final List<IEntity> entities = new ArrayList<>();
    entities.addAll(this.getCombatEntities());
    entities.addAll(this.getMovableEntities());
    entities.addAll(this.getEmitters());
    entities.addAll(this.getProps());
    return entities;
  }

  @Override
  public AmbientLight getAmbientLight() {
    return this.ambientLight;
  }

  @Override
  public List<IMapObject> getCollisionBoxes() {
    final List<IMapObject> collisionBoxes = new ArrayList<>();
    for (final IMapObjectLayer shapeLayer : this.getMap().getMapObjectLayers()) {
      for (final IMapObject obj : shapeLayer.getMapObjects()) {
        if (obj.getType() == null || obj.getType().isEmpty()) {
          continue;
        }

        if (obj.getType().equals(MapObjectTypes.COLLISIONBOX)) {
          collisionBoxes.add(obj);
        }
      }
    }

    return collisionBoxes;
  }

  @Override
  public Collection<ICombatEntity> getCombatEntities() {
    return this.combatEntities.values();
  }

  @Override
  public ICombatEntity getCombatEntity(final int mapId) {
    if (this.combatEntities.containsKey(mapId)) {
      return this.combatEntities.get(mapId);
    }

    return null;
  }

  @Override
  public List<Emitter> getEmitters() {
    return this.emitters;
  }

  @Override
  public List<Emitter> getGroundEmitters() {
    return this.groundEmitters;
  }

  @Override
  public List<IRenderable> getGroundRenderable() {
    return this.groundRenderable;
  }

  @Override
  public List<LightSource> getLightSources() {
    return this.lightSources;
  }

  /**
   * Negative map ids are only used locally.
   */
  @Override
  public synchronized int getLocalMapId() {
    return --localIdSequence;
  }

  /*
   * (non-Javadoc)
   *
   * @see de.gurkenlabs.liti.map.IMapContainer#getMap()
   */
  @Override
  public IMap getMap() {
    return this.map;
  }

  @Override
  public synchronized int getMapId() {
    return ++mapIdSequence;
  }

  @Override
  public Collection<IMovableEntity> getMovableEntities() {
    return this.movableEntities.values();
  }

  @Override
  public IMovableEntity getMovableEntity(final int mapId) {
    if (this.movableEntities.containsKey(mapId)) {
      return this.movableEntities.get(mapId);
    }

    return null;
  }

  @Override
  public List<Emitter> getOverlayEmitters() {
    return this.overlayEmitters;
  }

  @Override
  public List<IRenderable> getOverlayRenderable() {
    return this.overlayRenderable;
  }

  @Override
  public List<Prop> getProps() {
    return this.props;
  }

  @Override
  public List<MapLocation> getSpawnPoints() {
    return this.spawnPoints;
  }

  public Image getStaticShadowImage() {
    return this.staticShadowImage;
  }

  @Override
  public WeatherType getWeather() {
    return this.weather == null ? WeatherType.Clear : this.weather.getType();
  }

  protected Weather getWeatherEmitter() {
    return this.weather;
  }

  private void informConsumers(final Graphics2D g, final List<Consumer<Graphics2D>> consumers) {
    for (final Consumer<Graphics2D> consumer : consumers) {
      consumer.accept(g);
    }
  }

  @Override
  public void init() {
    this.loadMapObjects();
    this.addStaticShadows();
    this.addAmbientLight();
  }

  private void loadMapObjects() {
    for (final IMapObjectLayer layer : this.getMap().getMapObjectLayers()) {
      for (final IMapObject mapObject : layer.getMapObjects()) {
        if (mapObject.getType() == null || mapObject.getType().isEmpty()) {
          continue;
        }

        this.addMapObject(mapObject);
      }
    }
  }

  @Override
  public void onEntitiesRendered(final Consumer<Graphics2D> consumer) {
    this.entitiesRenderedConsumer.add(consumer);
  }

  @Override
  public void onMapRendered(final Consumer<Graphics2D> consumer) {
    this.mapRenderedConsumer.add(consumer);
  }

  @Override
  public void onOverlayRendered(final Consumer<Graphics2D> consumer) {
    this.overlayRenderedConsumer.add(consumer);
  }

  @Override
  public void remove(final int mapId) {
    if (this.movableEntities.containsKey(mapId)) {
      this.movableEntities.remove(mapId);
    }

    if (this.combatEntities.containsKey(mapId)) {
      this.combatEntities.remove(mapId);
    }
  }

  @Override
  public void remove(final IRenderable renderable) {
    if (this.getGroundRenderable().contains(renderable)) {
      this.getGroundRenderable().remove(renderable);
    }

    if (this.getOverlayRenderable().contains(renderable)) {
      this.getOverlayRenderable().remove(renderable);
    }
  }

  @Override
  public void render(final Graphics2D g) {
    g.scale(Game.getInfo().getRenderScale(), Game.getInfo().getRenderScale());

    Game.getRenderEngine().renderMap(g, this.getMap());
    this.informConsumers(g, this.mapRenderedConsumer);

    for (final IRenderable rend : this.getGroundRenderable()) {
      rend.render(g);
    }

    Game.getRenderEngine().renderEntities(g, this.getGroundEmitters());
    if (Game.getConfiguration().GRAPHICS.getGraphicQuality() == Quality.VERYHIGH) {
      Game.getRenderEngine().renderEntities(g, this.getLightSources());
    }

    Game.getRenderEngine().renderEntities(g, this.getAllEntities());
    this.informConsumers(g, this.entitiesRenderedConsumer);

    Game.getRenderEngine().renderEntities(g, this.getOverlayEmitters());

    Game.getRenderEngine().renderLayers(g, this.getMap(), RenderType.OVERLAY);

    // render static shadows
    RenderEngine.renderImage(g, this.getStaticShadowImage(), Game.getScreenManager().getCamera().getViewPortLocation(0, 0));

    if (this.getAmbientLight() != null && this.getAmbientLight().getAlpha() != 0) {
      RenderEngine.renderImage(g, this.getAmbientLight().getImage(), Game.getScreenManager().getCamera().getViewPortLocation(0, 0));
    }

    if (this.weather != null) {
      this.weather.render(g);
    }

    for (final IRenderable rend : this.getOverlayRenderable()) {
      rend.render(g);
    }

    this.informConsumers(g, this.overlayRenderedConsumer);

    g.scale(1.0 / Game.getInfo().getRenderScale(), 1.0 / Game.getInfo().getRenderScale());
  }

  @Override
  public void setWeather(final WeatherType weather) {
    switch (weather) {
    case Rain:
      this.weather = new RainEmitter();
      break;
    case Snow:
      this.weather = new SnowEmitter();
      break;
    case Clear:
    default:
      this.weather = null;
      break;
    }

    if (weather != null) {
      this.weather.activate(Game.getLoop());
    }
  }

}