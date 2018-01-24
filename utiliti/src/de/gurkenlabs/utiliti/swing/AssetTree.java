package de.gurkenlabs.utiliti.swing;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.stream.Collectors;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import de.gurkenlabs.litiengine.Resources;
import de.gurkenlabs.litiengine.entities.Prop;
import de.gurkenlabs.litiengine.environment.tilemap.ITileset;
import de.gurkenlabs.litiengine.environment.tilemap.xml.Map;
import de.gurkenlabs.litiengine.environment.tilemap.xml.Tileset;
import de.gurkenlabs.utiliti.EditorScreen;
import de.gurkenlabs.utiliti.Program;

public class AssetTree extends JTree {
  private static final Icon ASSET_ICON = new ImageIcon(Resources.getImage("asset.png"));
  private static final Icon SPRITESHEET_ICON = new ImageIcon(Resources.getImage("spritesheet.png"));
  private static final Icon PROP_ICON = new ImageIcon(Resources.getImage("entity.png"));
  private static final Icon MISC_ICON = new ImageIcon(Resources.getImage("misc.png"));
  private static final Icon TILESET_ICON = new ImageIcon(Resources.getImage("tileset.png"));

  private final DefaultTreeModel entitiesTreeModel;
  private final DefaultMutableTreeNode nodeRoot;
  private final DefaultMutableTreeNode nodeSpritesheets;
  private final DefaultMutableTreeNode nodeSpriteProps;
  private final DefaultMutableTreeNode nodeSpriteMisc;
  private final DefaultMutableTreeNode nodeTileSets;

  public AssetTree() {
    this.nodeRoot = new DefaultMutableTreeNode(new IconTreeListItem(Resources.get("assettree_assets"), ASSET_ICON));
    this.nodeSpritesheets = new DefaultMutableTreeNode(new IconTreeListItem(Resources.get("assettree_spritesheets"), SPRITESHEET_ICON));
    this.nodeSpriteProps = new DefaultMutableTreeNode(new IconTreeListItem(Resources.get("assettree_spritesheets_props"), PROP_ICON));
    this.nodeSpriteMisc = new DefaultMutableTreeNode(new IconTreeListItem(Resources.get("assettree_spritesheets_misc"), MISC_ICON));
    this.nodeTileSets = new DefaultMutableTreeNode(new IconTreeListItem(Resources.get("assettree_spritesheets_tilesets"), TILESET_ICON));
    this.nodeSpritesheets.add(this.nodeSpriteProps);
    this.nodeSpritesheets.add(this.nodeSpriteMisc);

    this.nodeRoot.add(nodeSpritesheets);
    this.nodeRoot.add(nodeTileSets);

    this.entitiesTreeModel = new DefaultTreeModel(this.nodeRoot);

    this.setModel(this.entitiesTreeModel);
    this.setCellRenderer(new IconTreeListRenderer());
    this.setMaximumSize(new Dimension(0, 250));
    for (int i = 0; i < this.getRowCount(); i++) {
      this.expandRow(i);
    }

    this.addTreeSelectionListener(e -> {
      loadAssetsOfCurrentSelection(e.getPath());
    });
  }

  public void forceUpdate() {
    loadAssetsOfCurrentSelection(this.getSelectionPath());
  }

  @Override
  protected void setExpandedState(TreePath path, boolean state) {
    if (state) {
      super.setExpandedState(path, state);
    }
  }

  private void loadAssetsOfCurrentSelection(TreePath selectedPath) {
    final TreePath propPath = new TreePath(this.nodeSpriteProps.getPath());
    final TreePath miscPath = new TreePath(this.nodeSpriteMisc.getPath());
    final TreePath tilesetPath = new TreePath(this.nodeTileSets.getPath());

    if (selectedPath != null && this.getSelectionPath().equals(propPath)) {
      Program.getAssetPanel().loadSprites(EditorScreen.instance().getGameFile().getSpriteSheets().stream().filter(x -> x.getName() != null && x.getName().contains(Prop.SPRITESHEET_PREFIX)).collect(Collectors.toList()));
    } else if (selectedPath != null && getSelectionPath().equals(miscPath)) {
      Program.getAssetPanel().loadSprites(EditorScreen.instance().getGameFile().getSpriteSheets().stream().filter(x -> x.getName() != null && !x.getName().contains(Prop.SPRITESHEET_PREFIX)).collect(Collectors.toList()));
    } else if (selectedPath != null && getSelectionPath().equals(tilesetPath)) {
      ArrayList<Tileset> allTilesets = new ArrayList<>();
      allTilesets.addAll(EditorScreen.instance().getGameFile().getTilesets().stream().filter(x -> x.getName() != null).collect(Collectors.toList()));

      for (Map map : EditorScreen.instance().getGameFile().getMaps()) {
        for (ITileset tileset : map.getTilesets()) {
          if (allTilesets.stream().anyMatch(x -> x.getName() != null && x.getName().equals(tileset.getName()))) {
            continue;
          }

          allTilesets.add((Tileset) tileset);
        }
      }

      Program.getAssetPanel().loadTilesets(allTilesets);
    }
  }
}